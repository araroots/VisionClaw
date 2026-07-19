package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.AppLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.LocalAppLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

// Shows both language options side by side, with the active one highlighted, rather than a
// single flag that silently swaps on tap -- a lone flag reads as a status badge, not a control
// (that's exactly the decorative flag this toggle originally replaced). Seeing "PT | EN" with
// one lit up makes it obvious there's a second option and that this thing is tappable.
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    val appLanguage = LocalAppLanguage.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(3.dp),
    ) {
        LanguageSegment(
            emoji = "🇧🇷",
            label = "PT",
            selected = appLanguage == AppLanguage.PORTUGUESE,
            onClick = { SettingsManager.appLanguage = AppLanguage.PORTUGUESE },
        )
        LanguageSegment(
            emoji = "🇺🇸",
            label = "EN",
            selected = appLanguage == AppLanguage.ENGLISH,
            onClick = { SettingsManager.appLanguage = AppLanguage.ENGLISH },
        )
    }
}

@Composable
private fun LanguageSegment(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = emoji, fontSize = 15.sp)
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
