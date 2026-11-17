package com.example.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.YouTubeAuthManager
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.ai.CommentAiEngine
import com.example.data.firestore.UserWorkspace
import com.example.data.firestore.WorkspaceRepository
import com.example.data.youtube.YouTubeRepository
import com.example.domain.AutoReplyEngine
import com.example.ui.screens.AiPromptSettingsScreen
import com.example.ui.screens.AiReplySettingsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CommentManagementScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LanguageSettingsScreen
import com.example.ui.screens.ReplyHistoryScreen
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.GrGamerViewModel
import com.example.ui.viewmodel.UiState
import com.example.worker.AutoReplyScheduler
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object CommentsRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsHubRoute
@Serializable data object AiReplySettingsRoute
@Serializable data object LanguageSettingsRoute
@Serializable data object AiPromptSettingsRoute

private data class TopNavItem(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_NAV_ITEMS = listOf(
    TopNavItem("Dashboard", DashboardRoute, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_tab_dashboard"),
    TopNavItem("Comments", CommentsRoute, Icons.Filled.Forum, Icons.Outlined.Forum, "nav_tab_comments"),
    TopNavItem("History", HistoryRoute, Icons.Filled.History, Icons.Outlined.History, "nav_tab_history"),
    TopNavItem("Settings", SettingsHubRoute, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var attemptedSilentSignIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!attemptedSilentSignIn && auth.currentUser == null) {
            attemptedSilentSignIn = true
            // Wait for initial window surface sync and first frame draw to complete before binder IPC.
            snapshotFlow { windowInfo.isWindowFocused }.filter { it }.first()
            withFrameNanos { }
            delay(900L)
            if (auth.currentUser == null) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = scope
                )
            }
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            credentialManager = credentialManager,
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                currentUserId = user.uid,
                onSignOut = {
                    AutoReplyScheduler.cancel(context.applicationContext)
                    signOut(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: GrGamerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val workspaceRepo = WorkspaceRepository(db)
                val youTubeRepo = YouTubeRepository()
                val ytAuth = YouTubeAuthManager()
                val aiEngine = CommentAiEngine()
                val autoReplyEngine = AutoReplyEngine(workspaceRepo, youTubeRepo, aiEngine)
                GrGamerViewModel(
                    currentUserId = currentUserId,
                    workspaceRepository = workspaceRepo,
                    youTubeRepository = youTubeRepo,
                    youTubeAuthManager = ytAuth,
                    aiEngine = aiEngine,
                    autoReplyEngine = autoReplyEngine
                )
            }
        }
    )

    val oauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.handleYouTubeConsentActivityResult(context, activityResult.data)
    }

    LaunchedEffect(viewModel) {
        viewModel.oauthConsentEvents.collect { request ->
            oauthLauncher.launch(request)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val cycleProgress by viewModel.cycleProgress.collectAsStateWithLifecycle()
    val isConnectingChannel by viewModel.isConnectingChannel.collectAsStateWithLifecycle()
    val availableChannels by viewModel.availableChannels.collectAsStateWithLifecycle()
    val showChannelPicker by viewModel.showChannelPicker.collectAsStateWithLifecycle()
    val promptTestResult by viewModel.promptTestResult.collectAsStateWithLifecycle()
    val previewState by viewModel.commentPreviewState.collectAsStateWithLifecycle()
    val activeCommentActionId by viewModel.activeCommentActionId.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navigateTopLevel: (Any) -> Unit = { targetRoute ->
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        tonalElevation = 6.dp
                    ) {
                        TOP_NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                modifier = Modifier.testTag(item.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .navigationBarsPadding()
                    ) {
                        TOP_NAV_ITEMS.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                modifier = Modifier.testTag(item.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val wsState = workspaceState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(40.dp))
                                Text(
                                    text = "Loading GR GAMER AI Workspace...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = wsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyberCrimson
                                )
                                Button(
                                    onClick = onSignOut,
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("Sign Out")
                                }
                            }
                        }

                        is UiState.Success<UserWorkspace> -> {
                            val workspace = wsState.data
                            val comments = (commentsState as? UiState.Success)?.data.orEmpty()
                            val history = (historyState as? UiState.Success)?.data.orEmpty()

                            NavHost(
                                navController = navController,
                                startDestination = DashboardRoute,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<DashboardRoute> {
                                    HomeDashboardScreen(
                                        workspace = workspace,
                                        cycleProgress = cycleProgress,
                                        isConnectingChannel = isConnectingChannel,
                                        availableChannels = availableChannels,
                                        showChannelPicker = showChannelPicker,
                                        onConnectYouTubeClick = {
                                            activity?.let { viewModel.startYouTubeConnect(it) }
                                        },
                                        onSelectChannel = viewModel::selectYouTubeChannel,
                                        onDismissChannelPicker = viewModel::dismissChannelPicker,
                                        onDisconnectChannel = { viewModel.disconnectYouTubeChannel(context) },
                                        onStartAutoReply = {
                                            activity?.let { viewModel.startAutoReply(it) }
                                        },
                                        onStopAutoReply = { viewModel.stopAutoReply(context) },
                                        onToggleAutoReply = { enabled ->
                                            activity?.let { viewModel.toggleAutoReply(it, enabled) }
                                        },
                                        onOpenComments = { navigateTopLevel(CommentsRoute) },
                                        onOpenHistory = { navigateTopLevel(HistoryRoute) },
                                        onOpenAiSettings = { navController.navigate(AiReplySettingsRoute) },
                                        onOpenLanguageSettings = { navController.navigate(LanguageSettingsRoute) },
                                        onOpenPromptSettings = { navController.navigate(AiPromptSettingsRoute) }
                                    )
                                }

                                composable<CommentsRoute> {
                                    CommentManagementScreen(
                                        comments = comments,
                                        channelConnected = workspace.channelConnected,
                                        isSyncing = cycleProgress.isRunning || isConnectingChannel,
                                        activeCommentActionId = activeCommentActionId,
                                        previewState = previewState,
                                        onSyncComments = {
                                            activity?.let { viewModel.syncComments(it) }
                                        },
                                        onConnectChannel = {
                                            activity?.let { viewModel.startYouTubeConnect(it) }
                                        },
                                        onPreviewAiReply = viewModel::openReplyPreview,
                                        onUpdatePreviewReply = viewModel::updatePreviewReplyText,
                                        onDismissPreview = viewModel::dismissPreviewDialog,
                                        onSendReply = { comment, overrideReply ->
                                            activity?.let { viewModel.replyToSingleComment(it, comment, overrideReply) }
                                        },
                                        onIgnoreComment = viewModel::ignoreComment
                                    )
                                }

                                composable<HistoryRoute> {
                                    ReplyHistoryScreen(
                                        historyItems = history,
                                        onDeleteItem = viewModel::deleteHistoryItem
                                    )
                                }

                                composable<SettingsHubRoute> {
                                    SettingsHubScreen(
                                        workspace = workspace,
                                        onOpenAiReplySettings = { navController.navigate(AiReplySettingsRoute) },
                                        onOpenLanguageSettings = { navController.navigate(LanguageSettingsRoute) },
                                        onOpenPromptSettings = { navController.navigate(AiPromptSettingsRoute) },
                                        onDisconnectChannel = { viewModel.disconnectYouTubeChannel(context) },
                                        onSignOut = onSignOut
                                    )
                                }

                                composable<AiReplySettingsRoute> {
                                    AiReplySettingsScreen(
                                        workspace = workspace,
                                        onBack = { navController.popBackStack() },
                                        onSave = { interval, ignoreSpam, ignoreAbusive, avoidDuplicates ->
                                            viewModel.saveAiSettings(
                                                context = context,
                                                intervalMinutes = interval,
                                                ignoreSpam = ignoreSpam,
                                                ignoreAbusive = ignoreAbusive,
                                                avoidDuplicates = avoidDuplicates
                                            )
                                            navController.popBackStack()
                                        }
                                    )
                                }

                                composable<LanguageSettingsRoute> {
                                    LanguageSettingsScreen(
                                        workspace = workspace,
                                        onBack = { navController.popBackStack() },
                                        onSelectLanguage = { lang ->
                                            viewModel.saveLanguagePreference(lang)
                                        }
                                    )
                                }

                                composable<AiPromptSettingsRoute> {
                                    AiPromptSettingsScreen(
                                        workspace = workspace,
                                        testResult = promptTestResult,
                                        onBack = { navController.popBackStack() },
                                        onSavePrompt = { prompt ->
                                            viewModel.saveCustomPrompt(prompt)
                                        },
                                        onResetDefault = viewModel::resetPromptToDefault,
                                        onTestPrompt = viewModel::testPromptWithSampleComment
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
