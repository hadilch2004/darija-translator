"""Tests for darija_client, run against a throw-away fake of the REST service.

    python3 -m unittest -v
"""
import base64
import contextlib
import io
import json
import os
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest import mock

import darija_client
from darija_client import DarijaClient, DarijaError, normalise_base_url

USER, PASSWORD = "student", "pässword"
EXPECTED_AUTH = "Basic " + base64.b64encode(f"{USER}:{PASSWORD}".encode("utf-8")).decode("ascii")


class FakeService(BaseHTTPRequestHandler):
    """Mimics the parts of the API the client uses."""

    requests: list = []

    def log_message(self, *args):  # keep test output clean
        pass

    def _send(self, status, body):
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if self.path == "/api/health":
            self._send(200, {"status": "UP", "provider": "mock", "model": "mock"})
        else:
            self._send(404, {"error": "not_found", "message": "No such resource."})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])).decode("utf-8"))
        FakeService.requests.append({"path": self.path, "auth": self.headers.get("Authorization"), "body": body})
        if self.headers.get("Authorization") != EXPECTED_AUTH:
            self._send(401, {"error": "unauthorized", "message": "Valid credentials are required."})
        elif body.get("text") == "quota":
            self._send(429, {"error": "llm_rate_limited", "message": "Quota reached."})
        elif body.get("text") == "garbage":
            payload = b"<html>not json</html>"
            self.send_response(200)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        else:
            self._send(200, {
                "translation": "كيف داير؟" if body.get("script") != "latin" else "kif dayr?",
                "sourceLanguage": body.get("sourceLanguage"),
                "targetLanguage": "Moroccan Arabic (Darija)",
                "script": body.get("script"),
                "provider": "mock",
                "model": "mock",
            })


class ClientTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeService)
        cls.url = f"http://127.0.0.1:{cls.server.server_address[1]}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    def setUp(self):
        FakeService.requests.clear()
        self.client = DarijaClient(self.url, USER, PASSWORD, timeout=5)

    def test_translate_sends_expected_request(self):
        result = self.client.translate("How are you?", "English", "arabic")
        self.assertEqual(result["translation"], "كيف داير؟")
        sent = FakeService.requests[0]
        self.assertEqual(sent["path"], "/api/translate")
        self.assertEqual(sent["auth"], EXPECTED_AUTH)  # non-ASCII password survives
        self.assertEqual(sent["body"], {"text": "How are you?", "sourceLanguage": "English", "script": "arabic"})

    def test_latin_script(self):
        self.assertEqual(self.client.translate("How are you?", script="latin")["translation"], "kif dayr?")

    def test_health_needs_no_credentials(self):
        self.assertEqual(DarijaClient(self.url, "x", "y").health()["status"], "UP")

    def test_wrong_password_is_a_clear_error(self):
        with self.assertRaises(DarijaError) as raised:
            DarijaClient(self.url, USER, "nope").translate("Hi")
        self.assertEqual(raised.exception.status, 401)
        self.assertIn("password", str(raised.exception).lower())

    def test_server_error_message_is_passed_through(self):
        with self.assertRaises(DarijaError) as raised:
            self.client.translate("quota")
        self.assertEqual(raised.exception.status, 429)
        self.assertEqual(raised.exception.code, "llm_rate_limited")
        self.assertEqual(str(raised.exception), "Quota reached.")

    def test_non_json_answer(self):
        with self.assertRaises(DarijaError):
            self.client.translate("garbage")

    def test_unreachable_server(self):
        with self.assertRaises(DarijaError) as raised:
            DarijaClient("http://127.0.0.1:9", USER, PASSWORD, timeout=2).translate("Hi")
        self.assertIn("Cannot reach", str(raised.exception))

    def test_blank_text_is_rejected_locally(self):
        with self.assertRaises(DarijaError):
            self.client.translate("   ")
        self.assertEqual(FakeService.requests, [])

    def test_normalise_base_url(self):
        self.assertEqual(normalise_base_url("localhost:8080/"), "http://localhost:8080")
        self.assertEqual(normalise_base_url("https://example.org/api/translate"), "https://example.org")
        with self.assertRaises(DarijaError):
            normalise_base_url("  ")


class CommandLineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        ClientTests.setUpClass.__func__(cls)

    @classmethod
    def tearDownClass(cls):
        ClientTests.tearDownClass.__func__(cls)

    def run_cli(self, *argv, stdin=None, password=PASSWORD):
        out, err = io.StringIO(), io.StringIO()
        env = {"DARIJA_PASSWORD": password} if password else {}
        with mock.patch.dict(os.environ, env, clear=False), \
                mock.patch("sys.stdin", io.StringIO(stdin or "")), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = darija_client.main(["--url", self.url, "--user", USER, *argv])
        return code, out.getvalue(), err.getvalue()

    def test_translates_arguments(self):
        code, out, _ = self.run_cli("How", "are", "you?")
        self.assertEqual((code, out.strip()), (0, "كيف داير؟"))

    def test_reads_stdin(self):
        code, out, _ = self.run_cli(stdin="How are you?")
        self.assertEqual((code, out.strip()), (0, "كيف داير؟"))

    def test_json_output(self):
        code, out, _ = self.run_cli("--json", "--script", "latin", "Hi")
        self.assertEqual(code, 0)
        self.assertEqual(json.loads(out)["translation"], "kif dayr?")

    def test_failure_sets_exit_code(self):
        code, out, err = self.run_cli("Hi", password="wrong")
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertIn("Wrong username or password", err)

    def test_interactive_mode(self):
        code, out, _ = self.run_cli("-i", stdin="Hello\nThank you\n\n")
        self.assertEqual(code, 0)
        self.assertEqual(out.count("كيف داير؟"), 2)


if __name__ == "__main__":
    unittest.main()
