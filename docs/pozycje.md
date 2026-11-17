# Podział paragonu na pozycje

Trzeci sposób podziału wydatku obok „Równo” i „Kwoty”: **„Pozycje”**, czyli kto co brał. Każda pozycja paragonu
ma zaznaczone osoby, które się na nią składają, i dzieli się między nie po równo. Udział osoby w wydatku to suma
jej części pozycji.

## Jak to działa w aplikacji

1. **Nowy wydatek → Paragon → skan → „Użyj w wydatku”.** Pozycje z paragonu trafiają do formularza. Jeśli
   sumują się do kwoty z paragonu, podział sam przełącza się na „Pozycje” i każda pozycja jest na początku
   wspólna dla wszystkich (wynik taki sam jak „Równo”). Jeśli suma się nie zgadza, zostaje kwota z paragonu i
   dotychczasowy podział, a pozycje czekają pod przyciskiem „Pozycje”.
2. **Formularz** w trybie „Pozycje” pokazuje, ile wychodzi na każdą osobę i ile ma pozycji, oraz ostrzeżenia
   (pozycje bez osoby, różnica do paragonu). Kwota wydatku to suma pozycji i nie da się jej wpisać ręcznie.
3. **„Podziel pozycje”** (osobny ekran): lista pozycji, przy każdej osoby z grupy.
   - stuknięcie osoby dodaje ją do pozycji albo zdejmuje,
   - przytrzymanie osoby: pozycja jest tylko jej,
   - „Wszyscy” / „Odznacz wszystkich”,
   - ołówek: poprawa nazwy i ceny (błędy OCR), usunięcie pozycji, oznaczenie jako rabat,
   - „+” u góry: nowa pozycja (da się też rozpisać wydatek ręcznie, bez skanu),
   - gdy pozycje nie sumują się do paragonu: przycisk dodający pozycję „Inne” albo „Rabat” na różnicę,
   - pasek na dole: suma na osobę, liczona na bieżąco.
4. **Zapis** jest w formularzu. Nie da się zapisać, gdy jakaś pozycja nie ma osoby.
5. **Szczegóły wydatku** (stuknięcie wydatku w grupie): podział na osoby z liczbą pozycji i lista pozycji z
   osobami oraz „twoją częścią”. Na liście wydatków taki wydatek ma dopisek „· 6 poz.”.

Kilka paragonów na jednym zdjęciu: „Jeden wydatek” łączy pozycje wszystkich paragonów, „Osobno” otwiera po
zapisie formularz z następnym paragonem i jego pozycjami.

## Grosze

Cena, która nie dzieli się równo, zostawia 1–2 grosze. Dostaje je ta osoba, która do tej pory dostała ich
najmniej (przy remisie niższe id), więc na całym paragonie nikt nie dopłaca więcej niż 1 gr ponad równy podział.
Gdy wszyscy składają się na wszystko, wynik jest identyczny z „Równo”. Kod i testy:
`data/model/ItemSplit.kt`, `ItemSplitTest`.

## Dane

Tabele `expense_items` i `expense_item_shares` (baza w wersji 5, migracja 4 → 5 w `FairShareDatabase`), opis w
`docs/baza-danych.md`. `FairShareRepository.addExpense(..., items = ...)` sprawdza przed zapisem, że pozycje
sumują się do udziałów osób. Salda, rozliczenia i statystyki korzystają dalej z `expense_shares`, więc nic się
w nich nie zmienia.

## Pliki

| Plik | Co robi |
|---|---|
| `data/model/ItemSplit.kt` | dzielenie pozycji między osoby, co do grosza |
| `ui/viewmodel/AddExpenseViewModel.kt` | pozycje w formularzu (`ItemDraft`), tryb `SplitMode.ITEMS`, walidacja i zapis |
| `ui/screens/PodzielPozycjeScreen.kt` | ekran „Podziel pozycje” |
| `ui/screens/DodajWydatekScreen.kt` | przycisk „Pozycje” i podsumowanie na osobę |
| `ui/screens/GrupaScreen.kt` | pozycje w szczegółach wydatku |
| `ui/navigation/FairShareApp.kt` | trasa `podziel_pozycje`, przekazanie paragonu ze skanera (`ScanHandover`) |

## Co dalej

- Edycja zapisanego wydatku (teraz: usuń i dodaj ponownie).
- Nierówny podział jednej pozycji (np. 2 z 6 piw).
- Statystyki po produktach i eksport pozycji do CSV.
