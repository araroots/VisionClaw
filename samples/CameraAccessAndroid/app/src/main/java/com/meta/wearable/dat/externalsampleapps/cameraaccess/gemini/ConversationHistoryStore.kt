package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wakeword.normalizePhrase
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

// Persists the primary AI conversation to disk, grouped into browsable sessions (one file each)
// the way a modern chat app would, instead of one endless log -- lets the AI remember past
// conversations across restarts, and lets the user later find/search a specific one.
//
// A new turn joins the most recently updated session if it arrived within SESSION_GAP_MS of that
// session's last turn; otherwise it starts a fresh session. Retention is bounded by
// SettingsManager.conversationHistoryRetentionDays -- whole sessions older than that window are
// deleted on every write. A hard session-count cap also applies so "forever" retention can't grow
// storage without bound.
class ConversationHistoryStore(context: Context) {
    companion object {
        private const val TAG = "ConversationHistoryStore"
        private const val SESSION_GAP_MS = 30 * 60 * 1000L
        private const val MAX_TURNS_PER_SESSION = 200
        private const val MAX_SESSIONS = 200
        private const val TITLE_MAX_CHARS = 48
        const val RETENTION_DISABLED = 0
        const val RETENTION_FOREVER = -1
    }

    private val dir = File(context.filesDir, "conversation_sessions").apply { mkdirs() }
    private val lock = Any()

    // Turns to replay into a freshly (re)started AI session so it feels like a continued
    // conversation -- pulled from the single most recent session, not stitched across sessions,
    // since a real gap means the "recall the last few exchanges" purpose no longer applies.
    fun recentTurnsForSeed(retentionDays: Int, maxTurns: Int): List<ConversationTurn> {
        if (retentionDays == RETENTION_DISABLED) return emptyList()
        synchronized(lock) {
            val cutoffMs = cutoffMs(retentionDays)
            val latest = allSessions()
                .filter { cutoffMs == null || it.lastUpdatedAtMs >= cutoffMs }
                .maxByOrNull { it.lastUpdatedAtMs } ?: return emptyList()
            return latest.turns.takeLast(maxTurns).map { it.turn }
        }
    }

    fun appendTurn(turn: ConversationTurn, retentionDays: Int) {
        if (retentionDays == RETENTION_DISABLED) return
        synchronized(lock) {
            pruneExpired(retentionDays)
            val now = System.currentTimeMillis()
            val latest = allSessions().maxByOrNull { it.lastUpdatedAtMs }

            val session = if (latest != null && now - latest.lastUpdatedAtMs <= SESSION_GAP_MS) {
                latest
            } else {
                ConversationSession(
                    id = "${now}-${UUID.randomUUID().toString().take(8)}",
                    title = "",
                    startedAtMs = now,
                    lastUpdatedAtMs = now,
                    turns = emptyList(),
                )
            }

            val title = session.title.ifEmpty {
                if (turn.role == ConversationTurn.Role.USER) turn.text.take(TITLE_MAX_CHARS) else ""
            }
            val newTurns = (session.turns + ConversationTurnEntry(turn, now)).takeLast(MAX_TURNS_PER_SESSION)

            writeSession(session.copy(title = title, lastUpdatedAtMs = now, turns = newTurns))
            enforceSessionCap()
        }
    }

    fun listSessions(retentionDays: Int): List<ConversationSessionSummary> {
        synchronized(lock) {
            pruneExpired(retentionDays)
            return allSessions()
                .sortedByDescending { it.lastUpdatedAtMs }
                .map { it.toSummary() }
        }
    }

    fun loadSession(id: String): ConversationSession? {
        synchronized(lock) {
            return readSession(sessionFile(id))
        }
    }

    // Searches every stored session's title and message text, regardless of retention window --
    // anything still on disk is fair game, since appendTurn/listSessions already enforce the
    // retention window at write/prune time.
    fun searchSessions(query: String): List<ConversationSessionSummary> {
        val normalizedQuery = normalizePhrase(query)
        if (normalizedQuery.isBlank()) return emptyList()
        synchronized(lock) {
            return allSessions()
                .filter { session ->
                    normalizePhrase(session.title.ifEmpty { "untitled" }).contains(normalizedQuery) ||
                        session.turns.any { normalizePhrase(it.turn.text).contains(normalizedQuery) }
                }
                .sortedByDescending { it.lastUpdatedAtMs }
                .map { it.toSummary() }
        }
    }

    fun deleteSession(id: String) {
        synchronized(lock) { sessionFile(id).delete() }
    }

    fun clearAll() {
        synchronized(lock) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun ConversationSession.toSummary() = ConversationSessionSummary(
        id = id,
        title = title.ifEmpty { "Untitled conversation" },
        startedAtMs = startedAtMs,
        lastUpdatedAtMs = lastUpdatedAtMs,
        turnCount = turns.size,
    )

    private fun cutoffMs(retentionDays: Int): Long? {
        if (retentionDays == RETENTION_FOREVER) return null
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
    }

    private fun pruneExpired(retentionDays: Int) {
        val cutoffMs = cutoffMs(retentionDays) ?: return
        allSessionFiles().forEach { file ->
            val session = readSession(file)
            if (session == null || session.lastUpdatedAtMs < cutoffMs) {
                file.delete()
            }
        }
    }

    private fun enforceSessionCap() {
        val files = allSessionFiles()
        if (files.size <= MAX_SESSIONS) return
        files
            .mapNotNull { file -> readSession(file)?.let { file to it } }
            .sortedBy { it.second.lastUpdatedAtMs }
            .take(files.size - MAX_SESSIONS)
            .forEach { (file, _) -> file.delete() }
    }

    private fun sessionFile(id: String) = File(dir, "$id.json")

    private fun allSessionFiles(): List<File> =
        dir.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList()

    private fun allSessions(): List<ConversationSession> =
        allSessionFiles().mapNotNull(::readSession)

    private fun readSession(file: File): ConversationSession? {
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            val turnsArray = obj.getJSONArray("turns")
            val turns = (0 until turnsArray.length()).mapNotNull { i ->
                val turnObj = turnsArray.optJSONObject(i) ?: return@mapNotNull null
                val role = try {
                    ConversationTurn.Role.valueOf(turnObj.optString("role"))
                } catch (e: IllegalArgumentException) {
                    return@mapNotNull null
                }
                ConversationTurnEntry(
                    turn = ConversationTurn(role, turnObj.optString("text")),
                    timestampMs = turnObj.optLong("timestampMs"),
                )
            }
            ConversationSession(
                id = obj.optString("id"),
                title = obj.optString("title"),
                startedAtMs = obj.optLong("startedAtMs"),
                lastUpdatedAtMs = obj.optLong("lastUpdatedAtMs"),
                turns = turns,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read session ${file.name}, discarding: ${e.message}")
            null
        }
    }

    private fun writeSession(session: ConversationSession) {
        val turnsArray = JSONArray()
        for (entry in session.turns) {
            turnsArray.put(JSONObject().apply {
                put("role", entry.turn.role.name)
                put("text", entry.turn.text)
                put("timestampMs", entry.timestampMs)
            })
        }
        val obj = JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("startedAtMs", session.startedAtMs)
            put("lastUpdatedAtMs", session.lastUpdatedAtMs)
            put("turns", turnsArray)
        }
        try {
            sessionFile(session.id).writeText(obj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write session ${session.id}: ${e.message}")
        }
    }
}
