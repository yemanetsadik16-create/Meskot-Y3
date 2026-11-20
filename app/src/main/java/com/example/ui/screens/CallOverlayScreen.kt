package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.call.CallSignalingRepository
import com.example.call.WebRtcCallManager
import com.example.model.CallSession
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallOverlayScreen(
    call: CallSession,
    currentUserId: String,
    peerName: String,
    peerColorHex: Long,
    signaling: CallSignalingRepository,
    onCallClosed: (durationSeconds: Int, wasConnected: Boolean, finalStatus: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCaller = call.callerId == currentUserId
    val isVideo = call.callType == "video"

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionsAsked by remember { mutableStateOf(false) }

    // For incoming calls, user must tap Accept before WebRTC starts
    var isAcceptedByCallee by remember(call.id) { mutableStateOf(isCaller) }
    var elapsedSeconds by remember(call.id) { mutableIntStateOf(0) }
    var closedOnce by remember(call.id) { mutableStateOf(false) }

    val localCandidatesCol = if (isCaller) "callerCandidates" else "calleeCandidates"
    val remoteCandidatesCol = if (isCaller) "calleeCandidates" else "callerCandidates"

    val rtcManager = remember(call.id) {
        WebRtcCallManager(
            context = context,
            onLocalIceCandidate = { cand ->
                scope.launch { signaling.addIceCandidate(call.id, localCandidatesCol, cand) }
            },
            onOfferCreated = { sdp ->
                scope.launch { signaling.setOffer(call.id, sdp) }
            },
            onAnswerCreated = { sdp ->
                scope.launch { signaling.setAnswer(call.id, sdp) }
            }
        )
    }

    val localVideo by rtcManager.localVideo.collectAsStateWithLifecycle()
    val remoteVideo by rtcManager.remoteVideo.collectAsStateWithLifecycle()
    val rtcStatus by rtcManager.connectionState.collectAsStateWithLifecycle()
    val isConnected by rtcManager.isConnected.collectAsStateWithLifecycle()
    val isMuted by rtcManager.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by rtcManager.isSpeakerOn.collectAsStateWithLifecycle()
    val isCameraEnabled by rtcManager.isCameraEnabled.collectAsStateWithLifecycle()

    val liveSession by remember(call.id) { signaling.observeCall(call.id) }
        .collectAsStateWithLifecycle(initialValue = call)

    val remoteCandidates by remember(call.id) { signaling.observeIceCandidates(call.id, remoteCandidatesCol) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasAudioPerm = perms[Manifest.permission.RECORD_AUDIO] == true || hasAudioPerm
        hasCameraPerm = perms[Manifest.permission.CAMERA] == true || hasCameraPerm
        permissionsAsked = true
    }

    fun finishCall(statusToWrite: String? = null) {
        if (closedOnce) return
        closedOnce = true
        rtcManager.release()
        val finalStatus = statusToWrite ?: if (isConnected || elapsedSeconds > 0) "ended" else "missed"
        if (statusToWrite != null) {
            scope.launch { signaling.updateCallStatus(call.id, statusToWrite) }
        }
        onCallClosed(elapsedSeconds, isConnected || elapsedSeconds > 0, finalStatus)
    }

    // Request permissions when call is active/accepted
    LaunchedEffect(isAcceptedByCallee) {
        if (isAcceptedByCallee) {
            val needed = buildList {
                if (!hasAudioPerm) add(Manifest.permission.RECORD_AUDIO)
                if (isVideo && !hasCameraPerm) add(Manifest.permission.CAMERA)
            }
            if (needed.isNotEmpty() && !permissionsAsked) {
                permLauncher.launch(needed.toTypedArray())
            } else {
                permissionsAsked = true
            }
        }
    }

    // Ringtone for incoming / ringback tone for outgoing
    LaunchedEffect(call.id, isCaller, isAcceptedByCallee, isConnected) {
        if (!isConnected) {
            if (!isCaller && !isAcceptedByCallee) {
                rtcManager.startRingtone(isIncoming = true)
            } else if (isCaller && liveSession?.status == "ringing") {
                rtcManager.startRingtone(isIncoming = false)
            } else {
                rtcManager.stopRingtone()
            }
        } else {
            rtcManager.stopRingtone()
        }
    }

    // Initialize WebRTC once accepted & permission prompt resolved
    var offerOrAnswerStarted by remember(call.id) { mutableStateOf(false) }
    LaunchedEffect(isAcceptedByCallee, permissionsAsked, liveSession?.offerSdp) {
        if (!isAcceptedByCallee || !permissionsAsked || offerOrAnswerStarted) return@LaunchedEffect
        if (isCaller) {
            offerOrAnswerStarted = true
            rtcManager.initialize(isVideo, hasAudioPerm, hasCameraPerm)
            rtcManager.createOffer(isVideo)
        } else {
            val offer = liveSession?.offerSdp
            if (!offer.isNullOrBlank()) {
                offerOrAnswerStarted = true
                rtcManager.initialize(isVideo, hasAudioPerm, hasCameraPerm)
                rtcManager.acceptOfferAndCreateAnswer(offer, isVideo)
            }
        }
    }

    // Apply remote answer when caller receives it
    LaunchedEffect(liveSession?.answerSdp) {
        val ans = liveSession?.answerSdp
        if (isCaller && !ans.isNullOrBlank()) {
            rtcManager.onRemoteAnswerReceived(ans)
        }
    }

    // Apply incoming remote ICE candidates
    LaunchedEffect(remoteCandidates) {
        remoteCandidates.forEach { rtcManager.addRemoteIceCandidate(it) }
    }

    // React to remote hangup / decline
    LaunchedEffect(liveSession?.status) {
        val st = liveSession?.status
        if (st in setOf("ended", "declined", "missed")) {
            finishCall(statusToWrite = null)
        }
    }

    // Live call duration timer once connected or accepted
    LaunchedEffect(isConnected, liveSession?.status) {
        if (isConnected || liveSession?.status == "accepted") {
            while (!closedOnce) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    DisposableEffect(call.id) {
        onDispose { rtcManager.release() }
    }

    val pulse = rememberInfiniteTransition(label = "callRingPulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringScale"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler { finishCall(if (isAcceptedByCallee) "ended" else "declined") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1016))
                .testTag("call_overlay_screen")
        ) {
            // Video background (Remote peer stream) OR rich ambient Audio stage
            if (isVideo && isAcceptedByCallee && remoteVideo != null) {
                WebRtcVideoView(
                    videoTrack = remoteVideo,
                    eglContext = rtcManager.eglBase.eglBaseContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isVideo && isAcceptedByCallee && localVideo != null && !isConnected) {
                // While ringing on a video call, show caller's own camera full-screen
                WebRtcVideoView(
                    videoTrack = localVideo,
                    eglContext = rtcManager.eglBase.eglBaseContext,
                    mirror = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(peerColorHex).copy(alpha = 0.38f),
                                    Color(0xFF151822),
                                    Color(0xFF0B0C11)
                                )
                            )
                        )
                )
            }

            // Subtle dark scrim for text & controls readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )

            // Top Header (Peer Name + Status/Timer + Flip Camera)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = null,
                            tint = MeskotGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (isVideo) "Meskot Video Call" else "Meskot Audio Call",
                            color = Color(0xFFFFC199),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        peerName,
                        color = Color.White,
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    val statusText = when {
                        !isCaller && !isAcceptedByCallee -> "Incoming ${if (isVideo) "video" else "audio"} call…"
                        isConnected || elapsedSeconds > 0 -> formatCallTimer(elapsedSeconds)
                        else -> rtcStatus
                    }
                    Text(statusText, color = Color(0xFFD5D7DE), fontSize = 13.sp)
                }

                if (isVideo && isAcceptedByCallee && isCameraEnabled) {
                    IconButton(
                        onClick = { rtcManager.switchCamera() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .testTag("call_flip_camera_button")
                    ) {
                        Icon(Icons.Outlined.Cameraswitch, contentDescription = "Flip camera", tint = Color.White)
                    }
                }
            }

            // Center Avatar (shown during audio calls or before remote video arrives)
            if (!isVideo || !isAcceptedByCallee || remoteVideo == null) {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isConnected) {
                        Box(
                            modifier = Modifier
                                .size(136.dp)
                                .scale(ringScale)
                                .alpha(ringAlpha)
                                .clip(CircleShape)
                                .background(MeskotGold)
                        )
                    }
                    UserAvatar(peerName, peerColorHex, size = 112.dp)
                }
            }

            // Picture-in-Picture Local Camera Preview (when connected on a video call)
            if (isVideo && isAcceptedByCallee && isConnected && localVideo != null && isCameraEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 86.dp, end = 16.dp)
                        .width(108.dp)
                        .height(152.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    WebRtcVideoView(
                        videoTrack = localVideo,
                        eglContext = rtcManager.eglBase.eglBaseContext,
                        mirror = true,
                        zOrderMediaOverlay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Control Dock
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                if (!isCaller && !isAcceptedByCallee) {
                    // Incoming Call: Decline vs Accept
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { finishCall("declined") },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MeskotCross),
                                modifier = Modifier.size(68.dp).testTag("call_decline_button")
                            ) {
                                Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Decline", color = Color.White, fontSize = 13.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { isAcceptedByCallee = true },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MeskotAcceptGreen),
                                modifier = Modifier.size(68.dp).testTag("call_accept_button")
                            ) {
                                Icon(
                                    if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = "Accept",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Accept", color = Color.White, fontSize = 13.sp)
                        }
                    }
                } else {
                    // Active / Outgoing Call Controls
                    Surface(
                        color = Color(0xFF1A1C24).copy(alpha = 0.88f),
                        shape = RoundedCornerShape(32.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CallControlButton(
                                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                active = isMuted,
                                tag = "call_mute_button",
                                onClick = { rtcManager.toggleMute() }
                            )
                            CallControlButton(
                                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Hearing,
                                label = if (isSpeakerOn) "Speaker" else "Earpiece",
                                active = isSpeakerOn,
                                tag = "call_speaker_button",
                                onClick = { rtcManager.toggleSpeaker() }
                            )
                            if (isVideo) {
                                CallControlButton(
                                    icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                    label = if (isCameraEnabled) "Camera" else "Cam Off",
                                    active = !isCameraEnabled,
                                    tag = "call_camera_button",
                                    onClick = { rtcManager.toggleCamera() }
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledIconButton(
                                    onClick = { finishCall("ended") },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MeskotCross),
                                    modifier = Modifier.size(56.dp).testTag("call_end_button")
                                ) {
                                    Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("End", color = Color(0xFFFFB4A8), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.16f))
                .testTag(tag)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Color(0xFF141519) else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFD5D7DE), fontSize = 11.sp)
    }
}

@Composable
private fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context,
    mirror: Boolean,
    zOrderMediaOverlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(videoTrack, rendererRef) {
        val renderer = rendererRef
        if (renderer != null && videoTrack != null) {
            runCatching { videoTrack.addSink(renderer) }
        }
        onDispose {
            if (renderer != null && videoTrack != null) {
                runCatching { videoTrack.removeSink(renderer) }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                setEnableHardwareScaler(true)
                if (zOrderMediaOverlay) setZOrderMediaOverlay(true)
                rendererRef = this
            }
        },
        update = { view ->
            view.setMirror(mirror)
        },
        onRelease = { view ->
            runCatching { videoTrack?.removeSink(view) }
            runCatching { view.release() }
        },
        modifier = modifier
    )
}

private fun formatCallTimer(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
