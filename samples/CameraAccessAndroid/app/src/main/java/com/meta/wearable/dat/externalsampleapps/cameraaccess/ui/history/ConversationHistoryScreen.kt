package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.ConversationHistoryStore
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.ConversationSessionSummary
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import java.text.SimpleDateFormat
import java.util.Locale

// Browsable, searchable list of past AI conversations -- entry point lives outside Settings
// since this is content, not configuration.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val historyStore = remember { ConversationHistoryStore(context) }
    var searchQuery by remember { mutableStateOf("") }
    var sessions by remember {
        mutableStateOf(historyStore.listSessions(SettingsManager.conversationHistoryRetentionDays))
    }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        sessions = if (searchQuery.isBlank()) {
            historyStore.listSessions(SettingsManager.conversationHistoryRetentionDays)
        } else {
            historyStore.searchSessions(searchQuery)
        }
    }

    LaunchedEffect(searchQuery) { refresh() }

    val currentSelection = selectedSessionId
    if (currentSelection != null) {
        ConversationSessionDetailScreen(
            sessionId = currentSelection,
            onBack = { selectedSessionId = null; refresh() },
            onDeleted = { selectedSessionId = null; refresh() },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Conversation History") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search conversations") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isBlank()) "No conversations yet" else "No matches",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session, onClick = { selectedSessionId = session.id })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ConversationSessionSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(session.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${formatSessionTimestamp(session.lastUpdatedAtMs)} · ${session.turnCount} messages",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

private fun formatSessionTimestamp(timestampMs: Long): String {
    val format = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return format.format(timestampMs)
}
