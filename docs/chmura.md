# Chmura: wspólne grupy na serwerze

Każdy loguje się swoim kontem (e-mail + hasło), a grupy, wydatki i spłaty są na serwerze, więc wszyscy w grupie widzą
to samo. Aplikacja działa też bez internetu: zapisuje zmiany lokalnie i wysyła je, gdy wróci sieć.

```
telefon (Room = lokalna kopia + kolejka zmian)  ──HTTPS/JSON──>  api.php  ──>  MySQL/MariaDB na tym samym hostingu
```

Telefon nigdy nie łączy się z bazą bezpośrednio, bo hasło do bazy nie może trafić do APK. Pośredniczy
`server/api/api.php`.

## Gdzie to stoi

| | Adres | Dostęp | Środowisko |
|---|---|---|---|
| **Produkcja** (w `gradle.properties`) | `https://fairshareapp.alwaysdata.net/fairshare/api.php` | z całego internetu | alwaysdata (plan Free), PHP 8.5, MariaDB 11.8 |
| Kopia na AGH | `https://student.agh.edu.pl/~mateuszw/fairshare/api.php` | tylko sieć AGH / VPN AGH | PHP 8.2 (CGI), MariaDB 10.11 |

Oba serwery mają osobne bazy, więc konta i dane na nich się nie łączą. Aplikacja używa tylko adresu
z `gradle.properties`.

> **Dlaczego nie AGH:** Apache na `student.agh.edu.pl` wpuszcza do stron domowych **tylko adresy AGH**
> (`Require ip 149.156.96.0/19 149.156.192.0/20 …` oraz IPv6 AGH w `/etc/apache2/mods-enabled/userdir.conf`).
> Z telefonu na LTE albo z domowego Wi‑Fi API na AGH jest niedostępne.

## Wdrożenie na alwaysdata

Konto `fairshareapp`, panel `admin.alwaysdata.com`. PHP działa z uprawnieniami konta, więc plik z hasłem do bazy
ma `chmod 600` i nie czyta go nikt inny. HTTPS dla `*.alwaysdata.net` jest wbudowany.

1. **Baza:** panel → *Databases → MySQL*: użytkownik + baza z pełnymi uprawnieniami. Host to
   `mysql-fairshareapp.alwaysdata.net`.
2. **SSH kluczem:** panel → *Remote access → SSH* (włącz logowanie hasłem tylko do wgrania klucza):
   ```powershell
   ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\alwaysdata_fairshare" -C fairshare-deploy-ad
   Get-Content "$env:USERPROFILE\.ssh\alwaysdata_fairshare.pub" | ssh fairshareapp@ssh-fairshareapp.alwaysdata.net "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
   ```
   W `%USERPROFILE%\.ssh\config` dodaj **jeden** wpis:
   ```
   Host ad-fs
       HostName ssh-fairshareapp.alwaysdata.net
       User fairshareapp
       IdentityFile ~/.ssh/alwaysdata_fairshare
       IdentitiesOnly yes
   ```
3. **Dane do bazy** wpisz sam przez `ssh ad-fs` i `nano` (nie `cat > plik`, bo zostaje w historii), oba pliki
   z `chmod 600`:
   - `~/fairshare-config.php`: wzór w `server/fairshare-config.example.php`;
   - `~/.my.cnf`: sekcja `[client]` z `host=`, `user=`, `password="..."`, `database=`.
4. **Tabele** (Git Bash, z katalogu repozytorium): `ssh ad-fs mysql < server/schema.sql`
5. **API**, to samo przy każdej aktualizacji `api.php` (bez linii `#!`, której potrzebuje tylko AGH):
   ```
   ssh ad-fs "mkdir -p ~/www/fairshare && : > ~/www/fairshare/index.html"
   ssh ad-fs "cat > ~/www/fairshare/api.php" < server/api/api.php
   ```
   `api.php` szuka `fairshare-config.php` dwa katalogi wyżej, czyli w `~`.
6. **Test:** `curl "https://fairshareapp.alwaysdata.net/fairshare/api.php?action=ping"` zwraca
   `{"ok":true,"service":"fairshare","tables":13}`. Błędy PHP są w `~/admin/logs/php/php.log`.

