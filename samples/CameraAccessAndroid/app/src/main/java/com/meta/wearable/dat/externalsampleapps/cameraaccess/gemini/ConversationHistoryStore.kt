package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

// Persists the primary AI conversation to disk so it survives app restarts, letting the AI
// remember past conversations instead of starting cold every time the process is killed.
// Retention is bounded by SettingsManager.conversationHistoryRetentionDays -- entries older than
// that window are pruned on every write. A hard entry cap is also enforced so "forever" retention
// can't grow the file without bound.
class ConversationHistoryStore(context: Context) {
    companion object {
        private const val TAG = "ConversationHistoryStore"
        private const val FILE_NAME = "conversation_history.json"
        private const val MAX_STORED_ENTRIES = 500
        const val RETENTION_DISABLED = 0
        const val RETENTION_FOREVER = -1
    }

    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    // Returns turns within the retention window, oldest first.
    fun load(retentionDays: Int): List<ConversationTurn> {
        if (retentionDays == RETENTION_DISABLED) return emptyList()
        synchronized(lock) {
            val entries = readEntries()
            val cutoffMs = cutoffMs(retentionDays)
            return entries
                .filter { cutoffMs == null || it.timestampMs >= cutoffMs }
                .map { ConversationTurn(it.role, it.text) }
        }
    }

    fun append(turn: ConversationTurn, retentionDays: Int) {
        if (retentionDays == RETENTION_DISABLED) return
        synchronized(lock) {
            val entries = readEntries().toMutableList()
            entries.add(Entry(turn.role, turn.text, System.currentTimeMillis()))

            val cutoffMs = cutoffMs(retentionDays)
            val pruned = entries
                .filter { cutoffMs == null || it.timestampMs >= cutoffMs }
                .takeLast(MAX_STORED_ENTRIES)

            writeEntries(pruned)
        }
    }

    fun clear() {
        synchronized(lock) {
            file.delete()
        }
    }

    private fun cutoffMs(retentionDays: Int): Long? {
        if (retentionDays == RETENTION_FOREVER) return null
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
    }

    private fun readEntries(): List<Entry> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val role = try {
                    ConversationTurn.Role.valueOf(obj.optString("role"))
                } catch (e: IllegalArgumentException) {
                    return@mapNotNull null
                }
                Entry(role, obj.optString("text"), obj.optLong("timestampMs"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read history, discarding: ${e.message}")
            emptyList()
        }
    }

    private fun writeEntries(entries: List<Entry>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(JSONObject().apply {
                put("role", entry.role.name)
                put("text", entry.text)
                put("timestampMs", entry.timestampMs)
            })
        }
        try {
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write history: ${e.message}")
        }
    }

    private data class Entry(val role: ConversationTurn.Role, val text: String, val timestampMs: Long)
}
