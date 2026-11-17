package com.example.data.youtube

import android.util.Log
import com.example.data.firestore.CommentClassification
import com.example.data.firestore.CommentRecord
import com.example.data.firestore.CommentStatus
import com.example.data.firestore.DetectedLanguage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "YouTubeRepo"

class YouTubeRepository {

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    private val api: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }

    suspend fun fetchUserChannels(accessToken: String): Result<List<YouTubeChannelInfo>> {
        return try {
            val response = api.getMyChannels(authorization = "Bearer $accessToken")
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException(parseApiError(response)))
            }
            val items = response.body()?.items.orEmpty()
            val channels = items.mapNotNull { dto ->
                if (dto.id.isBlank()) return@mapNotNull null
                YouTubeChannelInfo(
                    channelId = dto.id,
                    title = dto.snippet?.title?.ifBlank { "My YouTube Channel" } ?: "My YouTube Channel",
                    customUrl = dto.snippet?.customUrl,
                    avatarUrl = dto.snippet?.thumbnails?.bestUrl(),
                    subscriberCount = dto.statistics?.subscriberCount?.toLongOrNull() ?: 0L,
                    videoCount = dto.statistics?.videoCount?.toLongOrNull() ?: 0L
                )
            }
            Result.success(channels)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user YouTube channels", e)
            Result.failure(e)
        }
    }

    suspend fun fetchChannelComments(
        accessToken: String,
        userId: String,
        channelId: String
    ): Result<List<CommentRecord>> {
        return try {
            val authHeader = "Bearer $accessToken"
            val response = api.getChannelCommentThreads(
                authorization = authHeader,
                channelId = channelId
            )
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException(parseApiError(response)))
            }

            val threads = response.body()?.items.orEmpty()
            val videoIds = threads.mapNotNull { it.snippet?.videoId }.distinct().take(40)
            val videoTitles = if (videoIds.isNotEmpty()) {
                fetchVideoTitlesMap(authHeader, videoIds)
            } else {
                emptyMap()
            }

            val records = threads.mapNotNull { thread ->
                val topComment = thread.snippet?.topLevelComment ?: return@mapNotNull null
                val snippet = topComment.snippet ?: return@mapNotNull null
                val commentId = topComment.id.ifBlank { thread.id }
                if (commentId.isBlank()) return@mapNotNull null

                // Skip comments authored by the channel owner itself
                val authorChannelId = snippet.authorChannelId?.value
                if (!authorChannelId.isNullOrBlank() && authorChannelId == channelId) {
                    return@mapNotNull null
                }

                val commentText = (snippet.textOriginal ?: snippet.textDisplay).orEmpty().trim()
                if (commentText.isBlank()) return@mapNotNull null

                val videoId = thread.snippet.videoId ?: snippet.videoId ?: "unknown"
                val ownerAlreadyReplied = thread.replies?.comments.orEmpty().any { reply ->
                    reply.snippet?.authorChannelId?.value == channelId
                }

                CommentRecord(
                    userId = userId,
                    youtubeCommentId = commentId,
                    channelId = channelId,
                    videoId = videoId,
                    videoTitle = videoTitles[videoId] ?: "Video $videoId",
                    authorName = snippet.authorDisplayName?.ifBlank { "YouTube Viewer" } ?: "YouTube Viewer",
                    authorAvatarUrl = snippet.authorProfileImageUrl,
                    commentText = commentText,
                    detectedLanguage = DetectedLanguage.UNKNOWN.name,
                    classification = CommentClassification.NORMAL.name,
                    status = if (ownerAlreadyReplied) {
                        CommentStatus.SKIPPED_DUPLICATE.name
                    } else {
                        CommentStatus.PENDING.name
                    },
                    publishedAtText = snippet.publishedAt
                )
            }
            Result.success(records)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch channel comments", e)
            Result.failure(e)
        }
    }

    suspend fun postCommentReply(
        accessToken: String,
        parentCommentId: String,
        replyText: String
    ): Result<String> {
        return try {
            val response = api.insertCommentReply(
                authorization = "Bearer $accessToken",
                body = CommentInsertRequest(
                    snippet = CommentInsertSnippet(
                        parentId = parentCommentId,
                        textOriginal = replyText
                    )
                )
            )
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException(parseApiError(response)))
            }
            val createdId = response.body()?.id ?: parentCommentId
            Result.success(createdId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post YouTube comment reply", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchVideoTitlesMap(
        authHeader: String,
        videoIds: List<String>
    ): Map<String, String> {
        return try {
            val response = api.getVideos(
                authorization = authHeader,
                ids = videoIds.joinToString(",")
            )
            if (!response.isSuccessful) return emptyMap()
            response.body()?.items.orEmpty()
                .mapNotNull { item ->
                    val title = item.snippet?.title
                    if (item.id.isNotBlank() && !title.isNullOrBlank()) item.id to title else null
                }
                .toMap()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun <T> parseApiError(response: Response<T>): String {
        val code = response.code()
        val rawBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        val apiMessage = try {
            if (!rawBody.isNullOrBlank()) {
                JSONObject(rawBody).optJSONObject("error")?.optString("message")
            } else null
        } catch (_: Exception) {
            null
        }

        return when (code) {
            401 -> "YouTube OAuth session expired. Please reconnect your YouTube channel."
            403 -> apiMessage?.takeIf { it.isNotBlank() }
                ?: "YouTube Data API v3 permission or quota denied (HTTP 403)."
            404 -> "YouTube resource not found or comments are disabled."
            else -> apiMessage?.takeIf { it.isNotBlank() } ?: "YouTube API error (HTTP $code)."
        }
    }
}
