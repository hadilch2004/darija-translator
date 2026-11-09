<?php
declare(strict_types=1);

/** Raised for every failure: bad credentials, server errors, network problems. */
final class DarijaException extends RuntimeException
{
    public function __construct(string $message, private readonly int $httpStatus = 0, private readonly string $errorCode = '')
    {
        parent::__construct($message);
    }

    public function httpStatus(): int
    {
        return $this->httpStatus;
    }

    public function errorCode(): string
    {
        return $this->errorCode;
    }
}

/**
 * Client for the Darija Translator REST service, built on PHP's cURL extension.
 *
 *   $client = new DarijaClient('http://localhost:8080', 'student', 'my-password');
 *   $result = $client->translate('How are you?');
 *   echo $result['translation'];
 */
final class DarijaClient
{
    private string $baseUrl;

    public function __construct(
        string $baseUrl,
        private readonly string $username,
        private readonly string $password,
        private readonly int $timeoutSeconds = 60,
    ) {
        $this->baseUrl = self::normaliseBaseUrl($baseUrl);
    }

    /** Builds a client from DARIJA_URL, DARIJA_USER and DARIJA_PASSWORD (all optional except the password). */
    public static function fromEnvironment(): self
    {
        return new self(
            getenv('DARIJA_URL') ?: 'http://localhost:8080',
            getenv('DARIJA_USER') ?: 'student',
            getenv('DARIJA_PASSWORD') ?: '',
        );
    }

    /**
     * POST /api/translate
     *
     * @param string $sourceLanguage e.g. "English", or "auto" to detect
     * @param string $script         "arabic" or "latin"
     * @return array{translation: string, provider: string, model: string, script: string}
     * @throws DarijaException
     */
    public function translate(string $text, string $sourceLanguage = 'auto', string $script = 'arabic'): array
    {
        if (trim($text) === '') {
            throw new DarijaException('There is no text to translate.');
        }
        $answer = $this->request('POST', '/translate', [
            'text' => $text,
            'sourceLanguage' => $sourceLanguage,
            'script' => $script,
        ]);
        if (!isset($answer['translation']) || !is_string($answer['translation']) || $answer['translation'] === '') {
            throw new DarijaException('The server answered without a translation.');
        }
        return $answer;
    }

    /**
     * GET /api/health (no credentials needed)
     *
     * @return array<string, mixed>
     * @throws DarijaException
     */
    public function health(): array
    {
        return $this->request('GET', '/health', null, false);
    }

    /** 'localhost:8080/', 'http://host/api/translate' ... => 'http://host[:port]' */
    public static function normaliseBaseUrl(string $url): string
    {
        $value = trim($url);
        if ($value === '') {
            throw new DarijaException('The server URL is empty.');
        }
        if (!str_contains($value, '://')) {
            $value = 'http://' . $value;
        }
        $value = rtrim($value, '/');
        return preg_replace('#/api(/translate|/health)?$#i', '', $value) ?? $value;
    }

    /**
     * @param array<string, mixed>|null $payload
     * @return array<string, mixed>
     */
    private function request(string $method, string $path, ?array $payload = null, bool $authenticate = true): array
    {
        $curl = curl_init($this->baseUrl . '/api' . $path);
        if ($curl === false) {
            throw new DarijaException('Could not initialise cURL.');
        }

        $headers = ['Accept: application/json'];
        $options = [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_TIMEOUT => $this->timeoutSeconds,
            CURLOPT_CUSTOMREQUEST => $method,
        ];
        if ($authenticate) {
            $options[CURLOPT_HTTPAUTH] = CURLAUTH_BASIC;
            $options[CURLOPT_USERPWD] = $this->username . ':' . $this->password;
        }
        if ($payload !== null) {
            $headers[] = 'Content-Type: application/json; charset=utf-8';
            $options[CURLOPT_POSTFIELDS] = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        }
        $options[CURLOPT_HTTPHEADER] = $headers;
        curl_setopt_array($curl, $options);

        $body = curl_exec($curl);
        if ($body === false) {
            $errno = curl_errno($curl);
            $reason = curl_error($curl);
            curl_close($curl);
            if ($errno === CURLE_OPERATION_TIMEDOUT) {
                throw new DarijaException('The server took too long to answer.');
            }
            throw new DarijaException("Cannot reach the server at {$this->baseUrl}: {$reason}");
        }
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);

        try {
            $decoded = json_decode((string) $body, true, 512, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            $decoded = null;
        }

        if ($status >= 200 && $status < 300) {
            if (!is_array($decoded)) {
                throw new DarijaException("{$this->baseUrl} did not answer with JSON. Is it the Darija service?", $status);
            }
            return $decoded;
        }

        $code = is_array($decoded) && isset($decoded['error']) ? (string) $decoded['error'] : '';
        $message = is_array($decoded) && isset($decoded['message']) ? (string) $decoded['message'] : "The server answered with HTTP {$status}.";
        if ($status === 401) {
            $message = 'Wrong username or password.';
        }
        throw new DarijaException($message, $status, $code);
    }
}
