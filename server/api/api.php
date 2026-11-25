<?php
/*
 * FairShare API: the phones talk only to this file, never to MySQL directly.
 *
 * Every call is  POST api.php?action=<name>  with a JSON body; the answer is JSON:
 *   {"ok": true, ...}  or  {"ok": false, "error": "<message for the user>"} with an HTTP error status.
 * Calls other than ping/register/login need the header  X-FairShare-Token: <token from login>.
 * Secrets (database password, Gemini key) live only in ~/fairshare-config.php on the server, never in the app.
 *
 * Plain PHP 7.1+ with PDO MySQL, no framework and no Composer, so it runs on the AGH student server as it is.
 * Description of the sync protocol: docs/chmura.md.
 */
declare(strict_types=1);

error_reporting(E_ALL);
ini_set('display_errors', '0');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

/** 8 MB: a receipt photo for the Gemini proxy is ~1-2 MB as base64. */
const MAX_BODY_BYTES = 8388608;
const MAX_OPS_PER_PUSH = 500;
const UUID_PATTERN = '/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/';
/** Invite codes: no 0/O and 1/I/L, so a code read aloud or retyped from a screenshot still works. */
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 8;
const MINUTE_MS = 60000;
const DAY_MS = 86400000;
/** A login not used for this long is ended: a lost phone stops being a way in. */
const TOKEN_IDLE_MS = 180 * DAY_MS;

/** A change from a phone that breaks a rule (not a member, shares do not add up...). The phone drops it. */
class Rejected extends Exception
{
}