## Wdrożenie na AGH (tylko sieć AGH)

Potrzebne: konto w panelu AGH, **VPN AGH**, jeśli nie jesteś w sieci uczelni (SSH i phpMyAdmin działają tylko z niej).

Jak tam działa PHP: strony domowe uruchamiają `.php` jako **CGI przez suexec**, czyli z uprawnieniami właściciela
konta. Dlatego:
- plik musi zaczynać się od `#!/usr/bin/php-cgi` i mieć `chmod 755` (w repo tej linii nie ma, dokleja ją komenda
  wgrywania poniżej);
- plik z hasłem do bazy może mieć `chmod 600`. Czyta go tylko Twój użytkownik, nie `www-data` ani inni studenci.

1. **Konto UNIX:** panel AGH → *UNIX* → przy `student` kliknij *Załóż konto*.
2. **Baza:** panel AGH → *MySQL* → *Utwórz nową bazę danych*. Zapisz nazwę bazy, użytkownika i hasło.
3. **Klucz SSH** zamiast hasła, żeby wgrywać bez wpisywania hasła (PowerShell, `LOGIN` = login AGH):
   ```powershell
   ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\agh_fairshare" -C fairshare-deploy
   Get-Content "$env:USERPROFILE\.ssh\agh_fairshare.pub" | ssh LOGIN@student.agh.edu.pl "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
   ```
   Do `%USERPROFILE%\.ssh\config` dodaj **jeden** wpis:
   ```
   Host agh-fs
       HostName student.agh.edu.pl
       User LOGIN
       IdentityFile ~/.ssh/agh_fairshare
       IdentitiesOnly yes
   ```
4. **Dane logowania do bazy** wpisz sam, przez `ssh agh-fs` i `nano` (nie `cat > plik`, bo zostaje w historii
   shella), w dwóch plikach w katalogu domowym, oba z `chmod 600`:
   - `~/fairshare-config.php`: wzór w `server/fairshare-config.example.php`; czyta go `api.php`;
   - `~/.my.cnf`: sekcja `[client]` z `host=mysql.agh.edu.pl`, `user=`, `password="..."`, `database=`; dzięki niej
     `mysql` łączy się bez podawania hasła w komendzie.
5. **Tabele** (z katalogu repozytorium, Git Bash): `ssh agh-fs mysql < server/schema.sql`
6. **API** (to samo przy każdej aktualizacji `api.php`):
   ```
   ssh agh-fs "mkdir -p ~/public_html/fairshare && chmod 755 ~/public_html ~/public_html/fairshare && : > ~/public_html/fairshare/index.html"
   { printf '#!/usr/bin/php-cgi\n'; cat server/api/api.php; } | ssh agh-fs "cat > ~/public_html/fairshare/api.php && chmod 755 ~/public_html/fairshare/api.php"
   ```
   `api.php` szuka `fairshare-config.php` dwa katalogi wyżej, więc trzymaj się układu `~/public_html/fairshare/api.php`.
7. **Test** (z sieci AGH albo przez VPN):
   `curl "https://student.agh.edu.pl/~LOGIN/fairshare/api.php?action=ping"` zwraca
   `{"ok":true,"service":"fairshare","tables":13}`.

   | Widzisz | Co to znaczy |
   |---|---|
   | `"tables":0` albo mniej niż 13 | `schema.sql` nie jest wczytany do tej bazy (krok 5) |
   | `Serwer nie jest skonfigurowany` | Brak `~/fairshare-config.php` albo zły układ katalogów |
   | `Błąd serwera…` (HTTP 500) | Najczęściej złe dane w `~/fairshare-config.php` |
   | Widać kod PHP albo błąd 500 z HTML | Brak linii `#!/usr/bin/php-cgi` albo `chmod 755` (krok 6) |
   | `403 Forbidden` | Łączysz się spoza sieci AGH: włącz VPN |

8. **Adres w aplikacji:** `fairshare.apiUrl` w `gradle.properties`. Własny serwer testowy możesz ustawić
   w `local.properties` (ten sam klucz, ma pierwszeństwo).

