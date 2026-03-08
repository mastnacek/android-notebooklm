# NotebookLM for Android

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="128" alt="NotebookLM icon"/>
</p>

<p align="center">
  <b>Neoficiální Android klient pro Google NotebookLM</b><br/>
  <sub>Postavený na reverse-engineered batchexecute RPC API</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-green?logo=android" alt="Min SDK 26"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1-blue?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-2025.03-blue?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/license-personal-lightgrey" alt="License"/>
</p>

---

## Co to umí

- **Autentizace přes WebView** — Google cookies → CSRF + session tokeny, šifrované úložiště (AES-256-GCM)
- **Seznam sešitů** — fulltext hledání v názvech, sémantické hledání přes embeddings, řazení, oblíbené, AI kategorizace
- **Detail sešitu** se 4 taby:
  - **Chat** — konverzace s AI (streaming), AI summary, ukládání odpovědí jako poznámky
  - **Sources** — seznam zdrojů (PDF, URL, YouTube, Text), přidávání, swipe-to-delete, deduplikace
  - **Artifacts** — audio, reporty, kvízy, mind mapy, infografiky — přehrávání, stahování, generování
  - **Notes** — poznámky vytvořené z chatu
- **Audio přehrávač** — ExoPlayer s cookie-authenticated streaming
- **Sémantické vyhledávání** — OpenRouter embeddings (qwen3-embedding-8b, 512 dim), SQLite KNN s cosine similarity
- **Deduplikace zdrojů** — detekce duplicit dle názvu, typu a hash obsahu
- **Dark/Light theme** — Gruvbox paleta, terminálová estetika, monospace font

## Screenshoty

> TODO: Přidej screenshoty do `docs/screenshots/`

## Tech stack

| | |
|---|---|
| **Jazyk** | Kotlin 2.1, Java 17 |
| **UI** | Jetpack Compose (BOM 2025.03), Material 3 |
| **HTTP** | Ktor 3.1.1 (CIO engine) |
| **Serializace** | kotlinx-serialization-json 1.8 |
| **Auth** | EncryptedSharedPreferences (AndroidX Security Crypto) |
| **Audio** | Media3 ExoPlayer 1.6 |
| **Embeddings** | SQLite (vlastní KNN), OpenRouter API |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 |

## Architektura

```
app/src/main/java/dev/jara/notebooklm/
├── auth/           # WebView login, cookie management, token extraction
├── rpc/            # batchexecute RPC klient (16 metod), encoder/decoder
├── search/         # Embedding DB (SQLite KNN), OpenRouter API klient
└── ui/             # Compose screens, ViewModel, theme, komponenty
    ├── AppViewModel.kt          # Hlavní state management (StateFlow)
    ├── NotebookListScreen.kt    # Seznam sešitů + hledání
    ├── NotebookDetailScreen.kt  # Detail se 4 taby
    ├── Theme.kt                 # Gruvbox paleta, design system
    └── ...                      # AudioPlayer, Markdown, Settings
```

### RPC API

Komunikace s NotebookLM probíhá přes Google's interní `batchexecute` RPC protokol. Aplikace implementuje 16 RPC metod:

| Metoda | Popis |
|--------|-------|
| `listNotebooks` | Seznam všech sešitů |
| `createNotebook` | Nový sešit |
| `getSources` / `deleteSource` | Správa zdrojů |
| `getSummary` | AI summary sešitu |
| `listArtifacts` / `generateArtifact` | Artefakty (audio, reporty, kvízy...) |
| `sendChat` | Streaming chat (GenerateFreeFormStreamed) |
| `listNotes` / `createNote` / `deleteNote` | Poznámky |
| ... | a další |

Chat používá dedikovaný streaming endpoint mimo batchexecute.

## UX detaily

- **Thumb-zone first** — všechny interaktivní prvky (search, akce, generování) jsou dole na obrazovce
- **Swipe-to-delete** s Undo snackbar a haptickým feedbackem
- **Long-press** pro multi-select mód
- **Marquee efekt** na názvu sešitu — plynulý scroll celého názvu + návrat + "..." indikátor
- **Edge-to-edge** layout s korektním IME paddingem
- **Skeleton loading** se shimmer animací

## Design systém

Gruvbox terminálová paleta s definovanými konstantami:

- **Radii:** 14dp (card), 10dp (button), 16dp (dialog/search), 8dp (chip)
- **Bordery:** 1dp standard, 1.5dp selected, alpha 0.3
- **Fonty:** Monospace, 13sp / 15sp / 18sp
- **Barvy:** Sémantické mapování (green = chat, cyan = sources, purple = artifacts, orange = notes)

## Build

```bash
# Debug build
./gradlew assembleDebug

# APK bude v app/build/outputs/apk/debug/
```

### Požadavky

- Android Studio Ladybug+ (nebo Gradle CLI)
- JDK 17
- Android SDK 35

## Omezení

- **Neoficiální** — používá reverse-engineered API, může se kdykoli rozbít
- **Vyžaduje Google účet** s přístupem k NotebookLM
- **Sémantické hledání** vyžaduje OpenRouter API klíč (volitelné)
- Žádné offline mód — vše běží přes síť

## Licence

Osobní projekt. Kód je veřejný pro inspiraci, ale není zamýšlen jako production-ready aplikace.

---

<p align="center">
  <sub>Vytvořeno s pomocí <b>Claude Code</b> (Claude Opus 4.6) a nadměrného množství kávy.</sub>
</p>
