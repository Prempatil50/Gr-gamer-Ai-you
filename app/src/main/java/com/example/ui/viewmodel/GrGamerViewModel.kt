package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.YouTubeAuthManager
import com.example.data.ai.AiCommentDecision
import com.example.data.ai.CommentAiEngine
import com.example.data.firestore.CommentRecord
import com.example.data.firestore.CommentStatus
import com.example.data.firestore.DEFAULT_AI_PROMPT
import com.example.data.firestore.LanguagePreference
import com.example.data.firestore.ReplyHistoryItem
import com.example.data.firestore.UserWorkspace
import com.example.data.firestore.WorkspaceRepository
import com.example.data.youtube.YouTubeChannelInfo
import com.example.data.youtube.YouTubeRepository
import com.example.domain.AutoReplyEngine
import com.example.domain.CycleProgress
import com.example.worker.AutoReplyScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class PromptTestResult(
    val isLoading: Boolean = false,
    val decision: AiCommentDecision? = null,
    val error: String? = null
)

data class CommentPreviewState(
    val comment: CommentRecord? = null,
    val isLoading: Boolean = false,
    val decision: AiCommentDecision? = null,
    val editedReply: String = "",
    val error: String? = null
)

class GrGamerViewModel(
    private val currentUserId: String,
    private val workspaceRepository: WorkspaceRepository,
    private val youTubeRepository: YouTubeRepository,
    private val youTubeAuthManager: YouTubeAuthManager,
    private val aiEngine: CommentAiEngine,
    private val autoReplyEngine: AutoReplyEngine
) : ViewModel() {

    private companion object {
        const val TAG = "GrGamerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    private enum class PendingOAuthAction {
        CONNECT_CHANNEL,
        SYNC_COMMENTS,
        RUN_AUTO_REPLY,
        NONE
    }

    private var pendingOAuthAction = PendingOAuthAction.NONE

    val workspaceState: StateFlow<UiState<UserWorkspace>> =
        workspaceRepository.observeWorkspace(currentUserId)
            .map<UserWorkspace?, UiState<UserWorkspace>> { ws ->
                if (ws != null) UiState.Success(ws) else UiState.Loading
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing workspace", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load workspace"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val commentsState: StateFlow<UiState<List<CommentRecord>>> =
        workspaceRepository.observeComments(currentUserId)
            .map<List<CommentRecord>, UiState<List<CommentRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing comments", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load comments"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val historyState: StateFlow<UiState<List<ReplyHistoryItem>>> =
        workspaceRepository.observeReplyHistory(currentUserId)
            .map<List<ReplyHistoryItem>, UiState<List<ReplyHistoryItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing reply history", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load reply history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val cycleProgress: StateFlow<CycleProgress> = autoReplyEngine.progress

    private val _isConnectingChannel = MutableStateFlow(false)
    val isConnectingChannel: StateFlow<Boolean> = _isConnectingChannel.asStateFlow()

    private val _availableChannels = MutableStateFlow<List<YouTubeChannelInfo>>(emptyList())
    val availableChannels: StateFlow<List<YouTubeChannelInfo>> = _availableChannels.asStateFlow()

    private val _showChannelPicker = MutableStateFlow(false)
    val showChannelPicker: StateFlow<Boolean> = _showChannelPicker.asStateFlow()

    private val _promptTestResult = MutableStateFlow(PromptTestResult())
    val promptTestResult: StateFlow<PromptTestResult> = _promptTestResult.asStateFlow()

    private val _commentPreviewState = MutableStateFlow(CommentPreviewState())
    val commentPreviewState: StateFlow<CommentPreviewState> = _commentPreviewState.asStateFlow()

    private val _activeCommentActionId = MutableStateFlow<String?>(null)
    val activeCommentActionId: StateFlow<String?> = _activeCommentActionId.asStateFlow()

    private val _oauthConsentEvents = MutableSharedFlow<IntentSenderRequest>()
    val oauthConsentEvents = _oauthConsentEvents.asSharedFlow()

    private val _snackMessages = MutableSharedFlow<String>()
    val snackMessages = _snackMessages.asSharedFlow()

    init {
        initializeUserWorkspace()
    }

    private fun initializeUserWorkspace() {
        val user = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            val res = workspaceRepository.ensureWorkspaceInitialized(user)
            if (res.isFailure) {
                _snackMessages.emit(
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to initialize workspace."
                )
            }
        }
    }

    fun startYouTubeConnect(activity: Activity) {
        viewModelScope.launch {
            _isConnectingChannel.value = true
            pendingOAuthAction = PendingOAuthAction.CONNECT_CHANNEL
            when (val result = youTubeAuthManager.authorizeYouTubeAccess(activity)) {
                is YouTubeAuthManager.AuthFlowResult.TokenReady -> {
                    loadUserChannelsWithToken(result.accessToken)
                }
                is YouTubeAuthManager.AuthFlowResult.NeedsConsent -> {
                    _oauthConsentEvents.emit(result.intentSenderRequest)
                }
                is YouTubeAuthManager.AuthFlowResult.Failed -> {
                    _isConnectingChannel.value = false
                    pendingOAuthAction = PendingOAuthAction.NONE
                    _snackMessages.emit(result.message)
                }
            }
        }
    }

    fun handleYouTubeConsentActivityResult(context: Context, data: Intent?) {
        viewModelScope.launch {
            val tokenResult = youTubeAuthManager.handleConsentResult(context, data)
            if (tokenResult.isFailure) {
                _isConnectingChannel.value = false
                pendingOAuthAction = PendingOAuthAction.NONE
                _snackMessages.emit(
                    tokenResult.exceptionOrNull()?.message ?: "YouTube OAuth authorization cancelled."
                )
                return@launch
            }
            val token = tokenResult.getOrThrow()
            when (pendingOAuthAction) {
                PendingOAuthAction.CONNECT_CHANNEL -> loadUserChannelsWithToken(token)
                PendingOAuthAction.SYNC_COMMENTS -> syncCommentsInternal(token)
                PendingOAuthAction.RUN_AUTO_REPLY -> runAutoReplyInternal(token, context)
                PendingOAuthAction.NONE -> _isConnectingChannel.value = false
            }
            pendingOAuthAction = PendingOAuthAction.NONE
        }
    }

    private suspend fun loadUserChannelsWithToken(token: String) {
        val channelsResult = youTubeRepository.fetchUserChannels(token)
        _isConnectingChannel.value = false
        if (channelsResult.isFailure) {
            _snackMessages.emit(
                channelsResult.exceptionOrNull()?.message ?: "Could not load YouTube channels."
            )
            return
        }
        val channels = channelsResult.getOrThrow()
        if (channels.isEmpty()) {
            _snackMessages.emit("No YouTube channels found on this Google account.")
        } else {
            _availableChannels.value = channels
            _showChannelPicker.value = true
        }
    }

    fun dismissChannelPicker() {
        _showChannelPicker.value = false
    }

    fun selectYouTubeChannel(channel: YouTubeChannelInfo) {
        viewModelScope.launch {
            _showChannelPicker.value = false
            val res = workspaceRepository.connectChannel(channel)
            if (res.isSuccess) {
                _snackMessages.emit("Connected channel: ${channel.title}")
                val token = youTubeAuthManager.accessToken.value
                if (!token.isNullOrBlank()) {
                    autoReplyEngine.syncCommentsOnly(token, currentUserId, channel.channelId)
                }
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to save channel.")
            }
        }
    }

    fun disconnectYouTubeChannel(context: Context) {
        viewModelScope.launch {
            AutoReplyScheduler.cancel(context)
            youTubeAuthManager.clearToken()
            val res = workspaceRepository.disconnectChannel()
            if (res.isSuccess) {
                _snackMessages.emit("YouTube channel disconnected & auto-reply stopped.")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to disconnect channel.")
            }
        }
    }

    fun startAutoReply(activity: Activity) {
        viewModelScope.launch {
            val ws = (workspaceState.value as? UiState.Success)?.data
            if (ws == null || !ws.channelConnected || ws.connectedChannelId.isNullOrBlank()) {
                _snackMessages.emit("Connect your YouTube channel first.")
                return@launch
            }
            workspaceRepository.setAutoReplyEnabled(true)
            AutoReplyScheduler.schedule(activity.applicationContext, ws.autoReplyIntervalMinutes)

            val existingToken = youTubeAuthManager.accessToken.value
            if (!existingToken.isNullOrBlank()) {
                runAutoReplyInternal(existingToken, activity)
                return@launch
            }

            pendingOAuthAction = PendingOAuthAction.RUN_AUTO_REPLY
            when (val authRes = youTubeAuthManager.authorizeYouTubeAccess(activity)) {
                is YouTubeAuthManager.AuthFlowResult.TokenReady -> {
                    pendingOAuthAction = PendingOAuthAction.NONE
                    runAutoReplyInternal(authRes.accessToken, activity)
                }
                is YouTubeAuthManager.AuthFlowResult.NeedsConsent -> {
                    _oauthConsentEvents.emit(authRes.intentSenderRequest)
                }
                is YouTubeAuthManager.AuthFlowResult.Failed -> {
                    pendingOAuthAction = PendingOAuthAction.NONE
                    _snackMessages.emit(authRes.message)
                }
            }
        }
    }

    fun stopAutoReply(context: Context) {
        viewModelScope.launch {
            AutoReplyScheduler.cancel(context)
            val res = workspaceRepository.setAutoReplyEnabled(false)
            if (res.isSuccess) {
                _snackMessages.emit("AI Auto Reply stopped.")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Could not stop auto reply.")
            }
        }
    }

    fun toggleAutoReply(activity: Activity, enabled: Boolean) {
        if (enabled) startAutoReply(activity) else stopAutoReply(activity)
    }

    fun syncComments(activity: Activity) {
        viewModelScope.launch {
            val ws = (workspaceState.value as? UiState.Success)?.data
            if (ws == null || !ws.channelConnected || ws.connectedChannelId.isNullOrBlank()) {
                _snackMessages.emit("Connect your YouTube channel to fetch comments.")
                return@launch
            }

            val existingToken = youTubeAuthManager.accessToken.value
            if (!existingToken.isNullOrBlank()) {
                syncCommentsInternal(existingToken)
                return@launch
            }

            pendingOAuthAction = PendingOAuthAction.SYNC_COMMENTS
            when (val authRes = youTubeAuthManager.authorizeYouTubeAccess(activity)) {
                is YouTubeAuthManager.AuthFlowResult.TokenReady -> {
                    pendingOAuthAction = PendingOAuthAction.NONE
                    syncCommentsInternal(authRes.accessToken)
                }
                is YouTubeAuthManager.AuthFlowResult.NeedsConsent -> {
                    _oauthConsentEvents.emit(authRes.intentSenderRequest)
                }
                is YouTubeAuthManager.AuthFlowResult.Failed -> {
                    pendingOAuthAction = PendingOAuthAction.NONE
                    _snackMessages.emit(authRes.message)
                }
            }
        }
    }

    private suspend fun syncCommentsInternal(token: String) {
        val ws = (workspaceState.value as? UiState.Success)?.data ?: return
        val channelId = ws.connectedChannelId ?: return
        val res = autoReplyEngine.syncCommentsOnly(token, currentUserId, channelId)
        if (res.isSuccess) {
            _snackMessages.emit("Fetched ${res.getOrDefault(0)} YouTube comment threads.")
        } else {
            _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to sync comments.")
        }
    }

    private suspend fun runAutoReplyInternal(token: String, context: Context) {
        val res = autoReplyEngine.runAutoReplyCycle(token, currentUserId)
        if (res.isSuccess) {
            _snackMessages.emit(res.getOrDefault("Auto-reply cycle complete."))
        } else {
            val msg = res.exceptionOrNull()?.message ?: "Auto-reply cycle failed."
            _snackMessages.emit(msg)
            if (msg.contains("expired", ignoreCase = true)) {
                youTubeAuthManager.clearToken()
            }
        }
        val ws = workspaceRepository.getWorkspaceOnce(currentUserId).getOrNull()
        if (ws != null && ws.autoReplyEnabled) {
            AutoReplyScheduler.schedule(context.applicationContext, ws.autoReplyIntervalMinutes)
        }
    }

    fun openReplyPreview(comment: CommentRecord) {
        _commentPreviewState.value = CommentPreviewState(comment = comment, isLoading = true)
        viewModelScope.launch {
            val res = autoReplyEngine.generateReplyPreview(comment, currentUserId)
            if (res.isSuccess) {
                val decision = res.getOrThrow()
                _commentPreviewState.value = CommentPreviewState(
                    comment = comment,
                    isLoading = false,
                    decision = decision,
                    editedReply = decision.replyText
                )
            } else {
                _commentPreviewState.value = CommentPreviewState(
                    comment = comment,
                    isLoading = false,
                    error = res.exceptionOrNull()?.message ?: "AI preview generation failed."
                )
            }
        }
    }

    fun updatePreviewReplyText(text: String) {
        _commentPreviewState.value = _commentPreviewState.value.copy(editedReply = text)
    }

    fun dismissPreviewDialog() {
        _commentPreviewState.value = CommentPreviewState()
    }

    fun replyToSingleComment(
        activity: Activity,
        comment: CommentRecord,
        customReplyOverride: String? = null
    ) {
        viewModelScope.launch {
            val token = obtainTokenOrPrompt(activity) ?: return@launch
            _activeCommentActionId.value = comment.id
            val res = autoReplyEngine.sendSingleReply(token, comment, currentUserId, customReplyOverride)
            _activeCommentActionId.value = null
            _commentPreviewState.value = CommentPreviewState()

            if (res.isSuccess) {
                _snackMessages.emit("Reply posted to ${comment.authorName}!")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to post reply.")
            }
        }
    }

    fun ignoreComment(comment: CommentRecord) {
        viewModelScope.launch {
            _activeCommentActionId.value = comment.id
            val res = workspaceRepository.markCommentIgnoredOrSkipped(
                comment = comment,
                status = CommentStatus.IGNORED_SPAM,
                detectedLanguage = comment.detectedLanguageEnum,
                classification = comment.classificationEnum,
                reason = "Manually ignored by creator"
            )
            _activeCommentActionId.value = null
            if (res.isSuccess) {
                _snackMessages.emit("Comment marked as ignored.")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Could not ignore comment.")
            }
        }
    }

    private suspend fun obtainTokenOrPrompt(activity: Activity): String? {
        val cached = youTubeAuthManager.accessToken.value
        if (!cached.isNullOrBlank()) return cached
        return when (val authRes = youTubeAuthManager.authorizeYouTubeAccess(activity)) {
            is YouTubeAuthManager.AuthFlowResult.TokenReady -> authRes.accessToken
            is YouTubeAuthManager.AuthFlowResult.NeedsConsent -> {
                _oauthConsentEvents.emit(authRes.intentSenderRequest)
                _snackMessages.emit("Grant YouTube permission, then retry the action.")
                null
            }
            is YouTubeAuthManager.AuthFlowResult.Failed -> {
                _snackMessages.emit(authRes.message)
                null
            }
        }
    }

    fun saveAiSettings(
        context: Context,
        intervalMinutes: Long,
        ignoreSpam: Boolean,
        ignoreAbusive: Boolean,
        avoidDuplicates: Boolean
    ) {
        viewModelScope.launch {
            val res = workspaceRepository.updateAiSettings(
                intervalMinutes = intervalMinutes,
                ignoreSpam = ignoreSpam,
                ignoreAbusive = ignoreAbusive,
                avoidDuplicates = avoidDuplicates
            )
            if (res.isSuccess) {
                val ws = (workspaceState.value as? UiState.Success)?.data
                if (ws?.autoReplyEnabled == true) {
                    AutoReplyScheduler.schedule(context, intervalMinutes)
                }
                _snackMessages.emit("AI Reply Settings saved.")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to save settings.")
            }
        }
    }

    fun saveLanguagePreference(language: LanguagePreference) {
        viewModelScope.launch {
            val res = workspaceRepository.updateLanguagePreference(language)
            if (res.isSuccess) {
                _snackMessages.emit("Language set to ${language.label}.")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to save language.")
            }
        }
    }

    fun saveCustomPrompt(prompt: String) {
        viewModelScope.launch {
            val res = workspaceRepository.updateCustomPrompt(prompt)
            if (res.isSuccess) {
                _snackMessages.emit("Custom AI prompt saved.")
            } else {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to save prompt.")
            }
        }
    }

    fun resetPromptToDefault() {
        saveCustomPrompt(DEFAULT_AI_PROMPT)
    }

    fun testPromptWithSampleComment(sampleComment: String, activePrompt: String) {
        if (sampleComment.isBlank()) return
        val ws = (workspaceState.value as? UiState.Success)?.data
        viewModelScope.launch {
            _promptTestResult.value = PromptTestResult(isLoading = true)
            val res = aiEngine.analyzeAndGenerateReply(
                commentText = sampleComment.trim(),
                authorName = "FreeFireFan_YT",
                videoTitle = "Free Fire Ranked Gameplay Highlight",
                customPrompt = activePrompt.ifBlank { DEFAULT_AI_PROMPT },
                languagePreference = ws?.languagePreferenceEnum ?: LanguagePreference.AUTO,
                recentReplies = emptySet()
            )
            _promptTestResult.value = if (res.isSuccess) {
                PromptTestResult(isLoading = false, decision = res.getOrThrow())
            } else {
                PromptTestResult(
                    isLoading = false,
                    error = res.exceptionOrNull()?.message ?: "Failed to test AI prompt."
                )
            }
        }
    }

    fun deleteHistoryItem(historyId: String) {
        viewModelScope.launch {
            val res = workspaceRepository.deleteHistoryItem(historyId)
            if (res.isFailure) {
                _snackMessages.emit(res.exceptionOrNull()?.message ?: "Failed to delete history entry.")
            }
        }
    }
}
