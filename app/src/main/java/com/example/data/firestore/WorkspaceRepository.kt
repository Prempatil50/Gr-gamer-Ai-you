package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.data.youtube.YouTubeChannelInfo
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
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

class WorkspaceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun commentsCollection(uid: String) = userDoc(uid).collection("comments")

    private fun historyCollection(uid: String) = userDoc(uid).collection("replyHistory")

    fun observeWorkspace(userId: String): Flow<UserWorkspace?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserWorkspace::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeComments(userId: String): Flow<List<CommentRecord>> = flow {
        val path = "users/$userId/comments"
        emitAll(
            commentsCollection(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(150)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommentRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeReplyHistory(userId: String): Flow<List<ReplyHistoryItem>> = flow {
        val path = "users/$userId/replyHistory"
        emitAll(
            historyCollection(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(150)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReplyHistoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureWorkspaceInitialized(user: FirebaseUser): Result<UserWorkspace> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = userDoc(uid)
            val existingSnap = docRef.get().await()
            val safeName = (user.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "GR Gamer")
                .take(100)
            val safeEmail = user.email?.trim()?.take(150)
            val safePhoto = user.photoUrl?.toString()?.take(500)

            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "channelConnected" to false,
                    "autoReplyEnabled" to false,
                    "autoReplyIntervalMinutes" to 15L,
                    "ignoreSpam" to true,
                    "ignoreAbusive" to true,
                    "avoidDuplicates" to true,
                    "primaryLanguage" to LanguagePreference.AUTO.name,
                    "customPrompt" to DEFAULT_AI_PROMPT,
                    "totalCommentsReceived" to 0L,
                    "totalRepliesSent" to 0L,
                    "pendingCommentsCount" to 0L,
                    "failedRepliesCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserWorkspace::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserWorkspace(userId = uid, displayName = safeName)
                Result.success(created)
            } else {
                val current = existingSnap.toObject(
                    UserWorkspace::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserWorkspace(userId = uid, displayName = safeName)
                val profileUpdate = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to safePhoto,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(profileUpdate).await()
                Result.success(current)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun getWorkspaceOnce(userId: String): Result<UserWorkspace?> {
        val path = "users/$userId"
        return try {
            val snap = userDoc(userId).get().await()
            val ws = if (snap.exists()) {
                snap.toObject(
                    UserWorkspace::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
            Result.success(ws)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun connectChannel(channel: YouTubeChannelInfo): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            require(channel.channelId.isNotBlank()) { "Channel ID cannot be blank" }
            val updateMap = mapOf(
                "connectedChannelId" to channel.channelId.take(128),
                "connectedChannelTitle" to channel.title.ifBlank { "YouTube Channel" }.take(120),
                "connectedChannelHandle" to channel.customUrl?.take(80),
                "connectedChannelAvatarUrl" to channel.avatarUrl?.take(500),
                "subscriberCount" to channel.subscriberCount.coerceAtLeast(0L),
                "videoCount" to channel.videoCount.coerceAtLeast(0L),
                "channelConnected" to true,
                "autoReplyEnabled" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            userDoc(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun disconnectChannel(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val updateMap = mapOf(
                "connectedChannelId" to "",
                "connectedChannelTitle" to "",
                "connectedChannelHandle" to "",
                "connectedChannelAvatarUrl" to "",
                "subscriberCount" to 0L,
                "videoCount" to 0L,
                "channelConnected" to false,
                "autoReplyEnabled" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setAutoReplyEnabled(enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            userDoc(uid).update(
                mapOf(
                    "autoReplyEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAiSettings(
        intervalMinutes: Long,
        ignoreSpam: Boolean,
        ignoreAbusive: Boolean,
        avoidDuplicates: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val clampedInterval = intervalMinutes.coerceIn(1L, 1440L)
            userDoc(uid).update(
                mapOf(
                    "autoReplyIntervalMinutes" to clampedInterval,
                    "ignoreSpam" to ignoreSpam,
                    "ignoreAbusive" to ignoreAbusive,
                    "avoidDuplicates" to avoidDuplicates,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLanguagePreference(language: LanguagePreference): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            userDoc(uid).update(
                mapOf(
                    "primaryLanguage" to language.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCustomPrompt(prompt: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val trimmed = prompt.trim()
            require(trimmed.length in 10..2000) {
                "AI prompt must be between 10 and 2000 characters."
            }
            userDoc(uid).update(
                mapOf(
                    "customPrompt" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertFetchedComments(
        channelId: String,
        fetched: List<CommentRecord>
    ): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val basePath = "users/$uid/comments"
        return try {
            var newOrPendingCount = 0
            for (comment in fetched) {
                val docId = safeDocId(comment.youtubeCommentId)
                val docRef = commentsCollection(uid).document(docId)
                val snap = docRef.get().await()
                if (!snap.exists()) {
                    val payload = mapOf(
                        "userId" to uid,
                        "youtubeCommentId" to comment.youtubeCommentId.take(128),
                        "channelId" to channelId.take(128),
                        "videoId" to comment.videoId.ifBlank { "unknown" }.take(64),
                        "videoTitle" to comment.videoTitle?.take(200),
                        "authorName" to comment.authorName.ifBlank { "YouTube Viewer" }.take(120),
                        "authorAvatarUrl" to comment.authorAvatarUrl?.take(500),
                        "commentText" to comment.commentText.ifBlank { "(empty)" }.take(2000),
                        "detectedLanguage" to comment.detectedLanguage,
                        "classification" to comment.classification,
                        "status" to comment.status,
                        "generatedReply" to comment.generatedReply?.take(1000),
                        "errorMessage" to comment.errorMessage?.take(500),
                        "publishedAtText" to comment.publishedAtText?.take(64),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    docRef.set(payload).await()
                    newOrPendingCount++
                }
            }
            recalculateTelemetry(uid)
            Result.success(newOrPendingCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, basePath)
            Result.failure(e)
        }
    }

    suspend fun getPendingCommentsOnce(userId: String): Result<List<CommentRecord>> {
        val path = "users/$userId/comments"
        return try {
            val snap = commentsCollection(userId)
                .whereEqualTo("status", CommentStatus.PENDING.name)
                .get()
                .await()
            val list = snap.toObjects(
                CommentRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getRecentReplyTextsOnce(userId: String): Set<String> {
        return try {
            val snap = historyCollection(userId)
                .whereEqualTo("status", ReplyDeliveryStatus.SENT.name)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            snap.documents.mapNotNull { it.getString("replyText")?.trim()?.lowercase() }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun markCommentReplied(
        comment: CommentRecord,
        replyText: String,
        detectedLanguage: DetectedLanguage,
        classification: CommentClassification
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docId = comment.id.ifBlank { safeDocId(comment.youtubeCommentId) }
        val commentPath = "users/$uid/comments/$docId"
        return try {
            val cleanReply = replyText.trim().take(1000)
            require(cleanReply.isNotEmpty()) { "Reply text cannot be empty" }
            commentsCollection(uid).document(docId).update(
                mapOf(
                    "detectedLanguage" to detectedLanguage.name,
                    "classification" to classification.name,
                    "status" to CommentStatus.REPLIED.name,
                    "generatedReply" to cleanReply,
                    "errorMessage" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val historyLang = when (detectedLanguage) {
                DetectedLanguage.HINDI -> DetectedLanguage.HINDI.name
                DetectedLanguage.HINGLISH -> DetectedLanguage.HINGLISH.name
                else -> DetectedLanguage.ENGLISH.name
            }
            val historyPayload = mapOf(
                "userId" to uid,
                "youtubeCommentId" to comment.youtubeCommentId.take(128),
                "channelId" to comment.channelId.take(128),
                "videoTitle" to comment.videoTitle?.take(200),
                "authorName" to comment.authorName.ifBlank { "YouTube Viewer" }.take(120),
                "originalComment" to comment.commentText.take(2000),
                "replyText" to cleanReply,
                "language" to historyLang,
                "status" to ReplyDeliveryStatus.SENT.name,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            historyCollection(uid).document().set(historyPayload).await()

            recalculateTelemetry(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, commentPath)
            Result.failure(e)
        }
    }

    suspend fun markCommentIgnoredOrSkipped(
        comment: CommentRecord,
        status: CommentStatus,
        detectedLanguage: DetectedLanguage,
        classification: CommentClassification,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docId = comment.id.ifBlank { safeDocId(comment.youtubeCommentId) }
        val commentPath = "users/$uid/comments/$docId"
        return try {
            commentsCollection(uid).document(docId).update(
                mapOf(
                    "detectedLanguage" to detectedLanguage.name,
                    "classification" to classification.name,
                    "status" to status.name,
                    "errorMessage" to reason.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recalculateTelemetry(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, commentPath)
            Result.failure(e)
        }
    }

    suspend fun markCommentFailed(
        comment: CommentRecord,
        detectedLanguage: DetectedLanguage,
        classification: CommentClassification,
        attemptedReply: String?,
        errorMessage: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docId = comment.id.ifBlank { safeDocId(comment.youtubeCommentId) }
        val commentPath = "users/$uid/comments/$docId"
        return try {
            val cleanError = errorMessage.ifBlank { "Failed to post reply" }.take(500)
            val updateMap = mapOf(
                "detectedLanguage" to detectedLanguage.name,
                "classification" to classification.name,
                "status" to CommentStatus.FAILED.name,
                "generatedReply" to attemptedReply?.take(1000),
                "errorMessage" to cleanError,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            commentsCollection(uid).document(docId).update(updateMap).await()

            val historyLang = when (detectedLanguage) {
                DetectedLanguage.HINDI -> DetectedLanguage.HINDI.name
                DetectedLanguage.HINGLISH -> DetectedLanguage.HINGLISH.name
                else -> DetectedLanguage.ENGLISH.name
            }
            val historyPayload = mapOf(
                "userId" to uid,
                "youtubeCommentId" to comment.youtubeCommentId.take(128),
                "channelId" to comment.channelId.take(128),
                "videoTitle" to comment.videoTitle?.take(200),
                "authorName" to comment.authorName.ifBlank { "YouTube Viewer" }.take(120),
                "originalComment" to comment.commentText.take(2000),
                "replyText" to (attemptedReply?.ifBlank { "(Generation / Delivery Failed)" } ?: "(Delivery Failed)").take(1000),
                "language" to historyLang,
                "status" to ReplyDeliveryStatus.FAILED.name,
                "errorMessage" to cleanError,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            historyCollection(uid).document().set(historyPayload).await()

            recalculateTelemetry(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, commentPath)
            Result.failure(e)
        }
    }

    suspend fun deleteHistoryItem(historyId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/replyHistory/$historyId"
        return try {
            require(historyId.isNotBlank()) { "History ID cannot be blank" }
            historyCollection(uid).document(historyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recalculateTelemetry(uid: String = requireUserId()) {
        val path = "users/$uid"
        try {
            val allComments = commentsCollection(uid).get().await()
                .toObjects(CommentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val totalReceived = allComments.size.toLong()
            val pendingCount = allComments.count { it.statusEnum == CommentStatus.PENDING }.toLong()
            val repliedCount = allComments.count { it.statusEnum == CommentStatus.REPLIED }.toLong()
            val failedCount = allComments.count { it.statusEnum == CommentStatus.FAILED }.toLong()

            userDoc(uid).update(
                mapOf(
                    "totalCommentsReceived" to totalReceived,
                    "totalRepliesSent" to repliedCount,
                    "pendingCommentsCount" to pendingCount,
                    "failedRepliesCount" to failedCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }
}
