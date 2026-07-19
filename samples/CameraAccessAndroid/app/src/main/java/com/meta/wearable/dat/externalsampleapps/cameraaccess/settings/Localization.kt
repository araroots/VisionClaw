package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Read via tr(pt, en) below; not meant to be read directly except by ProvideAppLanguage.
val LocalAppLanguage: ProvidableCompositionLocal<AppLanguage> =
    staticCompositionLocalOf { AppLanguage.PORTUGUESE }

// Wrap the app's root content with this once (in MainActivity's setContent) so every screen
// below it recomposes automatically when the language flag is toggled on HomeScreen.
@Composable
fun ProvideAppLanguage(content: @Composable () -> Unit) {
  val language by SettingsManager.appLanguageFlow.collectAsStateWithLifecycle()
  CompositionLocalProvider(LocalAppLanguage provides language) {
    content()
  }
}

// The one function every screen uses to get a translated string: tr("Parar", "Stop"). Keeping
// the two strings side by side at each call site (rather than a separate key-based resource
// catalog) is what keeps a translation from silently drifting out of sync with its English
// original as the surrounding UI copy changes.
@Composable
fun tr(pt: String, en: String): String =
    if (LocalAppLanguage.current == AppLanguage.ENGLISH) en else pt
