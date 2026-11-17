package com.example.domain

import com.example.data.ai.AiCommentDecision
import com.example.data.ai.CommentAiEngine
import com.example.data.firestore.CommentClassification
import com.example.data.firestore.CommentRecord
import com.example.data.firestore.CommentStatus
import com.example.data.firestore.WorkspaceRepository
import com.example.data.youtube.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CycleProgress(
    val isRunning: Boolean = false,
    val phaseLabel: String = "Idle",
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val lastSummary: String? = null
)

class AutoReplyEngine(
    private val workspaceRepository: WorkspaceRepository,
    private val youTubeRepository: YouTubeRepository,
    private val aiEngine: CommentAiEngine
) {
    private val _progress = MutableStateFlow(CycleProgress())
    val progress: StateFlow<CycleProgress> = _progress.asStateFlow()

    suspend fun syncCommentsOnly(
        accessToken: String,
        userId: String,
        channelId: String
    ): Result<Int> {
        _progress.value = CycleProgress(isRunning = true, phaseLabel = "Fetching YouTube comments...")
        return try {
            val fetchedResult = youTubeRepository.fetchChannelComments(accessToken, userId, channelId)
            val fetched = fetchedResult.getOrElse { err ->
                _progress.value = CycleProgress(isRunning = false, lastSummary = err.message)
                return Result.failure(err)
            }
            val inserted = workspaceRepository.upsertFetchedComments(channelId, fetched).getOrThrow()
            _progress.value = CycleProgress(
                isRunning = false,
                phaseLabel = "Sync complete",
                lastSummary = "Synced ${fetched.size} threads ($inserted new)."
            )
            Result.success(fetched.size)
        } catch (e: CancellationException) {
            _progress.value = CycleProgress(isRunning = false)
            throw e
        } catch (e: Exception) {
            _progress.value = CycleProgress(isRunning = false, lastSummary = e.message)
            Result.failure(e)
        }
    }

    suspend fun runAutoReplyCycle(
        accessToken: String,
        userId: String
    ): Result<String> {
        if (_progress.value.isRunning) {
            return Result.success("Auto-reply cycle is already running.")
        }

        _progress.value = CycleProgress(isRunning = true, phaseLabel = "Loading workspace & comments...")
        return try {
            val workspace = workspaceRepository.getWorkspaceOnce(userId).getOrThrow()
                ?: return Result.failure(IllegalStateException("Workspace not initialized."))

            val channelId = workspace.connectedChannelId
            if (!workspace.channelConnected || channelId.isNullOrBlank()) {
                _progress.value = CycleProgress(isRunning = false, lastSummary = "No YouTube channel connected.")
                return Result.failure(IllegalStateException("Connect your YouTube channel before starting Auto Reply."))
            }

            // 1. Fetch latest comments from YouTube Data API v3
            _progress.value = CycleProgress(isRunning = true, phaseLabel = "Fetching new YouTube comments...")
            val fetched = youTubeRepository.fetchChannelComments(accessToken, userId, channelId).getOrElse { err ->
                _progress.value = CycleProgress(isRunning = false, lastSummary = err.message)
                return Result.failure(err)
            }
            workspaceRepository.upsertFetchedComments(channelId, fetched).getOrThrow()

            // 2. Load pending comments & recent replies for duplicate avoidance
            val pending = workspaceRepository.getPendingCommentsOnce(userId).getOrThrow()
            if (pending.isEmpty()) {
                val msg = "Scanned ${fetched.size} comments — no pending comments need replies."
                _progress.value = CycleProgress(isRunning = false, phaseLabel = "Up to date", lastSummary = msg)
                return Result.success(msg)
            }

            val recentReplies = if (workspace.avoidDuplicates) {
                workspaceRepository.getRecentReplyTextsOnce(userId).toMutableSet()
            } else {
                mutableSetOf()
            }

            var repliedCount = 0
            var ignoredCount = 0
            var failedCount = 0

            val batch = pending.take(15)
            batch.forEachIndexed { index, comment ->
                // Check live workspace flag so STOP AUTO REPLY interrupts mid-batch immediately
                val latestWs = workspaceRepository.getWorkspaceOnce(userId).getOrNull()
                if (latestWs != null && !latestWs.autoReplyEnabled) {
                    val stoppedMsg = "Auto-reply paused after $repliedCount replies."
                    _progress.value = CycleProgress(isRunning = false, phaseLabel = "Paused", lastSummary = stoppedMsg)
                    return Result.success(stoppedMsg)
                }

                _progress.value = CycleProgress(
                    isRunning = true,
                    phaseLabel = "AI analyzing comment ${index + 1}/${batch.size}...",
                    processedCount = index,
                    totalCount = batch.size
                )

                val aiResult = aiEngine.analyzeAndGenerateReply(
                    commentText = comment.commentText,
                    authorName = comment.authorName,
                    videoTitle = comment.videoTitle,
                    customPrompt = workspace.customPrompt,
                    languagePreference = workspace.languagePreferenceEnum,
                    recentReplies = recentReplies
                )

                val decision = aiResult.getOrNull()
                if (decision == null) {
                    val errMsg = aiResult.exceptionOrNull()?.message ?: "AI generation error"
                    workspaceRepository.markCommentFailed(
                        comment = comment,
                        detectedLanguage = comment.detectedLanguageEnum,
                        classification = comment.classificationEnum,
                        attemptedReply = null,
                        errorMessage = errMsg
                    )
                    failedCount++
                    return@forEachIndexed
                }

                if (decision.classification == CommentClassification.SPAM && workspace.ignoreSpam) {
                    workspaceRepository.markCommentIgnoredOrSkipped(
                        comment = comment,
                        status = CommentStatus.IGNORED_SPAM,
                        detectedLanguage = decision.detectedLanguage,
                        classification = decision.classification,
                        reason = decision.reason.ifBlank { "Filtered as spam" }
                    )
                    ignoredCount++
                    return@forEachIndexed
                }

                if (decision.classification == CommentClassification.ABUSIVE && workspace.ignoreAbusive) {
                    workspaceRepository.markCommentIgnoredOrSkipped(
                        comment = comment,
                        status = CommentStatus.IGNORED_ABUSIVE,
                        detectedLanguage = decision.detectedLanguage,
                        classification = decision.classification,
                        reason = decision.reason.ifBlank { "Filtered as abusive" }
                    )
                    ignoredCount++
                    return@forEachIndexed
                }

                if (!decision.shouldReply || decision.replyText.isBlank()) {
                    workspaceRepository.markCommentIgnoredOrSkipped(
                        comment = comment,
                        status = CommentStatus.IGNORED_SPAM,
                        detectedLanguage = decision.detectedLanguage,
                        classification = decision.classification,
                        reason = decision.reason.ifBlank { "AI skipped reply" }
                    )
                    ignoredCount++
                    return@forEachIndexed
                }

                // Post real reply to YouTube Data API v3
                val postResult = youTubeRepository.postCommentReply(
                    accessToken = accessToken,
                    parentCommentId = comment.youtubeCommentId,
                    replyText = decision.replyText
                )

                if (postResult.isSuccess) {
                    workspaceRepository.markCommentReplied(
                        comment = comment,
                        replyText = decision.replyText,
                        detectedLanguage = decision.detectedLanguage,
                        classification = decision.classification
                    )
                    recentReplies.add(decision.replyText.trim().lowercase())
                    repliedCount++
                } else {
                    val postErr = postResult.exceptionOrNull()?.message ?: "YouTube API reply failed"
                    workspaceRepository.markCommentFailed(
                        comment = comment,
                        detectedLanguage = decision.detectedLanguage,
                        classification = decision.classification,
                        attemptedReply = decision.replyText,
                        errorMessage = postErr
                    )
                    failedCount++
                }
            }

            val summary = "Cycle complete: $repliedCount replied, $ignoredCount filtered, $failedCount failed."
            _progress.value = CycleProgress(
                isRunning = false,
                phaseLabel = "Cycle complete",
                processedCount = batch.size,
                totalCount = batch.size,
                lastSummary = summary
            )
            Result.success(summary)
        } catch (e: CancellationException) {
            _progress.value = CycleProgress(isRunning = false)
            throw e
        } catch (e: Exception) {
            _progress.value = CycleProgress(isRunning = false, lastSummary = e.message)
            Result.failure(e)
        }
    }

    suspend fun generateReplyPreview(
        comment: CommentRecord,
        userId: String
    ): Result<AiCommentDecision> {
        val workspace = workspaceRepository.getWorkspaceOnce(userId).getOrThrow()
            ?: return Result.failure(IllegalStateException("Workspace not loaded"))
        val recent = if (workspace.avoidDuplicates) {
            workspaceRepository.getRecentReplyTextsOnce(userId)
        } else emptySet()

        return aiEngine.analyzeAndGenerateReply(
            commentText = comment.commentText,
            authorName = comment.authorName,
            videoTitle = comment.videoTitle,
            customPrompt = workspace.customPrompt,
            languagePreference = workspace.languagePreferenceEnum,
            recentReplies = recent
        )
    }

    suspend fun sendSingleReply(
        accessToken: String,
        comment: CommentRecord,
        userId: String,
        customReplyOverride: String? = null
    ): Result<String> {
        val workspace = workspaceRepository.getWorkspaceOnce(userId).getOrThrow()
            ?: return Result.failure(IllegalStateException("Workspace not loaded"))

        val decision = if (customReplyOverride.isNullOrBlank()) {
            val recent = if (workspace.avoidDuplicates) {
                workspaceRepository.getRecentReplyTextsOnce(userId)
            } else emptySet()
            aiEngine.analyzeAndGenerateReply(
                commentText = comment.commentText,
                authorName = comment.authorName,
                videoTitle = comment.videoTitle,
                customPrompt = workspace.customPrompt,
                languagePreference = workspace.languagePreferenceEnum,
                recentReplies = recent
            ).getOrElse { err ->
                workspaceRepository.markCommentFailed(
                    comment = comment,
                    detectedLanguage = comment.detectedLanguageEnum,
                    classification = comment.classificationEnum,
                    attemptedReply = null,
                    errorMessage = err.message ?: "AI generation failed"
                )
                return Result.failure(err)
            }
        } else {
            AiCommentDecision(
                detectedLanguage = comment.detectedLanguageEnum,
                classification = CommentClassification.NORMAL,
                shouldReply = true,
                replyText = customReplyOverride.trim(),
                reason = "User-confirmed AI reply"
            )
        }

        if (decision.classification == CommentClassification.SPAM && workspace.ignoreSpam) {
            workspaceRepository.markCommentIgnoredOrSkipped(
                comment, CommentStatus.IGNORED_SPAM, decision.detectedLanguage, decision.classification,
                decision.reason.ifBlank { "Marked as spam" }
            )
            return Result.success("Ignored spam comment.")
        }
        if (decision.classification == CommentClassification.ABUSIVE && workspace.ignoreAbusive) {
            workspaceRepository.markCommentIgnoredOrSkipped(
                comment, CommentStatus.IGNORED_ABUSIVE, decision.detectedLanguage, decision.classification,
                decision.reason.ifBlank { "Marked as abusive" }
            )
            return Result.success("Ignored abusive comment.")
        }

        val postResult = youTubeRepository.postCommentReply(
            accessToken = accessToken,
            parentCommentId = comment.youtubeCommentId,
            replyText = decision.replyText
        )

        return if (postResult.isSuccess) {
            workspaceRepository.markCommentReplied(
                comment = comment,
                replyText = decision.replyText,
                detectedLanguage = decision.detectedLanguage,
                classification = decision.classification
            )
            Result.success(decision.replyText)
        } else {
            val err = postResult.exceptionOrNull() ?: IllegalStateException("YouTube reply failed")
            workspaceRepository.markCommentFailed(
                comment = comment,
                detectedLanguage = decision.detectedLanguage,
                classification = decision.classification,
                attemptedReply = decision.replyText,
                errorMessage = err.message ?: "YouTube API failure"
            )
            Result.failure(err)
        }
    }
}
