package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings.AIConversationSettingsScreen
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings.CameraVideoSettingsScreen
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings.NotificationSettingsScreen
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings.OpenClawSettingsScreen
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings.WakeWordSettingsScreen

private sealed class SettingsRoute {
    object Hub : SettingsRoute()
    object AIConversation : SettingsRoute()
    object WakeWords : SettingsRoute()
    object OpenClaw : SettingsRoute()
    object CameraVideo : SettingsRoute()
    object Notifications : SettingsRoute()
}

// Entry point into settings: a category hub, each category opening its own sub-screen. Kept as
// one router composable (rather than Jetpack Navigation) to match the rest of the app's simple
// boolean/state-driven screen switching (see CameraAccessScaffold).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Hub) }

    when (route) {
        SettingsRoute.AIConversation ->
            AIConversationSettingsScreen(onBack = { route = SettingsRoute.Hub }, modifier = modifier)
        SettingsRoute.WakeWords ->
            WakeWordSettingsScreen(onBack = { route = SettingsRoute.Hub }, modifier = modifier)
        SettingsRoute.OpenClaw ->
            OpenClawSettingsScreen(onBack = { route = SettingsRoute.Hub }, modifier = modifier)
        SettingsRoute.CameraVideo ->
            CameraVideoSettingsScreen(onBack = { route = SettingsRoute.Hub }, modifier = modifier)
        SettingsRoute.Notifications ->
            NotificationSettingsScreen(onBack = { route = SettingsRoute.Hub }, modifier = modifier)
        SettingsRoute.Hub ->
            SettingsHub(
                onBack = onBack,
                onOpenCategory = { route = it },
                modifier = modifier,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHub(
    onBack: () -> Unit,
    onOpenCategory: (SettingsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            SettingsCategoryRow(
                title = "AI & Conversation",
                description = "Provider, API key, system prompt, memory",
                icon = Icons.Default.Psychology,
                onClick = { onOpenCategory(SettingsRoute.AIConversation) },
            )
            SettingsCategoryRow(
                title = "Wake Words & Voice Control",
                description = "Hands-free phrases for the AI, camera, recording",
                icon = Icons.Default.RecordVoiceOver,
                onClick = { onOpenCategory(SettingsRoute.WakeWords) },
            )
            SettingsCategoryRow(
                title = "OpenClaw",
                description = "Gateway connection, tokens, agent routing",
                icon = Icons.Default.Hub,
                onClick = { onOpenCategory(SettingsRoute.OpenClaw) },
            )
            SettingsCategoryRow(
                title = "Camera & Video",
                description = "Quality, frame rate, WebRTC, image adjustments",
                icon = Icons.Default.Videocam,
                onClick = { onOpenCategory(SettingsRoute.CameraVideo) },
            )
            SettingsCategoryRow(
                title = "Notifications",
                description = "Proactive updates from OpenClaw",
                icon = Icons.Default.Notifications,
                onClick = { onOpenCategory(SettingsRoute.Notifications) },
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Text("Reset to Defaults", color = Color.Red)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("This will reset all settings to the values built into the app.") },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.resetAll()
                    showResetDialog = false
                }) {
                    Text("Reset", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}
