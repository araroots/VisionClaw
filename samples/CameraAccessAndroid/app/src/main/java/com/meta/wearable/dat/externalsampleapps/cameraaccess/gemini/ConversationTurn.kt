package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

// A single completed turn in the primary voice/chat conversation, kept around so it can be
// replayed into a fresh Gemini Live / OpenAI Realtime session after the AI is toggled off and
// back on -- neither backend supports true session resumption, so this is how continuity is
// faked across reconnects.
data class ConversationTurn(
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT }
}
