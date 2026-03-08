# Git Workflow

## PRAVIDLA - POVINNÉ!
0. **START SESSION** → `git log -3 --pretty=format:"%h %s%n%b"` → **stručně shrň uživateli** co se dělalo
1. **KAŽDÁ dokončená změna = OKAMŽITÝ commit** - žádné odkládání!
2. **Body = tvoje PAMĚŤ** - Poučení čte příští Claude session
3. **Před risky operací** (delete, refactor) → snapshot commit
4. **Atomic**: 1 commit = 1 logická změna

## Emoji a kdy body MUSÍ být
| Emoji | Typ | Body? |
|-------|-----|-------|
| 🐛 | fix | **ANO** |
| ✨ | feat | **ANO** |
| ♻️ | refactor | **ANO** |
| 🔖 | snapshot | ne |
| 📝 | docs | ne |
| 🔧 | config | ne |
| 📦 | deps | ne |
| 🔥 | remove | ne |

## Jak psát body
| Sekce | Co tam patří |
|-------|--------------|
| **Kontext** | Proč se to dělalo, co bylo špatně, důvod změny |
| **Poučení** | Anti-patterny, tipy, co neopakovat, technické detaily |

## Příklady obsahu body
| Typ | Kontext | Poučení |
|-----|---------|---------|
| 🐛 fix | Co nefungovalo, proč | Co nedělat příště, správný pattern |
| ✨ feat | Proč se přidává, požadavek | Architektonická rozhodnutí, trade-offs |
| ♻️ refactor | Proč stará verze nevyhovovala | Nový pattern, proč je lepší |

## Commit formát
```
<emoji> <type>: <subject max 72 znaků>

## Kontext
<proč se to dělalo - důvod změny/nové funkce>

## Poučení
<co si zapamatovat pro příště, anti-patterny, tipy>
```

## Příklad (HEREDOC syntaxe)
```bash
git commit -m "$(cat <<'EOF'
🐛 fix: Mousewheel scroll pouze nad reference panelem

## Kontext
bind_all('<MouseWheel>') chytal události globálně - scroll fungoval
i když myš byla nad jiným panelem.

## Poučení
- bind_all() = globální, bind() = lokální pro widget
- V Tkinter musí mít každý widget vlastní bind na MouseWheel
- Eventy se nepropagují z children na parent automaticky
EOF
)"
```