function respond(int $status, array $body)
{
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function fail(int $status, string $message)
{
    respond($status, ['ok' => false, 'error' => $message]);
}

set_exception_handler(function ($e) {
    // message and place only: a stack trace lists call arguments, and those can include the database password
    error_log('FairShare API: ' . get_class($e) . ': ' . $e->getMessage() . ' at ' . basename($e->getFile()) . ':' . $e->getLine());
    fail(500, 'Błąd serwera, spróbuj ponownie za chwilę');
});

function now_ms(): int
{
    return (int) round(microtime(true) * 1000);
}

function uuid4(): string
{
    $b = random_bytes(16);
    $b[6] = chr((ord($b[6]) & 0x0f) | 0x40);
    $b[8] = chr((ord($b[8]) & 0x3f) | 0x80);
    $h = bin2hex($b);
    return substr($h, 0, 8) . '-' . substr($h, 8, 4) . '-' . substr($h, 12, 4) . '-' . substr($h, 16, 4) . '-' . substr($h, 20);
}

function text_length(string $s): int
{
    return function_exists('mb_strlen') ? mb_strlen($s, 'UTF-8') : (int) preg_match_all('/./us', $s);
}

// ---------- configuration and database ----------

/**
 * The database password is kept outside the web folder: ~/fairshare-config.php when this file is in
 * ~/public_html/fairshare/. config.php next to this file is only a fallback for other hosts.
 */
function load_config(): array
{
    $candidates = [dirname(__DIR__, 2) . '/fairshare-config.php', __DIR__ . '/config.php'];
    foreach ($candidates as $file) {
        if (is_file($file) && is_readable($file)) {
            $config = require $file;
            if (is_array($config)) {
                return $config;
            }
        }
    }
    fail(500, 'Serwer nie jest skonfigurowany (brak fairshare-config.php)');
    return [];
}

function connect(array $c): PDO
{
    $dsn = sprintf(
        'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
        $c['db_host'] ?? 'mysql.agh.edu.pl',
        (int) ($c['db_port'] ?? 3306),
        $c['db_name'] ?? ''
    );
    $pdo = new PDO($dsn, $c['db_user'] ?? '', $c['db_pass'] ?? '', [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        // emulated prepares let one :name appear several times in a query
        PDO::ATTR_EMULATE_PREPARES => true,
    ]);
    $pdo->exec('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ');
    return $pdo;
}

function q(PDO $pdo, string $sql, array $params = []): PDOStatement
{
    $st = $pdo->prepare($sql);
    $st->execute($params);
    return $st;
}

function one(PDO $pdo, string $sql, array $params = [])
{
    $row = q($pdo, $sql, $params)->fetch();
    return $row === false ? null : $row;
}

function all(PDO $pdo, string $sql, array $params = []): array
{
    return q($pdo, $sql, $params)->fetchAll();
}

/** "?, ?, ?" for an IN (...) list. */
function marks(array $values): string
{
    return implode(', ', array_fill(0, count($values), '?'));
}

/**
 * Takes the next sync version. The UPDATE locks the counter row until the transaction ends, so all writes are
 * serialized and a version is visible only after every smaller one is (see schema.sql).
 */
function next_version(PDO $pdo): int
{
    q($pdo, 'UPDATE fs_sync_counter SET value = LAST_INSERT_ID(value + 1) WHERE id = 1');
    return (int) $pdo->query('SELECT LAST_INSERT_ID()')->fetchColumn();
}

// ---------- input validation ----------

function req_string(array $a, string $key, int $max, bool $allowEmpty = false): string
{
    $v = $a[$key] ?? null;
    if (!is_string($v)) {
        throw new Rejected("Brak pola $key");
    }
    $v = trim($v);
    if (!$allowEmpty && $v === '') {
        throw new Rejected("Pole $key nie może być puste");
    }
    if (text_length($v) > $max) {
        throw new Rejected("Pole $key jest za długie (max $max znaków)");
    }
    return $v;
}

function opt_string(array $a, string $key, int $max)
{
    if (!isset($a[$key]) || $a[$key] === null) {
        return null;
    }
    $v = req_string($a, $key, $max, true);
    return $v === '' ? null : $v;
}

function req_int(array $a, string $key): int
{
    $v = $a[$key] ?? null;
    if (!is_int($v)) {
        throw new Rejected("Pole $key musi być liczbą całkowitą");
    }
    return $v;
}

function req_bool(array $a, string $key): bool
{
    $v = $a[$key] ?? null;
    if (!is_bool($v)) {
        throw new Rejected("Pole $key musi być true/false");
    }
    return $v;
}

function req_uuid(array $a, string $key): string
{
    $v = $a[$key] ?? null;
    if (!is_string($v) || !preg_match(UUID_PATTERN, $v)) {
        throw new Rejected("Pole $key musi być identyfikatorem UUID");
    }
    return $v;
}

function opt_uuid(array $a, string $key)
{
    return (isset($a[$key]) && $a[$key] !== null) ? req_uuid($a, $key) : null;
}

function req_array(array $a, string $key): array
{
    $v = $a[$key] ?? null;
    if (!is_array($v)) {
        throw new Rejected("Pole $key musi być listą");
    }
    return $v;
}

function normalize_code(string $raw): string
{
    return preg_replace('/[^A-Z0-9]/', '', strtoupper($raw));
}

function random_code(): string
{
    $code = '';
    for ($i = 0; $i < CODE_LENGTH; $i++) {
        $code .= CODE_ALPHABET[random_int(0, strlen(CODE_ALPHABET) - 1)];
    }
    return $code;
}

// ---------- rate limits ----------

/** Events of one kind for one key (account, e-mail, IP) in the last [window] ms. */
function rate_count(PDO $pdo, string $kind, string $key, int $windowMs): int
{
    return (int) one($pdo, 'SELECT COUNT(*) AS n FROM fs_rate_events WHERE kind = ? AND rkey = ? AND at > ?',
        [$kind, $key, now_ms() - $windowMs])['n'];
}

function rate_record(PDO $pdo, string $kind, string $key)
{
    q($pdo, 'INSERT INTO fs_rate_events (kind, rkey, at) VALUES (?, ?, ?)', [$kind, $key, now_ms()]);
    if (random_int(1, 50) === 1) {
        q($pdo, 'DELETE FROM fs_rate_events WHERE at < ?', [now_ms() - 2 * DAY_MS]);
    }
}

function client_ip(): string
{
    return substr((string) ($_SERVER['REMOTE_ADDR'] ?? ''), 0, 64);
}

// ---------- accounts and tokens ----------

function person_json(array $p): array
{
    return [
        'id' => $p['id'],
        'name' => $p['name'],
        'payment_details' => $p['payment_details'],
        'default_currency' => $p['default_currency'],
        'has_account' => $p['account_id'] !== null,
        'created_at' => (int) $p['created_at'],
    ];
}

function issue_token(PDO $pdo, int $accountId): string
{
    $token = bin2hex(random_bytes(32));
    $now = now_ms();
    q($pdo, 'INSERT INTO fs_auth_tokens (token_hash, account_id, created_at, last_used_at) VALUES (?, ?, ?, ?)',
        [hash('sha256', $token), $accountId, $now, $now]);
    return $token;
}

/** The logged-in account: ['account_id' => int, 'person_id' => string]. Ends the request with 401 when the token is bad. */
function authenticate(PDO $pdo): array
{
    $token = $_SERVER['HTTP_X_FAIRSHARE_TOKEN'] ?? '';
    if (!is_string($token) || !preg_match('/^[0-9a-f]{64}$/', $token)) {
        fail(401, 'Zaloguj się ponownie');
    }
    $hash = hash('sha256', $token);
    $row = one($pdo,
        'SELECT a.id AS account_id, a.person_id, t.last_used_at FROM fs_auth_tokens t
         INNER JOIN fs_accounts a ON a.id = t.account_id WHERE t.token_hash = ?', [$hash]);
    if ($row === null) {
        fail(401, 'Sesja wygasła, zaloguj się ponownie');
    }
    $idle = now_ms() - (int) $row['last_used_at'];
    if ($idle > TOKEN_IDLE_MS) {
        q($pdo, 'DELETE FROM fs_auth_tokens WHERE token_hash = ?', [$hash]);
        fail(401, 'Sesja wygasła, zaloguj się ponownie');
    }
    if ($idle > 3600000) {
        q($pdo, 'UPDATE fs_auth_tokens SET last_used_at = ? WHERE token_hash = ?', [now_ms(), $hash]);
    }
    return ['account_id' => (int) $row['account_id'], 'person_id' => $row['person_id']];
}

function action_register(PDO $pdo, array $in): array
{
    $email = strtolower(trim((string) ($in['email'] ?? '')));
    $password = (string) ($in['password'] ?? '');
    $name = trim((string) ($in['name'] ?? ''));
    if (!filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 190) {
        fail(400, 'Podaj poprawny adres e-mail');
    }
    if (strlen($password) < 8) {
        fail(400, 'Hasło musi mieć co najmniej 8 znaków');
    }
    if (strlen($password) > 200) {
        fail(400, 'Hasło jest za długie');
    }
    if ($name === '' || text_length($name) > 100) {
        fail(400, 'Podaj imię (do 100 znaków)');
    }
    // the API is public: one address must not be able to fill the database with accounts
    if (rate_count($pdo, 'register_ip', client_ip(), 60 * MINUTE_MS) >= 5) {
        fail(429, 'Za dużo nowych kont z tej sieci, spróbuj za godzinę');
    }
    if (one($pdo, 'SELECT id FROM fs_accounts WHERE email = ?', [$email]) !== null) {
        fail(409, 'Konto z tym adresem e-mail już istnieje');
    }

    $pdo->beginTransaction();
    $version = next_version($pdo);
    $personId = uuid4();
    $now = now_ms();
    q($pdo, 'INSERT INTO fs_accounts (email, password_hash, person_id, created_at) VALUES (?, ?, ?, ?)',
        [$email, password_hash($password, PASSWORD_DEFAULT), $personId, $now]);
    $accountId = (int) $pdo->lastInsertId();
    q($pdo, 'INSERT INTO fs_persons (id, account_id, name, created_at, version) VALUES (?, ?, ?, ?, ?)',
        [$personId, $accountId, $name, $now, $version]);
    $token = issue_token($pdo, $accountId);
    rate_record($pdo, 'register_ip', client_ip());
    $pdo->commit();

    $person = one($pdo, 'SELECT * FROM fs_persons WHERE id = ?', [$personId]);
    return ['token' => $token, 'email' => $email, 'person' => person_json($person)];
}

function action_login(PDO $pdo, array $in): array
{
    $email = strtolower(trim((string) ($in['email'] ?? '')));
    $password = (string) ($in['password'] ?? '');
    // Guessing passwords: 10 wrong tries per account or 30 per network in 15 minutes, then a pause
    if (rate_count($pdo, 'login_fail', $email, 15 * MINUTE_MS) >= 10
        || rate_count($pdo, 'login_fail_ip', client_ip(), 15 * MINUTE_MS) >= 30) {
        fail(429, 'Za dużo nieudanych prób logowania, spróbuj za 15 minut');
    }
    $account = one($pdo, 'SELECT * FROM fs_accounts WHERE email = ?', [$email]);
    if ($account === null || !password_verify($password, $account['password_hash'])) {
        rate_record($pdo, 'login_fail', $email);
        rate_record($pdo, 'login_fail_ip', client_ip());
        sleep(1);
        fail(401, 'Nieprawidłowy e-mail lub hasło');
    }
    if (password_needs_rehash($account['password_hash'], PASSWORD_DEFAULT)) {
        q($pdo, 'UPDATE fs_accounts SET password_hash = ? WHERE id = ?',
            [password_hash($password, PASSWORD_DEFAULT), $account['id']]);
    }
    $token = issue_token($pdo, (int) $account['id']);
    $person = one($pdo, 'SELECT * FROM fs_persons WHERE id = ?', [$account['person_id']]);
    return ['token' => $token, 'email' => $email, 'person' => person_json($person)];
}

function action_logout(PDO $pdo): array
{
    $token = $_SERVER['HTTP_X_FAIRSHARE_TOKEN'] ?? '';
    if (is_string($token) && $token !== '') {
        q($pdo, 'DELETE FROM fs_auth_tokens WHERE token_hash = ?', [hash('sha256', $token)]);
    }
    return [];
}

// ---------- membership helpers ----------

function is_member(PDO $pdo, string $groupId, string $personId): bool
{
    return one($pdo, 'SELECT 1 AS x FROM fs_group_members WHERE group_id = ? AND person_id = ? AND deleted = 0',
        [$groupId, $personId]) !== null;
}

function require_member(PDO $pdo, string $groupId, string $personId)
{
    if (!is_member($pdo, $groupId, $personId)) {
        throw new Rejected('Nie należysz do tej grupy');
    }
}

/** A person the caller may see: themselves or someone sharing a group with them. */
function is_visible(PDO $pdo, string $personId, string $me): bool
{
    if ($personId === $me) {
        return true;
    }
    return one($pdo,
        'SELECT 1 AS x FROM fs_group_members a INNER JOIN fs_group_members b ON b.group_id = a.group_id
         WHERE a.person_id = ? AND b.person_id = ? AND a.deleted = 0 AND b.deleted = 0 LIMIT 1',
        [$personId, $me]) !== null;
}

/** Placeholder -> the account that joined as them, in one group (or the id itself). */
function resolve_person(PDO $pdo, string $groupId, string $personId): string
{
    $row = one($pdo, 'SELECT person_id FROM fs_claims WHERE group_id = ? AND placeholder_id = ?', [$groupId, $personId]);
    return $row === null ? $personId : $row['person_id'];
}

/**
 * Adds a person to a group. The person row gets the new version too, so the other members' phones download it;
 * an account added to a group must pull everything again (join_version), because the group's rows are older.
 */
function add_membership(PDO $pdo, string $groupId, string $personId, int $version)
{
    q($pdo,
        'INSERT INTO fs_group_members (group_id, person_id, joined_at, deleted, version) VALUES (?, ?, ?, 0, ?)
         ON DUPLICATE KEY UPDATE deleted = 0, version = VALUES(version)',
        [$groupId, $personId, now_ms(), $version]);
    q($pdo, 'UPDATE fs_persons SET version = ? WHERE id = ?', [$version, $personId]);
    q($pdo, 'UPDATE fs_accounts SET join_version = ? WHERE person_id = ?', [$version, $personId]);
}

function insert_activity(PDO $pdo, string $id, $groupId, string $actor, string $type, string $description,
                         array $involves, int $createdAt, int $version)
{
    q($pdo,
        'INSERT IGNORE INTO fs_activity (id, group_id, actor_id, action_type, description, involves, created_at, version)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [$id, $groupId, $actor, $type, $description, implode(',', $involves), $createdAt, $version]);
}

// ---------- push: changes made on a phone ----------

function op_create_group(PDO $pdo, string $me, array $d, int $v)
{
    $id = req_uuid($d, 'id');
    $name = req_string($d, 'name', 100);
    $createdAt = req_int($d, 'created_at');
    $existing = one($pdo, 'SELECT created_by FROM fs_groups WHERE id = ?', [$id]);
    if ($existing !== null) {
        if ($existing['created_by'] !== $me) {
            throw new Rejected('Grupa o tym identyfikatorze już istnieje');
        }
        return; // the same change sent again
    }
    $code = normalize_code((string) ($d['invite_code'] ?? ''));
    $valid = strlen($code) === CODE_LENGTH && strspn($code, CODE_ALPHABET) === CODE_LENGTH;
    while (!$valid || one($pdo, 'SELECT id FROM fs_groups WHERE invite_code = ?', [$code]) !== null) {
        $code = random_code();
        $valid = true;
    }
    q($pdo, 'INSERT INTO fs_groups (id, name, archived, invite_code, created_by, created_at, version) VALUES (?, ?, 0, ?, ?, ?, ?)',
        [$id, $name, $code, $me, $createdAt, $v]);
    add_membership($pdo, $id, $me, $v);
}

function op_update_group(PDO $pdo, string $me, array $d, int $v)
{
    $id = req_uuid($d, 'id');
    require_member($pdo, $id, $me);
    q($pdo, 'UPDATE fs_groups SET name = ?, archived = ?, version = ? WHERE id = ?',
        [req_string($d, 'name', 100), req_bool($d, 'archived') ? 1 : 0, $v, $id]);
}

/** A new person added by name (no account) straight into a group. */
function op_add_person(PDO $pdo, string $me, array $d, int $v)
{
    $groupId = req_uuid($d, 'group_id');
    $personId = req_uuid($d, 'person_id');
    $name = req_string($d, 'name', 100);
    require_member($pdo, $groupId, $me);
    $existing = one($pdo, 'SELECT id FROM fs_persons WHERE id = ?', [$personId]);
    if ($existing === null) {
        q($pdo, 'INSERT INTO fs_persons (id, account_id, name, created_at, version) VALUES (?, NULL, ?, ?, ?)',
            [$personId, $name, now_ms(), $v]);
    } elseif (!is_visible($pdo, $personId, $me)) {
        throw new Rejected('Osoba o tym identyfikatorze już istnieje');
    }
    if (resolve_person($pdo, $groupId, $personId) !== $personId) {
        return; // this placeholder has already joined the group with an account
    }
    add_membership($pdo, $groupId, $personId, $v);
}

/** A person I already know from another group, added to one more group. */
function op_add_member(PDO $pdo, string $me, array $d, int $v)
{
    $groupId = req_uuid($d, 'group_id');
    $personId = req_uuid($d, 'person_id');
    require_member($pdo, $groupId, $me);
    if (!is_visible($pdo, $personId, $me)) {
        throw new Rejected('Nie znasz tej osoby z żadnej grupy');
    }
    $personId = resolve_person($pdo, $groupId, $personId);
    if (!is_member($pdo, $groupId, $personId)) {
        add_membership($pdo, $groupId, $personId, $v);
    }
}

/** person id -> grosze, with placeholders already taken over in this group replaced by the new person. */
function read_shares(PDO $pdo, string $groupId, array $list, string $what): array
{
    $result = [];
    foreach ($list as $row) {
        if (!is_array($row)) {
            throw new Rejected("Niepoprawny udział w $what");
        }
        $person = resolve_person($pdo, $groupId, req_uuid($row, 'person_id'));
        $result[$person] = ($result[$person] ?? 0) + req_int($row, 'amount');
    }
    return $result;
}

function op_add_expense(PDO $pdo, string $me, array $d, int $v)
{
    $id = req_uuid($d, 'id');
    $groupId = req_uuid($d, 'group_id');
    $existing = one($pdo, 'SELECT group_id FROM fs_expenses WHERE id = ?', [$id]);
    if ($existing !== null) {
        if ($existing['group_id'] !== $groupId) {
            throw new Rejected('Wydatek o tym identyfikatorze już istnieje');
        }
        return; // sent again (also when it was deleted in the meantime: the deletion wins)
    }
    require_member($pdo, $groupId, $me);

    $amount = req_int($d, 'amount');
    $payer = resolve_person($pdo, $groupId, req_uuid($d, 'payer_id'));
    $description = req_string($d, 'description', 200);
    $category = opt_string($d, 'category', 100);
    $day = req_int($d, 'day');
    $isSettlement = req_bool($d, 'is_settlement');
    $createdAt = req_int($d, 'created_at');
    $shares = read_shares($pdo, $groupId, req_array($d, 'shares'), 'wydatku');
    $items = req_array($d, 'items');

    if ($amount <= 0) {
        throw new Rejected('Kwota musi być większa od zera');
    }
    if ($day < 0 || $day > 2932896) {
        throw new Rejected('Niepoprawna data');
    }
    if (!$shares) {
        throw new Rejected('Wydatek musi mieć przynajmniej jedną osobę');
    }
    foreach ($shares as $part) {
        if ($part <= 0) {
            throw new Rejected('Udział każdej osoby musi być większy od zera');
        }
    }
    if (array_sum($shares) !== $amount) {
        throw new Rejected('Udziały muszą sumować się do kwoty wydatku');
    }
    foreach (array_merge([$payer], array_keys($shares)) as $person) {
        if (!is_member($pdo, $groupId, (string) $person)) {
            throw new Rejected('Ktoś z wydatku nie należy do grupy');
        }
    }

    $parsedItems = [];
    if ($items) {
        $perPerson = [];
        foreach ($items as $item) {
            if (!is_array($item)) {
                throw new Rejected('Niepoprawna pozycja paragonu');
            }
            $price = req_int($item, 'price');
            $itemShares = read_shares($pdo, $groupId, req_array($item, 'shares'), 'pozycji');
            if (!$itemShares || array_sum($itemShares) !== $price) {
                throw new Rejected('Udziały w pozycji muszą sumować się do jej ceny');
            }
            foreach ($itemShares as $person => $part) {
                $perPerson[$person] = ($perPerson[$person] ?? 0) + $part;
            }
            $parsedItems[] = [req_string($item, 'name', 200, true) ?: 'Pozycja', $price, req_int($item, 'position'), $itemShares];
        }
        $perPerson = array_filter($perPerson, function ($x) { return $x !== 0; });
        ksort($perPerson);
        $sortedShares = $shares;
        ksort($sortedShares);
        if ($perPerson !== $sortedShares) {
            throw new Rejected('Pozycje muszą sumować się do udziałów osób');
        }
    }

    q($pdo,
        'INSERT INTO fs_expenses (id, group_id, payer_id, amount, description, category, day, is_settlement,
            created_by, created_at, deleted, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)',
        [$id, $groupId, $payer, $amount, $description, $category, $day, $isSettlement ? 1 : 0, $me, $createdAt, $v]);
    foreach ($shares as $person => $part) {
        q($pdo, 'INSERT INTO fs_expense_shares (expense_id, person_id, amount) VALUES (?, ?, ?)', [$id, (string) $person, $part]);
    }
    foreach ($parsedItems as list($name, $price, $position, $itemShares)) {
        q($pdo, 'INSERT INTO fs_expense_items (expense_id, name, price, position) VALUES (?, ?, ?, ?)', [$id, $name, $price, $position]);
        $itemId = (int) $pdo->lastInsertId();
        foreach ($itemShares as $person => $part) {
            q($pdo, 'INSERT INTO fs_expense_item_shares (item_id, person_id, amount) VALUES (?, ?, ?)', [$itemId, (string) $person, $part]);
        }
    }
}

function op_delete_expense(PDO $pdo, string $me, array $d, int $v)
{
    $id = req_uuid($d, 'id');
    $row = one($pdo, 'SELECT group_id, deleted FROM fs_expenses WHERE id = ?', [$id]);
    if ($row === null) {
        throw new Rejected('Nie ma takiego wydatku');
    }
    if ((int) $row['deleted'] === 1) {
        return;
    }
    require_member($pdo, $row['group_id'], $me);
    q($pdo, 'UPDATE fs_expenses SET deleted = 1, version = ? WHERE id = ?', [$v, $id]);
    q($pdo, 'DELETE FROM fs_expense_shares WHERE expense_id = ?', [$id]);
    q($pdo, 'DELETE FROM fs_expense_items WHERE expense_id = ?', [$id]);
}

function op_update_profile(PDO $pdo, string $me, array $d, int $v)
{
    $currency = strtoupper(req_string($d, 'default_currency', 3));
    if (!preg_match('/^[A-Z]{3}$/', $currency)) {
        throw new Rejected('Niepoprawny kod waluty');
    }
    q($pdo, 'UPDATE fs_persons SET name = ?, payment_details = ?, default_currency = ?, version = ? WHERE id = ?',
        [req_string($d, 'name', 100), opt_string($d, 'payment_details', 500), $currency, $v, $me]);
}

/** Payment details of someone without an account (people with one set them in their own profile). */
function op_update_person(PDO $pdo, string $me, array $d, int $v)
{
    $personId = req_uuid($d, 'person_id');
    $person = one($pdo, 'SELECT account_id FROM fs_persons WHERE id = ?', [$personId]);
    if ($person === null || !is_visible($pdo, $personId, $me)) {
        throw new Rejected('Nie znasz tej osoby z żadnej grupy');
    }
    if ($person['account_id'] !== null) {
        throw new Rejected('Ta osoba ma konto i sama ustawia dane do spłat');
    }
    q($pdo, 'UPDATE fs_persons SET payment_details = ?, version = ? WHERE id = ?',
        [opt_string($d, 'payment_details', 500), $v, $personId]);
}

function op_add_activity(PDO $pdo, string $me, array $d, int $v)
{
    $id = req_uuid($d, 'id');
    $groupId = opt_uuid($d, 'group_id');
    if ($groupId !== null) {
        require_member($pdo, $groupId, $me);
    }
    $involves = [];
    foreach (req_array($d, 'involves') as $person) {
        if (is_string($person) && preg_match(UUID_PATTERN, $person)) {
            $involves[] = $groupId === null ? $person : resolve_person($pdo, $groupId, $person);
        }
    }
    insert_activity($pdo, $id, $groupId, $me, req_string($d, 'type', 40), req_string($d, 'description', 500),
        array_values(array_unique($involves)), req_int($d, 'created_at'), $v);
}

const OPS = [
    'create_group' => 'op_create_group',
    'update_group' => 'op_update_group',
    'add_person' => 'op_add_person',
    'add_member' => 'op_add_member',
    'add_expense' => 'op_add_expense',
    'delete_expense' => 'op_delete_expense',
    'update_profile' => 'op_update_profile',
    'update_person' => 'op_update_person',
    'add_activity' => 'op_add_activity',
];

/**
 * Applies the phone's queued changes in order, each in its own transaction. Every change can be sent twice
 * (the phone retries when the answer got lost), so all of them are idempotent. A change that breaks a rule
 * is reported back as rejected and the phone drops it; a database error stops the request with 500 and the
 * phone sends the whole queue again later.
 */
function action_push(PDO $pdo, array $auth, array $in): array
{
    $ops = $in['ops'] ?? null;
    if (!is_array($ops) || count($ops) > MAX_OPS_PER_PUSH) {
        fail(400, 'Niepoprawna lista zmian');
    }
    $results = [];
    foreach ($ops as $op) {
        $opId = is_array($op) ? ($op['op_id'] ?? null) : null;
        $type = is_array($op) ? ($op['type'] ?? '') : '';
        $data = is_array($op) ? ($op['data'] ?? null) : null;
        if (!is_int($opId)) {
            fail(400, 'Zmiana bez op_id');
        }
        if (!is_string($type) || !array_key_exists($type, OPS) || !is_array($data)) {
            $results[] = ['op_id' => $opId, 'ok' => false, 'error' => 'Nieznany rodzaj zmiany: ' . $type];
            continue;
        }
        $pdo->beginTransaction();
        try {
            $version = next_version($pdo);
            call_user_func(OPS[$type], $pdo, $auth['person_id'], $data, $version);
            $pdo->commit();
            $results[] = ['op_id' => $opId, 'ok' => true];
        } catch (Rejected $e) {
            $pdo->rollBack();
            $results[] = ['op_id' => $opId, 'ok' => false, 'error' => $e->getMessage()];
        }
    }
    return ['results' => $results];
}

// ---------- pull: what changed since the phone's last sync ----------

function action_pull(PDO $pdo, array $auth, array $in): array
{
    $me = $auth['person_id'];
    $since = $in['since'] ?? 0;
    if (!is_int($since) || $since < 0) {
        fail(400, 'Niepoprawny kursor synchronizacji');
    }

    // One consistent snapshot: in REPEATABLE READ the first read (the counter) fixes the snapshot, so the counter
    // and every table below are read as of the same moment
    $pdo->beginTransaction();
    $cursor = (int) one($pdo, 'SELECT value FROM fs_sync_counter WHERE id = 1')['value'];
    $joinVersion = (int) one($pdo, 'SELECT join_version FROM fs_accounts WHERE id = ?', [$auth['account_id']])['join_version'];
    // Added to a group after the last sync: that group's rows are older than the cursor, so send everything
    $full = $since === 0 || $since < $joinVersion;
    if ($full) {
        $since = 0;
    }

    $myGroups = 'SELECT group_id FROM fs_group_members WHERE person_id = :me AND deleted = 0';
    $p = ['me' => $me, 'since' => $since];

    $groups = array_map(function ($g) {
        return [
            'id' => $g['id'],
            'name' => $g['name'],
            'archived' => (int) $g['archived'] === 1,
            'invite_code' => $g['invite_code'],
            'created_at' => (int) $g['created_at'],
        ];
    }, all($pdo, "SELECT * FROM fs_groups WHERE version > :since AND id IN ($myGroups)", $p));

    $members = array_map(function ($m) {
        return [
            'group_id' => $m['group_id'],
            'person_id' => $m['person_id'],
            'joined_at' => (int) $m['joined_at'],
            'deleted' => (int) $m['deleted'] === 1,
        ];
    }, all($pdo, "SELECT * FROM fs_group_members WHERE version > :since AND group_id IN ($myGroups)", $p));

    $persons = array_map('person_json', all($pdo,
        "SELECT * FROM fs_persons WHERE version > :since AND (id = :me OR id IN (
            SELECT person_id FROM fs_group_members WHERE group_id IN ($myGroups)))", $p));

    $expenseRows = all($pdo, "SELECT * FROM fs_expenses WHERE version > :since AND group_id IN ($myGroups)", $p);
    $liveIds = [];
    foreach ($expenseRows as $e) {
        if ((int) $e['deleted'] === 0) {
            $liveIds[] = $e['id'];
        }
    }
    $sharesByExpense = [];
    $itemsByExpense = [];
    if ($liveIds) {
        foreach (all($pdo, 'SELECT * FROM fs_expense_shares WHERE expense_id IN (' . marks($liveIds) . ')', $liveIds) as $s) {
            $sharesByExpense[$s['expense_id']][] = ['person_id' => $s['person_id'], 'amount' => (int) $s['amount']];
        }
        $items = all($pdo, 'SELECT * FROM fs_expense_items WHERE expense_id IN (' . marks($liveIds) . ') ORDER BY position, id', $liveIds);
        $itemShares = [];
        $itemIds = array_map(function ($i) { return (int) $i['id']; }, $items);
        if ($itemIds) {
            foreach (all($pdo, 'SELECT * FROM fs_expense_item_shares WHERE item_id IN (' . marks($itemIds) . ')', $itemIds) as $x) {
                $itemShares[(int) $x['item_id']][] = ['person_id' => $x['person_id'], 'amount' => (int) $x['amount']];
            }
        }
        foreach ($items as $i) {
            $itemsByExpense[$i['expense_id']][] = [
                'name' => $i['name'],
                'price' => (int) $i['price'],
                'position' => (int) $i['position'],
                'shares' => $itemShares[(int) $i['id']] ?? [],
            ];
        }
    }
    $expenses = array_map(function ($e) use ($sharesByExpense, $itemsByExpense) {
        if ((int) $e['deleted'] === 1) {
            return ['id' => $e['id'], 'group_id' => $e['group_id'], 'deleted' => true];
        }
        return [
            'id' => $e['id'],
            'group_id' => $e['group_id'],
            'deleted' => false,
            'payer_id' => $e['payer_id'],
            'amount' => (int) $e['amount'],
            'description' => $e['description'],
            'category' => $e['category'],
            'day' => (int) $e['day'],
            'is_settlement' => (int) $e['is_settlement'] === 1,
            'created_at' => (int) $e['created_at'],
            'shares' => $sharesByExpense[$e['id']] ?? [],
            'items' => $itemsByExpense[$e['id']] ?? [],
        ];
    }, $expenseRows);

    $activity = array_map(function ($a) {
        return [
            'id' => $a['id'],
            'group_id' => $a['group_id'],
            'actor_id' => $a['actor_id'],
            'type' => $a['action_type'],
            'description' => $a['description'],
            'involves' => $a['involves'] === null || $a['involves'] === '' ? [] : explode(',', $a['involves']),
            'created_at' => (int) $a['created_at'],
        ];
    }, all($pdo,
        "SELECT * FROM fs_activity WHERE version > :since
           AND ((group_id IS NULL AND actor_id = :me) OR group_id IN ($myGroups))
         ORDER BY version", $p));

    $pdo->commit();
    return [
        'cursor' => $cursor,
        'full' => $full,
        'me' => $me,
        'persons' => $persons,
        'groups' => $groups,
        'members' => $members,
        'expenses' => $expenses,
        'activity' => $activity,
    ];
}

// ---------- joining a group with an invite code ----------

function find_group_by_code(PDO $pdo, array $in, bool $lock = false): array
{
    $code = normalize_code((string) ($in['code'] ?? ''));
    $group = strlen($code) === CODE_LENGTH
        ? one($pdo, 'SELECT id, name FROM fs_groups WHERE invite_code = ?' . ($lock ? ' FOR UPDATE' : ''), [$code])
        : null;
    if ($group === null) {
        fail(404, 'Nie ma grupy o takim kodzie');
    }
    return $group;
}

/** Placeholders of the group (people added by name), so the joining person can say which one they are. */
function action_join_preview(PDO $pdo, array $auth, array $in): array
{
    $group = find_group_by_code($pdo, $in);
    $members = all($pdo,
        'SELECT p.id, p.name, p.account_id FROM fs_group_members m INNER JOIN fs_persons p ON p.id = m.person_id
         WHERE m.group_id = ? AND m.deleted = 0 ORDER BY p.name', [$group['id']]);
    $placeholders = [];
    $names = [];
    foreach ($members as $m) {
        $names[] = $m['name'];
        if ($m['account_id'] === null) {
            $placeholders[] = ['id' => $m['id'], 'name' => $m['name']];
        }
    }
    return [
        'group' => ['id' => $group['id'], 'name' => $group['name'], 'member_names' => $names],
        'already_member' => is_member($pdo, $group['id'], $auth['person_id']),
        'placeholders' => $placeholders,
    ];
}

/**
 * Joins a group. With claim_person_id the account takes over a placeholder of that group: their expenses and
 * shares in the group are moved to the account, the placeholder leaves the group.
 */
function action_join(PDO $pdo, array $auth, array $in): array
{
    $me = $auth['person_id'];
    $pdo->beginTransaction();
    $version = next_version($pdo);
    $group = find_group_by_code($pdo, $in, true);
    $groupId = $group['id'];
    if (is_member($pdo, $groupId, $me)) {
        $pdo->rollBack();
        return ['group_id' => $groupId];
    }
    $claim = $in['claim_person_id'] ?? null;
    $description = 'Dołączenie do grupy';
    add_membership($pdo, $groupId, $me, $version);
    if ($claim !== null) {
        $placeholder = is_string($claim) && preg_match(UUID_PATTERN, $claim)
            ? one($pdo, 'SELECT p.id, p.name FROM fs_persons p INNER JOIN fs_group_members m ON m.person_id = p.id
                          WHERE p.id = ? AND p.account_id IS NULL AND m.group_id = ? AND m.deleted = 0', [$claim, $groupId])
            : null;
        if ($placeholder === null) {
            $pdo->rollBack();
            fail(400, 'Tej osoby nie ma już w grupie, wybierz ponownie');
        }
        $ph = $placeholder['id'];
        q($pdo, 'UPDATE fs_expenses SET payer_id = ? WHERE group_id = ? AND payer_id = ?', [$me, $groupId, $ph]);
        q($pdo, 'UPDATE fs_expense_shares s INNER JOIN fs_expenses e ON e.id = s.expense_id
                 SET s.person_id = ? WHERE e.group_id = ? AND s.person_id = ?', [$me, $groupId, $ph]);
        q($pdo, 'UPDATE fs_expense_item_shares x INNER JOIN fs_expense_items i ON i.id = x.item_id
                 INNER JOIN fs_expenses e ON e.id = i.expense_id
                 SET x.person_id = ? WHERE e.group_id = ? AND x.person_id = ?', [$me, $groupId, $ph]);
        q($pdo, 'UPDATE fs_expenses SET version = ? WHERE group_id = ? AND deleted = 0', [$version, $groupId]);
        q($pdo, 'UPDATE fs_group_members SET deleted = 1, version = ? WHERE group_id = ? AND person_id = ?', [$version, $groupId, $ph]);
        q($pdo, 'INSERT INTO fs_claims (group_id, placeholder_id, person_id) VALUES (?, ?, ?)
                 ON DUPLICATE KEY UPDATE person_id = VALUES(person_id)', [$groupId, $ph, $me]);
        // the placeholder's payment details are a good start when the account has none yet
        q($pdo, 'UPDATE fs_persons me_p INNER JOIN fs_persons ph_p ON ph_p.id = ?
                 SET me_p.payment_details = ph_p.payment_details
                 WHERE me_p.id = ? AND me_p.payment_details IS NULL', [$ph, $me]);
        $description = 'Dołączenie do grupy jako „' . $placeholder['name'] . '”';
    }
    insert_activity($pdo, uuid4(), $groupId, $me, 'JOIN_GROUP', $description, [$me], now_ms(), $version);
    $pdo->commit();
    return ['group_id' => $groupId];
}

// ---------- Gemini proxy (receipt scanner) ----------

/**
 * Forwards one generateContent request of the receipt scanner to Google Gemini. The API key stays here, in the
 * server config: an APK can be unpacked, so a key inside the app is a key anyone with the APK can use.
 * Only logged-in accounts, only the configured models, and a daily limit per account and for the whole server,
 * because anyone can create an account. Answers {status, response}: Google's HTTP status and body, unchanged.
 */
function action_gemini(PDO $pdo, array $auth, array $in, array $config): array
{
    $key = (string) ($config['gemini_api_key'] ?? '');
    if ($key === '') {
        fail(503, 'Skanowanie z AI nie jest włączone na serwerze');
    }
    $models = $config['gemini_models'] ?? ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite'];
    $model = $in['model'] ?? null;
    if (!is_string($model) || !in_array($model, $models, true)) {
        fail(400, 'Ten model nie jest dostępny');
    }
    $request = $in['request_json'] ?? null;
    if (!is_string($request) || !is_array(json_decode($request, true))) {
        fail(400, 'Niepoprawne zapytanie do Gemini');
    }
    $timeoutMs = $in['timeout_ms'] ?? 20000;
    $timeoutMs = is_int($timeoutMs) ? max(2000, min(30000, $timeoutMs)) : 20000;

    $account = (string) $auth['account_id'];
    if (rate_count($pdo, 'gemini', $account, DAY_MS) >= (int) ($config['gemini_daily_limit'] ?? 60)) {
        fail(429, 'Dzienny limit skanów z AI na tym koncie się skończył, spróbuj jutro');
    }
    if (rate_count($pdo, 'gemini', '*', DAY_MS) >= (int) ($config['gemini_global_daily_limit'] ?? 300)) {
        fail(429, 'Dzienny limit skanów z AI na serwerze się skończył, spróbuj jutro');
    }
    rate_record($pdo, 'gemini', $account);
    rate_record($pdo, 'gemini', '*');

    $curl = curl_init('https://generativelanguage.googleapis.com/v1beta/models/' . rawurlencode($model) . ':generateContent');
    curl_setopt_array($curl, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $request,
        CURLOPT_HTTPHEADER => ['Content-Type: application/json', 'x-goog-api-key: ' . $key],
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT_MS => 10000,
        CURLOPT_TIMEOUT_MS => $timeoutMs,
        // alwaysdata's IPv6 address is refused by Google ("User location is not supported"); IPv4 works
        CURLOPT_IPRESOLVE => CURL_IPRESOLVE_V4,
    ]);
    $body = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    $error = curl_errno($curl);
    curl_close($curl);
    if ($body === false || $status === 0) {
        // the phone treats 5xx as "this model is unavailable" and tries the next one
        $message = $error === CURLE_OPERATION_TIMEDOUT ? 'Gemini nie odpowiedziało na czas' : 'Brak połączenia z Gemini';
        return ['status' => 504, 'response' => json_encode(['error' => ['message' => $message]], JSON_UNESCAPED_UNICODE)];
    }
    return ['status' => $status, 'response' => (string) $body];
}

// ---------- routing ----------

$action = (string) ($_GET['action'] ?? '');
$config = load_config();

if ($action === 'ping') {
    $pdo = connect($config);
    $tables = (int) one($pdo, "SELECT COUNT(*) AS n FROM information_schema.tables
                               WHERE table_schema = DATABASE() AND table_name LIKE 'fs\\_%'")['n'];
    // no versions here: the address is public and they would only help an attacker
    respond(200, ['ok' => true, 'service' => 'fairshare', 'tables' => $tables]);
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    fail(405, 'Użyj POST');
}
$raw = file_get_contents('php://input', false, null, 0, MAX_BODY_BYTES + 1);
if ($raw === false || strlen($raw) > MAX_BODY_BYTES) {
    fail(413, 'Za dużo danych naraz');
}
$in = $raw === '' ? [] : json_decode($raw, true);
if (!is_array($in)) {
    fail(400, 'Niepoprawny JSON');
}

$pdo = connect($config);
switch ($action) {
    case 'register':
        respond(200, ['ok' => true] + action_register($pdo, $in));
        break;
    case 'login':
        respond(200, ['ok' => true] + action_login($pdo, $in));
        break;
    case 'logout':
        respond(200, ['ok' => true] + action_logout($pdo));
        break;
    case 'push':
        respond(200, ['ok' => true] + action_push($pdo, authenticate($pdo), $in));
        break;
    case 'pull':
        respond(200, ['ok' => true] + action_pull($pdo, authenticate($pdo), $in));
        break;
    case 'join_preview':
        respond(200, ['ok' => true] + action_join_preview($pdo, authenticate($pdo), $in));
        break;
    case 'gemini':
        respond(200, ['ok' => true] + action_gemini($pdo, authenticate($pdo), $in, $config));
        break;
    case 'join':
        respond(200, ['ok' => true] + action_join($pdo, authenticate($pdo), $in));
        break;
    default:
        fail(404, 'Nieznana akcja');
}
