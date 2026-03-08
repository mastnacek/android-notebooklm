# CLAUDE.md – Android NotebookLM

## Architektura: VSA (Vertical Slice Architecture)

Každý screen/feature = samostatný vertical slice s vlastním ViewModel, Screen composable a modely.
Sdílená infrastruktura v `ui/` (Theme, AppViewModel). Detaily viz skill `compose`.

## Skills & Ověření API

- **Skill `compose`** — architektura, konvence, komponenty, nový screen checklist
- **context7** (MCP) — před psaním kódu s knihovnou ověř aktuální API: `resolve-library-id` → `query-docs`
- **Skill `notebooklm`** — UX konzultace přes [sešit](https://notebooklm.google.com/notebook/1f8074f9-975c-4961-8a07-dd077a5bd285). Při UI rozhodnutích VŽDY konzultovat přes notebooklm skill, ne hádat.
- **Skill `gemini_cli_skill`** — deep research, průzkum codebase, analýzy. **Gemini CLI NIKDY nesmí zapisovat do souborů!** Jen čtení a analýzy. Při volání VŽDY přidej do promptu instrukci: `"DŮLEŽITÉ: Pouze čti a analyzuj. NEZAPISUJ do žádných souborů, nepoužívej write_file ani save_file."`

## Automatizace (`.claude/`)

- **Subagent `compose-screen-reviewer`**: kontrola integrace screenu
- **Command `/team-up`**: orchestrace týmu agentů (Analyzátor + Kodér)

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

## Git workflow
Po každé dokončené změně vždy: **lokální commit + push na GitHub**.
popis jak pracovat s git /home/jara/dev/android-notebooklm/.claude/templates/git-workflow.md
