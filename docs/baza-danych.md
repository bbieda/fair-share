# Baza danych (SQLite / Room)

Dane zapisują się lokalnie w pliku **`fairshare.db`** na telefonie:
`/data/data/com.fairshare/databases/fairshare.db`

Podgląd na żywo: Android Studio → **App Inspection → Database Inspector** (aplikacja uruchomiona w trybie debug).
Pobranie pliku: **Device Explorer** → ścieżka wyżej → prawy klik → *Save As* (razem z plikami `-wal` i `-shm`), potem otwórz np. w DB Browser for SQLite.

Schemat każdej wersji bazy Room zapisuje przy buildzie do `app/schemas/com.fairshare.data.db.FairShareDatabase/<wersja>.json` — commitujcie te pliki.

## Tabele

| Tabela | Co trzyma | Kolumny |
|---|---|---|
| `users` | osoby; dokładnie jedna ma `isMe = 1` (właściciel telefonu, domyślnie „Ty”) | `id`, `name`, `isMe`, `createdAt` |
| `friend_groups` | grupy | `id`, `name`, `archived`, `createdAt` |
| `group_members` | kto jest w której grupie | `groupId`, `userId`, `joinedAt` |
| `expenses` | wydatki | `id`, `groupId`, `payerId`, `amount`, `description`, `category`, `date`, `createdAt` |
| `expense_shares` | ile z wydatku przypada na osobę | `expenseId`, `userId`, `amount` |

- Kwoty (`amount`) są w **groszach** (`Long`): 12,50 zł = `1250`. Nigdy `Double`.
- `date` to `LocalDate.toEpochDay()`.
- `category` to nazwa z `ExpenseCategory` (`FOOD`, `GROCERIES`, …) albo `NULL`.
- Udziały jednego wydatku zawsze sumują się do jego kwoty (100 zł na 3 → 33,34 + 33,33 + 33,33).
- Usunięcie grupy/wydatku usuwa kaskadowo powiązane wiersze.

## Saldo

- **W grupie:** `zapłacił − suma jego udziałów`. Dodatnie = inni są mu winni. Salda w grupie sumują się do zera.
- **Między mną a osobą X:** `udziały X w wydatkach, które ja płaciłem − moje udziały w wydatkach, które płacił X`.

## Kod

- `data/db/Entities.kt` – tabele
- `data/db/FairShareDaos.kt` – zapytania SQL
- `data/db/FairShareDatabase.kt` – konfiguracja bazy i wpis „Ty” przy tworzeniu
- `data/FairShareRepository.kt` – jedyne miejsce, z którego UI czyta/zapisuje dane
- `data/model/Money.kt` – parsowanie, formatowanie i dzielenie kwot

Zmiana tabel = podbicie `version` w `FairShareDatabase` + migracja (inaczej aplikacja się wywali u osób z istniejącą bazą).
