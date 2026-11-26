package com.example.ui.room

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ShadowAiService
import com.example.data.model.ConversationMessage
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MemorySource
import com.example.data.model.MessageRole
import com.example.data.model.ProposedMemory
import com.example.data.model.ReasoningMode
import com.example.data.model.ShadowPresenceState
import com.example.data.model.UserProfile
import com.example.data.repository.LocalPreferencesRepository
import com.example.data.repository.LocalRoomPreferences
import com.example.data.repository.ShadowRepository
import com.example.voice.ShadowVoiceController
import com.example.voice.VoiceUiStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ShadowRoomVM"

sealed interface FirestoreStreamState<out T> {
    data object Loading : FirestoreStreamState<Nothing>
    data class Ready<T>(val data: T) : FirestoreStreamState<T>
    data class Error(val message: String) : FirestoreStreamState<Nothing>
}

data class ShadowRoomUiState(
    val isReady: Boolean = false,
    val presenceState: ShadowPresenceState = ShadowPresenceState.IDLE,
    val activeCaption: String = "",
    val activeReasoningMode: ReasoningMode = ReasoningMode.GREETING,
    val messages: List<ConversationMessage> = emptyList(),
    val memories: List<MemoryItem> = emptyList(),
    val userProfile: UserProfile? = null,
    val localPrefs: LocalRoomPreferences = LocalRoomPreferences(),
    val voiceStatus: VoiceUiStatus = VoiceUiStatus(),
    val pendingMemoryProposal: ProposedMemory? = null,
    val errorMessage: String? = null,
    val showEnableVoiceHint: Boolean = false,
    val isWorking: Boolean = false
)

