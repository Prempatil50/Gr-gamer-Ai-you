package com.example.data.youtube

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface YouTubeApiService {

    @GET("youtube/v3/channels")
    suspend fun getMyChannels(
        @Header("Authorization") authorization: String,
        @Query("part") part: String = "snippet,statistics",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 25
    ): Response<ChannelListResponse>

    @GET("youtube/v3/commentThreads")
    suspend fun getChannelCommentThreads(
        @Header("Authorization") authorization: String,
        @Query("part") part: String = "snippet,replies",
        @Query("allThreadsRelatedToChannelId") channelId: String,
        @Query("maxResults") maxResults: Int = 40,
        @Query("order") order: String = "time",
        @Query("textFormat") textFormat: String = "plainText"
    ): Response<CommentThreadListResponse>

    @GET("youtube/v3/videos")
    suspend fun getVideos(
        @Header("Authorization") authorization: String,
        @Query("part") part: String = "snippet",
        @Query("id") ids: String
    ): Response<VideoListResponse>

    @POST("youtube/v3/comments")
    suspend fun insertCommentReply(
        @Header("Authorization") authorization: String,
        @Query("part") part: String = "snippet",
        @Body body: CommentInsertRequest
    ): Response<CommentInsertResponse>
}
