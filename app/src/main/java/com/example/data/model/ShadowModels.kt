package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MemoryCategory(val label: String) {
    GOAL("Long-term Goal"),
    PREFERENCE("Preference"),
    IDEA("Core Idea"),
    DECISION("Decision"),
    PATTERN("Thinking Pattern"),
    PROBLEM("Problem"),
    OUTCOME("Outcome");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name == raw } ?: IDEA
    }
}

enum class MemorySource {
    USER,
    SHADOW_PROPOSED;

    companion object {
        fun fromRaw(raw: String?): MemorySource =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

enum class MessageRole {
    USER,
    SHADOW;

    companion object {
        fun fromRaw(raw: String?): MessageRole =
            entries.firstOrNull { it.name == raw } ?: SHADOW
    }
}

enum class ReasoningMode(val displayLabel: String) {
    GREETING("Presence"),
    UNDERSTAND("Understanding"),
    CHALLENGE("Challenging"),
    QUESTION("Deep Inquiry"),
    BLIND_SPOT("Blind Spot"),
    CREATIVE_SYNTHESIS("Cross-Domain Synthesis");

    companion object {
        fun fromRaw(raw: String?): ReasoningMode =
            entries.firstOrNull { it.name == raw } ?: UNDERSTAND
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val memoryEnabled: Boolean = true,
    val faceConsentGranted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = MemoryCategory.IDEA.name,
    val title: String = "",
    val content: String = "",
    val approved: Boolean = false,
    val source: String = MemorySource.USER.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): MemoryCategory = MemoryCategory.fromRaw(category)
    fun sourceEnum(): MemorySource = MemorySource.fromRaw(source)
}

@Keep
@IgnoreExtraProperties
data class ConversationMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = MessageRole.SHADOW.name,
    val text: String = "",
    val reasoningMode: String = ReasoningMode.UNDERSTAND.name,
    val clientOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): MessageRole = MessageRole.fromRaw(role)
    fun reasoningModeEnum(): ReasoningMode = ReasoningMode.fromRaw(reasoningMode)
}

data class ProposedMemory(
    val category: MemoryCategory,
    val title: String,
    val content: String
)

data class ShadowAiResponse(
    val spokenResponse: String,
    val reasoningMode: ReasoningMode,
    val proposedMemory: ProposedMemory? = null
)

enum class ShadowPresenceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}
