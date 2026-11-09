<?php
declare(strict_types=1);

/**
 * Small web front end:   DARIJA_PASSWORD=my-password php -S localhost:8000
 * then open http://localhost:8000. The PHP server talks to the REST service; the browser never sees
 * the service credentials.
 */

require __DIR__ . '/src/DarijaClient.php';

function h(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

$text = '';
$script = 'arabic';
$source = 'auto';
$translation = null;
$error = null;
$meta = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $text = (string) ($_POST['text'] ?? '');
    $script = ($_POST['script'] ?? 'arabic') === 'latin' ? 'latin' : 'arabic';
    $source = (string) ($_POST['source'] ?? 'auto');
    try {
        $result = DarijaClient::fromEnvironment()->translate($text, $source, $script);
        $translation = $result['translation'];
        $meta = ($result['provider'] ?? '') . ' · ' . ($result['model'] ?? '');
    } catch (DarijaException $e) {
        $error = $e->getMessage();
    }
}

$languages = ['auto' => 'Detect automatically', 'English' => 'English', 'French' => 'French', 'Spanish' => 'Spanish',
    'German' => 'German', 'Italian' => 'Italian', 'Modern Standard Arabic' => 'Modern Standard Arabic'];
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Darija Translator (PHP client)</title>
  <style>
    :root { color-scheme: light dark; --accent: #006233; }
    body { font: 16px/1.5 system-ui, sans-serif; max-width: 40rem; margin: 2rem auto; padding: 0 1rem; }
    textarea, select { width: 100%; font: inherit; padding: .5rem; box-sizing: border-box; }
    .row { display: flex; gap: .75rem; margin: .75rem 0; }
    .row > * { flex: 1; }
    button { font: inherit; padding: .6rem 1rem; border: 0; border-radius: 8px; background: var(--accent); color: #fff; cursor: pointer; }
    .result { margin-top: 1.5rem; padding: 1rem; border: 1px solid #8884; border-radius: 8px; font-size: 1.4rem; white-space: pre-wrap; }
    .error { margin-top: 1rem; padding: .75rem 1rem; border-radius: 8px; background: #fdecec; color: #8a1f1f; }
    small { opacity: .7; }
  </style>
</head>
<body>
  <h1>Darija Translator <small>PHP client</small></h1>
  <form method="post">
    <label for="text">Text to translate</label>
    <textarea id="text" name="text" rows="5" maxlength="5000" required><?= h($text) ?></textarea>
    <div class="row">
      <label>From
        <select name="source">
          <?php foreach ($languages as $value => $label): ?>
            <option value="<?= h($value) ?>"<?= $value === $source ? ' selected' : '' ?>><?= h($label) ?></option>
          <?php endforeach; ?>
        </select>
      </label>
      <label>Darija in
        <select name="script">
          <option value="arabic"<?= $script === 'arabic' ? ' selected' : '' ?>>Arabic letters</option>
          <option value="latin"<?= $script === 'latin' ? ' selected' : '' ?>>Latin letters</option>
        </select>
      </label>
    </div>
    <button type="submit">Translate</button>
  </form>

  <?php if ($error !== null): ?>
    <div class="error" role="alert"><?= h($error) ?></div>
  <?php endif; ?>

  <?php if ($translation !== null): ?>
    <div class="result" dir="<?= $script === 'arabic' ? 'rtl' : 'ltr' ?>" lang="<?= $script === 'arabic' ? 'ary-Arab' : 'ary-Latn' ?>"><?= h($translation) ?></div>
    <small><?= h($meta) ?></small>
  <?php endif; ?>
</body>
</html>