Zmiana tabel na działającym serwerze wymaga skryptu `ALTER TABLE`. Ponowne wczytanie `schema.sql` na istniejące
tabele się nie uda, a usunięcie tabel kasuje dane wszystkich użytkowników.

## Jak z tego korzystać we dwoje

1. Każdy zakłada konto na ekranie startowym (*Nowe konto*).
2. Osoba A tworzy grupę i może od razu dodać osobę B z imienia (np. „Kasia”) oraz wpisywać wspólne wydatki.
3. W grupie: *Zaproś* pokazuje kod, np. `K7MQ-2XPA`. A wysyła go B.
4. B: *Grupy → Dołącz z kodu* → wpisuje kod → wybiera z listy „Kasia” → *Dołącz*. Wszystkie wydatki i udziały
   „Kasi” przechodzą na konto B, a obie osoby widzą te same salda.
5. Zmiany drugiej osoby pojawiają się same: synchronizacja startuje przy otwarciu aplikacji, po każdej zmianie, co
   30 s, gdy aplikacja jest na ekranie, i po odzyskaniu sieci. Ręcznie: *Profil → Synchronizacja*.

## Jak działa synchronizacja (dla programistów)

- **Id:** każdy wiersz ma `uid` (UUID), wspólny dla serwera i telefonów, nadawany przez telefon przy tworzeniu. Lokalne
  `id` (Long) zostają tylko na telefonie i używa ich UI. Tłumaczenie między nimi jest w `SyncManager`.
- **Zapis (push):** repozytorium w jednej transakcji Room zapisuje zmianę lokalnie i dokłada wpis do `pending_ops`
  (`data/sync/Ops.kt`). `SyncManager` wysyła kolejkę po kolei do `api.php?action=push`. Każda operacja jest
  idempotentna: powtórzona po zerwanym połączeniu niczego nie dubluje. Operację łamiącą reguły (np. udziały nie
  sumują się do kwoty) serwer odrzuca. Telefon wtedy ją usuwa, wpisuje powód do Aktywności i przy następnym
  pobraniu ściąga wszystko od nowa.
- **Odczyt (pull):** serwer stempluje każdą zmianę kolejnym numerem z `fs_sync_counter` (`version`). Telefon pamięta
  najwyższy numer (`Session.cursor`) i prosi o nowsze wiersze ze swoich grup. Piszący trzyma blokadę licznika do
  commita, więc numery pojawiają się po kolei i żadnego wiersza nie da się przeskoczyć. Usunięcia przychodzą jako
  „nagrobki” (`deleted = 1`). Po dodaniu konta do grupy (`join_version`) serwer sam wysyła pełny stan, bo starsze
  wiersze tej grupy mają niższe numery.
- **Bez nadpisywania:** pobrane dane są zapisywane tylko przy pustej kolejce. Inaczej starsza wersja z serwera
  mogłaby przykryć lokalną zmianę, która jeszcze nie wyszła.
- **Konflikty:** wydatki się tylko dodaje i usuwa, więc konfliktów jest mało. Przy nazwie grupy i profilu wygrywa
  ostatni zapis.
- **Przejęcie osoby z imienia:** `join` z `claim_person_id` przepina w tej grupie płacącego i udziały (także
  w pozycjach paragonu) na konto i zapisuje to w `fs_claims`. Dzięki temu wydatek z tamtą osobą, zapisany offline
  przez kogoś innego, nadal przejdzie.

## Test serwera

`server/test/scenario.py` przechodzi przez scenariusz dwóch osób na pustej bazie: rejestracja, grupa, przejęcie
osoby z imienia, wydatek zapisany offline, odrzucone zmiany, usuwanie i drugi telefon tego samego konta. Puść go po
każdej zmianie `api.php` albo `schema.sql`. Przechodzi na PHP 7.4 z MySQL 8.0 i z MariaDB 10.6 (Docker), na AGH
(PHP 8.2, MariaDB 10.11) i na alwaysdata (PHP 8.5, MariaDB 11.8). Z katalogu
repozytorium, w Git Bash, z włączonym Dockerem:

