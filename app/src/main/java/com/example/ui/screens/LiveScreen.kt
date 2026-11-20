package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.LiveComment
import com.example.model.LiveStreamItem
import com.example.model.PostPrivacy
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.delay

private data class FloatingReaction(
    val id: Long,
    val emoji: String
)

private val LIVE_CATEGORIES = listOf(
    "General",
    "Music & Culture",
    "Coffee Chat",
    "News & Talk",
    "Sports",
    "Bazaar Live"
)

@Composable
fun LiveScreen(
    state: MeskotDataState,
    lang: AppLang,
    initialStreamId: String? = null,
    activeComments: List<LiveComment>,
    onBack: () -> Unit,
    onSelectStream: (String?) -> Unit,
    onStartLive: (title: String, category: String, privacy: PostPrivacy, onStarted: (LiveStreamItem) -> Unit) -> Unit,
    onEndLive: (String) -> Unit,
    onSendComment: (streamId: String, text: String) -> Unit,
    onSendReaction: (streamId: String, hostId: String, starsAmount: Int) -> Unit,
    onShareRecap: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val me = state.currentUser ?: return
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true || hasAudioPermission
    }

    // Active broadcast or watched stream
    var broadcastingStream by remember { mutableStateOf<LiveStreamItem?>(null) }
    var watchingStreamId by remember { mutableStateOf(initialStreamId) }
    var endedSummary by remember { mutableStateOf<Pair<LiveStreamItem, Int>?>(null) }

    // Camera & studio controls
    var useFrontCamera by remember { mutableStateOf(true) }
    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraEnabled by remember { mutableStateOf(true) }

    // Go-live setup form
    var streamTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LIVE_CATEGORIES.first()) }
    var selectedPrivacy by remember { mutableStateOf(PostPrivacy.PUBLIC) }
    var isStarting by remember { mutableStateOf(false) }

    // Elapsed seconds when broadcasting
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(broadcastingStream?.id) {
        if (broadcastingStream != null) {
            elapsedSeconds = 0
            while (broadcastingStream != null) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    // Sync active stream selection to ViewModel for live comments
    val activeStreamId = broadcastingStream?.id ?: watchingStreamId
    LaunchedEffect(activeStreamId) {
        onSelectStream(activeStreamId)
    }
    DisposableEffect(Unit) {
        onDispose {
            broadcastingStream?.let { onEndLive(it.id) }
            onSelectStream(null)
        }
    }

    // Merge active live streams with any live stories so nothing is missed
    val allLiveStreams = remember(state.liveStreams, state.stories) {
        val fromStories = state.stories.filter { it.isLive }.map { st ->
            LiveStreamItem(
                id = st.id,
                hostId = st.authorId,
                hostName = st.authorName,
                title = st.caption.ifBlank { "${st.authorName} is Live" },
                category = "General",
                isLive = true,
                viewerCount = 1,
                startedAtMs = st.timestampMs,
                coverColorHex = st.bgColorHex
            )
        }
        (state.liveStreams + fromStories)
            .associateBy { it.id }
            .values
            .filter { it.isLive }
            .sortedByDescending { it.startedAtMs }
    }

    val liveBroadcastObj = broadcastingStream?.let { current ->
        allLiveStreams.find { it.id == current.id } ?: current
    }
    val watchedStreamObj = watchingStreamId?.let { id ->
        allLiveStreams.find { it.id == id }
    }

    fun handleLeave() {
        val currentBroadcast = liveBroadcastObj
        if (currentBroadcast != null) {
            onEndLive(currentBroadcast.id)
            endedSummary = currentBroadcast to elapsedSeconds
            broadcastingStream = null
        } else if (watchingStreamId != null) {
            watchingStreamId = null
        } else {
            onBack()
        }
    }

    BackHandler { handleLeave() }

    when {
        liveBroadcastObj != null -> {
            LiveStageView(
                stream = liveBroadcastObj,
                isHost = true,
                lang = lang,
                hasCameraPermission = hasCameraPermission,
                isCameraEnabled = isCameraEnabled,
                useFrontCamera = useFrontCamera,
                isMicMuted = isMicMuted,
                elapsedSeconds = elapsedSeconds,
                comments = activeComments,
                onRequestCamera = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                onFlipCamera = { useFrontCamera = !useFrontCamera },
                onToggleMic = { isMicMuted = !isMicMuted },
                onToggleCamera = { isCameraEnabled = !isCameraEnabled },
                onSendComment = { text -> onSendComment(liveBroadcastObj.id, text) },
                onSendReaction = { stars -> onSendReaction(liveBroadcastObj.id, liveBroadcastObj.hostId, stars) },
                onOpenProfile = onOpenProfile,
                onEndOrClose = { handleLeave() }
            )
        }

        watchedStreamObj != null -> {
            LiveStageView(
                stream = watchedStreamObj,
                isHost = false,
                lang = lang,
                hasCameraPermission = false,
                isCameraEnabled = false,
                useFrontCamera = true,
                isMicMuted = false,
                elapsedSeconds = ((System.currentTimeMillis() - watchedStreamObj.startedAtMs) / 1000L).toInt().coerceAtLeast(0),
                comments = activeComments,
                onRequestCamera = {},
                onFlipCamera = {},
                onToggleMic = {},
                onToggleCamera = {},
                onSendComment = { text -> onSendComment(watchedStreamObj.id, text) },
                onSendReaction = { stars -> onSendReaction(watchedStreamObj.id, watchedStreamObj.hostId, stars) },
                onOpenProfile = onOpenProfile,
                onEndOrClose = { watchingStreamId = null }
            )
        }

        else -> {
            LiveStudioHub(
                meName = me.name,
                meColor = me.avatarColorHex,
                lang = lang,
                hasCameraPermission = hasCameraPermission,
                hasAudioPermission = hasAudioPermission,
                useFrontCamera = useFrontCamera,
                isMicMuted = isMicMuted,
                isCameraEnabled = isCameraEnabled,
                streamTitle = streamTitle,
                selectedCategory = selectedCategory,
                selectedPrivacy = selectedPrivacy,
                isStarting = isStarting,
                activeStreams = allLiveStreams,
                onBack = onBack,
                onRequestPermissions = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                onFlipCamera = { useFrontCamera = !useFrontCamera },
                onToggleMic = { isMicMuted = !isMicMuted },
                onToggleCamera = { isCameraEnabled = !isCameraEnabled },
                onTitleChange = { streamTitle = it },
                onCategoryChange = { selectedCategory = it },
                onPrivacyChange = { selectedPrivacy = it },
                onGoLive = {
                    if (!isStarting) {
                        isStarting = true
                        onStartLive(streamTitle, selectedCategory, selectedPrivacy) { started ->
                            isStarting = false
                            broadcastingStream = started
                        }
                    }
                },
                onWatchStream = { stream -> watchingStreamId = stream.id }
            )
        }
    }

    endedSummary?.let { (stream, durationSec) ->
        AlertDialog(
            onDismissRequest = { endedSummary = null },
            containerColor = Color(0xFF1A1B20),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFD5D7DE),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MeskotGold)
                    Text(MeskotStrings.t(lang, "broadcastEnded"), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stream.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStat("Duration", formatDuration(durationSec))
                        SummaryStat("Viewers", "${stream.viewerCount}")
                        SummaryStat("Reactions", "${stream.reactionsCount}")
                        SummaryStat("Stars", "${stream.starsEarned} ★")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onShareRecap("🔴 Just wrapped a live stream: \"${stream.title}\" (${formatDuration(durationSec)}) on Meskot Live!")
                        endedSummary = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) {
                    Text(MeskotStrings.t(lang, "shareRecap"), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { endedSummary = null }) {
                    Text("Done", color = Color(0xFFB5B8C2))
                }
            }
        )
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = Color(0xFF9EA2AE), fontSize = 11.sp)
    }
}