class ShadowRoomViewModel(
    private val repository: ShadowRepository,
    private val localPrefsRepository: LocalPreferencesRepository,
    private val aiService: ShadowAiService,
    private val voiceController: ShadowVoiceController,
    private val currentUserId: String,
    private val currentUserDisplayName: String?
) : ViewModel() {

    private val isProcessingAi = MutableStateFlow(false)
    private val transientCaption = MutableStateFlow<String?>(null)
    private val transientMode = MutableStateFlow(ReasoningMode.GREETING)
    private val pendingMemoryProposal = MutableStateFlow<ProposedMemory?>(null)
    private val errorBanner = MutableStateFlow<String?>(null)
    private val showEnableVoiceHint = MutableStateFlow(false)

    private var hasGreetedThisSession = false

    private val profileStream: StateFlow<FirestoreStreamState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, FirestoreStreamState<UserProfile?>> { FirestoreStreamState.Ready(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing profile", rootError)
                emit(FirestoreStreamState.Error(rootError.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = FirestoreStreamState.Loading
            )

    private val memoriesStream: StateFlow<FirestoreStreamState<List<MemoryItem>>> =
        repository.observeMemories(currentUserId)
            .map<List<MemoryItem>, FirestoreStreamState<List<MemoryItem>>> {
                FirestoreStreamState.Ready(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing memories", rootError)
                emit(FirestoreStreamState.Error(rootError.message ?: "Failed to observe memories"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = FirestoreStreamState.Loading
            )

    private val messagesStream: StateFlow<FirestoreStreamState<List<ConversationMessage>>> =
        repository.observeMessages(currentUserId)
            .map<List<ConversationMessage>, FirestoreStreamState<List<ConversationMessage>>> {
                FirestoreStreamState.Ready(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing messages", rootError)
                emit(FirestoreStreamState.Error(rootError.message ?: "Failed to observe conversation"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = FirestoreStreamState.Loading
            )

    val uiState: StateFlow<ShadowRoomUiState> = combine(
        profileStream,
        memoriesStream,
        messagesStream,
        localPrefsRepository.preferencesFlow,
        voiceController.status,
        isProcessingAi,
        transientCaption,
        transientMode,
        pendingMemoryProposal,
        errorBanner,
        showEnableVoiceHint
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profState = values[0] as FirestoreStreamState<UserProfile?>
        @Suppress("UNCHECKED_CAST")
        val memState = values[1] as FirestoreStreamState<List<MemoryItem>>
        @Suppress("UNCHECKED_CAST")
        val msgState = values[2] as FirestoreStreamState<List<ConversationMessage>>
        val localPrefs = values[3] as LocalRoomPreferences
        val voice = values[4] as VoiceUiStatus
        val processing = values[5] as Boolean
        val liveCaption = values[6] as String?
        val liveMode = values[7] as ReasoningMode
        val proposal = values[8] as ProposedMemory?
        val err = values[9] as String?
        val voiceHint = values[10] as Boolean

        val profile = (profState as? FirestoreStreamState.Ready)?.data
        val memories = (memState as? FirestoreStreamState.Ready)?.data.orEmpty()
        val messages = (msgState as? FirestoreStreamState.Ready)?.data.orEmpty()

        val streamError = (profState as? FirestoreStreamState.Error)?.message
            ?: (memState as? FirestoreStreamState.Error)?.message
            ?: (msgState as? FirestoreStreamState.Error)?.message

        val presence = when {
            voice.isListening -> ShadowPresenceState.LISTENING
            processing -> ShadowPresenceState.PROCESSING
            voice.isSpeaking -> ShadowPresenceState.SPEAKING
            else -> ShadowPresenceState.IDLE
        }

        val lastShadowMsg = messages.lastOrNull { it.roleEnum() == MessageRole.SHADOW }
        val captionToDisplay = liveCaption ?: lastShadowMsg?.text.orEmpty()
        val modeToDisplay = if (liveCaption != null) liveMode else (lastShadowMsg?.reasoningModeEnum() ?: ReasoningMode.GREETING)

        val ready = profState is FirestoreStreamState.Ready &&
            memState is FirestoreStreamState.Ready &&
            msgState is FirestoreStreamState.Ready

        ShadowRoomUiState(
            isReady = ready,
            presenceState = presence,
            activeCaption = captionToDisplay,
            activeReasoningMode = modeToDisplay,
            messages = messages,
            memories = memories,
            userProfile = profile,
            localPrefs = localPrefs,
            voiceStatus = voice,
            pendingMemoryProposal = proposal,
            errorMessage = err ?: streamError ?: voice.voiceError,
            showEnableVoiceHint = voiceHint && !localPrefs.voiceEnabled,
            isWorking = processing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ShadowRoomUiState()
    )

    init {
        bootstrapRoomEntry()
    }

    private fun bootstrapRoomEntry() {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, currentUserDisplayName)
                .onFailure { err ->
                    errorBanner.value = err.localizedMessage ?: "Failed to initialize user profile."
                }

            // Wait once until Firestore streams emit initial Ready state so greeting respects real history
            val snapshotState = uiState.first { it.isReady }
            if (!hasGreetedThisSession) {
                hasGreetedThisSession = true
                val approved = snapshotState.memories.filter { it.approved }
                val memoryEnabled = snapshotState.userProfile?.memoryEnabled ?: true
                val greeting = aiService.buildEntryGreeting(
                    approvedMemories = approved,
                    hasPriorConversation = snapshotState.messages.isNotEmpty(),
                    memoryEnabled = memoryEnabled
                )
                transientCaption.value = greeting
                transientMode.value = ReasoningMode.GREETING

                if (snapshotState.localPrefs.voiceEnabled) {
                    voiceController.speak(
                        text = greeting,
                        rate = snapshotState.localPrefs.speechRate,
                        volume = snapshotState.localPrefs.speechVolume
                    )
                } else {
                    showEnableVoiceHint.value = true
                }
            }
        }
    }

    fun sendUserMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || isProcessingAi.value) return

        voiceController.stopSpeaking()
        voiceController.stopListening()
        voiceController.clearVoiceError()
        errorBanner.value = null
        showEnableVoiceHint.value = false

        val snapshot = uiState.value
        val nowOrder = System.currentTimeMillis()

        viewModelScope.launch {
            isProcessingAi.value = true
            try {
                // Persist user turn
                repository.addMessage(
                    userId = currentUserId,
                    role = MessageRole.USER,
                    text = text,
                    reasoningMode = ReasoningMode.UNDERSTAND,
                    clientOrder = nowOrder
                ).onFailure { err ->
                    Log.w(TAG, "Could not persist user message", err)
                }

                val memoryEnabled = snapshot.userProfile?.memoryEnabled ?: true
                val approvedMemories = snapshot.memories.filter { it.approved }

                val aiReply = aiService.respondToUser(
                    userMessage = text,
                    recentHistory = snapshot.messages,
                    approvedMemories = approvedMemories,
                    memoryEnabled = memoryEnabled
                )

                transientCaption.value = aiReply.spokenResponse
                transientMode.value = aiReply.reasoningMode

                repository.addMessage(
                    userId = currentUserId,
                    role = MessageRole.SHADOW,
                    text = aiReply.spokenResponse,
                    reasoningMode = aiReply.reasoningMode,
                    clientOrder = System.currentTimeMillis().coerceAtLeast(nowOrder + 1)
                ).onFailure { err ->
                    errorBanner.value = err.localizedMessage ?: "Failed to save response."
                }

                if (memoryEnabled && aiReply.proposedMemory != null) {
                    pendingMemoryProposal.value = aiReply.proposedMemory
                }

                if (snapshot.localPrefs.voiceEnabled) {
                    voiceController.speak(
                        text = aiReply.spokenResponse,
                        rate = snapshot.localPrefs.speechRate,
                        volume = snapshot.localPrefs.speechVolume
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Shadow response timed out", e)
                errorBanner.value = "The Shadow took too long to respond. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Shadow AI inference failed", e)
                errorBanner.value = e.localizedMessage ?: "Unable to reach the Shadow intelligence."
            } finally {
                isProcessingAi.value = false
            }
        }
    }

    fun startListening() {
        voiceController.clearVoiceError()
        errorBanner.value = null
        voiceController.startListening()
    }

    fun stopListening() {
        voiceController.stopListening()
    }

    fun stopSpeaking() {
        voiceController.stopSpeaking()
    }

    fun enableVoiceAndSpeakCurrent() {
        viewModelScope.launch {
            localPrefsRepository.setVoiceEnabled(true)
            showEnableVoiceHint.value = false
            val currentText = uiState.value.activeCaption
            if (currentText.isNotBlank()) {
                voiceController.speak(
                    text = currentText,
                    rate = uiState.value.localPrefs.speechRate,
                    volume = uiState.value.localPrefs.speechVolume
                )
            }
        }
    }

    fun toggleVoiceEnabled() {
        viewModelScope.launch {
            val next = !uiState.value.localPrefs.voiceEnabled
            localPrefsRepository.setVoiceEnabled(next)
            if (!next) {
                voiceController.stopSpeaking()
            } else {
                showEnableVoiceHint.value = false
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { localPrefsRepository.setSpeechRate(rate) }
    }

    fun setSpeechVolume(volume: Float) {
        viewModelScope.launch { localPrefsRepository.setSpeechVolume(volume) }
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        viewModelScope.launch { localPrefsRepository.setLowPerformanceMode(enabled) }
    }

    // ---- Memory Controls ----

    fun approveProposedMemory(
        category: MemoryCategory,
        title: String,
        content: String
    ) {
        viewModelScope.launch {
            repository.addMemory(
                userId = currentUserId,
                category = category,
                title = title,
                content = content,
                approved = true,
                source = MemorySource.SHADOW_PROPOSED
            ).onSuccess {
                pendingMemoryProposal.value = null
            }.onFailure { err ->
                errorBanner.value = err.localizedMessage ?: "Failed to save memory."
            }
        }
    }

    fun dismissProposedMemory() {
        pendingMemoryProposal.value = null
    }

    fun addManualMemory(category: MemoryCategory, title: String, content: String) {
        viewModelScope.launch {
            repository.addMemory(
                userId = currentUserId,
                category = category,
                title = title,
                content = content,
                approved = true,
                source = MemorySource.USER
            ).onFailure { err ->
                errorBanner.value = err.localizedMessage ?: "Failed to save memory."
            }
        }
    }

    fun updateMemory(
        memoryId: String,
        category: MemoryCategory,
        title: String,
        content: String,
        approved: Boolean
    ) {
        viewModelScope.launch {
            repository.updateMemory(
                userId = currentUserId,
                memoryId = memoryId,
                category = category,
                title = title,
                content = content,
                approved = approved
            ).onFailure { err ->
                errorBanner.value = err.localizedMessage ?: "Failed to update memory."
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(currentUserId, memoryId)
                .onFailure { err ->
                    errorBanner.value = err.localizedMessage ?: "Failed to delete memory."
                }
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePreferences(currentUserId, memoryEnabled = enabled)
                .onFailure { err ->
                    errorBanner.value = err.localizedMessage ?: "Failed to update memory preference."
                }
            if (!enabled) {
                pendingMemoryProposal.value = null
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            voiceController.stopSpeaking()
            repository.clearConversation(currentUserId)
                .onSuccess {
                    transientCaption.value = "The room is clear. What would you like to explore?"
                    transientMode.value = ReasoningMode.GREETING
                }
                .onFailure { err ->
                    errorBanner.value = err.localizedMessage ?: "Failed to clear conversation."
                }
        }
    }

    fun clearAllPersonalData() {
        viewModelScope.launch {
            voiceController.stopSpeaking()
            pendingMemoryProposal.value = null
            localPrefsRepository.removeLocalFaceImage()
            repository.clearAllPersonalData(currentUserId)
                .onSuccess {
                    transientCaption.value = "All personal memories and conversation history have been cleared."
                    transientMode.value = ReasoningMode.GREETING
                }
                .onFailure { err ->
                    errorBanner.value = err.localizedMessage ?: "Failed to clear personal data."
                }
        }
    }

    // ---- Shadow Face Personalization ----

    fun applyFaceImage(uri: Uri) {
        viewModelScope.launch {
            val savedPath = localPrefsRepository.saveLocalFaceImage(uri)
            if (savedPath != null) {
                repository.updatePreferences(currentUserId, faceConsentGranted = true)
            } else {
                errorBanner.value = "Could not load selected image."
            }
        }
    }

    fun removeFaceImageAndKeepAnonymous() {
        viewModelScope.launch {
            localPrefsRepository.removeLocalFaceImage()
            localPrefsRepository.setFaceChoicePrompted(true)
            repository.updatePreferences(currentUserId, faceConsentGranted = false)
        }
    }

    fun dismissFaceChoicePrompt() {
        viewModelScope.launch {
            localPrefsRepository.setFaceChoicePrompted(true)
        }
    }

    fun dismissError() {
        errorBanner.value = null
        voiceController.clearVoiceError()
    }

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }
}
