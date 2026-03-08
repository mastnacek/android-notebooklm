---
name: compose
description: Build and maintain Android screens in Kotlin with Jetpack Compose. Use when creating new features/screens, modifying existing UI, fixing bugs, adding components, or working with navigation, ViewModels, and themes.
---

# Jetpack Compose — Android NotebookLM

Kotlin + Jetpack Compose + Material 3. Každá feature = samostatný vertical slice (package) s vlastním ViewModel, Screen composable a modely.

## Architektura

```
app/src/main/java/dev/jara/notebooklm/
├── ui/              ← Screeny, ViewModely, Theme, sdílené komponenty
├── rpc/             ← NotebookLM batchexecute API (encoder, decoder, metody)
├── auth/            ← Login (WebView), AuthManager (EncryptedSharedPreferences)
├── search/          ← OpenRouter embeddings + SQLite KNN
└── MainActivity.kt  ← Entry point, AnimatedContent navigace
```

Každý screen = self-contained composable s vlastním state managementem. Sdílený kontrakt = `Screen` sealed class pro navigaci + `AppViewModel` pro centrální stav. Žádné cross-feature importy — jen přes sdílené utility.

## Filosofie: Deep Modules

Každý screen/feature = **deep module** (Ousterhout). Malé veřejné rozhraní (composable funkce + ViewModel), obrovská implementace schovaná uvnitř.

- **Interface << Implementace** — Screen composable je jeden `@Composable fun`, vše ostatní je privátní
- **Skryj "jak", ukaž "co"** — volající neví o RPC callech, DB queries, state machines uvnitř
- **Začni jedním souborem** — nejdřív vše do jednoho .kt souboru, teprve až roste, extrahuj privátní části
- **Neděl předčasně** — 3 soubory po 50 řádcích jsou horší než 1 soubor se 150 řádky

## Klíčová pravidla

- **Theme:** Vždy `LocalAppColors.current.*` nebo `Term.*` — nikdy hardcoded `Color(0xFF...)`
- **State:** `StateFlow` v ViewModel, `collectAsStateWithLifecycle()` v UI
- **Async:** `viewModelScope.launch` + `suspend fun` + `Flow`, NE raw Thread
- **DB:** `EncryptedSharedPreferences` pro auth, `SQLiteOpenHelper` pro data
- **Soubory:** Děl podle konceptů, ne podle počtu řádků. Hloubka > délka. Hard limit 500 řádků — nad tím povinně rozděl.
- **Viditelnost:** `public` = API, `internal` = modul, `private` = implementace
- **Error handling:** `try/catch` + propagace přes state, žádný tichý `catch (e: Exception) {}`
- **Thumb area:** Interaktivní prvky (tlačítka, search, panely) VŽDY dole na obrazovce
- **Edge-to-edge:** `imePadding()` na Scaffold, `navigationBarsPadding()` na bottom bar
- **Dialogy:** `DialogProperties(usePlatformDefaultWidth = false)` pro plnou šířku

## Quick Start — Nový Screen

1. Vytvoř `{NazevScreen}.kt` s `@Composable fun {Nazev}Screen(...)` + potřebný ViewModel
2. Přidej variantu do `Screen` sealed class v `AppViewModel.kt`
3. Přidej `AnimatedContent` branch v `MainActivity.kt`
4. Přidej navigační akci (tlačítko/callback) z existujícího screenu
5. `./gradlew assembleDebug` + ověř vizuálně

## Reference

| Dokument | Obsah |
|----------|-------|
| [architecture.md](references/architecture.md) | Screen sealed class, AppViewModel, navigace, event loop, layout |
| [conventions.md](references/conventions.md) | Theme integrace, gestures, async pattern, DB persistence, anti-patterny |
| [new-screen-checklist.md](references/new-screen-checklist.md) | Krok-za-krokem checklist + minimální Screen template + časté chyby |
| [components.md](references/components.md) | LazyColumn, SwipeToDismiss, Dialogy, BottomBar, Markdown, AudioPlayer |
| [best-practices.md](references/best-practices.md) | Compose performance, state management, testování, accessibility |
