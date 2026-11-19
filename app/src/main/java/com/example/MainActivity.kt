package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.MeskotRepository
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.ui.components.MeskotBrandMark
import com.example.ui.components.UserAvatar
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.MeskotViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private val Context.meskotDataStore by preferencesDataStore(name = "meskot_prefs")
private val KEY_LANG = stringPreferencesKey("meskot_lang")

@Serializable data object FeedRoute
@Serializable data object FriendsRoute
@Serializable data object MessagesRoute
@Serializable data object MarketRoute
@Serializable data object HubRoute
@Serializable data class ProfileRoute(val uid: String)
@Serializable data object GroupsRoute
@Serializable data object PhotosRoute
@Serializable data object SavedRoute
@Serializable data object NotificationsRoute
@Serializable data object ReelsRoute
@Serializable data object CreatorRoute
@Serializable data object AdsRoute

private data class TopLevelTab(
    val route: Any,
    val labelKey: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            MeskotTheme {
                MeskotAppRoot(isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded)
            }
        }
    }
}

@Composable
fun MeskotAppRoot(isExpanded: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val firebaseUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val lang by remember(context) {
        context.meskotDataStore.data.map { prefs ->
            if (prefs[KEY_LANG] == "am") AppLang.AM else AppLang.EN
        }
    }.collectAsStateWithLifecycle(initialValue = AppLang.EN)

    fun setLanguage(target: AppLang) {
        scope.launch {
            context.meskotDataStore.edit { prefs -> prefs[KEY_LANG] = target.code }
        }
    }

    fun toggleLanguage() {
        setLanguage(if (lang == AppLang.EN) AppLang.AM else AppLang.EN)
    }

    var pendingDisplayName by remember { mutableStateOf("") }
    var pendingLocation by remember { mutableStateOf("") }

    val signedInUser = firebaseUser
    if (signedInUser == null) {
        AuthScreen(
            lang = lang,
            onToggleLang = ::toggleLanguage,
            onPendingProfileSet = { name, loc ->
                pendingDisplayName = name
                pendingLocation = loc
            }
        )
    } else {
        key(signedInUser.uid) {
            MeskotAuthenticatedApp(
                firebaseUser = signedInUser,
                preferredDisplayName = pendingDisplayName,
                preferredLocation = pendingLocation,
                isExpanded = isExpanded,
                lang = lang,
                onToggleLang = ::toggleLanguage,
                onSetLang = ::setLanguage
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeskotAuthenticatedApp(
    firebaseUser: FirebaseUser,
    preferredDisplayName: String,
    preferredLocation: String,
    isExpanded: Boolean,
    lang: AppLang,
    onToggleLang: () -> Unit,
    onSetLang: (AppLang) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    val vm: MeskotViewModel = viewModel(
        key = firebaseUser.uid,
        factory = remember(firebaseUser.uid) {
            MeskotViewModel.Factory(
                repository = MeskotRepository(appContext),
                currentUserId = firebaseUser.uid,
                initialDisplayName = preferredDisplayName.ifBlank { firebaseUser.displayName ?: "Meskot Member" },
                initialEmail = firebaseUser.email ?: "",
                initialLocation = preferredLocation
            )
        }
    )

    LaunchedEffect(lang) {
        vm.updateLanguageCache(lang)
    }

    val state by vm.dataState.collectAsStateWithLifecycle()
    val activeConvoId by vm.activeConvoId.collectAsStateWithLifecycle()
    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.consumeToast()
        }
    }

    val currentUser = state.currentUser
    if (currentUser == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(MeskotPaper),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MeskotGold)
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val unreadNotifs = state.notifications.count { !it.isRead }
    val pendingReqs = state.friendRequests.count { it.toUid == currentUser.uid }
    val unreadMsgs = state.conversations.sumOf { it.unreadCount }

    val topLevelTabs = listOf(
        TopLevelTab(FeedRoute, "nvStream", Icons.Filled.Home, Icons.Outlined.Home, "nav_stream"),
        TopLevelTab(FriendsRoute, "nvCircle", Icons.Filled.People, Icons.Outlined.People, "nav_circle"),
        TopLevelTab(MessagesRoute, "nvChats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chats"),
        TopLevelTab(MarketRoute, "nvBazaar", Icons.Filled.Storefront, Icons.Outlined.Storefront, "nav_bazaar"),
        TopLevelTab(HubRoute, "nvHub", Icons.Filled.GridView, Icons.Outlined.GridView, "nav_hub")
    )

    fun navigateTopLevel(route: Any) {
        vm.selectConversation(null)
        navController.navigate(route) {
            popUpTo(FeedRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openChatWith(uid: String) {
        vm.openOrCreateChat(uid) {
            navController.navigate(MessagesRoute) {
                popUpTo(FeedRoute) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    fun performSignOut() {
        scope.launch {
            withContext(NonCancellable) {
                runCatching { vm.markOfflineBeforeSignOut() }
                runCatching {
                    CredentialManager.create(context)
                        .clearCredentialState(ClearCredentialStateRequest())
                }
                Firebase.auth.signOut()
            }
        }
    }

    Scaffold(
        containerColor = MeskotPaper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeskotPaper),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MeskotBrandMark(size = 32.dp)
                        Column {
                            Text("Meskot", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MeskotInk)
                            Text(MeskotStrings.t(lang, "brandSub"), fontSize = 11.sp, color = MeskotMuted, lineHeight = 12.sp)
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MeskotPaper2,
                        border = BorderStroke(1.dp, MeskotLine),
                        modifier = Modifier
                            .clickable { onToggleLang() }
                            .testTag("topbar_lang_toggle")
                    ) {
                        Text(
                            text = if (lang == AppLang.EN) "EN" else "አማ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeskotInk,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { navController.navigate(NotificationsRoute) },
                        modifier = Modifier.testTag("topbar_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifs > 0) {
                                    Badge(containerColor = MeskotCross) { Text("$unreadNotifs", color = Color.White) }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = MeskotInk)
                        }
                    }
                    Box(modifier = Modifier.padding(end = 10.dp)) {
                        UserAvatar(
                            name = currentUser.name,
                            colorHex = currentUser.avatarColorHex,
                            size = 34.dp,
                            onClick = { navController.navigate(ProfileRoute(currentUser.uid)) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!isExpanded) {
                NavigationBar(containerColor = MeskotCard, tonalElevation = 2.dp) {
                    topLevelTabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        val badgeCount = when (tab.route) {
                            FriendsRoute -> pendingReqs
                            MessagesRoute -> unreadMsgs
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(tab.route) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) Badge(containerColor = MeskotCross) { Text("$badgeCount", color = Color.White) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = MeskotStrings.t(lang, tab.labelKey)
                                    )
                                }
                            },
                            label = { Text(MeskotStrings.t(lang, tab.labelKey), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MeskotGold,
                                selectedTextColor = MeskotGoldDeep,
                                indicatorColor = MeskotGold.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isExpanded) {
                NavigationRail(containerColor = MeskotCard) {
                    topLevelTabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.unselectedIcon, MeskotStrings.t(lang, tab.labelKey)) },
                            label = { Text(MeskotStrings.t(lang, tab.labelKey)) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MeskotPaper),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = navController,
                    startDestination = FeedRoute,
                    modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)
                ) {
                    composable<FeedRoute> {
                        FeedScreen(
                            state = state,
                            lang = lang,
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) },
                            onOpenReels = { navController.navigate(ReelsRoute) },
                            onCreatePost = { text, privacy, bg, uri -> vm.createPost(text, privacy, bg, uri) },
                            onReaction = vm::toggleReaction,
                            onAddComment = vm::addComment,
                            onAddReply = vm::addReply,
                            onShare = vm::sharePost,
                            onToggleSave = vm::toggleSavePost,
                            onEditPost = vm::editPost,
                            onDeletePost = vm::deletePost,
                            onHidePost = vm::hidePost,
                            onReportPost = vm::reportPost,
                            onAddStory = vm::addStory
                        )
                    }
                    composable<FriendsRoute> {
                        FriendsScreen(
                            state = state,
                            lang = lang,
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) },
                            onOpenChat = { openChatWith(it) },
                            onSendRequest = vm::sendFriendRequest,
                            onCancelRequest = vm::cancelFriendRequest,
                            onAcceptRequest = vm::acceptFriendRequest,
                            onDeclineRequest = vm::declineFriendRequest,
                            onUnfriend = vm::unfriend
                        )
                    }
                    composable<MessagesRoute> {
                        MessagesScreen(
                            state = state,
                            lang = lang,
                            activeConvoId = activeConvoId,
                            onSelectConvo = vm::selectConversation,
                            onStartChatWithUser = { openChatWith(it) },
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) },
                            onSendMessage = vm::sendMessage,
                            onEditMessage = vm::editMessage,
                            onDeleteMessage = vm::deleteMessage
                        )
                    }
                    composable<MarketRoute> {
                        MarketScreen(
                            state = state,
                            lang = lang,
                            onCreateListing = vm::createListing,
                            onToggleSaveListing = vm::toggleSaveListing,
                            onMessageSeller = { openChatWith(it) }
                        )
                    }
                    composable<HubRoute> {
                        HubScreen(
                            state = state,
                            lang = lang,
                            onSetLang = onSetLang,
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) },
                            onOpenGroups = { navController.navigate(GroupsRoute) },
                            onOpenPhotos = { navController.navigate(PhotosRoute) },
                            onOpenSaved = { navController.navigate(SavedRoute) },
                            onOpenNotifications = { navController.navigate(NotificationsRoute) },
                            onOpenReels = { navController.navigate(ReelsRoute) },
                            onOpenCreator = { navController.navigate(CreatorRoute) },
                            onOpenAds = { navController.navigate(AdsRoute) },
                            onOpenMarket = { navigateTopLevel(MarketRoute) },
                            onUpdateProfile = vm::updateProfile,
                            onLogout = ::performSignOut
                        )
                    }
                    composable<ProfileRoute> { entry ->
                        val route = entry.toRoute<ProfileRoute>()
                        ProfileScreen(
                            uid = route.uid,
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onOpenChat = { openChatWith(it) },
                            onUpdateProfile = vm::updateProfile,
                            onSendFriendRequest = vm::sendFriendRequest,
                            onUnfriend = vm::unfriend,
                            onReaction = vm::toggleReaction,
                            onAddComment = vm::addComment,
                            onAddReply = vm::addReply,
                            onShare = vm::sharePost,
                            onToggleSave = vm::toggleSavePost,
                            onEditPost = vm::editPost,
                            onDeletePost = vm::deletePost,
                            onHidePost = vm::hidePost,
                            onReportPost = vm::reportPost
                        )
                    }
                    composable<GroupsRoute> {
                        GroupsScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) },
                            onCreateGroup = vm::createGroup,
                            onToggleMembership = vm::toggleGroupMembership,
                            onCreateGroupPost = { gid, text, privacy, bg, uri ->
                                vm.createPost(text, privacy, bg, uri, groupId = gid)
                            },
                            onReaction = vm::toggleReaction,
                            onAddComment = vm::addComment,
                            onAddReply = vm::addReply,
                            onShare = vm::sharePost,
                            onToggleSave = vm::toggleSavePost,
                            onEditPost = vm::editPost,
                            onDeletePost = vm::deletePost,
                            onHidePost = vm::hidePost,
                            onReportPost = vm::reportPost
                        )
                    }
                    composable<PhotosRoute> {
                        PhotosAlbumsScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onCreateAlbum = vm::createAlbum
                        )
                    }
                    composable<SavedRoute> {
                        SavedPostsScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) },
                            onReaction = vm::toggleReaction,
                            onAddComment = vm::addComment,
                            onAddReply = vm::addReply,
                            onShare = vm::sharePost,
                            onToggleSave = vm::toggleSavePost,
                            onEditPost = vm::editPost,
                            onDeletePost = vm::deletePost,
                            onHidePost = vm::hidePost,
                            onReportPost = vm::reportPost
                        )
                    }
                    composable<NotificationsRoute> {
                        NotificationsScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onMarkAllRead = vm::markAllNotificationsRead,
                            onOpenProfile = { navController.navigate(ProfileRoute(it)) }
                        )
                    }
                    composable<ReelsRoute> {
                        ReelsScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onCreateReel = vm::createReel,
                            onToggleLike = vm::toggleReelLike,
                            onAddComment = vm::addReelComment
                        )
                    }
                    composable<CreatorRoute> {
                        CreatorStudioScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<AdsRoute> {
                        AdsManagerScreen(
                            state = state,
                            lang = lang,
                            onBack = { navController.popBackStack() },
                            onCreateCampaign = vm::createAdCampaign
                        )
                    }
                }
            }
        }
    }
}
