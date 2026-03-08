# Facetová klasifikace — Implementační plán

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rozšířit jednorozměrnou AI klasifikaci na 5 facet (PMEST model) s filter bottom sheetem a live filtrováním.

**Architecture:** SQLite tabulka `notebook_facets` v EmbeddingDb, rozšířený AI prompt vracející 5 facet jako JSON, filter bottom sheet v NotebookListScreen s AND logikou, migrace kategorií z SharedPreferences do SQLite.

**Tech Stack:** Kotlin, Jetpack Compose, SQLite, OpenRouter LLM API, Material 3 ModalBottomSheet

---

### Task 1: SQLite tabulka + data class NotebookFacets

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/search/EmbeddingDb.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModelModels.kt`

**Step 1: Přidej data class NotebookFacets do AppViewModelModels.kt**

```kotlin
/** PMEST facety pro notebook */
data class NotebookFacets(
    val topic: String = "",      // Téma: "Programování", "AI", "Finance"
    val format: String = "",     // Formát: "Tutorial", "Reference", "Poznámky"
    val purpose: String = "",    // Účel: "Učení", "Projekt", "Archiv"
    val domain: String = "",     // Doména: "Android", "Python", "Web"
    val freshness: String = "",  // Aktuálnost: "Aktivní", "Archivní", "Sezónní"
)
```

**Step 2: Přidej tabulku `notebook_facets` do EmbeddingDb**

V `EmbeddingDb`:
- Zvýš `DATABASE_VERSION` z `1` na `2`
- V `onCreate` přidej `CREATE TABLE notebook_facets`
- V `onUpgrade` přidej migraci (CREATE TABLE IF NOT EXISTS, ne DROP)
- Přidej metody: `upsertFacets`, `getFacets`, `getAllFacets`, `getDistinctValues`

```kotlin
// V onCreate přidej:
db.execSQL("""
    CREATE TABLE IF NOT EXISTS notebook_facets (
        notebook_id TEXT PRIMARY KEY,
        topic TEXT NOT NULL DEFAULT '',
        format TEXT NOT NULL DEFAULT '',
        purpose TEXT NOT NULL DEFAULT '',
        domain TEXT NOT NULL DEFAULT '',
        freshness TEXT NOT NULL DEFAULT ''
    )
""")

