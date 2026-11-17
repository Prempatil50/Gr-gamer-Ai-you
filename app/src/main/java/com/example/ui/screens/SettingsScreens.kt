package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.firestore.LanguagePreference
import com.example.data.firestore.UserWorkspace
import com.example.ui.components.NeonGamingCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.PromptTestResult

@Composable
fun SettingsHubScreen(
    workspace: UserWorkspace,
    onOpenAiReplySettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenPromptSettings: () -> Unit,
    onDisconnectChannel: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "AI & System Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonCyan
                )
                Text(
                    text = "Signed in as ${workspace.email ?: workspace.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            item {
                SettingsNavRow(
                    title = "AI Reply Settings",
                    subtitle = "Interval: ${workspace.autoReplyIntervalMinutes}m • Spam/Abuse Filter: ${if (workspace.ignoreSpam) "ON" else "OFF"}",
                    icon = Icons.Filled.Tune,
                    accent = NeonBlue,
                    testTag = "nav_ai_reply_settings",
                    onClick = onOpenAiReplySettings
                )
            }

            item {
                SettingsNavRow(
                    title = "Language Settings",
                    subtitle = "Active mode: ${workspace.languagePreferenceEnum.label} (Hindi, Hinglish, English)",
                    icon = Icons.Filled.Translate,
                    accent = CyberViolet,
                    testTag = "nav_language_settings",
                    onClick = onOpenLanguageSettings
                )
            }

            item {
                SettingsNavRow(
                    title = "AI Prompt Settings",
                    subtitle = "Customize GR GAMER AI personality & test live replies",
                    icon = Icons.Filled.EditNote,
                    accent = NeonCyan,
                    testTag = "nav_ai_prompt_settings",
                    onClick = onOpenPromptSettings
                )
            }

            if (workspace.channelConnected) {
                item {
                    OutlinedButton(
                        onClick = onDisconnectChannel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("settings_disconnect_channel_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCrimson),
                        border = BorderStroke(1.dp, CyberCrimson.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Disconnect YouTube Channel (${workspace.connectedChannelTitle.orEmpty()})")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out of Google Account", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = ObsidianElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = TextSecondary
            )
        }
    }
}

@Composable
fun AiReplySettingsScreen(
    workspace: UserWorkspace,
    onBack: () -> Unit,
    onSave: (intervalMinutes: Long, ignoreSpam: Boolean, ignoreAbusive: Boolean, avoidDuplicates: Boolean) -> Unit
) {
    var interval by remember(workspace.autoReplyIntervalMinutes) {
        mutableLongStateOf(workspace.autoReplyIntervalMinutes)
    }
    var ignoreSpam by remember(workspace.ignoreSpam) { mutableStateOf(workspace.ignoreSpam) }
    var ignoreAbusive by remember(workspace.ignoreAbusive) { mutableStateOf(workspace.ignoreAbusive) }
    var avoidDuplicates by remember(workspace.avoidDuplicates) { mutableStateOf(workspace.avoidDuplicates) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SubScreenTopBar(title = "AI Reply Settings", onBack = onBack)
            }

            item {
                NeonGamingCard {
                    Text(
                        text = "Background Check Interval",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                    Text(
                        text = "Android WorkManager periodically checks for new YouTube comments when Auto Reply is ON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15L, 30L, 60L).forEach { mins ->
                            val selected = interval == mins
                            FilterChip(
                                selected = selected,
                                onClick = { interval = mins },
                                label = { Text("${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = NeonCyan,
                                    containerColor = ObsidianElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            item {
                NeonGamingCard {
                    SettingToggleRow(
                        title = "Ignore Spam Comments",
                        subtitle = "Skip links, bot promotions, and scam giveaway comments",
                        checked = ignoreSpam,
                        onCheckedChange = { ignoreSpam = it },
                        testTag = "toggle_ignore_spam"
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingToggleRow(
                        title = "Ignore Abusive Comments",
                        subtitle = "Automatically skip toxic, hateful, or profane comments",
                        checked = ignoreAbusive,
                        onCheckedChange = { ignoreAbusive = it },
                        testTag = "toggle_ignore_abusive"
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingToggleRow(
                        title = "Avoid Duplicate Replies",
                        subtitle = "Never reply twice to the same comment or repeat identical text",
                        checked = avoidDuplicates,
                        onCheckedChange = { avoidDuplicates = it },
                        testTag = "toggle_avoid_duplicates"
                    )
                }
            }

            item {
                Button(
                    onClick = { onSave(interval, ignoreSpam, ignoreAbusive, avoidDuplicates) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_ai_settings_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianVoid)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("SAVE AI REPLY SETTINGS", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = CyberEmerald,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = ObsidianElevated
            )
        )
    }
}

@Composable
fun LanguageSettingsScreen(
    workspace: UserWorkspace,
    onBack: () -> Unit,
    onSelectLanguage: (LanguagePreference) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SubScreenTopBar(title = "Language Settings", onBack = onBack)
            }

            item {
                Text(
                    text = "GR GAMER AI supports Hindi, Hinglish, and English. Choose Auto-Detect to automatically match each viewer's comment language.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            LanguagePreference.entries.forEach { pref ->
                item(key = pref.name) {
                    val isSelected = workspace.languagePreferenceEnum == pref
                    val sample = when (pref) {
                        LanguagePreference.AUTO -> "Matches viewer language: Hindi -> Hindi, Hinglish -> Hinglish, English -> English"
                        LanguagePreference.HINDI -> "उदाहरण: \"बहुत बढ़िया भाई! अगली लाइव स्ट्रीम में ज़रूर जुड़ना!\""
                        LanguagePreference.HINGLISH -> "Example: \"Shukriya bhai! Booyah squad ready rakho next custom mein!\""
                        LanguagePreference.ENGLISH -> "Example: \"Thanks legend! Drop your UID for the next Free Fire custom room!\""
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onSelectLanguage(pref) }
                            .testTag("language_option_${pref.name.lowercase()}"),
                        shape = MaterialTheme.shapes.large,
                        color = ObsidianElevated,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) NeonCyan else ObsidianOutline
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pref.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) NeonCyan else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = NeonCyan)
                                }
                            }
                            Text(pref.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(sample, style = MaterialTheme.typography.labelMedium, color = CyberEmerald)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiPromptSettingsScreen(
    workspace: UserWorkspace,
    testResult: PromptTestResult,
    onBack: () -> Unit,
    onSavePrompt: (String) -> Unit,
    onResetDefault: () -> Unit,
    onTestPrompt: (sampleComment: String, activePrompt: String) -> Unit
) {
    var promptText by remember(workspace.customPrompt) { mutableStateOf(workspace.customPrompt) }
    var sampleComment by remember { mutableStateOf("Bhai Free Fire sensitivity setting batao headshot ke liye!") }
    val isValidLength = promptText.trim().length in 10..2000

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SubScreenTopBar(title = "AI Prompt Settings", onBack = onBack)
            }

            item {
                NeonGamingCard(highlighted = true) {
                    Text(
                        text = "GR GAMER AI System Prompt",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { if (it.length <= 2000) promptText = it },
                        label = { Text("Custom AI Instructions") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_prompt_input")
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isValidLength) "Ready to save" else "Prompt must be 10–2000 characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isValidLength) TextSecondary else CyberCrimson
                        )
                        Text(
                            text = "${promptText.trim().length}/2000",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSavePrompt(promptText) },
                            enabled = isValidLength,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_prompt_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianVoid),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SAVE PROMPT")
                        }
                        OutlinedButton(
                            onClick = onResetDefault,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("reset_default_prompt_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ObsidianOutline)
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = "Reset", tint = TextSecondary)
                            Spacer(Modifier.width(4.dp))
                            Text("Default", color = TextSecondary)
                        }
                    }
                }
            }

            item {
                NeonGamingCard {
                    Text(
                        text = "Test Prompt Live with Gemini AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberEmerald
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sampleComment,
                        onValueChange = { sampleComment = it },
                        label = { Text("Sample YouTube Comment") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_comment_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onTestPrompt(sampleComment, promptText) },
                        enabled = !testResult.isLoading && sampleComment.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_prompt_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = ObsidianVoid),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (testResult.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ObsidianVoid, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("GENERATING TEST REPLY...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("GENERATE AI TEST REPLY")
                        }
                    }

                    AnimatedVisibility(visible = testResult.decision != null || !testResult.error.isNullOrBlank()) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            testResult.error?.let { err ->
                                Text(err, color = CyberCrimson, style = MaterialTheme.typography.bodySmall)
                            }
                            testResult.decision?.let { dec ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPill(text = dec.detectedLanguage.label.uppercase(), color = CyberViolet)
                                    StatusPill(text = dec.classification.name, color = CyberEmerald)
                                }
                                Surface(
                                    color = CyberEmerald.copy(alpha = 0.08f),
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = if (dec.shouldReply) dec.replyText else "Skipped (${dec.classification.name}): ${dec.reason}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubScreenTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = NeonCyan
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = NeonCyan
        )
    }
}
