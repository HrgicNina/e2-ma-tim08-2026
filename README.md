# Slagalica (Mobilne aplikacije 2025/26)

Android aplikacija inspirisana kvizom Slagalica.

## Tehnologije
- Java
- XML
- Firebase Authentication
- Firebase Firestore
- Android Studio

## Preduslovi
- Android Studio (stable)
- Android SDK (min SDK 30)
- Emulator ili fizicki Android uredjaj
- Firebase projekat + `google-services.json`

## Pokretanje aplikacije

1. Klonirati repozitorijum:
   ```bash
   git clone <URL_REPO>
   ```

2. Otvoriti projekat u Android Studio (`Open`).

3. Proveriti da je fajl `google-services.json` u:
   - `app/google-services.json`

4. Sacekati Gradle sync.

5. Pokrenuti aplikaciju:
   - `Run app` (emulator ili telefon).

## Napomena
Ako se javi greska pri build-u:
1. `Build > Clean Project`
2. `Build > Assemble Project`
3. ponovo `Run app`.
