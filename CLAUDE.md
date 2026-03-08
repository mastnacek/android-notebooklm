# CLAUDE.md – Android NotebookLM

## O projektu
Android klient pro Google NotebookLM postavený na reverse-engineered batchexecute RPC API.
Portováno z Rust TUI implementace: `/home/jara/dev/tui/src/notebooklm/`

### Funkce
- Autentizace přes WebView (Google cookies → CSRF + session tokeny)
- Seznam sešitů, detail se zdroji a AI summary
- Chat s notebookem (GenerateFreeFormStreamed)
- Artefakty (list, play audio, download)
- Sémantické vyhledávání přes OpenRouter embeddings (qwen/qwen3-embedding-8b, 512 dim)
- Terminal dark aesthetic (Compose UI, ne čistý terminál)

### Tech stack
- Kotlin, Jetpack Compose, Ktor CIO, kotlinx.serialization
- EncryptedSharedPreferences (auth), SQLite (embeddings KNN)
- Edge-to-edge layout

## Referenční implementace
- **Rust port**: `/home/jara/dev/tui/src/notebooklm/` — kompletní implementace (api.rs, auth.rs, notebooks.rs, chat.rs, models.rs, render.rs)
- Při nejasnostech ohledně RPC formátu vždy konzultuj Rust kód a puvodni pythnon knihovnu `/home/jara/dev/android-notebooklm/references/notebooklm-py/`
## Instrukce pro AI
- Pro Kotlin dokumentaci a best practices použij **knowledge_base** skill (kb)
- Pro aktuální dokumentaci knihoven (Compose, Ktor, kotlinx.serialization) použij **context7** MCP server
- Komunikace v češtině, kód v angličtině
- Před změnou vždy přečti existující kód
- Commit po každé dokončené změně
