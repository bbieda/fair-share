# Skaner paragonów

Zdjęcie paragonu → lista pozycji i kwota do zapłaty. Wersja testowa: ekran pokazuje, co odczytano, ile to trwało i
który etap to odczytał, a „Użyj w wydatku” wpisuje kwotę, sklep i kategorię do formularza „Nowy wydatek”.

## Jak to działa

1. **ML Kit** (Google, na telefonie, bez internetu) czyta tekst ze zdjęcia. Gdy zdjęcie jest obrócone, próbuje
   innych orientacji. Linie składa w wiersze paragonu (nazwa i cena w jednym wierszu), z poprawką na pochylenie
   zdjęcia: przy 1° cena na prawym brzegu jest pół wiersza niżej niż nazwa i trafiała do złego wiersza.
2. **Reguły** (`scanner/rules/RuleBasedReceiptParser.kt`) wyciągają z tekstu sklep, pozycje (z rabatami, kaucjami,
   „ilość × cena”) i kwotę do zapłaty. Trwa to milisekundy.
3. **Suma kontrolna:** jeśli pozycje sumują się do kwoty z paragonu, wynik jest od razu gotowy (zielony znaczek).
4. Tylko gdy się nie sumują, zdjęcie idzie do **Gemini 3.5 Flash-Lite**. Jeśli nie odpowie w 4 s (zwykle
   odpowiada w 2,5 s), równolegle startuje **Gemini 3.1 Flash-Lite** i wygrywa ten, który odpowie pierwszy; przy
   błędzie 503 następny model startuje od razu. Ten sam prompt i schemat JSON co w benchmarku, jedna runda
   poprawki. Model tylko przepisuje i oznacza linie, sumy liczy aplikacja. Cały krok AI ma limit 25 s; potem
   zostaje odczyt reguł i przycisk „Spróbuj ponownie”.
5. **Kilka paragonów na jednym zdjęciu:** gdy ML Kit znajdzie 2+ nagłówki „PARAGON FISKALNY” oddzielone pustym
   pasem (paragony obok siebie, jeden pod drugim, także 3 w kształcie litery L), każdy paragon jest czytany osobno,
   z własną sumą kontrolną;
   do Gemini idzie tylko wycinek zdjęcia z paragonem, który się nie zgadza. Ekran pyta: **jeden wydatek** (sumy
   razem) albo **osobno** (po zapisaniu pierwszego wydatku od razu otwiera się formularz z następnym paragonem).
   Gdy nagłówki są dwa, ale paragonów nie da się rozdzielić (stykają się), ekran ostrzega zamiast pokazać zieloną sumę.

Skąd taki wybór: `WNIOSKI.md` z benchmarku (ocena 28.10.2026 na 44 nowych paragonach testowych). Ten układ
odczytał 44/44 paragonów bez błędu (204/204 cen), AI było potrzebne dla 6 z 44, typowo 1,6 s, ok. 0,19 $ za
1000 paragonów. Same reguły: 38/44, i nigdy nie dały zgodnej sumy przy błędnym odczycie.

Reguły to kopia pliku z benchmarku (`backend/src/main/kotlin/paragon/rules/RuleBasedReceiptParser.kt`, różni się
tylko pakietem). Zmieniajcie je w obu miejscach i puszczajcie testy z obu stron.

## Uruchomienie na telefonie (USB)

**Telefon (raz):**
1. Ustawienia → Informacje o telefonie → 7 razy stuknij „Numer kompilacji” (włącza Opcje programisty).
2. Ustawienia → System → Opcje programisty → włącz **Debugowanie USB**.
   Xiaomi / Redmi / POCO: włącz też **Instaluj przez USB**.
3. Podłącz kablem do komputera i na telefonie zaakceptuj „Zezwolić na debugowanie USB?”.

**Komputer:** JDK 17+ i Android SDK (najprościej: Android Studio). Potem dwuklik
**`scripts\uruchom-na-telefonie.bat`**: zbuduje aplikację, zainstaluje ją na telefonie i uruchomi. Za pierwszym
razem kilka minut (Gradle pobiera biblioteki), potem kilkadziesiąt sekund. Przebieg zapisuje się w `run-phone.log`.

Można też w Android Studio: Open → ten folder → wybrać telefon → Run.

**Test:** przycisk „+” → „Nowy wydatek” → wiersz **Paragon: Zeskanuj paragon** → „Zrób zdjęcie” albo
„Wybierz z galerii” → wynik → „Użyj w wydatku”.

