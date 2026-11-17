package com.example.data.firestore

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val DEFAULT_AI_PROMPT =
    "You are GR GAMER AI, an intelligent gaming YouTube assistant. You manage comments for a Free Fire gaming YouTube channel. Reply naturally, politely, and enthusiastically. Understand every comment before replying. Use Hindi, Hinglish, or English according to the comment. Keep replies short, friendly, engaging, and relevant. Never generate spam, abusive content, fake promises, or misleading information. Avoid repeating the same reply."

enum class LanguagePreference(val label: String, val subtitle: String) {
    AUTO("Auto-Detect", "Detects Hindi, Hinglish, or English and replies in the same language"),
    HINDI("Hindi", "Replies in natural Devanagari / Hindi gaming style"),
    HINGLISH("Hinglish", "Replies in friendly Roman Hindi + English gaming style"),
    ENGLISH("English", "Replies in crisp, enthusiastic English gaming style");

    companion object {
        fun fromRaw(raw: String?): LanguagePreference =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

enum class DetectedLanguage(val label: String) {
    ENGLISH("English"),
    HINDI("Hindi"),
    HINGLISH("Hinglish"),
    UNKNOWN("Unknown");

    companion object {
        fun fromRaw(raw: String?): DetectedLanguage =
            entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

enum class CommentClassification {
    NORMAL,
    SPAM,
    ABUSIVE;

    companion object {
        fun fromRaw(raw: String?): CommentClassification =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class CommentStatus(val label: String) {
    PENDING("Pending"),
    REPLIED("Replied"),
    IGNORED_SPAM("Ignored (Spam)"),
    IGNORED_ABUSIVE("Ignored (Abusive)"),
    FAILED("Failed"),
    SKIPPED_DUPLICATE("Skipped (Duplicate)");

    companion object {
        fun fromRaw(raw: String?): CommentStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class ReplyDeliveryStatus {
    SENT,
    FAILED;

    companion object {
        fun fromRaw(raw: String?): ReplyDeliveryStatus =
            entries.firstOrNull { it.name == raw } ?: SENT
    }
}

@Keep
@IgnoreExtraProperties
data class UserWorkspace(
    val userId: String = "",
    val displayName: String = "GR Gamer",
    val email: String? = null,
    val photoUrl: String? = null,
    val connectedChannelId: String? = null,
    val connectedChannelTitle: String? = null,
    val connectedChannelHandle: String? = null,
    val connectedChannelAvatarUrl: String? = null,
    val subscriberCount: Long = 0L,
    val videoCount: Long = 0L,
    val channelConnected: Boolean = false,
    val autoReplyEnabled: Boolean = false,
    val autoReplyIntervalMinutes: Long = 15L,
    val ignoreSpam: Boolean = true,
    val ignoreAbusive: Boolean = true,
    val avoidDuplicates: Boolean = true,
    val primaryLanguage: String = LanguagePreference.AUTO.name,
    val customPrompt: String = DEFAULT_AI_PROMPT,
    val totalCommentsReceived: Long = 0L,
    val totalRepliesSent: Long = 0L,
    val pendingCommentsCount: Long = 0L,
    val failedRepliesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val languagePreferenceEnum: LanguagePreference
        get() = LanguagePreference.fromRaw(primaryLanguage)
}

@Keep
@IgnoreExtraProperties
data class CommentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val youtubeCommentId: String = "",
    val channelId: String = "",
    val videoId: String = "",
    val videoTitle: String? = null,
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val commentText: String = "",
    val detectedLanguage: String = DetectedLanguage.UNKNOWN.name,
    val classification: String = CommentClassification.NORMAL.name,
    val status: String = CommentStatus.PENDING.name,
    val generatedReply: String? = null,
    val errorMessage: String? = null,
    val publishedAtText: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val detectedLanguageEnum: DetectedLanguage
        get() = DetectedLanguage.fromRaw(detectedLanguage)

    val classificationEnum: CommentClassification
        get() = CommentClassification.fromRaw(classification)

    val statusEnum: CommentStatus
        get() = CommentStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class ReplyHistoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val youtubeCommentId: String = "",
    val channelId: String = "",
    val videoTitle: String? = null,
    val authorName: String = "",
    val originalComment: String = "",
    val replyText: String = "",
    val language: String = DetectedLanguage.ENGLISH.name,
    val status: String = ReplyDeliveryStatus.SENT.name,
    val errorMessage: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val languageEnum: DetectedLanguage
        get() = DetectedLanguage.fromRaw(language)

    val statusEnum: ReplyDeliveryStatus
        get() = ReplyDeliveryStatus.fromRaw(status)
}

/**
 * Sanitizes external YouTube IDs to conform strictly to Firestore rule ID constraints (^[a-zA-Z0-9_-]+$).
 */
fun safeDocId(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    return cleaned.ifBlank { "doc_${System.currentTimeMillis()}" }.take(120)
}
