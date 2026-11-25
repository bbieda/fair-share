# Baza danych (SQLite / Room)

Od wersji 6 lokalna baza jest **kopią danych z serwera** (grupy zalogowanego konta) plus kolejką zmian jeszcze
niewysłanych (`pending_ops`). Serwer, logowanie i synchronizacja są opisane w `docs/chmura.md`. Przy przejściu
z wersji ≤ 5 lokalne dane są kasowane.

Plik **`fairshare.db`** na telefonie:
`/data/data/com.fairshare/databases/fairshare.db`

Podgląd na żywo: Android Studio → **App Inspection → Database Inspector** (aplikacja uruchomiona w trybie debug).
Pobranie pliku: **Device Explorer** → ścieżka wyżej → prawy klik → *Save As* (razem z plikami `-wal` i `-shm`), potem otwórz np. w DB Browser for SQLite.

Schemat każdej wersji bazy Room zapisuje przy buildzie do `app/schemas/com.fairshare.data.db.FairShareDatabase/<wersja>.json` — commitujcie te pliki.

## Tabele

| Tabela | Co trzyma | Kolumny |
|---|---|---|
| `users` | osoby; dokładnie jedna ma `isMe = 1` (zalogowane konto); `hasAccount = 0` to osoba dodana z imienia | `id`, `uid`, `name`, `isMe`, `hasAccount`, `paymentDetails`, `photoUri` (tylko lokalnie), `createdAt` |
| `friend_groups` | grupy | `id`, `uid`, `name`, `archived`, `inviteCode`, `createdAt` |
| `group_members` | kto jest w której grupie | `groupId`, `userId`, `joinedAt` |
| `expenses` | wydatki | `id`, `uid`, `groupId`, `payerId`, `amount`, `description`, `category`, `date`, `isSettlement`, `createdAt` |
| `expense_shares` | ile z wydatku przypada na osobę | `expenseId`, `userId`, `amount` |
| `expense_items` | pozycje paragonu wydatku dzielonego „po pozycjach” (od wersji 5) | `id`, `expenseId`, `name`, `price`, `position` |
| `expense_item_shares` | ile z pozycji przypada na osobę | `itemId`, `userId`, `amount` |
| `activity_logs` | aktywność: moje zmiany i (po synchronizacji) zmiany innych w moich grupach; `seen` tylko lokalnie | `id`, `uid`, `groupId`, `actionType`, `description`, `seen`, `involvesMe`, `timestamp` |
| `pending_ops` | kolejka zmian do wysłania na serwer (offline) | `id`, `type`, `payload` (JSON), `createdAt` |

- Kwoty (`amount`) są w **groszach** (`Long`): 12,50 zł = `1250`. Nigdy `Double`.
- `date` to `LocalDate.toEpochDay()`.
- `id` zostaje na telefonie; `uid` (UUID) to identyfikator wspólny z serwerem i innymi telefonami.
- `category` to nazwa z `ExpenseCategory` (`FOOD`, `GROCERIES`, …) albo `NULL`.
- Udziały jednego wydatku zawsze sumują się do jego kwoty (100 zł na 3 → 33,34 + 33,33 + 33,33).
- Wydatek dzielony po pozycjach: ceny pozycji sumują się do kwoty wydatku, udziały w pozycji do jej ceny, a
  udziały jednej osoby we wszystkich pozycjach do jej wiersza w `expense_shares`. Salda i statystyki liczą się
  dalej tylko z `expense_shares`; pozycje są po to, żeby było widać, kto za co płaci. Opis: `docs/pozycje.md`.
- Usunięcie grupy/wydatku usuwa kaskadowo powiązane wiersze.

## Saldo

- **W grupie:** `zapłacił − suma jego udziałów`. Dodatnie = inni są mu winni. Salda w grupie sumują się do zera.
- **Między mną a osobą X:** `udziały X w wydatkach, które ja płaciłem − moje udziały w wydatkach, które płacił X`.

## Kod

- `data/db/Entities.kt` – tabele
- `data/db/FairShareDaos.kt` – zapytania SQL
- `data/db/FairShareDatabase.kt` – konfiguracja bazy i wpis „Ty” przy tworzeniu
- `data/FairShareRepository.kt` – jedyne miejsce, z którego UI czyta/zapisuje dane (każdy zapis trafia też do `pending_ops`)
- `data/sync/` – sesja, klient API, synchronizacja z serwerem
- `data/model/Money.kt` – parsowanie, formatowanie i dzielenie kwot

Zmiana tabel = podbicie `version` w `FairShareDatabase` + migracja (inaczej aplikacja się wywali u osób z istniejącą bazą).