// onUpgrade:
override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
    if (old < 2) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notebook_facets (
                notebook_id TEXT PRIMARY KEY,
                topic TEXT NOT NULL DEFAULT '',
                format TEXT NOT NULL DEFAULT '',
                purpose TEXT NOT NULL DEFAULT '',
                domain TEXT NOT NULL DEFAULT '',
                freshness TEXT NOT NULL DEFAULT ''
            )
        """)
    }
}

fun upsertFacets(notebookId: String, facets: NotebookFacets) {
    val cv = ContentValues().apply {
        put("notebook_id", notebookId)
        put("topic", facets.topic)
        put("format", facets.format)
        put("purpose", facets.purpose)
        put("domain", facets.domain)
        put("freshness", facets.freshness)
    }
    writableDatabase.insertWithOnConflict("notebook_facets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

fun getFacets(notebookId: String): NotebookFacets? {
    val cursor = readableDatabase.rawQuery(
        "SELECT topic, format, purpose, domain, freshness FROM notebook_facets WHERE notebook_id = ?",
        arrayOf(notebookId)
    )
    val result = if (cursor.moveToFirst()) {
        NotebookFacets(
            topic = cursor.getString(0),
            format = cursor.getString(1),
            purpose = cursor.getString(2),
            domain = cursor.getString(3),
            freshness = cursor.getString(4),
        )
    } else null
    cursor.close()
    return result
}

fun getAllFacets(): Map<String, NotebookFacets> {
    val result = mutableMapOf<String, NotebookFacets>()
    val cursor = readableDatabase.rawQuery(
        "SELECT notebook_id, topic, format, purpose, domain, freshness FROM notebook_facets", null
    )
    while (cursor.moveToNext()) {
        result[cursor.getString(0)] = NotebookFacets(
            topic = cursor.getString(1),
            format = cursor.getString(2),
            purpose = cursor.getString(3),
            domain = cursor.getString(4),
            freshness = cursor.getString(5),
        )
    }
    cursor.close()
    return result
}

/** Vrátí distinct hodnoty pro daný facet sloupec (pro filter chipy) */
fun getDistinctFacetValues(column: String): List<String> {
    val allowed = setOf("topic", "format", "purpose", "domain", "freshness")
    if (column !in allowed) return emptyList()
    val result = mutableListOf<String>()
    val cursor = readableDatabase.rawQuery(
        "SELECT DISTINCT $column FROM notebook_facets WHERE $column != '' ORDER BY $column", null
    )
    while (cursor.moveToNext()) { result.add(cursor.getString(0)) }
    cursor.close()
    return result
}
```

**Step 3: Build ověření**

Run: `./gradlew assembleDebug`

**Step 4: Commit**

```
feat: SQLite tabulka notebook_facets + NotebookFacets data class
```

---

### Task 2: AppViewModel — facets StateFlow + migrace kategorií

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModel.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModelModels.kt`

**Step 1: Přidej facets StateFlow do AppViewModel**

```kotlin
// Pod _categories:
internal val _facets = MutableStateFlow<Map<String, NotebookFacets>>(emptyMap())
val facets: StateFlow<Map<String, NotebookFacets>> get() = _facets
```

**Step 2: Migrace kategorií z SharedPreferences do SQLite**

V `init` bloku AppViewModel, po načtení kategorií z catPrefs:

```kotlin
// Migrace kategorii do notebook_facets (jednorázově)
if (prefs.getBoolean("facets_migrated", false).not()) {
    for ((id, cat) in rawCats) {
        embeddingDb.upsertFacets(id, NotebookFacets(topic = cat))
    }
    prefs.edit().putBoolean("facets_migrated", true).apply()
}
// Načti facety z DB
_facets.value = embeddingDb.getAllFacets()
// Categories z facet topic (zpětná kompatibilita)
_categories.value = _facets.value.mapValues { it.value.topic }.filterValues { it.isNotEmpty() }
```

**Step 3: Build ověření**

Run: `./gradlew assembleDebug`

**Step 4: Commit**

```
feat: facets StateFlow + migrace kategorií z SharedPreferences do SQLite
```

---

### Task 3: Rozšířený AI prompt pro 5 facet

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModelGlobalOps.kt`

**Step 1: Uprav metodu `startClassification`**

Změny v promptu:
- Místo jedné kategorie požaduj 5 facet
- JSON formát: `[{"id": "...", "topic": "...", "format": "...", "purpose": "...", "domain": "...", "freshness": "..."}]`
- Kontrolovaný slovník — existující hodnoty z DB pro každou facetu

Změny v parsování:
- Parsuj 5 polí místo jednoho `category`
- Ukládej přes `embeddingDb.upsertFacets()`
- Aktualizuj `_facets` a `_categories` (zpětná kompatibilita)

```kotlin
// Před batchem — načti existující hodnoty per facet
val knownTopics = embeddingDb.getDistinctFacetValues("topic").toMutableSet()
val knownFormats = embeddingDb.getDistinctFacetValues("format").toMutableSet()
val knownPurposes = embeddingDb.getDistinctFacetValues("purpose").toMutableSet()
val knownDomains = embeddingDb.getDistinctFacetValues("domain").toMutableSet()
val knownFreshness = embeddingDb.getDistinctFacetValues("freshness").toMutableSet()

// Nový prompt (nahradí starý):
val prompt = """
Jsi organizacni asistent. Prirad kazdemu notebooku 5 facet (PMEST model).

FACETY:
1. topic — Obecne tema (max 2 slova, cesky): Programovani, Ai nastroje, Finance, Design...
2. format — Typ obsahu: Tutorial, Reference, Poznamky, Vyzkum, Projekt, Clanek, Kurz
3. purpose — Ucel: Uceni, Projekt, Archiv, Inspirace, Prace, Osobni
4. domain — Konkretni oblast/technologie: Android, Python, Web, Hardware, Obecne...
5. freshness — Aktuálnost: Aktivni, Archivni, Sezonni