```
docker network create fstest
docker run -d --name fsdb --network fstest -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=fairshare \
  -e MYSQL_USER=fs -e MYSQL_PASSWORD=fs-test-pass mysql:8.0
docker build -t fstest-php server/test
# gdy baza wstanie (kilkanaście sekund):
docker exec -i fsdb mysql -ufs -pfs-test-pass fairshare < server/schema.sql
MSYS_NO_PATHCONV=1 docker run -d --name fsweb --network fstest -p 8089:80 \
  -v "$PWD/server/api:/var/www/html/fairshare:ro" \
  -v "$PWD/server/test/fairshare-config.test.php:/var/www/fairshare-config.php:ro" fstest-php
python server/test/scenario.py http://localhost:8089/fairshare/api.php
docker rm -f fsdb fsweb && docker network rm fstest     # sprzątanie
```

Scenariusz zakłada pustą bazę. Przed kolejnym uruchomieniem wczytaj schemat od nowa
(`DROP DATABASE` / `CREATE DATABASE`) albo postaw nowe kontenery.

## Bezpieczeństwo

- **Żadnych sekretów w repo ani w APK.** Hasło do bazy i klucz Gemini są tylko w `~/fairshare-config.php` na
  serwerze (`chmod 600`, poza katalogiem WWW). Skaner woła Gemini przez `api.php?action=gemini`. Repo jest publiczne,
  a APK da się rozpakować, więc nic, co w nich jest, nie może być tajne. Adres API tajny nie jest.
- **PHP działa z uprawnieniami konta** (alwaysdata: własny proces, AGH: suexec), więc innych kont na serwerze
  config nie dotyczy.
- **Hasła kont** są przechowywane jako `password_hash` (bcrypt). Tokeny logowania to 256 losowych bitów; serwer
  trzyma tylko ich SHA-256. Token nieużywany 180 dni wygasa, a wylogowanie usuwa go od razu.
- **Limity** (`fs_rate_events`): 10 błędnych haseł na konto i 30 na adres IP w 15 minut, 5 nowych kont na IP na
  godzinę, Gemini 60 zapytań na konto i 300 na serwer dziennie.
- **Dostęp do danych:** każda zmiana jest sprawdzana pod kątem członkostwa w grupie. Pobierane są tylko grupy, do
  których się należy. Do grupy wchodzi się wyłącznie z kodem (31^8 kombinacji), a kto ma kod, może wybrać, którą
  osobą dodaną z imienia jest, więc kod wysyłaj tylko właściwej osobie.
- **Telefon:** tylko HTTPS (Android blokuje nieszyfrowany ruch), `allowBackup="false"`, więc token i lokalna kopia
  nie trafiają do kopii zapasowych.
- **Logi serwera** zawierają tylko komunikat i miejsce błędu, bez argumentów, czyli bez haseł.
- **Wdrożenie:** dostęp SSH tylko kluczem (`fairshare-deploy`, `fairshare-deploy-ad`). Gdy nie jest potrzebny, usuń
  go z `~/.ssh/authorized_keys` i z komputera.
- Zmiany schematu na działających bazach: `server/migrations/` (np. `ssh ad-fs mysql < server/migrations/001_rate_limits.sql`).

## Ograniczenia

- Nie ma resetu hasła (serwer nie wysyła e-maili). W razie potrzeby ustawia się nowy hash w `fs_accounts` przez
  phpMyAdmin (`password_hash()` w PHP).
- Synchronizacja działa, gdy aplikacja jest otwarta (oraz przy odzyskaniu sieci, póki proces żyje). Nie ma pushy
  w tle, więc powiadomienie o wydatku drugiej osoby przychodzi przy najbliższej synchronizacji.
- Zdjęcie profilowe zostaje na telefonie, inni go nie widzą.
- Nie da się usunąć osoby z grupy ani usunąć grupy (aplikacja tego wcześniej też nie miała).
- Ważność bazy na serwerze AGH jest powiązana z ważnością konta jej właściciela. Limit to 256 MB, czyli bardzo dużo
  jak na tę aplikację.
