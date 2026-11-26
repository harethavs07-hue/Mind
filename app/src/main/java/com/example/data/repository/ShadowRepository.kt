package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ConversationMessage
import com.example.data.model.MemoryCategory
import com.example.data.model.MemoryItem
import com.example.data.model.MemorySource
import com.example.data.model.MessageRole
import com.example.data.model.ReasoningMode
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ShadowRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    // ---- User Profile & Consent Preferences ----

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(userId: String, displayName: String?): Result<Unit> {
        var docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    displayName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }?.let {
                        put("displayName", it)
                    }
                    put("memoryEnabled", true)
                    put("faceConsentGranted", false)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        userId: String,
        memoryEnabled: Boolean? = null,
        faceConsentGranted: Boolean? = null
    ): Result<Unit> {
        var docPath = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val updates = buildMap<String, Any> {
                memoryEnabled?.let { put("memoryEnabled", it) }
                faceConsentGranted?.let { put("faceConsentGranted", it) }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            if (updates.size > 1) {
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // ---- Persistent Cognitive Memories ----

    fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMemory(
        userId: String,
        category: MemoryCategory,
        title: String,
        content: String,
        approved: Boolean,
        source: MemorySource
    ): Result<Unit> {
        var docPath = "users/$userId/memories"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(120)
            val cleanContent = content.trim().take(1000)
            require(cleanTitle.isNotEmpty()) { "Memory title cannot be empty." }
            require(cleanContent.isNotEmpty()) { "Memory content cannot be empty." }

            val docRef = db.collection("users").document(uid).collection("memories").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "category" to category.name,
                "title" to cleanTitle,
                "content" to cleanContent,
                "approved" to approved,
                "source" to source.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(
        userId: String,
        memoryId: String,
        category: MemoryCategory,
        title: String,
        content: String,
        approved: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/memories/$memoryId"
        return try {
            val uid = requireUserId(userId)
            require(memoryId.isNotBlank()) { "Memory ID must not be blank." }
            val cleanTitle = title.trim().take(120)
            val cleanContent = content.trim().take(1000)
            require(cleanTitle.isNotEmpty()) { "Memory title cannot be empty." }
            require(cleanContent.isNotEmpty()) { "Memory content cannot be empty." }

            val docRef = db.collection("users").document(uid)
                .collection("memories").document(memoryId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "category" to category.name,
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "approved" to approved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
        var docPath = "users/$userId/memories/$memoryId"
        return try {
            val uid = requireUserId(userId)
            require(memoryId.isNotBlank()) { "Memory ID must not be blank." }
            val docRef = db.collection("users").document(uid)
                .collection("memories").document(memoryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // ---- Continuous Shadow Room Messages ----

    fun observeMessages(userId: String): Flow<List<ConversationMessage>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/messages"
        emitAll(
            db.collection("users").document(uid).collection("messages")
                .orderBy("clientOrder", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConversationMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMessage(
        userId: String,
        role: MessageRole,
        text: String,
        reasoningMode: ReasoningMode,
        clientOrder: Long
    ): Result<Unit> {
        var docPath = "users/$userId/messages"
        return try {
            val uid = requireUserId(userId)
            val cleanText = text.trim().take(8000)
            require(cleanText.isNotEmpty()) { "Message text cannot be empty." }

            val docRef = db.collection("users").document(uid).collection("messages").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "role" to role.name,
                "text" to cleanText,
                "reasoningMode" to reasoningMode.name,
                "clientOrder" to clientOrder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearConversation(userId: String): Result<Unit> {
        val path = "users/$userId/messages"
        return try {
            val uid = requireUserId(userId)
            val collectionRef = db.collection("users").document(uid).collection("messages")
            val docs = collectionRef.get().await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllPersonalData(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val userRef = db.collection("users").document(uid)
            val messageDocs = userRef.collection("messages").get().await().documents
            val memoryDocs = userRef.collection("memories").get().await().documents
            (messageDocs + memoryDocs).chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            userRef.update(
                mapOf(
                    "faceConsentGranted" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
