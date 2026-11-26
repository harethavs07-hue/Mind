package com.example.ui.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ShadowPresenceState
import com.example.ui.components.ShadowRoomBackdrop
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldInsight
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PortalCyan
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.ShadowGlass
import com.example.ui.theme.ShadowSurface
import com.example.ui.theme.ShadowSurfaceElevated
import com.example.ui.theme.SoftLavender
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.WhisperSilver

private enum class ActiveSheet {
    NONE,
    HISTORY,
    MEMORY,
    PERSONALIZATION,
    SETTINGS
}

@Composable
fun ShadowRoomScreen(
    viewModel: ShadowRoomViewModel,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var micPermissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                micPermissionDeniedMessage = null
                viewModel.startListening()
            } else {
                micPermissionDeniedMessage = "Microphone permission denied. You can continue seamlessly using text below."
            }
        }
    )

    val triggerMic = {
        if (state.voiceStatus.isListening) {
            viewModel.stopListening()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                micPermissionDeniedMessage = null
                viewModel.startListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. The Shadow Room Environment (always visually central)
        ShadowRoomBackdrop(
            presenceState = state.presenceState,
            micLevel = state.voiceStatus.micRmsLevel,
            lowPerformanceMode = state.localPrefs.lowPerformanceMode,
            faceImagePath = state.localPrefs.faceImagePath,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Minimal Unobtrusive Interaction Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Minimal Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresenceBadge(
                    presenceState = state.presenceState,
                    modeLabel = state.activeReasoningMode.displayLabel
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ShadowGlass,
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleVoiceEnabled() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("voice_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (state.localPrefs.voiceEnabled) {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Rounded.VolumeOff
                                },
                                contentDescription = if (state.localPrefs.voiceEnabled) {
                                    "Mute Shadow voice"
                                } else {
                                    "Enable Shadow voice"
                                },
                                tint = if (state.localPrefs.voiceEnabled) ElectricBlue else MutedSlate
                            )
                        }

                        IconButton(
                            onClick = { activeSheet = ActiveSheet.HISTORY },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Conversation history",
                                tint = WhisperSilver
                            )
                        }

                        IconButton(
                            onClick = { activeSheet = ActiveSheet.MEMORY },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Psychology,
                                contentDescription = "Personal memory controls",
                                tint = if (state.memories.isNotEmpty()) EmeraldInsight else WhisperSilver
                            )
                        }

                        IconButton(
                            onClick = { activeSheet = ActiveSheet.PERSONALIZATION },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("personalization_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Face,
                                contentDescription = "Shadow identity personalization",
                                tint = if (state.localPrefs.faceImagePath != null) PortalCyan else WhisperSilver
                            )
                        }

                        IconButton(
                            onClick = { activeSheet = ActiveSheet.SETTINGS },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Voice and room settings",
                                tint = WhisperSilver
                            )
                        }
                    }
                }
            }

            // Bottom Conversational Caption + Memory Proposal + Voice-First Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Voice Partial Transcript while listening
                AnimatedVisibility(
                    visible = state.voiceStatus.isListening,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ElectricBlue.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.voiceStatus.partialTranscript.ifBlank { "Listening… speak naturally." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = PortalCyan,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                // Error Banner (network, AI, permission, or Firestore error)
                val visibleError = micPermissionDeniedMessage ?: state.errorMessage
                AnimatedVisibility(visible = !visibleError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CrimsonAlert.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = visibleError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = WhisperSilver,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    micPermissionDeniedMessage = null
                                    viewModel.dismissError()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss error",
                                    tint = WhisperSilver
                                )
                            }
                        }
                    }
                }

                // Optional "Enable Voice" button if voice was off on greeting
                AnimatedVisibility(visible = state.showEnableVoiceHint) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ShadowSurfaceElevated,
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voice is muted.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSlate
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = { viewModel.enableVoiceAndSpeakCurrent() },
                                modifier = Modifier.testTag("enable_voice_prompt_button")
                            ) {
                                Text("Enable Voice", color = ElectricBlue)
                            }
                        }
                    }
                }

                // Unobtrusive optional Shadow personalization prompt (dismissible, never blocks chat)
                AnimatedVisibility(
                    visible = !state.localPrefs.faceChoicePrompted &&
                        state.localPrefs.faceImagePath == null &&
                        state.isReady
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ShadowGlass,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "Would you like your Shadow to carry your face, or remain anonymous?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftLavender
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.dismissFaceChoicePrompt() }) {
                                    Text("Keep Anonymous", color = MutedSlate)
                                }
                                TextButton(
                                    onClick = {
                                        viewModel.dismissFaceChoicePrompt()
                                        activeSheet = ActiveSheet.PERSONALIZATION
                                    }
                                ) {
                                    Text("Use My Face", color = ElectricBlue)
                                }
                            }
                        }
                    }
                }

                // Proposed Memory Approval Pill (never silently saved without consent)
                state.pendingMemoryProposal?.let { proposal ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ShadowSurfaceElevated.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, EmeraldInsight.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.BookmarkAdd,
                                    contentDescription = null,
                                    tint = EmeraldInsight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "REMEMBER THIS ${proposal.category.label.uppercase()}?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldInsight
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${proposal.title} — ${proposal.content}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WhisperSilver,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.dismissProposedMemory() }) {
                                    Text("Dismiss", color = MutedSlate)
                                }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        viewModel.approveProposedMemory(
                                            category = proposal.category,
                                            title = proposal.title,
                                            content = proposal.content
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldInsight,
                                        contentColor = ObsidianVoid
                                    )
                                ) {
                                    Text("Approve & Save")
                                }
                            }
                        }
                    }
                }

                // Synchronized Caption Surface (always visible & legible over the room)
                if (state.activeCaption.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = ShadowGlass,
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .heightIn(max = 185.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "THE SHADOW · ${state.activeReasoningMode.displayLabel.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoftLavender
                                )
                                if (state.voiceStatus.isSpeaking) {
                                    TextButton(
                                        onClick = { viewModel.stopSpeaking() },
                                        modifier = Modifier.testTag("stop_speaking_button")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Stop,
                                            contentDescription = "Stop speaking",
                                            tint = PortalCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Stop Voice", color = PortalCyan)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.activeCaption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = WhisperSilver
                            )
                        }
                    }
                }

                // Bottom Voice-First + Text Control Dock
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = ShadowSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary Voice Microphone / Stop Button
                        val isListening = state.voiceStatus.isListening
                        val isSpeaking = state.voiceStatus.isSpeaking
                        Surface(
                            onClick = {
                                if (isSpeaking) {
                                    viewModel.stopSpeaking()
                                } else {
                                    triggerMic()
                                }
                            },
                            shape = CircleShape,
                            color = when {
                                isListening -> CrimsonAlert
                                isSpeaking -> PortalCyan
                                else -> ElectricBlue
                            },
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("mic_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when {
                                        isListening || isSpeaking -> Icons.Rounded.Stop
                                        else -> Icons.Rounded.Mic
                                    },
                                    contentDescription = when {
                                        isListening -> "Stop listening"
                                        isSpeaking -> "Stop Shadow voice"
                                        else -> "Speak to your Shadow"
                                    },
                                    tint = ObsidianVoid,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Always-Accessible Text Alternative Input
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    text = if (state.isWorking) "Shadow is thinking…" else "Speak or write to your Shadow…",
                                    color = MutedSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            maxLines = 3,
                            enabled = !state.isWorking,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (textInput.isNotBlank() && !state.isWorking) {
                                        val payload = textInput
                                        textInput = ""
                                        viewModel.sendUserMessage(payload)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedTextColor = WhisperSilver,
                                unfocusedTextColor = WhisperSilver,
                                cursorColor = ElectricBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("text_input")
                        )

                        // Send Button
                        val canSend = textInput.isNotBlank() && !state.isWorking
                        IconButton(
                            onClick = {
                                val payload = textInput
                                textInput = ""
                                viewModel.sendUserMessage(payload)
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                tint = if (canSend) ElectricBlue else MutedSlate.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Slide-Up Sheets inside the Single Shadow Room
    when (activeSheet) {
        ActiveSheet.HISTORY -> {
            ConversationHistorySheet(
                messages = state.messages,
                onClearConversation = { viewModel.clearConversation() },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.MEMORY -> {
            PersonalMemorySheet(
                memories = state.memories,
                memoryEnabled = state.userProfile?.memoryEnabled ?: true,
                onToggleMemoryEnabled = { viewModel.setMemoryEnabled(it) },
                onAddMemory = { cat, title, content ->
                    viewModel.addManualMemory(cat, title, content)
                },
                onUpdateMemory = { id, cat, title, content, approved ->
                    viewModel.updateMemory(id, cat, title, content, approved)
                },
                onDeleteMemory = { viewModel.deleteMemory(it) },
                onClearAllPersonalData = { viewModel.clearAllPersonalData() },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.PERSONALIZATION -> {
            ShadowPersonalizationSheet(
                currentFacePath = state.localPrefs.faceImagePath,
                onApplyFaceUri = { viewModel.applyFaceImage(it) },
                onKeepAnonymous = { viewModel.removeFaceImageAndKeepAnonymous() },
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.SETTINGS -> {
            RoomSettingsSheet(
                voiceEnabled = state.localPrefs.voiceEnabled,
                speechRate = state.localPrefs.speechRate,
                speechVolume = state.localPrefs.speechVolume,
                lowPerformanceMode = state.localPrefs.lowPerformanceMode,
                userEmail = userEmail,
                onToggleVoice = { viewModel.toggleVoiceEnabled() },
                onSpeechRateChange = { viewModel.setSpeechRate(it) },
                onSpeechVolumeChange = { viewModel.setSpeechVolume(it) },
                onLowPerformanceChange = { viewModel.setLowPerformanceMode(it) },
                onSignOut = onSignOut,
                onDismiss = { activeSheet = ActiveSheet.NONE }
            )
        }
        ActiveSheet.NONE -> Unit
    }
}

@Composable
private fun PresenceBadge(
    presenceState: ShadowPresenceState,
    modeLabel: String
) {
    val label = when (presenceState) {
        ShadowPresenceState.IDLE -> "OBSERVING"
        ShadowPresenceState.LISTENING -> "LISTENING"
        ShadowPresenceState.PROCESSING -> "THINKING"
        ShadowPresenceState.SPEAKING -> "SPEAKING"
    }
    val accent = when (presenceState) {
        ShadowPresenceState.IDLE -> SoftLavender
        ShadowPresenceState.LISTENING -> PortalCyan
        ShadowPresenceState.PROCESSING -> RoyalViolet
        ShadowPresenceState.SPEAKING -> ElectricBlue
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ShadowGlass,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent,
                modifier = Modifier.size(8.dp)
            ) {}
            Text(
                text = if (presenceState == ShadowPresenceState.IDLE) label else "$label · ${modeLabel.uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = WhisperSilver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
