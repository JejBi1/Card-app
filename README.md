# CardStudy – kartičky na učení (Android + web/iPhone)

🇨🇿 Česky · 🇬🇧 [English](README.en.md)

Kartičky pro jazyky (s výslovností) i jiné předměty. Funguje offline, data zůstávají v zařízení.

## Instalace – Android
1. Zkopíruj `CardStudy.apk` do telefonu (Disk, kabel, e-mail…) a otevři ho.
2. Povol „Instalovat neznámé aplikace“ pro aplikaci, ze které APK otevíráš.
3. Pro výslovnost musí být v telefonu hlas daného jazyka: *Nastavení → Systém → Jazyky → Převod textu na řeč → Google → Nainstalovat hlasová data*. Aplikace na chybějící hlas sama upozorní.

Pozn.: CardStudy je jiná aplikace než dřívější „Španělština“ (jiné ID). Data z ní přeneseš exportem CSV a importem do CardStudy.

## Seznamy kartiček (CSV)
```
anglicky;česky;poznámka;stav
the dog;pes;;neumím
to speak;mluvit;nepravidelné;umím
```
- 1. sloupec = slovo / otázka, 2. = překlad / odpověď. Poznámka, stav a hlavička jsou nepovinné.
- **Jazyk** (pro hlas) se pozná z hlavičky: španělsky, anglicky, anglicky US, německy, francouzsky, italsky, portugalsky, rusky, polsky, česky (nebo es, en, de, …). Bez hlavičky se aplikace po importu zeptá; změnit jde v menu ⋮ → *Jazyk a hlas*.
- **Jiné předměty:** hlavička `otázka;odpověď` → seznam bez hlasu.
- Oddělovač `;`, `,` nebo tabulátor. Z Excelu ukládej jako **CSV UTF-8** (klasické „CSV“ ve Windows-1250 neumí např. ñ).
- Název souboru = název seznamu. Opakovaný import stejného souboru seznam doplní a pokrok zachová.
- Export: menu ⋮ → *Export CSV – všechny / jen neumím* (s jazykem v hlavičce a sloupcem stav, jde zpět importovat).

## Ovládání
- **Neumím (N)** / **Všechny (N)** – kolo jen s neznámými, nebo se všemi kartičkami.
- Klepnutí na kartičku = otočení (co se ukáže první, jde přepnout v nastavení).
- **Swipe doleva = umím**, **swipe doprava = neumím** (nebo tlačítka dole).
- 🔊 přehraje výslovnost, 🐢 pomaleji. Hlas pro každý jazyk a rychlost v nastavení.
- ⋮ → *Trénink výslovnosti* (jen u jazyků) – řekneš slovo do mikrofonu, aplikace ukáže, jak přesně tě rozpoznala.

## iPhone (a jakýkoli telefon) – webová verze ve složce `pwa/`
Stejné ovládání a stejný CSV formát, běží v Safari a funguje offline.

**Aplikace běží na <https://jejbi1.github.io/Card-app/pwa/>**
1. Na iPhonu otevři adresu v **Safari** → Sdílet → **Přidat na plochu**.
2. CSV dostaneš do iPhonu přes iCloud Drive / AirDrop / e-mail → v aplikaci *Import CSV* → Procházet.

Kartičky a pokrok zůstávají jen v prohlížeči daného zařízení, nic se neodesílá na server.

Vlastní kopie na GitHub Pages (nepovinné): na GitHubu **Fork** tohoto repozitáře →
**Settings → Pages** → Source: *Deploy from a branch*, Branch: `main`, složka `/ (root)` → Save.
Po 1–2 minutách běží na `https://<uživatel>.github.io/Card-app/pwa/`.

Nová verze: nahraj změněné soubory a v `sw.js` zvyš číslo v `CACHE = 'cardstudy-v3'`.
Hlasy na iPhonu: Nastavení → Zpřístupnění → Předčítaný obsah → Hlasy → jazyk → stáhnout (ideálně „vylepšený“).

## Build Android
`build.ps1` (JDK 17, Android SDK a Gradle jsou v `%USERPROFILE%\android-dev`). Projekt jde otevřít i v Android Studiu.
