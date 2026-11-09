#!/usr/bin/env python3
"""Python client for the Darija Translator REST service (standard library only).

Use it as a library::

    from darija_client import DarijaClient
    client = DarijaClient("http://localhost:8080", "student", "my-password")
    print(client.translate("How are you?")["translation"])

or from the command line::

    export DARIJA_PASSWORD=my-password
    python3 darija_client.py "How are you?"
    python3 darija_client.py --script latin "Good morning"
    echo "See you tomorrow" | python3 darija_client.py
    python3 darija_client.py --interactive
"""
from __future__ import annotations

import argparse
import base64
import getpass
import json
import os
import socket
import sys
import urllib.error
import urllib.request
from typing import Any, Sequence

DEFAULT_URL = "http://localhost:8080"
DEFAULT_USER = "student"


class DarijaError(Exception):
    """Anything that stops a translation: bad credentials, server errors, network problems."""

    def __init__(self, message: str, status: int | None = None, code: str | None = None):
        super().__init__(message)
        self.status = status
        self.code = code


def normalise_base_url(url: str) -> str:
    """'localhost:8080/', 'http://host/api/translate' ... -> 'http://host[:port]'."""
    value = (url or "").strip()
    if not value:
        raise DarijaError("The server URL is empty.")
    if "://" not in value:
        value = "http://" + value
    value = value.rstrip("/")
    for suffix in ("/api/translate", "/api/health", "/api"):
        if value.lower().endswith(suffix):
            value = value[: -len(suffix)]
            break
    return value


class DarijaClient:
    def __init__(self, base_url: str = DEFAULT_URL, username: str = DEFAULT_USER,
                 password: str = "", timeout: float = 60.0):
        self.base_url = normalise_base_url(base_url)
        self.username = username
        self.password = password
        self.timeout = timeout

    # -- public API -----------------------------------------------------------------------

    def translate(self, text: str, source_language: str = "auto", script: str = "arabic") -> dict[str, Any]:
        """POST /api/translate. Returns the JSON answer (``translation``, ``model``, ...)."""
        if not text or not text.strip():
            raise DarijaError("There is no text to translate.")
        answer = self._request("POST", "/translate", {
            "text": text,
            "sourceLanguage": source_language,
            "script": script,
        })
        if not isinstance(answer, dict) or not answer.get("translation"):
            raise DarijaError("The server answered without a translation.")
        return answer

    def health(self) -> dict[str, Any]:
        """GET /api/health (no credentials needed)."""
        return self._request("GET", "/health", authenticate=False)

    # -- internals --------------------------------------------------------------------------

    def _request(self, method: str, path: str, payload: dict | None = None,
                 authenticate: bool = True) -> Any:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
        request = urllib.request.Request(f"{self.base_url}/api{path}", data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json; charset=utf-8")
        if authenticate:
            token = base64.b64encode(f"{self.username}:{self.password}".encode("utf-8")).decode("ascii")
            request.add_header("Authorization", f"Basic {token}")

        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise self._http_error(error) from None
        except urllib.error.URLError as error:
            if isinstance(error.reason, (socket.timeout, TimeoutError)):
                raise DarijaError("The server took too long to answer.") from None
            raise DarijaError(f"Cannot reach the server at {self.base_url}: {error.reason}") from None
        except (socket.timeout, TimeoutError):
            raise DarijaError("The server took too long to answer.") from None
        except json.JSONDecodeError:
            raise DarijaError(f"{self.base_url} did not answer with JSON. Is it the Darija service?") from None

    @staticmethod
    def _http_error(error: urllib.error.HTTPError) -> DarijaError:
        code = message = None
        try:
            body = json.loads(error.read().decode("utf-8"))
            code, message = body.get("error"), body.get("message")
        except (ValueError, UnicodeDecodeError, AttributeError):
            pass
        if error.code == 401:
            message = "Wrong username or password."
        return DarijaError(message or f"The server answered with HTTP {error.code}.", error.code, code)


# -- command line --------------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Translate text into Moroccan Arabic (Darija).")
    parser.add_argument("text", nargs="*", help="text to translate (omit to read stdin)")
    parser.add_argument("--url", default=os.environ.get("DARIJA_URL", DEFAULT_URL),
                        help="server base URL (env DARIJA_URL, default %(default)s)")
    parser.add_argument("--user", default=os.environ.get("DARIJA_USER", DEFAULT_USER),
                        help="username (env DARIJA_USER, default %(default)s)")
    parser.add_argument("--password", default=None,
                        help="password; prefer the DARIJA_PASSWORD env var or the prompt, "
                             "since command-line arguments are visible to other users")
    parser.add_argument("--source", default="auto", help="source language, e.g. English (default: auto-detect)")
    parser.add_argument("--script", choices=("arabic", "latin"), default="arabic",
                        help="write Darija in Arabic letters or Latin letters (default: arabic)")
    parser.add_argument("--json", action="store_true", help="print the full JSON answer")
    parser.add_argument("-i", "--interactive", action="store_true", help="translate line after line")
    return parser


def resolve_password(cli_value: str | None) -> str:
    if cli_value is not None:
        return cli_value
    env_value = os.environ.get("DARIJA_PASSWORD")
    if env_value:
        return env_value
    if sys.stdin.isatty():
        return getpass.getpass(f"Password for {os.environ.get('DARIJA_USER', DEFAULT_USER)}: ")
    raise DarijaError("No password: set DARIJA_PASSWORD or pass --password.")


def _print_result(result: dict[str, Any], as_json: bool) -> None:
    if as_json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(result["translation"])


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        client = DarijaClient(args.url, args.user, resolve_password(args.password))

        if args.interactive:
            print("Type text and press Enter. Empty line or Ctrl+D to quit.", file=sys.stderr)
            while True:
                try:
                    line = input("> ").strip()
                except EOFError:
                    break
                if not line:
                    break
                try:
                    _print_result(client.translate(line, args.source, args.script), args.json)
                except DarijaError as error:
                    print(f"error: {error}", file=sys.stderr)
            return 0

        text = " ".join(args.text) if args.text else sys.stdin.read()
        _print_result(client.translate(text, args.source, args.script), args.json)
        return 0
    except DarijaError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
