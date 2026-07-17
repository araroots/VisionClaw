package com.meta.wearable.dat.externalsampleapps.cameraaccess.wakeword

import java.text.Normalizer

// Strips accents/diacritics and case so spoken-phrase matching (wake words, stop phrases) is
// forgiving of accent marks the speech recognizer may or may not transcribe consistently.
fun normalizePhrase(text: String): String {
    val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped.lowercase().trim()
}