@Composable
private fun LiveStudioHub(
    meName: String,
    meColor: Long,
    lang: AppLang,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    useFrontCamera: Boolean,
    isMicMuted: Boolean,
    isCameraEnabled: Boolean,
    streamTitle: String,
    selectedCategory: String,
    selectedPrivacy: PostPrivacy,
    isStarting: Boolean,
    activeStreams: List<LiveStreamItem>,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPrivacyChange: (PostPrivacy) -> Unit,
    onGoLive: () -> Unit,
    onWatchStream: (LiveStreamItem) -> Unit
) {
    val studioBg = Color(0xFF141519)
    val studioCard = Color(0xFF1E2028)
    val studioBorder = Color(0xFF2D303C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(studioBg)
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .testTag("live_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("live_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    MeskotStrings.t(lang, "liveStreaming"),
                    color = Color.White,
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text("Meskot Live Studio · ቀጥታ ስርጭት", color = Color(0xFF9EA2AE), fontSize = 12.sp)
            }
            Surface(
                color = MeskotCross.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MeskotCross.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(MeskotCross))
                    Text("${activeStreams.size} LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Camera Preview Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF252834), Color(0xFF14151B)))
                )
                .border(1.dp, studioBorder, RoundedCornerShape(20.dp))
        ) {
            if (hasCameraPermission && isCameraEnabled) {
                CameraPreviewSurface(
                    useFrontCamera = useFrontCamera,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    UserAvatar(meName, meColor, size = 68.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (!hasCameraPermission) MeskotStrings.t(lang, "liveCameraPerm")
                        else "Camera turned off (Audio-only mode)",
                        color = Color(0xFFD5D7DE),
                        fontSize = 13.sp
                    )
                    if (!hasCameraPermission || !hasAudioPermission) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.testTag("live_grant_permissions_button")
                        ) {
                            Icon(Icons.Outlined.Videocam, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(MeskotStrings.t(lang, "grantPermissions"), color = Color.White)
                        }
                    }
                }
            }

            // Preview top-left badge
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Text(
                    "PREVIEW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Camera controls overlay bottom-right
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioRoundControl(
                    icon = Icons.Outlined.Cameraswitch,
                    contentDesc = "Flip camera",
                    active = true,
                    onClick = onFlipCamera
                )
                StudioRoundControl(
                    icon = if (isMicMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    contentDesc = "Toggle microphone",
                    active = !isMicMuted,
                    onClick = onToggleMic
                )
                StudioRoundControl(
                    icon = if (isCameraEnabled) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    contentDesc = "Toggle camera",
                    active = isCameraEnabled,
                    onClick = onToggleCamera
                )
            }
        }

        // Go Live Form Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = studioCard),
            border = BorderStroke(1.dp, studioBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = streamTitle,
                    onValueChange = onTitleChange,
                    placeholder = { Text(MeskotStrings.t(lang, "liveTitlePh"), color = Color(0xFF8C90A0)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeskotGold,
                        unfocusedBorderColor = studioBorder,
                        cursorColor = MeskotGold
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("live_title_input")
                )

                // Category chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LIVE_CATEGORIES.forEach { cat ->
                        val selected = selectedCategory == cat
                        Surface(
                            color = if (selected) MeskotGold else Color(0xFF282B36),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.clickable { onCategoryChange(cat) }
                        ) {
                            Text(
                                cat,
                                color = if (selected) Color.White else Color(0xFFB5B8C2),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Privacy toggle + Go Live button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF282B36),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                onPrivacyChange(
                                    if (selectedPrivacy == PostPrivacy.PUBLIC) PostPrivacy.FRIENDS else PostPrivacy.PUBLIC
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (selectedPrivacy == PostPrivacy.PUBLIC) Icons.Outlined.Public else Icons.Outlined.People,
                                contentDescription = null,
                                tint = Color(0xFFD5D7DE),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                MeskotStrings.t(lang, selectedPrivacy.labelKey),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = onGoLive,
                        enabled = !isStarting,
                        colors = ButtonDefaults.buttonColors(containerColor = MeskotCross),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("go_live_button")
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.RadioButtonChecked, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(MeskotStrings.t(lang, "goLive"), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Live Now Community Directory
        Text(
            "${MeskotStrings.t(lang, "liveNow")} (${activeStreams.size})",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        if (activeStreams.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = studioCard),
                border = BorderStroke(1.dp, studioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.LiveTv, null, tint = Color(0xFF7B8090), modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(MeskotStrings.t(lang, "noLiveStreams"), color = Color(0xFF9EA2AE), fontSize = 13.sp)
                }
            }
        } else {
            activeStreams.forEach { stream ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = studioCard),
                    border = BorderStroke(1.dp, MeskotCross.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWatchStream(stream) }
                        .testTag("live_stream_card_${stream.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(stream.coverColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(stream.hostName, stream.coverColorHex, size = 44.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(color = MeskotCross, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(stream.category, color = Color(0xFFFFB27D), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("· 👁 ${stream.viewerCount}", color = Color(0xFF9EA2AE), fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stream.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stream.hostName, color = Color(0xFFB5B8C2), fontSize = 12.sp)
                        }
                        Surface(
                            color = MeskotGold,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                MeskotStrings.t(lang, "watchLive"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStageView(
    stream: LiveStreamItem,
    isHost: Boolean,
    lang: AppLang,
    hasCameraPermission: Boolean,
    isCameraEnabled: Boolean,
    useFrontCamera: Boolean,
    isMicMuted: Boolean,
    elapsedSeconds: Int,
    comments: List<LiveComment>,
    onRequestCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSendComment: (String) -> Unit,
    onSendReaction: (starsAmount: Int) -> Unit,
    onOpenProfile: (String) -> Unit,
    onEndOrClose: () -> Unit
) {
    var commentDraft by remember { mutableStateOf("") }
    var floatingReactions by remember { mutableStateOf<List<FloatingReaction>>(emptyList()) }
    val chatListState = rememberLazyListState()

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            chatListState.animateScrollToItem(comments.lastIndex)
        }
    }

    fun triggerFloating(emoji: String, starsAmount: Int = 0) {
        val item = FloatingReaction(System.nanoTime(), emoji)
        floatingReactions = (floatingReactions + item).takeLast(10)
        onSendReaction(starsAmount)
    }

    val pulseTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .testTag("live_stage_view")
    ) {
        // Background: Real CameraX feed for Host, or vibrant live audio/video stage for Viewers / camera-off
        if (isHost && hasCameraPermission && isCameraEnabled) {
            CameraPreviewSurface(
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(stream.coverColorHex).copy(alpha = 0.45f),
                                Color(0xFF151720),
                                Color(0xFF0A0B0F)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(stream.hostName, stream.coverColorHex, size = 96.dp, onClick = { onOpenProfile(stream.hostId) })
                    Spacer(Modifier.height(14.dp))
                    Text(stream.hostName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(stream.title, color = Color(0xFFD5D7DE), fontSize = 14.sp)
                    if (isHost && !hasCameraPermission) {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onRequestCamera,
                            colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                        ) {
                            Text(MeskotStrings.t(lang, "grantPermissions"), color = Color.White)
                        }
                    }
                }
            }
        }

        // Top gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
        )

        // Bottom gradient scrim for live chat readability
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(340.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        // Top HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.clickable { onOpenProfile(stream.hostId) }
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UserAvatar(stream.hostName, stream.coverColorHex, size = 32.dp)
                    Column {
                        Text(stream.hostName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(stream.category, color = Color(0xFFFFC199), fontSize = 10.sp)
                    }
                }
            }

            // LIVE badge + timer
            Surface(color = MeskotCross, shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(6.dp).alpha(pulseAlpha).clip(CircleShape).background(Color.White))
                    Text("LIVE ${formatDuration(elapsedSeconds)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Viewers badge
            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.Visibility, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("${stream.viewerCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.weight(1f))

            if (isHost) {
                Button(
                    onClick = onEndOrClose,
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotCross),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(34.dp).testTag("end_live_button")
                ) {
                    Text(MeskotStrings.t(lang, "endLive"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(
                    onClick = onEndOrClose,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Host right-side studio controls
        if (isHost) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioRoundControl(Icons.Outlined.Cameraswitch, "Flip camera", true, onFlipCamera)
                StudioRoundControl(
                    if (isMicMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    "Toggle mic",
                    !isMicMuted,
                    onToggleMic
                )
                StudioRoundControl(
                    if (isCameraEnabled) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    "Toggle camera",
                    isCameraEnabled,
                    onToggleCamera
                )
            }
        }

        // Floating reaction bubbles
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 140.dp)
                .width(56.dp)
                .height(160.dp)
        ) {
            floatingReactions.forEach { reaction ->
                key(reaction.id) {
                    FloatingEmojiBubble(
                        emoji = reaction.emoji,
                        onFinished = {
                            floatingReactions = floatingReactions.filterNot { it.id == reaction.id }
                        }
                    )
                }
            }
        }

        // Bottom Live Chat + Reactions + Composer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Stream Title Pill
            Text(
                stream.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Real-time live chat list
            LazyColumn(
                state = chatListState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    Surface(
                        color = if (comment.isGift) MeskotGold.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (comment.isGift && comment.giftEmoji != null) {
                                Text(comment.giftEmoji, fontSize = 14.sp)
                            }
                            Text(
                                comment.authorName,
                                color = if (comment.isGift) Color.White else Color(0xFFFFC199),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                comment.text,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Quick emoji reactions + Send Stars
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("❤️", "🔥", "👏", "☕").forEach { emoji ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(38.dp)
                            .clickable { triggerFloating(emoji, 0) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!isHost) {
                    Button(
                        onClick = { triggerFloating("⭐", 10) },
                        colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(36.dp).testTag("live_send_stars_button")
                    ) {
                        Text(MeskotStrings.t(lang, "sendStars"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Live Chat Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    placeholder = { Text(MeskotStrings.t(lang, "liveChatPh"), color = Color(0xFFB5B8C2), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Black.copy(alpha = 0.55f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                        focusedBorderColor = MeskotGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).testTag("live_chat_input")
                )
                IconButton(
                    onClick = {
                        if (commentDraft.isNotBlank()) {
                            onSendComment(commentDraft)
                            commentDraft = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MeskotGold)
                        .testTag("live_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StudioRoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color.Black.copy(alpha = 0.6f) else MeskotCross.copy(alpha = 0.85f))
    ) {
        Icon(icon, contentDescription = contentDesc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FloatingEmojiBubble(
    emoji: String,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(1400, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = (-130f * progress.value).dp)
            .alpha(1f - progress.value),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(emoji, fontSize = 26.sp)
    }
}

@Composable
private fun CameraPreviewSurface(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private fun formatDuration(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
