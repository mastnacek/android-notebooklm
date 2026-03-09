# Seskupení sešitů podle sdílených zdrojů — Implementační plán

> **Pro Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Batch sken zdrojů sešitů s hashováním obsahu, union-find seskupení, indikátory stavu na kartách, batch dedup v selection baru.

**Architecture:** Rozšíření EmbeddingDb o 2 nové tabulky (notebook_sources, notebook_status). Nová batch akce scanSources v AppViewModelGlobalOps. Union-find algoritmus pro seskupení v sortedNotebooks(). 4 pastelové indikátory na NotebookCard.

**Tech Stack:** Kotlin, SQLite, Jetpack Compose, SHA-256 (java.security.MessageDigest)

**Design doc:** `docs/plans/2026-03-09-source-grouping-design.md`

---

### Task 1: DB schéma — nové tabulky v EmbeddingDb

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/search/EmbeddingDb.kt`

**Step 1: Zvýšit DB verzi a přidat tabulky**

V `EmbeddingDb.kt`:
- Změnit verzi z `2` na `3` (řádek 18)
- Přidat `createSourcesTable(db)` a `createStatusTable(db)` do `onCreate()` (řádek 25–36)
- Přidat migrace do `onUpgrade()` (řádek 51–56)

```kotlin
// Verze: 2 -> 3
class EmbeddingDb(context: Context) : SQLiteOpenHelper(
    context, "notebooklm_embeddings.db", null, 3
)

// Nové create metody
private fun createSourcesTable(db: SQLiteDatabase) {
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS notebook_sources (
            notebook_id TEXT NOT NULL,
            source_id TEXT NOT NULL,
            title TEXT NOT NULL,
            type TEXT NOT NULL,
            content_hash TEXT,
            scanned_at TEXT NOT NULL DEFAULT (datetime('now')),
            PRIMARY KEY (notebook_id, source_id)
        )
    """)
}

private fun createStatusTable(db: SQLiteDatabase) {
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS notebook_status (
            notebook_id TEXT PRIMARY KEY,
            sources_scanned_at TEXT,
            dedup_done_at TEXT
        )
    """)
}

// onCreate — přidat volání
override fun onCreate(db: SQLiteDatabase) {
    // ... existující embeddings + facets ...
    createSourcesTable(db)
    createStatusTable(db)
}

