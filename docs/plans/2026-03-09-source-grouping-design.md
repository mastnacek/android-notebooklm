# Seskupení sešitů podle sdílených zdrojů

## Přehled

Nová funkce umožní:
1. **Batch sken zdrojů** — stáhne zdroje každého sešitu, zahashuje obsah, uloží do SQLite
2. **Sort mód "zdroje"** — seskupí sešity s ≥1 společným zdrojem (union-find)
3. **Indikátory stavu** — 4 tečky na kartě sešitu (zdroje, embed, AI kat., dedup)
4. **Batch deduplikace v selection baru** — přesun z ⋯ menu

## DB schéma

### Nová tabulka `notebook_sources`

```sql
CREATE TABLE notebook_sources (
    notebook_id TEXT NOT NULL,
    source_id TEXT NOT NULL,
    title TEXT NOT NULL,
    type TEXT NOT NULL,        -- PDF, WEB, YOUTUBE, TEXT, OTHER
    content_hash TEXT,         -- SHA-256
    scanned_at TEXT NOT NULL,
    PRIMARY KEY (notebook_id, source_id)
)
```

### Nová tabulka `notebook_status`

```sql
CREATE TABLE notebook_status (
    notebook_id TEXT PRIMARY KEY,
    sources_scanned_at TEXT,
    dedup_done_at TEXT
)
```

## Hashování zdrojů

| Typ zdroje | Vstup pro SHA-256 |
|------------|-------------------|
| TEXT | celý fulltext (`getSourceFulltext()`) |
| PDF | `title + první strana fulltextu` (split po `\f` nebo první ~3000 znaků) |
| WEB | `title` |
| YOUTUBE | `title` |
| OTHER | `title` |

## Batch akce "Skenuj zdroje"

1. Pro každý sešit zavolat `getSources(notebookId)`
2. Pro každý zdroj: stáhnout fulltext (TEXT, PDF), vytvořit hash
3. Upsert do `notebook_sources` + update `notebook_status.sources_scanned_at`
4. StateFlow `_sourcesScanStatus: MutableStateFlow<SourceScanState>()`

**UI spouštění:**
- Selection bar: tlačítko "Zdroje" (vedle Embed, AI kat.)
- ⋯ menu: "Skenuj zdroje všech"
- StatusBar: "Zdroje: 3/10 — Sešit XY"

## Seskupení (sort mód SOURCES)

### Union-Find algoritmus

1. Načíst `notebook_sources` → mapa `content_hash → Set<notebookId>`
2. Pro každý hash se ≥2 sešity: union
3. Výsledek: skupiny propojených sešitů + skupina "Bez sdílených zdrojů"

### Název skupiny

- Zdroj s nejvíce propojeními (nejčastější sdílený `title`)
- Formát: "ML.pdf (+2 další)" pokud více sdílených zdrojů
- Nepropojené sešity: "Bez sdílených zdrojů"

### Sort enum

```kotlin
enum class NotebookSort {
    DEFAULT, NAME_ASC, NAME_DESC, CATEGORY, SOURCES
}
```

Cyklování: datum → A-Z → Z-A → kat. → zdroje → datum...

## Indikátory na kartě sešitu

4 tečky v řadě na kartě:

| # | Význam | Barva (pastelová) | Zdroj dat |
|---|--------|-------------------|-----------|
| 1 | Zdroje naskenovány | modrá | `notebook_sources` existuje záznam |
| 2 | Embedován | zelená | `notebook_embeddings` existuje záznam |
| 3 | AI klasifikace | žlutá | `notebook_facets` existuje záznam |
| 4 | Deduplikován | růžová | `notebook_status.dedup_done_at` != null |

### Vizuální stavy

- **Hotovo**: vyplněná tečka s pastelovou glow (shadow/blur efekt)
- **Nehotovo**: prázdný kroužek (outline), bez glow

### Legenda

Pod nadpisem "NotebookLM" v horní části seznamu — řada 4 teček s krátkými popisky, malým písmem.

## Batch deduplikace v selection baru

Přidat tlačítko "Dedup" do selection baru (vedle Embed, AI kat., Delete).
Využije existující `startDeduplication()` s filtrací na vybrané ID.
Po dokončení: update `notebook_status.dedup_done_at`.
