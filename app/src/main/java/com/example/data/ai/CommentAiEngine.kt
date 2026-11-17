package com.example.data.ai

import android.util.Log
import com.example.data.firestore.CommentClassification
import com.example.data.firestore.DetectedLanguage
import com.example.data.firestore.LanguagePreference
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CommentAiEngine"

data class AiCommentDecision(
    val detectedLanguage: DetectedLanguage,
    val classification: CommentClassification,
    val shouldReply: Boolean,
    val replyText: String,
    val reason: String
)

class CommentAiEngine {

    private val decisionSchema = Schema.obj(
        properties = mapOf(
            "detectedLanguage" to Schema.enumeration(
                values = listOf("ENGLISH", "HINDI", "HINGLISH"),
                description = "Primary language of the YouTube comment"
            ),
            "classification" to Schema.enumeration(
                values = listOf("NORMAL", "SPAM", "ABUSIVE"),
                description = "Safety classification of the comment"
            ),
            "shouldReply" to Schema.boolean(
                description = "True if the comment is safe and appropriate for a reply"
            ),
            "replyText" to Schema.string(
                description = "Natural, short, enthusiastic gaming reply to post on YouTube (empty if shouldReply is false)"
            ),
            "reason" to Schema.string(
                description = "Brief explanation of language detection and moderation decision"
            )
        )
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = decisionSchema
            temperature = 0.7f
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun analyzeAndGenerateReply(
        commentText: String,
        authorName: String,
        videoTitle: String?,
        customPrompt: String,
        languagePreference: LanguagePreference,
        recentReplies: Set<String>
    ): Result<AiCommentDecision> {
        return try {
            val languageRule = when (languagePreference) {
                LanguagePreference.AUTO ->
                    "Automatically detect whether the comment is in HINDI, HINGLISH (Hindi written in Latin/English script), or ENGLISH, and generate replyText in the EXACT same language."
                LanguagePreference.HINDI ->
                    "Detect the comment's language in detectedLanguage, and ALWAYS write replyText in natural Hindi (Devanagari script)."
                LanguagePreference.HINGLISH ->
                    "Detect the comment's language in detectedLanguage, and ALWAYS write replyText in natural Hinglish (friendly Roman Hindi + English gaming style)."
                LanguagePreference.ENGLISH ->
                    "Detect the comment's language in detectedLanguage, and ALWAYS write replyText in crisp, enthusiastic English."
            }

            val recentHint = if (recentReplies.isNotEmpty()) {
                "Do NOT repeat or closely paraphrase any of these recent replies:\n" +
                    recentReplies.take(8).joinToString("\n") { "- $it" }
            } else {
                "Keep the reply fresh and personalized to the commenter's message."
            }

            val prompt = """
                SYSTEM INSTRUCTIONS:
                $customPrompt

                MODERATION & LANGUAGE RULES:
                1. Classify the comment as SPAM (links, self-promotion, scam giveaways, bot gibberish), ABUSIVE (hate speech, slurs, profanity, harassment, threats), or NORMAL.
                2. If classification is SPAM or ABUSIVE, set shouldReply = false and replyText = "".
                3. $languageRule
                4. Keep replyText concise (under 220 characters), natural, polite, gaming-authentic, and relevant to the viewer's comment.
                5. $recentHint

                CONTEXT:
                Video Title: ${videoTitle ?: "Gaming Video"}
                Commenter Name: $authorName
                Comment Text: $commentText
            """.trimIndent()

            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val json = JSONObject(rawJson)
            val lang = DetectedLanguage.fromRaw(json.optString("detectedLanguage", "ENGLISH"))
            val classification = CommentClassification.fromRaw(json.optString("classification", "NORMAL"))
            val shouldReply = json.optBoolean("shouldReply", classification == CommentClassification.NORMAL)
            val replyText = json.optString("replyText", "").trim()
            val reason = json.optString("reason", "").trim()

            Result.success(
                AiCommentDecision(
                    detectedLanguage = lang,
                    classification = classification,
                    shouldReply = shouldReply && classification == CommentClassification.NORMAL && replyText.isNotBlank(),
                    replyText = replyText,
                    reason = reason
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI comment generation timed out", e)
            Result.failure(IllegalStateException("AI generation timed out. Please try again.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI comment generation failed", e)
            Result.failure(e)
        }
    }
}