// onUpgrade — přidat migraci
override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
    if (old < 2) createFacetsTable(db)
    if (old < 3) { createSourcesTable(db); createStatusTable(db) }
}
```

**Step 2: Přidat CRUD metody pro notebook_sources**

```kotlin
/** Uloží nebo aktualizuje zdroje pro notebook (smaže staré, vloží nové) */
fun upsertSources(notebookId: String, sources: List<SourceRecord>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("notebook_sources", "notebook_id = ?", arrayOf(notebookId))
        for (src in sources) {
            val cv = ContentValues().apply {
                put("notebook_id", notebookId)
                put("source_id", src.sourceId)
                put("title", src.title)
                put("type", src.type)
                put("content_hash", src.contentHash)
            }
            db.insertWithOnConflict("notebook_sources", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

/** Vrátí všechny zdroje jako mapu content_hash -> Set<notebookId> (pro union-find) */
fun getSourceHashGroups(): Map<String, Set<String>> {
    val result = mutableMapOf<String, MutableSet<String>>()
    val cursor = readableDatabase.rawQuery(
        "SELECT notebook_id, content_hash FROM notebook_sources WHERE content_hash IS NOT NULL", null
    )
    while (cursor.moveToNext()) {
        val nbId = cursor.getString(0)
        val hash = cursor.getString(1)
        result.getOrPut(hash) { mutableSetOf() }.add(nbId)
    }
    cursor.close()
    return result
}

/** Vrací sdílené zdroje mezi skupinou notebooků (pro název skupiny) */
fun getSharedSourceTitles(notebookIds: Set<String>): List<Pair<String, Int>> {
    if (notebookIds.size < 2) return emptyList()
    val placeholders = notebookIds.joinToString(",") { "?" }
    val cursor = readableDatabase.rawQuery("""
        SELECT title, COUNT(DISTINCT notebook_id) as cnt
        FROM notebook_sources
        WHERE notebook_id IN ($placeholders) AND content_hash IS NOT NULL
        GROUP BY content_hash
        HAVING cnt >= 2
        ORDER BY cnt DESC
    """, notebookIds.toTypedArray())
    val result = mutableListOf<Pair<String, Int>>()
    while (cursor.moveToNext()) {
        result.add(cursor.getString(0) to cursor.getInt(1))
    }
    cursor.close()
    return result
}

/** Vrátí Set notebook ID které mají naskenované zdroje */
fun getScannedNotebookIds(): Set<String> {
    val result = mutableSetOf<String>()
    val cursor = readableDatabase.rawQuery(
        "SELECT DISTINCT notebook_id FROM notebook_sources", null
    )
    while (cursor.moveToNext()) { result.add(cursor.getString(0)) }
    cursor.close()
    return result
}

/** Vrátí Set notebook ID které mají embedding */
fun getEmbeddedNotebookIds(): Set<String> {
    val result = mutableSetOf<String>()
    val cursor = readableDatabase.rawQuery(
        "SELECT notebook_id FROM notebook_embeddings", null
    )
    while (cursor.moveToNext()) { result.add(cursor.getString(0)) }
    cursor.close()
    return result
}

/** Vrátí Set notebook ID které mají facety */
fun getClassifiedNotebookIds(): Set<String> {
    val result = mutableSetOf<String>()
    val cursor = readableDatabase.rawQuery(
        "SELECT notebook_id FROM notebook_facets", null
    )
    while (cursor.moveToNext()) { result.add(cursor.getString(0)) }
    cursor.close()
    return result
}
```

**Step 3: CRUD metody pro notebook_status**

```kotlin
/** Zapíše sources_scanned_at pro notebook */
fun markSourcesScanned(notebookId: String) {
    val cv = ContentValues().apply {
        put("notebook_id", notebookId)
        put("sources_scanned_at", java.time.Instant.now().toString())
    }
    writableDatabase.insertWithOnConflict("notebook_status", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Zapíše dedup_done_at pro notebook */
fun markDedupDone(notebookId: String) {
    val cv = ContentValues().apply {
        put("notebook_id", notebookId)
        put("dedup_done_at", java.time.Instant.now().toString())
    }
    // UPSERT — zachovej sources_scanned_at pokud existuje
    val existing = readableDatabase.rawQuery(
        "SELECT sources_scanned_at FROM notebook_status WHERE notebook_id = ?",
        arrayOf(notebookId)
    )
    if (existing.moveToFirst()) {
        cv.put("sources_scanned_at", existing.getString(0))
    }
    existing.close()
    writableDatabase.insertWithOnConflict("notebook_status", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Vrátí Set notebook ID kde dedup proběhl */
fun getDedupedNotebookIds(): Set<String> {
    val result = mutableSetOf<String>()
    val cursor = readableDatabase.rawQuery(
        "SELECT notebook_id FROM notebook_status WHERE dedup_done_at IS NOT NULL", null
    )
    while (cursor.moveToNext()) { result.add(cursor.getString(0)) }
    cursor.close()
    return result
}
```

**Step 4: Commit**

```
feat: DB schéma — notebook_sources a notebook_status tabulky
```

---

### Task 2: Data modely — SourceRecord, SourceScanState, NotebookStatusIndicators

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModelModels.kt`

**Step 1: Přidat nové modely**

Na konec `AppViewModelModels.kt`:

```kotlin
/** Záznam zdroje pro DB */
data class SourceRecord(
    val sourceId: String,
    val title: String,
    val type: String,      // PDF, WEB, YOUTUBE, TEXT, OTHER
    val contentHash: String?,
)

/** Stav batch skenu zdrojů */
data class SourceScanState(
    val running: Boolean = false,
    val currentNotebook: String = "",
    val progress: String = "",
    val done: Boolean = false,
    val error: String? = null,
)

/** Indikátory stavu notebooku (pro UI tečky) */
data class NotebookIndicators(
    val scanned: Boolean = false,
    val embedded: Boolean = false,
    val classified: Boolean = false,
    val deduped: Boolean = false,
)
```

**Step 2: Přidat SOURCES do NotebookSort**

V `AppViewModelModels.kt` řádek 20–27, přidat `SOURCES`:

```kotlin
enum class NotebookSort(val label: String) {
    DEFAULT("datum"),
    NAME_ASC("A-Z"),
    NAME_DESC("Z-A"),
    CATEGORY("kat."),
    SOURCES("zdroje");

    fun next(): NotebookSort = entries[(ordinal + 1) % entries.size]
}
```

**Step 3: Commit**

```
feat: modely SourceRecord, SourceScanState, NotebookIndicators + sort SOURCES
```

---

### Task 3: Batch akce scanSources v AppViewModelGlobalOps

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModelGlobalOps.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModel.kt`

**Step 1: Přidat StateFlow do AppViewModel.kt**

Po řádku 67 (za `_classify`):

```kotlin
internal val _sourceScan = MutableStateFlow(SourceScanState())
val sourceScan: StateFlow<SourceScanState> get() = _sourceScan

// Indikátory stavu notebooků — refreshují se po batch akcích
internal val _indicators = MutableStateFlow<Map<String, NotebookIndicators>>(emptyMap())
val indicators: StateFlow<Map<String, NotebookIndicators>> get() = _indicators
```

Přidat dismiss helper (kolem řádku 484):

```kotlin
fun dismissSourceScan() { _sourceScan.value = SourceScanState() }
```

**Step 2: Přidat refreshIndicators() do AppViewModel.kt**

```kotlin
/** Načte indikátory stavu všech notebooků z DB */
fun refreshIndicators() {
    viewModelScope.launch(Dispatchers.IO) {
        val scanned = embeddingDb.getScannedNotebookIds()
        val embedded = embeddingDb.getEmbeddedNotebookIds()
        val classified = embeddingDb.getClassifiedNotebookIds()
        val deduped = embeddingDb.getDedupedNotebookIds()
        val allIds = scanned + embedded + classified + deduped
        _indicators.value = allIds.associateWith { id ->
            NotebookIndicators(
                scanned = id in scanned,
                embedded = id in embedded,
                classified = id in classified,
                deduped = id in deduped,
            )
        }
    }
}
```

Zavolat `refreshIndicators()` na konci `init {}` bloku.

**Step 3: Přidat scanSources() do AppViewModelGlobalOps.kt**

Na konec souboru (za `semanticSearch()`):

```kotlin
/** Batch sken zdrojů — stáhne zdroje, zahashuje obsah, uloží do DB */
fun AppViewModel.scanSources(ids: Set<String>? = null) {
    val tokens = authManager.loadTokens() ?: return
    val allNbs = _notebooks.value.toList()
    val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
    if (nbs.isEmpty()) return

    _sourceScan.value = SourceScanState(running = true)

    viewModelScope.launch {
        val api = NotebookLmApi(httpClient, tokens)

        for ((idx, nb) in nbs.withIndex()) {
            _sourceScan.value = _sourceScan.value.copy(
                currentNotebook = nb.title,
                progress = "${idx + 1}/${nbs.size}",
            )

            try {
                val sources = api.getSources(nb.id)
                val records = mutableListOf<SourceRecord>()

                for (src in sources) {
                    val hash = try {
                        when (src.type) {
                            SourceType.TEXT -> {
                                val content = api.getSourceFulltext(nb.id, src.id)
                                sha256(content)
                            }
                            SourceType.PDF -> {
                                val content = api.getSourceFulltext(nb.id, src.id)
                                // První strana: split po form feed, nebo první ~3000 znaků
                                val firstPage = content.split('\u000C').firstOrNull()
                                    ?: content.take(3000)
                                sha256("${src.title}\n$firstPage")
                            }
                            else -> sha256(src.title)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "hash ${src.title}: ${e.message}")
                        sha256(src.title) // fallback na title
                    }

                    records.add(SourceRecord(
                        sourceId = src.id,
                        title = src.title,
                        type = src.type.name,
                        contentHash = hash,
                    ))
                }

                withContext(Dispatchers.IO) {
                    embeddingDb.upsertSources(nb.id, records)
                    embeddingDb.markSourcesScanned(nb.id)
                }
            } catch (e: Exception) {
                Log.w(TAG, "scanSources ${nb.title}: ${e.message}")
            }
        }

        refreshIndicators()
        _sourceScan.value = SourceScanState(done = true, progress = "hotovo")
    }
}

private fun sha256(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}
```

**Step 4: Aktualizovat existující batch akce — refresh indikátorů**

V `startDeduplication()` (řádek 98–103), po nastavení done stavu přidat:

```kotlin
// Po _dedup.value = DeduplicationState(done = true, ...)
// Označit sešity jako deduplikované
for (nb in nbs) {
    withContext(Dispatchers.IO) { embeddingDb.markDedupDone(nb.id) }
}
refreshIndicators()
```

V `startClassification()` (řádek 231), po `_facets.value = ...`:

```kotlin
refreshIndicators()
```

V `embedNotebooks()` (řádek 304), po `_embeddingStatus.value = null`:

```kotlin
refreshIndicators()
```

**Step 5: Commit**

```
feat: batch akce scanSources + indikátory stavu notebooků
```

---

### Task 4: Union-Find seskupení v sortedNotebooks

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/AppViewModel.kt`

**Step 1: Přidat sourceGroups StateFlow a union-find logiku**

Přidat do AppViewModel (za `_indicators`):

```kotlin
/** Skupiny sešitů podle sdílených zdrojů (union-find) — mapa notebookId -> groupLabel */
internal val _sourceGroups = MutableStateFlow<Map<String, String>>(emptyMap())
val sourceGroups: StateFlow<Map<String, String>> get() = _sourceGroups

/** Přepočítá union-find skupiny ze zdrojů v DB */
fun refreshSourceGroups() {
    viewModelScope.launch(Dispatchers.IO) {
        val hashGroups = embeddingDb.getSourceHashGroups()
        // Filtruj jen hashe sdílené ≥2 sešity
        val shared = hashGroups.filter { it.value.size >= 2 }

        if (shared.isEmpty()) {
            _sourceGroups.value = emptyMap()
            return@launch
        }

        // Union-Find
        val parent = mutableMapOf<String, String>()
        fun find(x: String): String {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent[r]!!
            // Path compression
            var c = x
            while (c != r) { val next = parent[c] ?: r; parent[c] = r; c = next }
            return r
        }
        fun union(a: String, b: String) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        for ((_, nbIds) in shared) {
            val list = nbIds.toList()
            for (i in 1 until list.size) union(list[0], list[i])
        }

        // Seskup podle root
        val allNbIds = hashGroups.values.flatten().toSet()
        val groups = mutableMapOf<String, MutableSet<String>>()
        for (id in allNbIds) {
            val root = find(id)
            groups.getOrPut(root) { mutableSetOf() }.add(id)
        }

        // Pro každou skupinu najdi nejčastější sdílený title
        val result = mutableMapOf<String, String>()
        for ((_, members) in groups) {
            if (members.size < 2) continue
            val titles = embeddingDb.getSharedSourceTitles(members)
            val label = if (titles.isEmpty()) "Sdílené zdroje"
            else if (titles.size == 1) titles[0].first
            else "${titles[0].first} (+${titles.size - 1})"
            for (id in members) result[id] = label
        }
        _sourceGroups.value = result
    }
}
```

**Step 2: Rozšířit sortedNotebooks() pro SOURCES mód**

V `sortedNotebooks()` (řádek 448–460):

```kotlin
fun sortedNotebooks(notebooks: List<Notebook>): List<Notebook> {
    val favs = _favorites.value
    val cats = _categories.value
    val sorted = when (_notebookSort.value) {
        NotebookSort.DEFAULT -> notebooks
        NotebookSort.NAME_ASC -> notebooks.sortedBy { it.title.lowercase() }
        NotebookSort.NAME_DESC -> notebooks.sortedByDescending { it.title.lowercase() }
        NotebookSort.CATEGORY -> notebooks.sortedBy { (cats[it.id] ?: "zzz").lowercase() }
        NotebookSort.SOURCES -> notebooks.sortedBy {
            (_sourceGroups.value[it.id] ?: "zzz Bez sdílených zdrojů").lowercase()
        }
    }
    return if (_notebookSort.value in setOf(NotebookSort.CATEGORY, NotebookSort.SOURCES)) sorted
    else sorted.sortedByDescending { it.id in favs }
}
```

**Step 3: Zavolat refreshSourceGroups() po scanSources**

V `scanSources()`, po `refreshIndicators()` přidat:

```kotlin
refreshSourceGroups()
```

A v `init {}` po `refreshIndicators()`:

```kotlin
refreshSourceGroups()
```

**Step 4: Commit**

```
feat: union-find seskupení sešitů podle sdílených zdrojů
```

---

### Task 5: Indikátory na NotebookCard

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/NotebookListComponents.kt`

**Step 1: Přidat StatusDots composable**

Před `NotebookCard` (před řádek 95):

```kotlin
/** 4 indikátorové tečky stavu notebooku */
@Composable
private fun StatusDots(indicators: NotebookIndicators) {
    val dots = listOf(
        indicators.scanned to Color(0xFF7AA2F7),  // modrá (pastelová)
        indicators.embedded to Color(0xFF9ECE6A),  // zelená
        indicators.classified to Color(0xFFE0AF68), // žlutá
        indicators.deduped to Color(0xFFF7768E),   // růžová
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((active, color) in dots) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .then(
                        if (active) Modifier
                            .shadow(4.dp, CircleShape, ambientColor = color, spotColor = color)
                            .background(color, CircleShape)
                        else Modifier
                            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
                    )
            )
        }
    }
}
```

**Step 2: Přidat indicators parametr do NotebookCard**

Přidat `indicators: NotebookIndicators` parametr do `NotebookCard()` (řádek 97–106).

Zobrazit StatusDots v Column s texty (za title, před/vedle category):

```kotlin
Column(modifier = Modifier.weight(1f)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = nb.title,
            color = Term.white,
            fontFamily = Term.font,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        StatusDots(indicators)
    }
    if (category != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = category,
            color = Term.purple,
            fontFamily = Term.font,
            fontSize = Term.fontSize,
        )
    }
}
```

**Step 3: Přidat indicators parametr do SwipeableNotebookItem**

Propagovat `indicators` parametr přes `SwipeableNotebookItem` → `NotebookCard`.

**Step 4: Commit**

```
feat: indikátory stavu (4 tečky) na kartě sešitu
```

---

### Task 6: Legenda pod nadpisem

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/NotebookListScreen.kt`

**Step 1: Přidat StatusLegend composable**

V `NotebookListComponents.kt`:

```kotlin
/** Legenda indikátorů pod nadpisem */
@Composable
internal fun StatusLegend() {
    val items = listOf(
        "Zdroje" to Color(0xFF7AA2F7),
        "Embed" to Color(0xFF9ECE6A),
        "AI kat." to Color(0xFFE0AF68),
        "Dedup" to Color(0xFFF7768E),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 16.dp),
    ) {
        for ((label, color) in items) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .shadow(3.dp, CircleShape, ambientColor = color, spotColor = color)
                        .background(color, CircleShape)
                )
                Text(
                    text = label,
                    color = Term.textDim,
                    fontFamily = Term.font,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
```

**Step 2: Vložit do NotebookListScreen za nadpis**

V `NotebookListScreen.kt`, pod Row s nadpisem "NotebookLM" a zelenou tečkou (řádek ~276–293):

```kotlin
StatusLegend()
```

**Step 3: Commit**

```
feat: legenda indikátorů pod nadpisem NotebookLM
```

---

### Task 7: UI — batch akce v selection baru a ⋯ menu

**Files:**
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/NotebookListScreen.kt`
- Modify: `app/src/main/java/dev/jara/notebooklm/ui/NotebookListComponents.kt`

**Step 1: Přidat parametry do NotebookListScreen**

Přidat do parametrů `NotebookListScreen()`:

```kotlin
sourceScan: SourceScanState,
onScanSources: (Set<String>?) -> Unit,
onDismissSourceScan: () -> Unit,
indicators: Map<String, NotebookIndicators>,
sourceGroups: Map<String, String>,
onDedupSelected: (Set<String>) -> Unit,
```

**Step 2: Přidat "Zdroje" a "Dedup" do selection baru**

V selection baru (řádek 321–343), za `AI kat.` ActionPill:

```kotlin
ActionPill(
    text = "Zdroje",
    color = Color(0xFF7AA2F7),
    onClick = {
        onScanSources(selectedIds)
        selectedIds = emptySet()
    },
)
ActionPill(
    text = "Dedup",
    color = Term.red,
    onClick = {
        onDedupSelected(selectedIds)
        selectedIds = emptySet()
    },
)
```

**Step 3: Přidat "Skenuj zdroje všech" do ⋯ menu**

V `BottomActionBar`, přidat `onScanSourcesAll` parametr a menu item:

```kotlin
DropdownMenuItem(
    text = { Text("Skenuj zdroje všech", color = Term.text, fontFamily = Term.font, fontSize = Term.fontSizeLg) },
    onClick = { menuExpanded = false; onScanSourcesAll() },
)
```

**Step 4: StatusBar pro source scan**

V `NotebookListScreen.kt`, vedle existujících StatusBar podmínek (dedup, classify, embed):

```kotlin
if (sourceScan.running) {
    StatusBar(
        text = "Zdroje: ${sourceScan.progress} — ${sourceScan.currentNotebook}",
        color = Color(0xFF7AA2F7),
    )
} else if (sourceScan.done) {
    StatusBar(
        text = "Sken zdrojů dokončen",
        color = Color(0xFF7AA2F7),
        onDismiss = onDismissSourceScan,
    )
}
```

**Step 5: SOURCES groupování v LazyColumn**

Přidat podmínku pro `sortMode == NotebookSort.SOURCES` v LazyColumn (řádek 443), analogicky ke CATEGORY:

```kotlin
if (sortMode == NotebookSort.SOURCES) {
    val grouped = facetFiltered
        .groupBy { sourceGroups[it.id] ?: "Bez sdílených zdrojů" }
        .toSortedMap(compareBy { if (it == "Bez sdílených zdrojů") "zzz" else it.lowercase() })
    for ((group, nbs) in grouped) {
        item(key = "src_$group") {
            Text(
                text = group,
                color = Color(0xFF7AA2F7),
                fontFamily = Term.font,
                fontSize = Term.fontSizeLg,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        for (nb in nbs) {
            item(key = nb.id) {
                SwipeableNotebookItem(
                    nb = nb,
                    isFavorite = nb.id in favorites,
                    category = null,
                    isSelected = nb.id in selectedIds,
                    selectionMode = selectionMode,
                    indicators = indicators[nb.id] ?: NotebookIndicators(),
                    // ... rest params ...
                )
            }
        }
    }
}
```

**Step 6: Propagace indicators do všech SwipeableNotebookItem volání**

Přidat `indicators = indicators[nb.id] ?: NotebookIndicators()` do všech míst kde se volá `SwipeableNotebookItem`.

**Step 7: Commit**

```
feat: UI — batch sken zdrojů, dedup v selection baru, SOURCES seskupení
```

---

### Task 8: Propojení v MainActivity / hlavním Compose

**Files:**
- Modify: soubor kde se volá `NotebookListScreen()` (pravděpodobně `MainActivity.kt` nebo `AppNavigation.kt`)

**Step 1: Najít kde se volá NotebookListScreen a přidat nové parametry**

Přidat:
```kotlin
sourceScan = viewModel.sourceScan.collectAsState().value,
onScanSources = { ids -> viewModel.scanSources(ids) },
onDismissSourceScan = { viewModel.dismissSourceScan() },
indicators = viewModel.indicators.collectAsState().value,
sourceGroups = viewModel.sourceGroups.collectAsState().value,
onDedupSelected = { ids -> viewModel.startDeduplication(ids) },
```

**Step 2: Rozšířit startDeduplication o filtraci dle IDs**

V `AppViewModelGlobalOps.kt`, přidat parametr `ids: Set<String>? = null` do `startDeduplication()`:

```kotlin
fun AppViewModel.startDeduplication(ids: Set<String>? = null) {
    val tokens = authManager.loadTokens() ?: return
    val allNbs = _notebooks.value.toList()
    val nbs = if (ids != null) allNbs.filter { it.id in ids } else allNbs
    if (nbs.isEmpty()) return
    // ... rest ...
}
```

**Step 3: Commit + push**

```
feat: propojení nových batch akcí a indikátorů do hlavního UI
```

---

### Task 9: Finální integrace a test

**Step 1: Build**

```bash
cd /home/jara/dev/android-notebooklm && ./gradlew assembleDebug
```

**Step 2: Ověřit kompilaci, opravit chyby**

**Step 3: Ruční test na zařízení/emulátoru**

Checklist:
- [ ] Sort cykluje přes všech 5 módů
- [ ] Sken zdrojů funguje (selection + ⋯ menu)
- [ ] Indikátory se zobrazují na kartách po batch akcích
- [ ] Legenda je vidět pod nadpisem
- [ ] SOURCES sort seskupuje sešity se sdílenými zdroji
- [ ] Dedup v selection baru funguje
- [ ] StatusBar zobrazuje progress

**Step 4: Final commit + push**

```
feat: seskupení sešitů podle sdílených zdrojů — kompletní
```
