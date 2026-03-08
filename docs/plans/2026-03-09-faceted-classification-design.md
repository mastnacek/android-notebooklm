# Multi-facetová klasifikace s PMEST modelem

## Kontext

Aktuální klasifikace přiřazuje každému sešitu jednu kategorii (string) přes OpenRouter LLM.
Rozšiřujeme na 5 facet podle Ranganathanova PMEST modelu s pokročilým filtrováním.

## Datový model

SQLite tabulka `notebook_facets` (v existujícím `EmbeddingDb`):

```sql
CREATE TABLE notebook_facets (
    notebook_id TEXT PRIMARY KEY,
    topic TEXT,      -- Téma: "Programování", "AI", "Finance"
    format TEXT,     -- Formát: "Tutorial", "Reference", "Poznámky"
    purpose TEXT,    -- Účel: "Učení", "Projekt", "Archiv"
    domain TEXT,     -- Doména: "Android", "Python", "Web"
    freshness TEXT   -- Aktuálnost: "Aktivní", "Archivní", "Sezónní"
)
```

Migrace: existující `SharedPreferences("categories")` → do sloupce `topic`, pak smazat prefs.

## AI prompt

Rozšířený prompt vrací 5 facet per sešit:

```json
[{"id": "xxx", "topic": "AI", "format": "Tutorial",
  "purpose": "Učení", "domain": "Android", "freshness": "Aktivní"}]
```

Kontrolovaný slovník — AI musí přednostně vybírat z existujících hodnot v DB,
novou vytvoří jen když žádná nesedí. Stejný pattern jako teď u kategorií.

## UI — NotebookCard

- Pod názvem se zobrazí jen `topic` (jako teď kategorie) — barevný text
- Tap na kartu = normální navigace do detailu (beze změny)

## UI — Filter bottom sheet

- Nové tlačítko "Filtr" v `BottomActionBar` (vedle Sort)
- Otevře bottom sheet se 5 sekcemi (Topic, Formát, Účel, Doména, Aktuálnost)
- Každá sekce = horizontální řádek chipů s hodnotami z DB
- Tap na chip = toggle, live filtrování seznamu (AND logika)
- Badge na tlačítku Filtr ukazuje počet aktivních filtrů
- "Vymazat vše" tlačítko v sheetu

## Zpětná kompatibilita

- `NotebookSort.CATEGORY` řazení → řadí podle `topic`
- `categories` StateFlow → plněný z SQLite místo SharedPreferences
- Nový `facets` StateFlow s `Map<String, NotebookFacets>` pro filtrování

## Inspirace

Ranganathanův PMEST model + Elasticsearch Self-Query Retriever pattern
(facetové metadata + kontrolovaný slovník + kombinované filtry).
