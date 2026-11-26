package com.example.ui.room

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.ConversationMessage
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MessageRole
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldInsight
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoomDepth
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.ShadowSurface
import com.example.ui.theme.ShadowSurfaceElevated
import com.example.ui.theme.SoftLavender
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.WhisperSilver
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistorySheet(
    messages: List<ConversationMessage>,
    onClearConversation: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmClear by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RoomDepth,
        contentColor = WhisperSilver
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Shadow Room Transcript",
                        style = MaterialTheme.typography.headlineSmall,
                        color = WhisperSilver
                    )
                    Text(
                        text = "Continuous conversation history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
                if (messages.isNotEmpty()) {
                    TextButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.testTag("clear_conversation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear conversation",
                            tint = CrimsonAlert
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear", color = CrimsonAlert)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No dialogue recorded yet. Speak or write to begin exploring with your Shadow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id.ifEmpty { it.clientOrder.toString() } }) { msg ->
                        val isUser = msg.roleEnum() == MessageRole.USER
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isUser) ShadowSurfaceElevated else ShadowSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isUser) ElectricBlue.copy(alpha = 0.25f) else SubtleBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isUser) "YOU" else "THE SHADOW",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isUser) ElectricBlue else SoftLavender
                                    )
                                    if (!isUser) {
                                        Text(
                                            text = msg.reasoningModeEnum().displayLabel.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedSlate
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WhisperSilver
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = ShadowSurfaceElevated,
            titleContentColor = WhisperSilver,
            textContentColor = MutedSlate,
            title = { Text("Clear conversation?") },
            text = { Text("This permanently deletes your conversation history in the Shadow Room while keeping your approved memories intact.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearConversation()
                    }
                ) {
                    Text("Clear", color = CrimsonAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = WhisperSilver)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonalMemorySheet(
    memories: List<MemoryItem>,
    memoryEnabled: Boolean,
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onAddMemory: (MemoryCategory, String, String) -> Unit,
    onUpdateMemory: (String, MemoryCategory, String, String, Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClearAllPersonalData: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var creatingMemory by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RoomDepth,
        contentColor = WhisperSilver
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personal Memory",
                        style = MaterialTheme.typography.headlineSmall,
                        color = WhisperSilver
                    )
                    Text(
                        text = "Only approved context guides future sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
                Switch(
                    checked = memoryEnabled,
                    onCheckedChange = onToggleMemoryEnabled,
                    modifier = Modifier.testTag("memory_enabled_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { creatingMemory = true },
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_memory_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = ElectricBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Memory", color = ElectricBlue)
                }
                OutlinedButton(
                    onClick = { confirmWipe = true },
                    border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wipe_all_data_button")
                ) {
                    Text("Wipe Data", color = CrimsonAlert)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved memories yet. When you share goals, preferences, or decisions, you can approve what the Shadow remembers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(memories, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ShadowSurface,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.categoryEnum().label.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (item.approved) EmeraldInsight else SoftLavender
                                    )
                                    Row {
                                        IconButton(
                                            onClick = { editingMemory = item },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Edit,
                                                contentDescription = "Edit memory",
                                                tint = MutedSlate
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteMemory(item.id) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete memory",
                                                tint = CrimsonAlert
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WhisperSilver
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSlate
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    if (creatingMemory || editingMemory != null) {
        val target = editingMemory
        var title by remember(target) { mutableStateOf(target?.title.orEmpty()) }
        var content by remember(target) { mutableStateOf(target?.content.orEmpty()) }
        var category by remember(target) {
            mutableStateOf(target?.categoryEnum() ?: MemoryCategory.GOAL)
        }

        AlertDialog(
            onDismissRequest = {
                creatingMemory = false
                editingMemory = null
            },
            containerColor = ShadowSurfaceElevated,
            titleContentColor = WhisperSilver,
            title = { Text(if (target == null) "Add Personal Memory" else "Refine Memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MemoryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalViolet.copy(alpha = 0.35f),
                                    selectedLabelColor = WhisperSilver
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it.take(1000) },
                        label = { Text("Context / Insight") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            if (target == null) {
                                onAddMemory(category, title, content)
                            } else {
                                onUpdateMemory(target.id, category, title, content, true)
                            }
                            creatingMemory = false
                            editingMemory = null
                        }
                    },
                    enabled = title.isNotBlank() && content.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = ObsidianVoid
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creatingMemory = false
                        editingMemory = null
                    }
                ) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = ShadowSurfaceElevated,
            titleContentColor = WhisperSilver,
            textContentColor = MutedSlate,
            title = { Text("Clear all personal data?") },
            text = { Text("This deletes all saved memories, conversation transcripts, and any personalized Shadow face image.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWipe = false
                        onClearAllPersonalData()
                    }
                ) {
                    Text("Delete Everything", color = CrimsonAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text("Cancel", color = WhisperSilver)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowPersonalizationSheet(
    currentFacePath: String?,
    onApplyFaceUri: (Uri) -> Unit,
    onKeepAnonymous: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var consentChecked by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                previewUri = uri
                consentChecked = false
                permissionNotice = null
            }
        }
    )

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCaptureUri != null) {
                previewUri = pendingCaptureUri
                consentChecked = false
                permissionNotice = null
            }
        }
    )

    val launchCameraCapture = {
        val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val photoFile = File(capturesDir, "shadow_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCaptureUri = uri
        cameraCaptureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                permissionNotice = null
                launchCameraCapture()
            } else {
                permissionNotice = "Camera permission was denied. You can still upload a photo via the system Photo Picker or keep your Shadow anonymous."
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RoomDepth,
        contentColor = WhisperSilver
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Shadow Identity",
                style = MaterialTheme.typography.headlineSmall,
                color = WhisperSilver
            )
            Text(
                text = "Would you like your Shadow to carry your face, or remain anonymous?",
                style = MaterialTheme.typography.bodyLarge,
                color = SoftLavender
            )
            Text(
                text = "This only changes the visual silhouette in your room. Intelligence and reasoning remain identical.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate
            )

            if (previewUri != null || (currentFacePath != null && File(currentFacePath).exists())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val model: Any = previewUri ?: File(currentFacePath!!)
                    Image(
                        painter = rememberAsyncImagePainter(model = model),
                        contentDescription = "Face preview for Shadow silhouette",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (previewUri != null) "Preview ready" else "Active on silhouette",
                            style = MaterialTheme.typography.titleMedium,
                            color = WhisperSilver
                        )
                        Text(
                            text = "Stored only inside your device's private app storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate
                        )
                    }
                }
            }

            if (previewUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I consent to compositing this image onto my Shadow locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhisperSilver
                    )
                }

                Button(
                    onClick = {
                        previewUri?.let { uri ->
                            onApplyFaceUri(uri)
                            onDismiss()
                        }
                    },
                    enabled = consentChecked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = ObsidianVoid
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply to Shadow")
                }
            }

            if (permissionNotice != null) {
                Text(
                    text = permissionNotice.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonAlert
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("pick_face_photo_button")
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = ElectricBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Upload", color = ElectricBlue)
                }

                OutlinedButton(
                    onClick = {
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam) {
                            launchCameraCapture()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    border = BorderStroke(1.dp, RoyalViolet.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("capture_face_camera_button")
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = SoftLavender)
                    Spacer(Modifier.width(6.dp))
                    Text("Camera", color = SoftLavender)
                }
            }

            OutlinedButton(
                onClick = {
                    onKeepAnonymous()
                    onDismiss()
                },
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("keep_anonymous_button")
            ) {
                Icon(Icons.Rounded.PersonOff, contentDescription = null, tint = WhisperSilver)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (currentFacePath != null) "Remove Face & Keep Anonymous" else "Keep It Anonymous",
                    color = WhisperSilver
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSettingsSheet(
    voiceEnabled: Boolean,
    speechRate: Float,
    speechVolume: Float,
    lowPerformanceMode: Boolean,
    userEmail: String?,
    onToggleVoice: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechVolumeChange: (Float) -> Unit,
    onLowPerformanceChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RoomDepth,
        contentColor = WhisperSilver
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Voice & Room Controls",
                style = MaterialTheme.typography.headlineSmall,
                color = WhisperSilver
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shadow Voice Output", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Speak responses aloud inside the room",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
                Switch(
                    checked = voiceEnabled,
                    onCheckedChange = { onToggleVoice() },
                    modifier = Modifier.testTag("voice_output_switch")
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Voice Speed", style = MaterialTheme.typography.bodyMedium, color = WhisperSilver)
                    Text(
                        text = "${"%.2f".format(speechRate)}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue
                    )
                }
                Slider(
                    value = speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = 0.65f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricBlue,
                        activeTrackColor = ElectricBlue
                    )
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Voice Volume", style = MaterialTheme.typography.bodyMedium, color = WhisperSilver)
                    Text(
                        text = "${(speechVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue
                    )
                }
                Slider(
                    value = speechVolume,
                    onValueChange = onSpeechVolumeChange,
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = RoyalViolet,
                        activeTrackColor = RoyalViolet
                    )
                )
            }

            HorizontalDivider(color = SubtleBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reduced Animation Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Lower particle & portal motion for battery or older devices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                }
                Switch(
                    checked = lowPerformanceMode,
                    onCheckedChange = onLowPerformanceChange,
                    modifier = Modifier.testTag("low_perf_switch")
                )
            }

            HorizontalDivider(color = SubtleBorder)

            if (!userEmail.isNullOrBlank()) {
                Text(
                    text = "Signed in as $userEmail",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate
                )
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onSignOut()
                },
                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = CrimsonAlert)
                Spacer(Modifier.width(8.dp))
                Text("Leave Room (Sign Out)", color = CrimsonAlert)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