Gdy coś jest źle odczytane albo aplikacja się wysypie: dwuklik **`scripts\zapisz-log.bat`**. Do `phone-log.txt`
trafią czasy, tekst z ML Kit, odczyt reguł i Gemini oraz błędy. Z tego tekstu da się dopisać przypadek testowy
do reguł.

## Zapisane skany (tylko wersja debug)

Wersja debug (ta budowana skryptem i z Android Studio) zapisuje każdy skan na telefonie, żeby błędny odczyt dało
się obejrzeć obok logu: `Android/data/com.fairshare/files/skany/<data_godzina>/` z plikami `zdjecie.jpg`,
`ocr.txt` (tekst z ML Kit, który dostały reguły), `wynik.txt` (czasy, odczyt reguł i Gemini, błędy) i
`linie.json` (linie z ML Kit z ich położeniem na zdjęciu, do sprawdzania podziału na kilka paragonów). Zostaje 50
ostatnich. Ekran wyniku pokazuje nazwę folderu. Galeria tych zdjęć nie widzi, ale przez kabel USB w trybie
przesyłania plików są widoczne z komputera.

`scripts\zapisz-log.bat` kopiuje nowe skany do folderu `skany\` w projekcie (jest w `.gitignore`, bo to zdjęcia
prawdziwych paragonów). Wersja release niczego nie zapisuje (`SAVE_SCANS` w `app/build.gradle.kts`).

## Klucz Gemini

Bez klucza skaner działa tylko z ML Kit + regułami (ekran o tym mówi). Klucz z https://aistudio.google.com/apikey
podaje się na jeden z dwóch sposobów:

- w `local.properties` (nie trafia do gita): `gemini.apiKey=TWÓJ_KLUCZ`
- albo zmienna środowiskowa `GEMINI_API_KEY` (np. `setx GEMINI_API_KEY "..."` i nowe okno).

Klucz jest wbudowywany w APK przy buildzie, więc to tylko do testów na własnym telefonie. W aplikacji dla
użytkowników wywołanie Gemini musi iść przez nasz backend (np. `POST /api/receipts/parse-image`), żeby klucz nie
wyciekł. Na darmowym planie Google może używać zdjęć do trenowania; na płatnym nie.

## Testy

`gradlew testDebugUnitTest`: reguły (te same przypadki co w benchmarku), składanie wierszy z ML Kit, suma
kontrolna i przeliczanie odpowiedzi modelu (rabaty, kaucje, rabat na cały paragon).

## Pliki

| Plik | Co robi |
|---|---|
| `scanner/ReceiptScanner.kt` | cały przebieg: ML Kit → reguły → (Gemini, gdy suma się nie zgadza) |
| `scanner/ReceiptOcr.kt` | ML Kit: wczytanie zdjęcia (EXIF, max 4096 px), orientacja, wiersze |
| `scanner/ReceiptRows.kt` | linie z ML Kit → wiersze paragonu (ten sam algorytm co w benchmarku) |
| `scanner/rules/RuleBasedReceiptParser.kt` | reguły (kopia z benchmarku) |
| `scanner/GeminiReceiptReader.kt` | wywołanie Gemini, model zapasowy, runda poprawki |
| `scanner/ReceiptPrompt.kt` | prompt i prośba o poprawkę (identyczne z backendem) |
| `scanner/ScannedReceipt.kt` | wynik w groszach, suma kontrolna, przeliczanie linii modelu |
| `scanner/ScanArchive.kt` | wersja debug: zapis zdjęcia i odczytu każdego skanu |
| `scanner/ReceiptSplitter.kt` | kilka paragonów na jednym zdjęciu: podział linii między paragony |
| `ui/screens/SkanujParagonScreen.kt`, `ui/viewmodel/ScanViewModel.kt` | ekran skanera |

## Co dalej

- **Podział po pozycjach:** skaner zwraca listę pozycji, ale wydatek nadal dzieli się równo, bo tak działa
  model danych (`expense_shares`). Następny krok: przypisywanie pozycji do osób.
- Wywołanie Gemini przez backend zamiast klucza w APK.
- Zmierzyć czas ML Kit na prawdziwych telefonach (w benchmarku był emulator).
- Ewentualnie przycinanie zdjęcia do paragonu (ML Kit Document Scanner) przed odczytem.
