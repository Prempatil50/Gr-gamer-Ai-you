package com.example.data.youtube

import com.squareup.moshi.JsonClass

data class YouTubeChannelInfo(
    val channelId: String,
    val title: String,
    val customUrl: String?,
    val avatarUrl: String?,
    val subscriberCount: Long,
    val videoCount: Long
)

@JsonClass(generateAdapter = true)
data class ChannelListResponse(
    val items: List<ChannelItemDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ChannelItemDto(
    val id: String = "",
    val snippet: ChannelSnippetDto? = null,
    val statistics: ChannelStatisticsDto? = null
)

@JsonClass(generateAdapter = true)
data class ChannelSnippetDto(
    val title: String? = null,
    val customUrl: String? = null,
    val thumbnails: ThumbnailMapDto? = null
)

@JsonClass(generateAdapter = true)
data class ChannelStatisticsDto(
    val subscriberCount: String? = null,
    val videoCount: String? = null,
    val hiddenSubscriberCount: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ThumbnailMapDto(
    val default: ThumbnailDto? = null,
    val medium: ThumbnailDto? = null,
    val high: ThumbnailDto? = null
) {
    fun bestUrl(): String? = high?.url ?: medium?.url ?: default?.url
}

@JsonClass(generateAdapter = true)
data class ThumbnailDto(
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class CommentThreadListResponse(
    val items: List<CommentThreadDto>? = emptyList(),
    val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class CommentThreadDto(
    val id: String = "",
    val snippet: CommentThreadSnippetDto? = null,
    val replies: CommentThreadRepliesDto? = null
)

@JsonClass(generateAdapter = true)
data class CommentThreadSnippetDto(
    val videoId: String? = null,
    val topLevelComment: CommentDto? = null,
    val totalReplyCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class CommentThreadRepliesDto(
    val comments: List<CommentDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class CommentDto(
    val id: String = "",
    val snippet: CommentSnippetDto? = null
)

@JsonClass(generateAdapter = true)
data class CommentSnippetDto(
    val videoId: String? = null,
    val textDisplay: String? = null,
    val textOriginal: String? = null,
    val authorDisplayName: String? = null,
    val authorProfileImageUrl: String? = null,
    val authorChannelId: AuthorChannelIdDto? = null,
    val publishedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthorChannelIdDto(
    val value: String? = null
)

@JsonClass(generateAdapter = true)
data class VideoListResponse(
    val items: List<VideoItemDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class VideoItemDto(
    val id: String = "",
    val snippet: VideoSnippetDto? = null
)

@JsonClass(generateAdapter = true)
data class VideoSnippetDto(
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class CommentInsertRequest(
    val snippet: CommentInsertSnippet
)

@JsonClass(generateAdapter = true)
data class CommentInsertSnippet(
    val parentId: String,
    val textOriginal: String
)

@JsonClass(generateAdapter = true)
data class CommentInsertResponse(
    val id: String = ""
)
