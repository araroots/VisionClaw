package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

// Controls the wake-word speech recognizer's locale and the default (uncustomized) voice
// trigger phrases, so the app is usable by voice for English speakers too, not just Portuguese.
enum class AppLanguage(val speechRecognizerLocale: String, val shortLabel: String) {
    PORTUGUESE("pt-BR", "PT"),
    ENGLISH("en-US", "EN"),
}
