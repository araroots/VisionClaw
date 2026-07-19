package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

// One timestamped turn as stored on disk (ConversationTurn itself carries no timestamp, since
// in-memory usage never needed one until sessions/search required showing "when").
data class ConversationTurnEntry(
    val turn: ConversationTurn,
    val timestampMs: Long,
)

// A single browsable conversation -- turns get grouped into the same session as long as they're
// within SESSION_GAP_MS of each other; a longer gap starts a new one. One file per session on
// disk (see ConversationHistoryStore).
data class ConversationSession(
    val id: String,
    val title: String,
    val startedAtMs: Long,
    val lastUpdatedAtMs: Long,
    val turns: List<ConversationTurnEntry>,
)

// Lightweight listing entry -- everything about a session except its full turn list, cheap to
// build for a scrollable history list.
data class ConversationSessionSummary(
    val id: String,
    val title: String,
    val startedAtMs: Long,
    val lastUpdatedAtMs: Long,
    val turnCount: Int,
)
