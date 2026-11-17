package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firestore.CommentClassification
import com.example.data.firestore.CommentRecord
import com.example.data.firestore.CommentStatus
import com.example.data.firestore.DetectedLanguage
import com.example.ui.components.NeonGamingCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CommentPreviewState

private enum class CommentFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    REPLIED("Replied"),
    IGNORED("Ignored"),
    FAILED("Failed")
}

@Composable
fun CommentManagementScreen(
    comments: List<CommentRecord>,
    channelConnected: Boolean,
    isSyncing: Boolean,
    activeCommentActionId: String?,
    previewState: CommentPreviewState,
    onSyncComments: () -> Unit,
    onConnectChannel: () -> Unit,
    onPreviewAiReply: (CommentRecord) -> Unit,
    onUpdatePreviewReply: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onSendReply: (CommentRecord, String?) -> Unit,
    onIgnoreComment: (CommentRecord) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(CommentFilter.ALL) }

    val filteredComments = remember(comments, selectedFilter) {
        when (selectedFilter) {
            CommentFilter.ALL -> comments
            CommentFilter.PENDING -> comments.filter { it.statusEnum == CommentStatus.PENDING }
            CommentFilter.REPLIED -> comments.filter { it.statusEnum == CommentStatus.REPLIED }
            CommentFilter.IGNORED -> comments.filter {
                it.statusEnum == CommentStatus.IGNORED_SPAM ||
                    it.statusEnum == CommentStatus.IGNORED_ABUSIVE ||
                    it.statusEnum == CommentStatus.SKIPPED_DUPLICATE
            }
            CommentFilter.FAILED -> comments.filter { it.statusEnum == CommentStatus.FAILED }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Comment Management",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NeonCyan
                    )
                    Text(
                        text = "${comments.size} synced threads",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = if (channelConnected) onSyncComments else onConnectChannel,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("fetch_comments_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = ObsidianVoid
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ObsidianVoid,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("SYNCING...")
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (channelConnected) "FETCH COMMENTS" else "CONNECT CHANNEL")
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CommentFilter.entries) { filter ->
                    val selected = selectedFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("comment_filter_${filter.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            containerColor = ObsidianElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = NeonCyan,
                            borderColor = ObsidianOutline
                        )
                    )
                }
            }

            if (filteredComments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeonGamingCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forum,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (!channelConnected) {
                                    "Connect Your YouTube Channel"
                                } else {
                                    "No Comments in ${selectedFilter.label}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!channelConnected) {
                                    "Link your YouTube channel with Google OAuth 2.0 to load live comments from your videos."
                                } else {
                                    "Tap FETCH COMMENTS to pull the latest viewer comments from YouTube Data API v3."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredComments, key = { it.id.ifBlank { it.youtubeCommentId } }) { comment ->
                        CommentCard(
                            comment = comment,
                            isBusy = activeCommentActionId == comment.id,
                            onPreviewAiReply = { onPreviewAiReply(comment) },
                            onDirectAiReply = { onSendReply(comment, null) },
                            onIgnore = { onIgnoreComment(comment) }
                        )
                    }
                }
            }
        }
    }

    previewState.comment?.let { targetComment ->
        AlertDialog(
            onDismissRequest = onDismissPreview,
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "AI Reply Preview • ${targetComment.authorName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = ObsidianElevated,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, ObsidianOutline)
                    ) {
                        Text(
                            text = targetComment.commentText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (previewState.isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonCyan)
                            Text("Generating reply with GR GAMER AI...", color = NeonCyan)
                        }
                    } else if (!previewState.error.isNullOrBlank()) {
                        Text(
                            text = previewState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCrimson
                        )
                    } else {
                        previewState.decision?.let { dec ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(text = dec.detectedLanguage.label, color = NeonBlue)
                                StatusPill(
                                    text = dec.classification.name,
                                    color = if (dec.classification == CommentClassification.NORMAL) CyberEmerald else CyberCrimson
                                )
                            }
                        }
                        OutlinedTextField(
                            value = previewState.editedReply,
                            onValueChange = onUpdatePreviewReply,
                            label = { Text("Edit AI Reply") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSendReply(targetComment, previewState.editedReply) },
                    enabled = !previewState.isLoading && previewState.editedReply.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberEmerald,
                        contentColor = ObsidianVoid
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Post to YouTube")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPreview) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CommentCard(
    comment: CommentRecord,
    isBusy: Boolean,
    onPreviewAiReply: () -> Unit,
    onDirectAiReply: () -> Unit,
    onIgnore: () -> Unit
) {
    val statusColor = when (comment.statusEnum) {
        CommentStatus.PENDING -> CyberAmber
        CommentStatus.REPLIED -> CyberEmerald
        CommentStatus.FAILED -> CyberCrimson
        CommentStatus.IGNORED_SPAM, CommentStatus.IGNORED_ABUSIVE, CommentStatus.SKIPPED_DUPLICATE -> TextMuted
    }

    NeonGamingCard(
        highlighted = comment.statusEnum == CommentStatus.PENDING,
        glowAccent = statusColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = comment.authorName,
                placeholder = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                error = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!comment.videoTitle.isNullOrBlank()) {
                    Text(
                        text = comment.videoTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            StatusPill(text = comment.statusEnum.label.uppercase(), color = statusColor)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = comment.commentText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (comment.detectedLanguageEnum != DetectedLanguage.UNKNOWN) {
                StatusPill(text = comment.detectedLanguageEnum.label.uppercase(), color = CyberViolet)
            }
            if (comment.classificationEnum != CommentClassification.NORMAL) {
                StatusPill(text = comment.classificationEnum.name, color = CyberCrimson)
            }
        }

        if (!comment.generatedReply.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = CyberEmerald.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "GR GAMER AI REPLY",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = comment.generatedReply,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (!comment.errorMessage.isNullOrBlank() && comment.statusEnum == CommentStatus.FAILED) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = comment.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CyberCrimson
            )
        }

        if (comment.statusEnum == CommentStatus.PENDING || comment.statusEnum == CommentStatus.FAILED) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDirectAiReply,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ai_reply_btn_${comment.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = ObsidianVoid
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ObsidianVoid, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (comment.statusEnum == CommentStatus.FAILED) "Retry AI" else "AI Reply")
                    }
                }

                OutlinedButton(
                    onClick = onPreviewAiReply,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("preview_reply_btn_${comment.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonBlue)
                ) {
                    Text("Preview", color = NeonCyan)
                }

                OutlinedButton(
                    onClick = onIgnore,
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ignore_comment_btn_${comment.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Icon(Icons.Filled.Block, contentDescription = "Ignore comment", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
