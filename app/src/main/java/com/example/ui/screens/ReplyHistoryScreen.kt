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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.firestore.ReplyDeliveryStatus
import com.example.data.firestore.ReplyHistoryItem
import com.example.ui.components.NeonGamingCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    SENT("Sent"),
    FAILED("Failed")
}

@Composable
fun ReplyHistoryScreen(
    historyItems: List<ReplyHistoryItem>,
    onDeleteItem: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val filtered = remember(historyItems, searchQuery, selectedFilter) {
        historyItems.filter { item ->
            val matchesStatus = when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.SENT -> item.statusEnum == ReplyDeliveryStatus.SENT
                HistoryFilter.FAILED -> item.statusEnum == ReplyDeliveryStatus.FAILED
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                item.authorName.contains(q, ignoreCase = true) ||
                item.originalComment.contains(q, ignoreCase = true) ||
                item.replyText.contains(q, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Reply History",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan
                )
                Text(
                    text = "${historyItems.size} logged AI replies",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search author, comment, or AI reply...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilter.entries.forEach { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
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
            }

            if (filtered.isEmpty()) {
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
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No Reply History Yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Replies sent by GR GAMER AI to YouTube comments will appear here with timestamps and detected languages.",
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
                    items(filtered, key = { it.id }) { item ->
                        val isSent = item.statusEnum == ReplyDeliveryStatus.SENT
                        val accent = if (isSent) CyberEmerald else CyberCrimson
                        val timestampText = item.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"

                        NeonGamingCard(glowAccent = accent) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.authorName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${item.videoTitle ?: "YouTube Video"} • $timestampText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(text = item.languageEnum.label.uppercase(), color = CyberViolet)
                                    StatusPill(text = item.statusEnum.name, color = accent)
                                    IconButton(
                                        onClick = { onDeleteItem(item.id) },
                                        modifier = Modifier.testTag("delete_history_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete history entry",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Viewer: \"${item.originalComment}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Spacer(Modifier.height(8.dp))

                            Surface(
                                color = accent.copy(alpha = 0.08f),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = item.replyText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            if (!item.errorMessage.isNullOrBlank() && !isSent) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberCrimson
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
