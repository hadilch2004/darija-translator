#!/usr/bin/env php
<?php
declare(strict_types=1);

/**
 * Command-line client.
 *
 *   export DARIJA_PASSWORD=my-password
 *   php translate.php "How are you?"
 *   php translate.php --script=latin --source=English "Good morning"
 *   echo "See you tomorrow" | php translate.php
 *   php translate.php --json "Thank you"
 *
 * Optional environment: DARIJA_URL (default http://localhost:8080), DARIJA_USER (default student).
 */

require __DIR__ . '/src/DarijaClient.php';

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$options = getopt('', ['script:', 'source:', 'json', 'help'], $firstText);
if (isset($options['help'])) {
    fwrite(STDOUT, "Usage: php translate.php [--script=arabic|latin] [--source=English] [--json] [text...]\n");
    exit(0);
}

$text = implode(' ', array_slice($argv, $firstText));
$stdinIsPipe = function_exists('posix_isatty') ? !posix_isatty(STDIN) : false;
if ($text === '' && $stdinIsPipe) {
    $text = (string) stream_get_contents(STDIN);
}

try {
    if (getenv('DARIJA_PASSWORD') === false || getenv('DARIJA_PASSWORD') === '') {
        throw new DarijaException('Set the DARIJA_PASSWORD environment variable first.');
    }
    $result = DarijaClient::fromEnvironment()->translate(
        $text,
        (string) ($options['source'] ?? 'auto'),
        (string) ($options['script'] ?? 'arabic'),
    );
    echo isset($options['json'])
        ? json_encode($result, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT) . PHP_EOL
        : $result['translation'] . PHP_EOL;
} catch (DarijaException $e) {
    fwrite(STDERR, 'error: ' . $e->getMessage() . PHP_EOL);
    exit(1);
}
