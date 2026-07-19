package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.ConversationHistoryStore
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.AIProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.tr

// Provider, API key, system prompt, speech behavior, and how long the AI remembers past
// conversations -- everything about how the AI itself thinks and talks.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConversationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var aiProvider by remember { mutableStateOf(SettingsManager.aiProvider) }
    var geminiAPIKey by remember { mutableStateOf(SettingsManager.geminiAPIKey) }
    var openaiAPIKey by remember { mutableStateOf(SettingsManager.openaiAPIKey) }
    var systemPrompt by remember { mutableStateOf(SettingsManager.geminiSystemPrompt) }
    var continuousConversationEnabled by remember { mutableStateOf(SettingsManager.continuousConversationEnabled) }
    var aiSpeechSpeed by remember { mutableStateOf(SettingsManager.aiSpeechSpeed) }
    var useGlassesMic by remember { mutableStateOf(SettingsManager.useGlassesMic) }
    var useSpeakerForAiVoice by remember { mutableStateOf(SettingsManager.useSpeakerForAiVoice) }
    var conversationHistoryEnabled by remember { mutableStateOf(SettingsManager.conversationHistoryRetentionDays != 0) }
    var conversationHistoryRetentionDays by remember {
        mutableStateOf(
            (if (SettingsManager.conversationHistoryRetentionDays == 0) SettingsManager.DEFAULT_HISTORY_RETENTION_DAYS
            else SettingsManager.conversationHistoryRetentionDays).toString()
        )
    }

    fun save() {
        SettingsManager.aiProvider = aiProvider
        SettingsManager.geminiAPIKey = geminiAPIKey.trim()
        SettingsManager.openaiAPIKey = openaiAPIKey.trim()
        SettingsManager.geminiSystemPrompt = systemPrompt.trim()
        SettingsManager.continuousConversationEnabled = continuousConversationEnabled
        SettingsManager.aiSpeechSpeed = aiSpeechSpeed
        SettingsManager.useGlassesMic = useGlassesMic
        SettingsManager.useSpeakerForAiVoice = useSpeakerForAiVoice
        val newRetentionDays = if (conversationHistoryEnabled) {
            conversationHistoryRetentionDays.trim().toIntOrNull() ?: SettingsManager.DEFAULT_HISTORY_RETENTION_DAYS
        } else {
            0
        }
        if (newRetentionDays == 0 && SettingsManager.conversationHistoryRetentionDays != 0) {
            ConversationHistoryStore(context).clearAll()
        }
        SettingsManager.conversationHistoryRetentionDays = newRetentionDays
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(tr("IA & Conversa", "AI & Conversation")) },
            navigationIcon = {
                IconButton(onClick = { save(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(tr("Provedor de IA", "AI Provider"))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AIProvider.entries.forEach { provider ->
                    val selected = aiProvider == provider
                    Button(
                        onClick = { aiProvider = provider },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(if (provider == AIProvider.GEMINI) "Gemini" else "ChatGPT")
                    }
                }
            }

            if (aiProvider == AIProvider.GEMINI) {
                SectionHeader(tr("API do Gemini", "Gemini API"))
                MonoTextField(
                    value = geminiAPIKey,
                    onValueChange = { geminiAPIKey = it },
                    label = tr("Chave de API", "API Key"),
                    placeholder = tr("Insira a chave de API do Gemini", "Enter Gemini API key"),
                )
            } else {
                SectionHeader(tr("API da OpenAI", "OpenAI API"))
                MonoTextField(
                    value = openaiAPIKey,
                    onValueChange = { openaiAPIKey = it },
                    label = tr("Chave de API", "API Key"),
                    placeholder = tr("Insira a chave de API da OpenAI", "Enter OpenAI API key"),
                )
            }

            SectionHeader(tr("Prompt do Sistema", "System Prompt"))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text(tr("Prompt do sistema", "System prompt")) },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )

            SectionHeader(tr("Comportamento da Conversa", "Conversation Behavior"))
            SwitchRow(
                title = tr("Conversa Contínua", "Continuous Conversation"),
                description = tr(
                    "Continua ouvindo por alguns segundos após cada resposta, sem repetir a palavra de ativação.",
                    "Keep listening for a few seconds after each reply, without repeating the wake phrase.",
                ),
                checked = continuousConversationEnabled,
                onCheckedChange = { continuousConversationEnabled = it },
            )
            SwitchRow(
                title = tr("Lembrar Conversas", "Remember Conversations"),
                description = tr(
                    "Mantém o histórico de conversas neste celular entre reinicializações, para a IA lembrar de conversas passadas.",
                    "Keep conversation history on this phone across restarts, so the AI remembers past conversations.",
                ),
                checked = conversationHistoryEnabled,
                onCheckedChange = { conversationHistoryEnabled = it },
            )
            if (conversationHistoryEnabled) {
                MonoTextField(
                    value = conversationHistoryRetentionDays,
                    onValueChange = { conversationHistoryRetentionDays = it },
                    label = tr("Manter Histórico Por (dias, -1 = para sempre)", "Keep History For (days, -1 = forever)"),
                    placeholder = SettingsManager.DEFAULT_HISTORY_RETENTION_DAYS.toString(),
                )
            }

            SectionHeader(tr("Microfone", "Microphone"))
            SwitchRow(
                title = tr("Usar Mic dos Óculos", "Use Glasses Mic"),
                description = tr(
                    "Captura a voz pelo mic Bluetooth dos óculos em vez do celular, para melhor captação com o celular no bolso. Pausa qualquer música Bluetooth no celular enquanto ouve (retoma automaticamente depois).",
                    "Capture voice through the glasses' Bluetooth mic instead of the phone's, for better pickup with the phone in a pocket. Pauses any Bluetooth music on the phone while listening (resumes automatically after).",
                ),
                checked = useGlassesMic,
                onCheckedChange = { useGlassesMic = it },
            )

            SectionHeader(tr("Fala da IA", "AI Speech"))
            LabeledSlider(
                label = tr("Velocidade da Fala", "Speech Speed"),
                value = aiSpeechSpeed,
                onValueChange = { aiSpeechSpeed = it },
                valueRange = 0.75f..2f,
            )
            SwitchRow(
                title = tr("Usar Alto-falante do Celular", "Use Phone Speaker"),
                description = tr(
                    "Reproduz as respostas da IA pelo alto-falante do celular em vez do fone/óculos, para pessoas por perto também ouvirem.",
                    "Play AI responses through the phone's loudspeaker instead of the earpiece/glasses, so people nearby can hear too.",
                ),
                checked = useSpeakerForAiVoice,
                onCheckedChange = { useSpeakerForAiVoice = it },
            )
        }
    }
}
