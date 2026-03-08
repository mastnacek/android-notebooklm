# CLAUDE.md – Android NotebookLM

## O projektu
Android klient pro Google NotebookLM postavený na reverse-engineered batchexecute RPC API.

### Funkce
- Autentizace přes WebView (Google cookies → CSRF + session tokeny)
- Seznam sešitů, detail se zdroji a AI summary
- Chat s notebookem (GenerateFreeFormStreamed)
- Artefakty (list, play audio, download)
- Sémantické vyhledávání přes OpenRouter embeddings (qwen/qwen3-embedding-8b, 512 dim)
- Terminal dark aesthetic (Compose UI, Gruvbox paleta)

### Tech stack
- Kotlin, Jetpack Compose, Ktor CIO, kotlinx.serialization
- EncryptedSharedPreferences (auth), SQLite (embeddings KNN)
- Edge-to-edge layout, Material 3 adaptive

## Instrukce pro AI
- Komunikace v češtině, kód v angličtině
- Před změnou vždy přečti existující kód
- Commit po každé dokončené změně
- Pro referenci RPC formátu viz `references/` (není v repu, nutno dodat zvlášť)
