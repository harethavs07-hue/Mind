package com.example.ai

import android.util.Log
import com.example.data.model.ConversationMessage
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MessageRole
import com.example.data.model.ProposedMemory
import com.example.data.model.ReasoningMode
import com.example.data.model.ShadowAiResponse
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "ShadowAiService"

class ShadowAiService {

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "spokenResponse" to Schema.string(
                description = "The Shadow's natural, calm, voice-ready conversational response. Avoid robotic bullet lists or markdown formatting."
            ),
            "reasoningMode" to Schema.enumeration(
                values = listOf(
                    "UNDERSTAND",
                    "CHALLENGE",
                    "QUESTION",
                    "BLIND_SPOT",
                    "CREATIVE_SYNTHESIS"
                ),
                description = "The dominant cognitive capability used in this response."
            ),
            "proposedMemory" to Schema.obj(
                properties = mapOf(
                    "shouldPropose" to Schema.boolean(
                        description = "True only if the user shared a clear, durable goal, preference, decision, problem, core idea, or outcome worth asking permission to save."
                    ),
                    "category" to Schema.enumeration(
                        values = listOf("GOAL", "PREFERENCE", "IDEA", "DECISION", "PATTERN", "PROBLEM", "OUTCOME")
                    ),
                    "title" to Schema.string(
                        description = "Short 3-8 word summary title of the memory."
                    ),
                    "content" to Schema.string(
                        description = "Concise 1-2 sentence memory description grounded strictly in what the user stated."
                    )
                ),
                description = "Optional memory proposal for explicit user approval. Never set shouldPropose=true for guesses or trivial chat."
            )
        ),
        optionalProperties = listOf("proposedMemory")
    )

    private val shadowSystemInstruction: Content = content {
        text(
            """
            You are THE SHADOW inside SUBCONSCIOUS AI — the user's own intelligent second self and subconscious cognitive mirror.
            There is only ONE Shadow, ONE intelligence, and ONE continuous conversation inside the Shadow Room.

            CORE IDENTITY & TONE:
            - Speak like a calm, deeply perceptive intellectual companion. Never sound like a customer-service chatbot, a form, a therapist cliché, or a horror character.
            - Use natural, crisp, spoken language designed to be heard aloud and read as synchronized captions. Never use markdown symbols (#, *, **) or long walls of text.
            - Never claim to literally read the user's mind or know hidden emotions with certainty. Clearly distinguish facts, interpretations, and hypotheses.
            - Never greet repeatedly once a conversation is active, and never ask questions the user already answered.

            UNIFIED COGNITIVE CAPABILITIES (seamlessly blend as the moment demands):
            1. GENUINE UNDERSTANDING: Identify the user's real goal, reasoning chain, stated priorities, implicit assumptions, and evidence. Do not merely paraphrase what they said.
            2. INTELLIGENT CHALLENGE: Never blindly flatter or agree. When an assumption is fragile, overly optimistic, or risky, challenge it constructively with clear logic and evidence.
            3. ONE DEEP QUESTION AT A TIME: When exploring or clarifying, ask at most ONE sharp, adaptive question (why, how, what-if, or alternative perspective) tailored to their exact words. If they ask for a direct answer or concrete ideas, deliver substance immediately without forcing an interrogation.
            4. BLIND SPOT DISCOVERY: Spot contradictions, missing evidence, overlooked risks, conflicting priorities, logical gaps, and cognitive biases — and explain briefly why each matters.
            5. MULTI-DOMAIN IDEA CONNECTION ENGINE: Never stop at obvious cliché suggestions (e.g., generic chatbots, generic courses). Explore cross-industry connections across AI, robotics, healthcare, education, agriculture, climate tech, space, human behavior, psychology, architecture, manufacturing, finance, energy, transportation, biology, material science, and social innovation. Combine technologies with real human problems for a logical reason, reject impractical fluff internally, present the strongest unconventional directions, evaluate feasibility/cost/impact/risks, and outline how to test them in the real world.
            6. HONESTY ABOUT NOVELTY: Never falsely claim an idea has never existed before. Since you do not have live patent/web search attached right now, explicitly note when novelty has not been verified against live existing products or patents.
            7. CONSENT-BASED MEMORY: Only propose a memory when the user reveals a meaningful goal, preference, core idea, decision, problem, pattern, or outcome that isn't already saved. Never silently assume or over-propose.
            """.trimIndent()
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@ShadowAiService.responseSchema
                temperature = 0.75f
            },
            systemInstruction = shadowSystemInstruction
        )
    }

    suspend fun respondToUser(
        userMessage: String,
        recentHistory: List<ConversationMessage>,
        approvedMemories: List<MemoryItem>,
        memoryEnabled: Boolean
    ): ShadowAiResponse {
        val memoryBlock = if (memoryEnabled && approvedMemories.isNotEmpty()) {
            approvedMemories.take(15).joinToString("\n") { mem ->
                "- [${mem.category}] ${mem.title}: ${mem.content}"
            }
        } else {
            "None active."
        }

        val contextPrompt = buildString {
            append("APPROVED USER MEMORIES (memoryEnabled=$memoryEnabled):\n")
            append(memoryBlock)
            append("\n\nRECENT CONVERSATION IN THE SHADOW ROOM:\n")
            val tail = recentHistory.takeLast(14)
            if (tail.isEmpty()) {
                append("(New session)\n")
            } else {
                tail.forEach { msg ->
                    val speaker = if (msg.roleEnum() == MessageRole.USER) "USER" else "SHADOW"
                    append("$speaker: ${msg.text}\n")
                }
            }
            append("\nLATEST USER INPUT:\n")
            append(userMessage.trim())
            append("\n\nRespond as THE SHADOW in JSON.")
        }

        val response = withTimeout(60_000L) {
            model.generateContent(contextPrompt)
        }

        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("The Shadow returned an empty response.")
        }

        return parseResponse(rawJson, memoryEnabled)
    }

    private fun parseResponse(jsonString: String, memoryEnabled: Boolean): ShadowAiResponse {
        val root = JSONObject(jsonString)
        val spoken = root.optString("spokenResponse").trim()
        if (spoken.isEmpty()) {
            throw IllegalStateException("The Shadow response did not contain spoken content.")
        }
        val mode = ReasoningMode.fromRaw(root.optString("reasoningMode"))

        var proposed: ProposedMemory? = null
        if (memoryEnabled && root.has("proposedMemory") && !root.isNull("proposedMemory")) {
            val memObj = root.optJSONObject("proposedMemory")
            if (memObj != null && memObj.optBoolean("shouldPropose", false)) {
                val title = memObj.optString("title").trim().take(120)
                val content = memObj.optString("content").trim().take(1000)
                val category = MemoryCategory.fromRaw(memObj.optString("category"))
                if (title.isNotEmpty() && content.isNotEmpty()) {
                    proposed = ProposedMemory(
                        category = category,
                        title = title,
                        content = content
                    )
                }
            }
        }

        return ShadowAiResponse(
            spokenResponse = spoken,
            reasoningMode = mode,
            proposedMemory = proposed
        )
    }

    fun buildEntryGreeting(
        approvedMemories: List<MemoryItem>,
        hasPriorConversation: Boolean,
        memoryEnabled: Boolean
    ): String {
        if (!hasPriorConversation && approvedMemories.isEmpty()) {
            return "Welcome. I am your Shadow. Tell me what you are thinking about, and we will explore it together."
        }
        if (memoryEnabled && approvedMemories.isNotEmpty()) {
            val focus = approvedMemories.first()
            return "Welcome back. Last time we were circling ${focus.title.lowercase()}. What is on your mind today?"
        }
        return "Welcome back. What is on your mind today?"
    }
}
