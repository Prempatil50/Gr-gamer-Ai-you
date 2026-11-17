package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "YouTubeAuth"

/**
 * Manages Google OAuth 2.0 authorization for YouTube Data API v3 using Google Play Services
 * AuthorizationClient. No passwords or client secrets are stored or requested inside the app.
 */
class YouTubeAuthManager {

    companion object {
        private const val SCOPE_YOUTUBE_FORCE_SSL = "https://www.googleapis.com/auth/youtube.force-ssl"
        private const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"

        val YOUTUBE_SCOPES: List<Scope> = listOf(
            Scope(SCOPE_YOUTUBE_FORCE_SSL),
            Scope(SCOPE_YOUTUBE_READONLY)
        )
    }

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    sealed interface AuthFlowResult {
        data class TokenReady(val accessToken: String) : AuthFlowResult
        data class NeedsConsent(val intentSenderRequest: IntentSenderRequest) : AuthFlowResult
        data class Failed(val message: String) : AuthFlowResult
    }

    suspend fun authorizeYouTubeAccess(activity: Activity): AuthFlowResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(YOUTUBE_SCOPES)
                .build()
            val result: AuthorizationResult = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    AuthFlowResult.NeedsConsent(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    AuthFlowResult.Failed("Google OAuth consent intent was unavailable.")
                }
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    _accessToken.value = token
                    AuthFlowResult.TokenReady(token)
                } else {
                    AuthFlowResult.Failed("Google OAuth returned an empty access token.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Log.e(TAG, "YouTube OAuth ApiException: status=${e.statusCode}", e)
            AuthFlowResult.Failed(mapApiExceptionMessage(e))
        } catch (e: Exception) {
            Log.e(TAG, "YouTube OAuth failed", e)
            AuthFlowResult.Failed(e.localizedMessage ?: "Failed to authorize YouTube access.")
        }
    }

    /**
     * Attempts silent token refresh when the user has already granted YouTube OAuth consent.
     * Used by foreground cycles and background WorkManager polling.
     */
    suspend fun getFreshTokenSilently(context: Context): String? {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(YOUTUBE_SCOPES)
                .build()
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .authorize(request)
                .await()
            if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                _accessToken.value = result.accessToken
                result.accessToken
            } else {
                _accessToken.value
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent YouTube OAuth refresh failed: ${e.message}")
            _accessToken.value
        }
    }

    fun handleConsentResult(context: Context, data: Intent?): Result<String> {
        return try {
            val authorizationResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authorizationResult.accessToken
            if (!token.isNullOrBlank()) {
                _accessToken.value = token
                Result.success(token)
            } else {
                Result.failure(IllegalStateException("YouTube OAuth did not return an access token."))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "YouTube OAuth consent result failed: status=${e.statusCode}", e)
            Result.failure(IllegalStateException(mapApiExceptionMessage(e), e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YouTube OAuth result", e)
            Result.failure(e)
        }
    }

    fun clearToken() {
        _accessToken.value = null
    }

    private fun mapApiExceptionMessage(e: ApiException): String {
        return when (e.statusCode) {
            16 -> "YouTube OAuth authorization was cancelled."
            10 -> "OAuth client configuration mismatch (SHA-1 / package). Verify Google Cloud OAuth setup."
            7 -> "Network error while contacting Google OAuth."
            else -> e.localizedMessage ?: "Google OAuth error (code ${e.statusCode})."
        }
    }
}
