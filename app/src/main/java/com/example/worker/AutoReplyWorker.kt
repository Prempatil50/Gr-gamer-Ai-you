package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.auth.YouTubeAuthManager
import com.example.data.ai.CommentAiEngine
import com.example.data.firestore.WorkspaceRepository
import com.example.data.youtube.YouTubeRepository
import com.example.domain.AutoReplyEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val TAG = "AutoReplyWorker"
private const val UNIQUE_WORK_NAME = "gr_gamer_ai_auto_reply_work"

class AutoReplyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentUser = Firebase.auth.currentUser ?: return Result.success()
        val repo = WorkspaceRepository(applicationContext)
        val workspace = repo.getWorkspaceOnce(currentUser.uid).getOrNull() ?: return Result.success()

        if (!workspace.channelConnected || !workspace.autoReplyEnabled) {
            return Result.success()
        }

        val ytAuth = YouTubeAuthManager()
        val token = ytAuth.getFreshTokenSilently(applicationContext)
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Skipping background auto-reply cycle: OAuth token requires interactive refresh.")
            return Result.retry()
        }

        val engine = AutoReplyEngine(
            workspaceRepository = repo,
            youTubeRepository = YouTubeRepository(),
            aiEngine = CommentAiEngine()
        )

        return try {
            val outcome = engine.runAutoReplyCycle(accessToken = token, userId = currentUser.uid)
            if (outcome.isSuccess) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Background auto-reply worker failed", e)
            Result.retry()
        }
    }
}

object AutoReplyScheduler {
    fun schedule(context: Context, intervalMinutes: Long) {
        val safeMinutes = intervalMinutes.coerceAtLeast(15L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoReplyWorker>(safeMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
