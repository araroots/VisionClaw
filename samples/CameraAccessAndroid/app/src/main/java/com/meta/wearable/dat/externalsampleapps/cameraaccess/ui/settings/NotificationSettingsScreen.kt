package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var proactiveNotificationsEnabled by remember { mutableStateOf(SettingsManager.proactiveNotificationsEnabled) }

    fun save() {
        SettingsManager.proactiveNotificationsEnabled = proactiveNotificationsEnabled
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(tr("Notificações", "Notifications")) },
            navigationIcon = {
                IconButton(onClick = { save(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SwitchRow(
                title = tr("Notificações Proativas", "Proactive Notifications"),
                description = tr(
                    "Receba atualizações do OpenClaw faladas pelos óculos.",
                    "Receive updates from OpenClaw spoken through glasses.",
                ),
                checked = proactiveNotificationsEnabled,
                onCheckedChange = { proactiveNotificationsEnabled = it },
            )
        }
    }
}
