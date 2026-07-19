package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.ConversationHistoryStore
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.ConversationTurn
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.ConversationTurnEntry
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr

// Read-only transcript of one saved conversation, with delete.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val historyStore = remember { ConversationHistoryStore(context) }
    val session = remember(sessionId) { historyStore.loadSession(sessionId) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    session?.title?.ifEmpty { tr("Conversa sem título", "Untitled conversation") }
                        ?: tr("Conversa", "Conversation"),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
                }
            },
            actions = {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = tr("Excluir conversa", "Delete conversation"))
                }
            },
        )

        if (session == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("Conversa não encontrada", "Conversation not found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(session.turns) { entry -> TranscriptBubble(entry) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(tr("Excluir Conversa", "Delete Conversation")) },
            text = { Text(tr("Isso não pode ser desfeito.", "This cannot be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    historyStore.deleteSession(sessionId)
                    showDeleteDialog = false
                    onDeleted()
                }) {
                    Text(tr("Excluir", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(tr("Cancelar", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun TranscriptBubble(entry: ConversationTurnEntry) {
    val isUser = entry.turn.role == ConversationTurn.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = entry.turn.text,
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