PRAVIDLA:
1. Hodnoty MAXIMALNE 2 slova, cesky, lowercase (prvni pismeno velke)
2. KONZISTENCE — pouzij existujici hodnoty pokud sedi:
   topic: ${knownTopics.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   format: ${knownFormats.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   purpose: ${knownPurposes.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   domain: ${knownDomains.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
   freshness: ${knownFreshness.sorted().joinToString(", ").ifEmpty { "(zatim zadne)" }}
3. Novou hodnotu vytvor JEN kdyz ZADNA existujici nesedi
4. Cil je 5-15 hodnot per facet, ne unikatni pro kazdy notebook

NOTEBOOKY:
$nbLines

Odpovez POUZE platnym JSON polem:
[{"id": "notebook_id", "topic": "...", "format": "...", "purpose": "...", "domain": "...", "freshness": "..."}]
""".trim()
```

Parsování výsledku:
```kotlin
val arr = Json.parseToJsonElement(clean).jsonArray
for (item in arr) {
    val obj = item.jsonObject
    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
    val facets = NotebookFacets(
        topic = obj["topic"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
        format = obj["format"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
        purpose = obj["purpose"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
        domain = obj["domain"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
        freshness = obj["freshness"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
    )
    embeddingDb.upsertFacets(id, facets)
    allResults[id] = facets.topic // zpětná kompatibilita
    // Aktualizuj known values
    if (facets.topic.isNotEmpty()) knownTopics.add(facets.topic)
    if (facets.format.isNotEmpty()) knownFormats.add(facets.format)
    if (facets.purpose.isNotEmpty()) knownPurposes.add(facets.purpose)
    if (facets.domain.isNotEmpty()) knownDomains.add(facets.domain)
    if (facets.freshness.isNotEmpty()) knownFreshness.add(facets.freshness)
}
```

Na konci po všech batchích:
```kotlin
_facets.value = embeddingDb.getAllFacets()
_categories.value = _facets.value.mapValues { it.value.topic }.filterValues { it.isNotEmpty() }
```

**Step 2: Uprav ClassificationState.results typ**

V `AppViewModelModels.kt` — `results` zůstane `Map<String, String>` (topic) pro zpětnou kompatibilitu. Plná data jsou ve `_facets`.

**Step 3: Build ověření**

Run: `./gradlew assembleDebug`

**Step 4: Commit**

```
feat: AI prompt rozšířen na 5 facet (PMEST model)
```

---

### Task 4: FacetFilterState + filtrační logika

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModelModels.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModel.kt`

**Step 1: Přidej FacetFilter data class**

V `AppViewModelModels.kt`:

```kotlin
/** Aktivní filtry — každý facet může mít vybrané hodnoty (AND mezi facetami) */
data class FacetFilter(
    val topics: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val purposes: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    val freshnesses: Set<String> = emptySet(),
) {
    val activeCount: Int get() = listOf(topics, formats, purposes, domains, freshnesses).count { it.isNotEmpty() }
    val isEmpty: Boolean get() = activeCount == 0

    fun matches(facets: NotebookFacets): Boolean {
        if (topics.isNotEmpty() && facets.topic !in topics) return false
        if (formats.isNotEmpty() && facets.format !in formats) return false
        if (purposes.isNotEmpty() && facets.purpose !in purposes) return false
        if (domains.isNotEmpty() && facets.domain !in domains) return false
        if (freshnesses.isNotEmpty() && facets.freshness !in freshnesses) return false
        return true
    }
}
```

**Step 2: Přidej StateFlow do AppViewModel**

```kotlin
private val _facetFilter = MutableStateFlow(FacetFilter())
val facetFilter: StateFlow<FacetFilter> get() = _facetFilter

fun setFacetFilter(filter: FacetFilter) { _facetFilter.value = filter }
fun clearFacetFilter() { _facetFilter.value = FacetFilter() }
```

**Step 3: Build + commit**

```
feat: FacetFilter data class + filtrační StateFlow
```

---

### Task 5: Filter Bottom Sheet UI

**Files:**
- Create: `app/src/main/java/dev/jara/notebooklm/ui/FacetFilterSheet.kt`

**Step 1: Composable FacetFilterSheet**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FacetFilterSheet(
    facets: Map<String, NotebookFacets>,
    currentFilter: FacetFilter,
    onFilterChange: (FacetFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    // ModalBottomSheet s 5 sekcemi
    // Každá sekce: label + horizontální FlowRow chipů
    // Chip = toggle, zapnutý = filled, vypnutý = outlined
    // Dole: "Vymazat vše" tlačítko pokud activeCount > 0

    // Extrahuj distinct hodnoty z facets mapy:
    val allTopics = facets.values.map { it.topic }.filter { it.isNotEmpty() }.distinct().sorted()
    val allFormats = facets.values.map { it.format }.filter { it.isNotEmpty() }.distinct().sorted()
    val allPurposes = facets.values.map { it.purpose }.filter { it.isNotEmpty() }.distinct().sorted()
    val allDomains = facets.values.map { it.domain }.filter { it.isNotEmpty() }.distinct().sorted()
    val allFreshnesses = facets.values.map { it.freshness }.filter { it.isNotEmpty() }.distinct().sorted()

    // Pro každou sekci: FacetSection composable
    // Chip onClick → toggle hodnotu v příslušném setu → onFilterChange(updatedFilter)
}
```

Komponenty: `FacetSection(label, values, selected, onToggle)` + `FacetChip(text, selected, color, onClick)`.

Barvy per facet: topic=zelená, format=modrá, purpose=oranžová, domain=fialová, freshness=šedá.

**Step 2: Build + commit**

```
feat: FacetFilterSheet — bottom sheet s facetovými filtry
```

---

### Task 6: Integrace do NotebookListScreen

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/NotebookListScreen.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/NotebookListComponents.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/MainActivity.kt`

**Step 1: Přidej parametry do NotebookListScreen**

```kotlin
// Nové parametry:
facets: Map<String, NotebookFacets>,
facetFilter: FacetFilter,
onFacetFilterChange: (FacetFilter) -> Unit,
```

**Step 2: Přidej filtrování do displayList logiky**

```kotlin
// Po fulltextFiltered / semanticResults:
val facetFiltered = if (facetFilter.isEmpty) displayList
else displayList.filter { nb ->
    val f = facets[nb.id] ?: return@filter true  // bez facet = zobrazit
    facetFilter.matches(f)
}
```

**Step 3: Přidej tlačítko Filtr do BottomActionBar**

```kotlin
// Nové parametry pro BottomActionBar:
filterCount: Int,
onFilter: () -> Unit,

// Nové tlačítko (před ⚙):
BottomAction(
    icon = if (filterCount > 0) "◉" else "☷",
    label = if (filterCount > 0) "Filtr ($filterCount)" else "Filtr",
    color = if (filterCount > 0) Term.cyan else Term.textDim,
) { onFilter() }
```

**Step 4: Přidej sheet state do NotebookListScreen**

```kotlin
var showFilterSheet by remember { mutableStateOf(false) }

if (showFilterSheet) {
    FacetFilterSheet(
        facets = facets,
        currentFilter = facetFilter,
        onFilterChange = onFacetFilterChange,
        onDismiss = { showFilterSheet = false },
    )
}
```

**Step 5: Propoj v MainActivity.kt**

Přidej `collectAsStateWithLifecycle` pro `facets` a `facetFilter`, předej do NotebookListScreen.

**Step 6: Build + commit**

```
feat: filter bottom sheet integrován do seznamu sešitů
```

---

### Task 7: Cleanup — odstranění SharedPreferences kategorií

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModel.kt`

**Step 1: Ověř že migrace proběhla a vše funguje z SQLite**

**Step 2: Odstraň `catPrefs` inicializaci** (ponech jen pro čtení při migraci)

Ponech `_categories` StateFlow — je plněný z `_facets.value.topic`.

**Step 3: Build + commit + push**

```
refactor: kategorie plně migrované na SQLite facets
```

---

## Pořadí a závislosti

```
Task 1 (DB tabulka)
  ↓
Task 2 (StateFlow + migrace)
  ↓
Task 3 (AI prompt)
  ↓
Task 4 (FacetFilter logika)
  ↓
Task 5 (Filter Sheet UI)
  ↓
Task 6 (Integrace)
  ↓
Task 7 (Cleanup)
```

Všechny tasky jsou sekvenční — každý závisí na předchozím.
