package dev.jara.notebooklm.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SQLite databaze pro notebook embeddingy.
 * Funkcne ekvivalent sqlite-vec z Rust implementace,
 * ale KNN se pocita v Kotlinu (cosine similarity).
 */
class EmbeddingDb(context: Context) : SQLiteOpenHelper(
    context, "notebooklm_embeddings.db", null, 1
) {
    companion object {
        private const val TAG = "EmbeddingDb"
        const val EMBEDDING_DIM = 512
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notebook_embeddings (
                notebook_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                embedding BLOB NOT NULL,
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS notebook_embeddings")
        onCreate(db)
    }

    /** Ulozi embedding pro notebook. Prepise pokud existuje. */
    fun upsertEmbedding(notebookId: String, title: String, description: String, embedding: FloatArray) {
        Log.i(TAG, "upsert: id=$notebookId, title=$title, embDim=${embedding.size}, first3=${embedding.take(3)}")
        val blob = floatArrayToBlob(embedding)
        val cv = ContentValues().apply {
            put("notebook_id", notebookId)
            put("title", title)
            put("description", description)
            put("embedding", blob)
        }
        writableDatabase.insertWithOnConflict("notebook_embeddings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Zjisti jestli notebook potrebuje re-embed (text se zmenil) */
    fun needsUpdate(notebookId: String, currentText: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT title, description FROM notebook_embeddings WHERE notebook_id = ?",
            arrayOf(notebookId)
        )
        val needs = if (cursor.moveToFirst()) {
            val oldTitle = cursor.getString(0)
            val oldDesc = cursor.getString(1)
            val oldText = if (oldDesc.isEmpty()) oldTitle else "$oldTitle $oldDesc"
            oldText != currentText
        } else true
        cursor.close()
        return needs
    }

    /** KNN search — cosine similarity, vraci top K vysledku */
    /** Pocet ulozenych embeddingu */
    fun count(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM notebook_embeddings", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    /** KNN search — cosine similarity, vraci top K vysledku */
    fun search(queryEmbedding: FloatArray, limit: Int = 20): List<Pair<String, Float>> {
        val totalCount = count()
        Log.i(TAG, "search: queryDim=${queryEmbedding.size}, dbCount=$totalCount")
        val results = mutableListOf<Pair<String, Float>>()
        val cursor = readableDatabase.rawQuery(
            "SELECT notebook_id, embedding FROM notebook_embeddings", null
        )
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val blob = cursor.getBlob(1)
            val emb = blobToFloatArray(blob)
            val similarity = cosineSimilarity(queryEmbedding, emb)
            results.add(id to similarity)
        }
        cursor.close()
        results.sortByDescending { it.second }
        Log.i(TAG, "search: ${results.size} vysledku, top3=${results.take(3).map { "${it.first.take(8)}:${it.second}" }}")
        return results.take(limit)
    }

    /** Smaze embeddingy ktere uz neexistuji v seznamu */
    fun pruneDeleted(currentIds: Set<String>) {
        if (currentIds.isEmpty()) return
        val placeholders = currentIds.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "DELETE FROM notebook_embeddings WHERE notebook_id NOT IN ($placeholders)",
            currentIds.toTypedArray()
        )
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denom > 0) (dot / denom).toFloat() else 0f
    }

    private fun floatArrayToBlob(arr: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in arr) buf.putFloat(f)
        return buf.array()
    }

    private fun blobToFloatArray(blob: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(blob.size / 4) { buf.getFloat() }
    }
}
