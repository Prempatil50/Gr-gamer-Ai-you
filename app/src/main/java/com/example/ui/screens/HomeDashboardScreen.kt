package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firestore.UserWorkspace
import com.example.data.youtube.YouTubeChannelInfo
import com.example.domain.CycleProgress
import com.example.ui.components.DashboardActionTile
import com.example.ui.components.NeonGamingCard
import com.example.ui.components.PulsingLiveBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryStatCard
import com.example.ui.components.formatCompactCount
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

@Composable
fun HomeDashboardScreen(
    workspace: UserWorkspace,
    cycleProgress: CycleProgress,
    isConnectingChannel: Boolean,
    availableChannels: List<YouTubeChannelInfo>,
    showChannelPicker: Boolean,
    onConnectYouTubeClick: () -> Unit,
    onSelectChannel: (YouTubeChannelInfo) -> Unit,
    onDismissChannelPicker: () -> Unit,
    onDisconnectChannel: () -> Unit,
    onStartAutoReply: () -> Unit,
    onStopAutoReply: () -> Unit,
    onToggleAutoReply: (Boolean) -> Unit,
    onOpenComments: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenPromptSettings: () -> Unit
) {
    var showDisconnectDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Gaming Header & Hero Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), MaterialTheme.shapes.extraLarge)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_gaming_hero_1794942546241),
                        contentDescription = "GR GAMER AI Command Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        ObsidianVoid.copy(alpha = 0.94f),
                                        ObsidianSurface.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "GR GAMER AI",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "Welcome, ${workspace.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            PulsingLiveBadge(isActive = workspace.autoReplyEnabled)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "LANG: ${workspace.languagePreferenceEnum.label.uppercase()}",
                                color = NeonCyan
                            )
                            StatusPill(
                                text = if (workspace.channelConnected) "CHANNEL LINKED" else "NO CHANNEL",
                                color = if (workspace.channelConnected) CyberEmerald else CyberAmber
                            )
                        }
                    }
                }
            }

            // 2. YouTube Channel Connect Card
            item {
                NeonGamingCard(
                    highlighted = workspace.channelConnected,
                    glowAccent = if (workspace.channelConnected) CyberEmerald else NeonCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SmartDisplay,
                                contentDescription = "YouTube Channel",
                                tint = CyberCrimson,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "YouTube Channel Connect",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (workspace.channelConnected) {
                            StatusPill(text = "OAUTH 2.0", color = CyberEmerald)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (workspace.channelConnected && !workspace.connectedChannelTitle.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (!workspace.connectedChannelAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = workspace.connectedChannelAvatarUrl,
                                    contentDescription = "Channel profile picture",
                                    placeholder = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                                    error = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, NeonCyan, CircleShape)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                                    contentDescription = "Channel profile picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, NeonCyan, CircleShape)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = workspace.connectedChannelTitle.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!workspace.connectedChannelHandle.isNullOrBlank()) {
                                    Text(
                                        text = workspace.connectedChannelHandle.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeonCyan
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${formatCompactCount(workspace.subscriberCount)} subscribers • ${workspace.videoCount} videos",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onConnectYouTubeClick,
                                enabled = !isConnectingChannel,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("switch_youtube_channel_button"),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Switch Channel")
                            }
                            OutlinedButton(
                                onClick = { showDisconnectDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("disconnect_youtube_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCrimson),
                                border = BorderStroke(1.dp, CyberCrimson.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Disconnect")
                            }
                        }
                    } else {
                        Text(
                            text = "Authorize your YouTube channel securely with Google OAuth 2.0 to fetch comments and post AI replies automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onConnectYouTubeClick,
                            enabled = !isConnectingChannel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("connect_youtube_channel_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = ObsidianVoid
                            )
                        ) {
                            if (isConnectingChannel) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ObsidianVoid,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("CONNECTING YOUTUBE...", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(Icons.Filled.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("CONNECT YOUTUBE CHANNEL", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // 3. Auto Reply Controls Card
            item {
                NeonGamingCard(
                    highlighted = workspace.autoReplyEnabled || cycleProgress.isRunning,
                    glowAccent = NeonCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Auto Reply Control",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (workspace.autoReplyEnabled) {
                                    "Engine active • Polling every ${workspace.autoReplyIntervalMinutes}m"
                                } else {
                                    "Engine paused • Tap START AUTO REPLY to run"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (workspace.autoReplyEnabled) "ON" else "OFF",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (workspace.autoReplyEnabled) CyberEmerald else TextMuted
                            )
                            Switch(
                                checked = workspace.autoReplyEnabled,
                                onCheckedChange = onToggleAutoReply,
                                modifier = Modifier.testTag("auto_reply_toggle"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianVoid,
                                    checkedTrackColor = CyberEmerald,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = ObsidianElevated
                                )
                            )
                        }
                    }

                    AnimatedVisibility(visible = cycleProgress.isRunning || !cycleProgress.lastSummary.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (cycleProgress.isRunning) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = NeonCyan,
                                    trackColor = ObsidianElevated
                                )
                                Text(
                                    text = cycleProgress.phaseLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan
                                )
                            } else if (!cycleProgress.lastSummary.isNullOrBlank()) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = ObsidianElevated,
                                    border = BorderStroke(1.dp, ObsidianOutline)
                                ) {
                                    Text(
                                        text = cycleProgress.lastSummary.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartAutoReply,
                            enabled = !cycleProgress.isRunning,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_auto_reply_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberEmerald,
                                contentColor = ObsidianVoid
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("START AUTO REPLY", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onStopAutoReply,
                            enabled = workspace.autoReplyEnabled || cycleProgress.isRunning,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_auto_reply_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCrimson),
                            border = BorderStroke(
                                1.dp,
                                if (workspace.autoReplyEnabled || cycleProgress.isRunning) {
                                    CyberCrimson
                                } else {
                                    ObsidianOutline
                                }
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("STOP AUTO REPLY", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }

            // 4. Live Telemetry Stats (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "LIVE COMMENT TELEMETRY",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatCard(
                            label = "Comments Received",
                            value = workspace.totalCommentsReceived.toString(),
                            icon = Icons.Filled.Forum,
                            accentColor = NeonCyan,
                            modifier = Modifier.weight(1f),
                            testTag = "stat_total_comments"
                        )
                        TelemetryStatCard(
                            label = "Replies Sent",
                            value = workspace.totalRepliesSent.toString(),
                            icon = Icons.Filled.MarkChatRead,
                            accentColor = CyberEmerald,
                            modifier = Modifier.weight(1f),
                            testTag = "stat_replies_sent"
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatCard(
                            label = "Pending Comments",
                            value = workspace.pendingCommentsCount.toString(),
                            icon = Icons.Filled.HourglassTop,
                            accentColor = CyberAmber,
                            modifier = Modifier.weight(1f),
                            testTag = "stat_pending_comments"
                        )
                        TelemetryStatCard(
                            label = "Failed Replies",
                            value = workspace.failedRepliesCount.toString(),
                            icon = Icons.Filled.ErrorOutline,
                            accentColor = CyberCrimson,
                            modifier = Modifier.weight(1f),
                            testTag = "stat_failed_replies"
                        )
                    }
                }
            }

            // 5. All 8 Dashboard Modules Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "COMMAND DASHBOARD",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardActionTile(
                            title = "YouTube Channel",
                            subtitle = if (workspace.channelConnected) "Connected • Tap to switch" else "Connect via Google OAuth",
                            icon = Icons.Filled.SmartDisplay,
                            accentColor = CyberCrimson,
                            testTag = "tile_youtube_connect",
                            onClick = onConnectYouTubeClick,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardActionTile(
                            title = "AI Auto Reply",
                            subtitle = if (workspace.autoReplyEnabled) "Active • Tap to pause" else "Run automatic AI cycle",
                            icon = Icons.Filled.AutoAwesome,
                            accentColor = CyberEmerald,
                            testTag = "tile_ai_auto_reply",
                            onClick = {
                                if (workspace.autoReplyEnabled) onStopAutoReply() else onStartAutoReply()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardActionTile(
                            title = "AI Reply Settings",
                            subtitle = "Spam filter, duplicates & timer",
                            icon = Icons.Filled.Tune,
                            accentColor = NeonBlue,
                            testTag = "tile_ai_reply_settings",
                            onClick = onOpenAiSettings,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardActionTile(
                            title = "Reply History",
                            subtitle = "${workspace.totalRepliesSent} sent • View full log",
                            icon = Icons.Filled.History,
                            accentColor = NeonCyan,
                            testTag = "tile_reply_history",
                            onClick = onOpenHistory,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardActionTile(
                            title = "Comment Mgmt",
                            subtitle = "${workspace.pendingCommentsCount} pending • Preview & post",
                            icon = Icons.Filled.Forum,
                            accentColor = CyberAmber,
                            testTag = "tile_comment_management",
                            onClick = onOpenComments,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardActionTile(
                            title = "Language Settings",
                            subtitle = workspace.languagePreferenceEnum.label,
                            icon = Icons.Filled.Translate,
                            accentColor = CyberViolet,
                            testTag = "tile_language_settings",
                            onClick = onOpenLanguageSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardActionTile(
                            title = "AI Prompt Settings",
                            subtitle = "Customize GR GAMER AI persona",
                            icon = Icons.Filled.EditNote,
                            accentColor = NeonCyan,
                            testTag = "tile_ai_prompt_settings",
                            onClick = onOpenPromptSettings,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardActionTile(
                            title = "Disconnect Channel",
                            subtitle = if (workspace.channelConnected) "Unlink ${workspace.connectedChannelTitle}" else "No channel linked",
                            icon = Icons.Filled.LinkOff,
                            accentColor = CyberCrimson,
                            testTag = "tile_disconnect_channel",
                            enabled = workspace.channelConnected,
                            onClick = { showDisconnectDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showChannelPicker) {
        AlertDialog(
            onDismissRequest = onDismissChannelPicker,
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Select YouTube Channel",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableChannels, key = { it.channelId }) { channel ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onSelectChannel(channel) }
                                .testTag("channel_option_${channel.channelId}"),
                            color = ObsidianElevated,
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f)),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = channel.avatarUrl,
                                    contentDescription = channel.title,
                                    placeholder = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                                    error = painterResource(id = R.drawable.img_channel_avatar_1794942544757),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${formatCompactCount(channel.subscriberCount)} subscribers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Select",
                                    tint = NeonCyan
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissChannelPicker) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = ObsidianSurface,
            title = {
                Text(
                    text = "Disconnect YouTube Channel?",
                    style = MaterialTheme.typography.titleLarge,
                    color = CyberCrimson
                )
            },
            text = {
                Text(
                    text = "This will pause AI Auto Reply and unlink ${workspace.connectedChannelTitle ?: "your channel"} from GR GAMER AI. Your saved reply history stays intact.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        onDisconnectChannel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCrimson)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Keep Connected", color = NeonCyan)
                }
            }
        )
    }
}
