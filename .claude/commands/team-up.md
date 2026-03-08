---
description: Sestav team agentů (analyzátor + kodér) pro práci na Android screenu/feature
---

# Team-Up: Analýza & úprava feature

Uživatel chce spustit team agentů pro práci na Android feature/screen.
Jeho zadání: $ARGUMENTS

## Architektura teamu

| Role | Model | Účel |
|------|-------|------|
| **Orchestrátor** (ty) | Opus | Koordinace, KB dotazy, reporting uživateli |
| **Analyzátor** | Sonnet | Čte kód screenu/feature, mapuje architekturu, reportuje nálezy |
| **Kodér** | Opus | Implementuje změny (až po schválení uživatelem) |

## Setup — proveď v tomto pořadí

### 1. Tmux session

Vytvoř tmux session pro sledování agentů:

```
tmux new-session -d -s team-up -n agents
```

### 2. Team

```
TeamCreate: team_name="feature-team"
```

### 3. Spuštění agentů

Spusť oba agenty přes Task tool s `run_in_background: true`:

**Analyzátor** (Sonnet):
- `subagent_type: "general-purpose"`
- `model: "sonnet"`
- `team_name: "feature-team"`
- `name: "analyzer"`
- Úkol: Přečti kompletně zdrojový kód cílového screenu/feature, zmapuj architekturu (ViewModel, Screen composable, state management, navigace, async operace). Pošli orchestrátorovi strukturovaný report.

**Kodér** (Opus):
- `subagent_type: "general-purpose"`
- `model: "opus"`
- `team_name: "feature-team"`
- `name: "coder"`
- Úkol: Čekej na úkoly od orchestrátora. Před každou změnou ověř API v dokumentaci (context7 nebo knowledge_base). Po implementaci spusť `./gradlew assembleDebug`.

### 4. Tmux panely pro tail

Po spuštění agentů získáš output_file cesty. Nastav tmux panely:

```bash
tmux send-keys -t team-up:agents "tail -f <analyzer_output>" Enter
tmux split-window -t team-up:agents -v
tmux send-keys -t team-up:agents "tail -f <coder_output>" Enter
```

Řekni uživateli: `tmux attach -t team-up` pro sledování.

### 5. Task list

Vytvoř úkoly přes TaskCreate podle uživatelova zadání.
Analyzátor dostane první úkol hned. Kodér čeká.

## Flow pravidla

1. **Analyzátor reportuje → ty obohacuješ o KB → prezentuješ uživateli**
2. **Kodér NIKDY nezačne kódovat bez explicitního schválení uživatelem**
3. Po každé změně Kodéra: `./gradlew assembleDebug` + krátký report uživateli
4. KB dotazy děláš ty sám přes knowledge_base skill nebo context7
5. Na konci práce: shutdown agentů, cleanup tmux session, nabídni commit
