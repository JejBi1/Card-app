# CardStudy – flashcards for learning (Android + web/iPhone)

🇨🇿 [Česky](README.md) · 🇬🇧 English

Flashcards for languages (with pronunciation) and any other subject. Works offline, your data stays on the device.

> The app's user interface is in Czech.

## Install – Android
1. Copy `CardStudy.apk` to your phone (Drive, cable, e-mail…) and open it.
2. Allow “Install unknown apps” for the app you open the APK from.
3. Pronunciation needs a voice for the language installed on the phone: *Settings → System → Languages → Text-to-speech output → Google → Install voice data*. The app warns you when a voice is missing.

## Card lists (CSV)
```
english;czech;note;status
the dog;pes;;no
to speak;mluvit;irregular;yes
```
- Column 1 = word / question, column 2 = translation / answer. Note, status and the header row are optional.
- **Language** (for the voice) is detected from the header: `spanish`, `english`, `german`, `french`, `italian`, `russian`, … (or `es`, `en`, `de`, …; Czech names work too). Without a header the app asks after import; you can change it later in menu ⋮ → *Jazyk a hlas* (Language and voice).
- **Other subjects:** header `question;answer` → a list without a voice.
- Status: `yes` / `1` / `x` = known, `no` / `0` = not known (Czech `umím` / `neumím` also work).
- Separator `;`, `,` or tab. From Excel save as **CSV UTF-8** (plain “CSV” in a legacy code page can't store e.g. ñ).
- File name = list name. Importing the same file again adds new cards and keeps your progress.
- Export: menu ⋮ → *Export CSV – všechny / jen neumím* (all / only not known). The export includes the language header and a status column, so it can be imported back.

## Controls
- **Neumím (N)** / **Všechny (N)** – a round with only the cards you don't know yet, or with all cards.
- Tap a card to flip it (which side is shown first can be changed in settings).
- **Swipe left = I know it**, **swipe right = I don't** (or use the buttons at the bottom).
- 🔊 plays the pronunciation, 🐢 slower. Voice per language and speech rate are in settings.
- ⋮ → *Trénink výslovnosti* (pronunciation practice, languages only) – say the word into the microphone and the app shows how exactly it recognised you.

## iPhone (and any phone) – web version in the `pwa/` folder
Same controls and the same CSV format, runs in Safari and works offline.

**The app runs at <https://jejbi1.github.io/Card-app/pwa/>**
1. On iPhone open the address in **Safari** → Share → **Add to Home Screen**.
2. Get CSV files onto the iPhone via iCloud Drive / AirDrop / e-mail → in the app *Import CSV* → Browse.

Cards and progress stay only in the browser on that device; nothing is sent to a server.

Your own copy on GitHub Pages (optional): **Fork** this repository on GitHub →
**Settings → Pages** → Source: *Deploy from a branch*, Branch: `main`, folder `/ (root)` → Save.
After 1–2 minutes it runs at `https://<user>.github.io/Card-app/pwa/`.

New version: upload the changed files and bump the number in `CACHE = 'cardstudy-v3'` in `sw.js`.
Voices on iPhone: Settings → Accessibility → Spoken Content → Voices → language → download (ideally an “Enhanced” one).

## Android build
`build.ps1` (JDK 17, Android SDK and Gradle are expected in `%USERPROFILE%\android-dev`). The project can also be opened in Android Studio.
