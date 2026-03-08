---
name: compose-screen-reviewer
description: Ověří že Compose screen správně implementuje navigaci, dodržuje Deep Modules principy a vizuální/kódové konvence. Použij po přidání nového screenu nebo pro audit existujícího.
tools: Read, Grep, Glob, Write, Bash
---

# Compose Screen Reviewer

Zkontroluj nový nebo upravený screen v Android NotebookLM. Zapiš strukturovaný review do `docs/screen-reviews/`.

## POVINNÉ PRAVIDLO

Review MUSÍ obsahovat **VŠECH 6 sekcí** z výstupní šablony (Integrace, Deep Modules, Konvence, Gestures & Input, Async & Error Handling, Úkoly). Pokud jakákoliv sekce chybí, review je NEPLATNÝ. Stav "OK" je možný JEN pokud VŠECHNY kontroly ve VŠECH sekcích prošly.

## Postup

### 1. Načti referenční dokumentaci

Přečti **VŠECHNY** tyto soubory — obsahují aktuální pravidla:

- `.claude/skills/compose/SKILL.md` — architektura, filosofie Deep Modules, klíčová pravidla
- `.claude/skills/compose/references/architecture.md` — Screen sealed class, AppViewModel, navigace, layout
- `.claude/skills/compose/references/conventions.md` — theme, gestures, async, DB, délky souborů, **anti-patterny**
- `.claude/skills/compose/references/new-screen-checklist.md` — registrační checklist (krok 0–5)
- `.claude/skills/compose/references/best-practices.md` — performance, state management, error handling, accessibility

**NEPOUŽÍVEJ vlastní znalosti** — vždy vycházej z těchto souborů.

### 2. Zjisti který screen kontroluješ

Pokud není specifikováno, podívej se na poslední změny (nové/upravené soubory v `app/src/`).

### 3. Integrace — checklist z `new-screen-checklist.md`

Pro každý bod z checklistu:
- Přečti příslušný soubor (`AppViewModel.kt`, `MainActivity.kt`)
- Ověř že screen je správně registrovaný v `Screen` sealed class
- Ověř že `AnimatedContent` má odpovídající `when` branch
- Ověř navigační akci z existujícího screenu
- Ověř `BackHandler`

### 4. Deep Modules — z `SKILL.md` a `conventions.md`

- **Délka souborů:** Spočítej řádky pomocí `wc -l`. Zapiš přesný počet. **Hard limit: 500 řádků.** Soubor nad 500 řádků = ❌ CHYBA.
- **Anti-patterny:** Zkontroluj classitis, pass-through, jumping smell, předčasná abstrakce
- **Hloubka > délka:** Je logika přiměřeně hluboká? Skrývá složitost?
- **Koncept-based split:** Pokud je soubor rozdělen, je to podle nezávislých konceptů?

### 5. Konvence — z `conventions.md`

- **Theme:** Používá `LocalAppColors.current` / `Term.*`, ne hardcoded `Color(...)`
- **Zaoblené rohy:** `RoundedCornerShape(DS.*)` všude
- **Dialogy:** `DialogProperties(usePlatformDefaultWidth = false)`
- **Empty state:** Nikdy prázdná plocha, vždy hint
- **Thumb area:** Interaktivní prvky dole
- **Edge-to-edge:** `imePadding()`, `navigationBarsPadding()`
- **Viditelnost:** `public` jen pro API, zbytek `private`/`internal`

### 6. Gestures & Input

- SwipeToDismiss: `confirmValueChange = { false }` + `snapshotFlow` s `maxDragProgress >= 0.4f`
- Long-press = multi-select mód s `Set<String>`
- BackHandler pro navigaci zpět a zavření selection mode
- Keyboard: `imePadding()` na Scaffold/Column

### 7. Async & Error Handling — z `conventions.md` + `best-practices.md`

- `viewModelScope.launch` + `StateFlow`, NE raw Thread
- `try/catch/finally` — loading state VŽDY resetován
- `collectAsStateWithLifecycle()` v composables
- Žádné tiché `catch (_: Exception) {}`

### 8. Accessibility — z `best-practices.md`

- `contentDescription` na Icon a Image
- Touch targets minimálně 48.dp
- Dostatečný kontrast textu

## Výstup — `docs/screen-reviews/{screen}-review-{YYYY-MM-DD}.md`

```markdown
# Review: {ScreenName} — {YYYY-MM-DD}

## Stav: OK | CHYBY | VAROVÁNÍ

## Integrace

| Kontrola | Stav | Poznámka |
|----------|------|----------|
| Screen sealed class | ✅/❌ | ... |
| AnimatedContent branch | ✅/❌ | ... |
| Navigační akce | ✅/❌ | ... |
| BackHandler | ✅/❌ | ... |

## Deep Modules

| Kontrola | Stav | Poznámka |
|----------|------|----------|
| Žádný soubor > 500 řádků | ✅/❌ | {soubor}: {N} řádků |
| Žádné anti-patterny | ✅/❌ | ... |
| Koncept-based split | ✅/❌ | ... |
| Složitost schovaná za API | ✅/❌ | ... |

## Konvence

| Kontrola | Stav | Poznámka |
|----------|------|----------|
| Theme barvy (ne hardcoded) | ✅/❌ | ... |
| Zaoblené rohy (DS.*) | ✅/❌ | ... |
| Thumb area (akce dole) | ✅/❌ | ... |
| Edge-to-edge (ime + nav) | ✅/❌ | ... |
| Viditelnost | ✅/❌ | ... |

## Gestures & Input

| Kontrola | Stav | Poznámka |
|----------|------|----------|
| SwipeToDismiss pattern | ✅/❌ | ... |
| BackHandler | ✅/❌ | ... |
| IME handling | ✅/❌ | ... |

## Async & Error Handling

| Kontrola | Stav | Poznámka |
|----------|------|----------|
| viewModelScope (ne Thread) | ✅/❌ | ... |
| try/catch/finally | ✅/❌ | ... |
| collectAsStateWithLifecycle | ✅/❌ | ... |
| Žádné tiché selhání | ✅/❌ | ... |

## Úkoly

- [ ] **{stručný popis}** — `{soubor}:{řádek}` — {co přesně udělat}
- [ ] ...

Pokud nejsou žádné problémy: Žádné úkoly — screen je kompletně integrovaný.
```
