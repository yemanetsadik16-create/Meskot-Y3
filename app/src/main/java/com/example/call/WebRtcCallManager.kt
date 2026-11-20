package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import com.example.model.IceCandidateItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*

/**
 * Real peer-to-peer WebRTC audio & video call engine using org.webrtc (Stream WebRTC Android).
 * Manages EglBase, PeerConnectionFactory, PeerConnection, Camera2 capturer, audio routing,
 * and ringing tones.
 */
class WebRtcCallManager(
    private val context: Context,
    private val onLocalIceCandidate: (IceCandidateItem) -> Unit,
    private val onOfferCreated: (String) -> Unit,
    private val onAnswerCreated: (String) -> Unit
) {
    private val tag = "WebRtcCallManager"
    val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerState: Boolean = false
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null

    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    private val _connectionState = MutableStateFlow("Ringing…")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val addedIceKeys = mutableSetOf<String>()
    private var remoteDescriptionSet = false
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isReleased = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )

    fun startRingtone(isIncoming: Boolean) {
        stopRingtone()
        runCatching {
            if (isIncoming) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            } else {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55).apply {
                    startTone(ToneGenerator.TONE_SUP_RINGTONE)
                }
            }
        }
    }

    fun stopRingtone() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        }
        toneGenerator = null
    }

    fun initialize(isVideoCall: Boolean, hasAudioPermission: Boolean, hasCameraPermission: Boolean) {
        if (isReleased || factory != null) return
        configureAudioForCall(defaultSpeaker = isVideoCall)

        runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    onLocalIceCandidate(
                        IceCandidateItem(
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            candidate = candidate.sdp
                        )
                    )
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTING -> {
                            _connectionState.value = "Connecting…"
                        }
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            stopRingtone()
                            _isConnected.value = true
                            _connectionState.value = "Connected"
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> {
                            _connectionState.value = "Reconnecting…"
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            _connectionState.value = "Connection lost"
                        }
                        PeerConnection.PeerConnectionState.CLOSED -> {
                            _connectionState.value = "Ended"
                        }
                        else -> Unit
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        track.setEnabled(true)
                        _remoteVideo.value = track
                    }
                }

                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        track.setEnabled(true)
                        _remoteVideo.value = track
                    }
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            })

            if (hasAudioPermission) {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                }
                audioSource = factory?.createAudioSource(constraints)
                localAudioTrack = factory?.createAudioTrack("MESKOT_AUDIO", audioSource)?.apply {
                    setEnabled(true)
                }
                localAudioTrack?.let { peerConnection?.addTrack(it, listOf("MESKOT_STREAM")) }
            }

            if (isVideoCall && hasCameraPermission) {
                startLocalVideoCapture()
            } else {
                _isCameraEnabled.value = false
            }
        }.onFailure { e ->
            Log.e(tag, "Failed to initialize WebRTC: ${e.message}", e)
            _connectionState.value = "Audio/Video init error"
        }
    }

    private fun startLocalVideoCapture() {
        runCatching {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            val frontCam = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            val chosen = frontCam ?: deviceNames.firstOrNull() ?: return
            val capturer = enumerator.createCapturer(chosen, null) ?: return
            videoCapturer = capturer

            surfaceTextureHelper = SurfaceTextureHelper.create("MeskotCaptureThread", eglBase.eglBaseContext)
            videoSource = factory?.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, context.applicationContext, videoSource?.capturerObserver)
            capturer.startCapture(720, 1280, 30)

            localVideoTrack = factory?.createVideoTrack("MESKOT_VIDEO", videoSource)?.apply {
                setEnabled(true)
            }
            localVideoTrack?.let {
                _localVideo.value = it
                _isCameraEnabled.value = true
                peerConnection?.addTrack(it, listOf("MESKOT_STREAM"))
            }
        }.onFailure { e ->
            Log.w(tag, "Camera capturer start failed: ${e.message}")
            _isCameraEnabled.value = false
        }
    }

    fun createOffer(isVideoCall: Boolean) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val sdp = desc ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                onOfferCreated(sdp.description)
            }
        }, constraints)
    }

    fun acceptOfferAndCreateAnswer(offerSdp: String, isVideoCall: Boolean) {
        stopRingtone()
        _connectionState.value = "Connecting…"
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingIce()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
                }
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        val answer = desc ?: return
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), answer)
                        onAnswerCreated(answer.description)
                    }
                }, constraints)
            }
        }, remoteDesc)
    }

    fun onRemoteAnswerReceived(answerSdp: String) {
        if (remoteDescriptionSet) return
        stopRingtone()
        _connectionState.value = "Connecting…"
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingIce()
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(item: IceCandidateItem) {
        val key = "${item.sdpMid}:${item.sdpMLineIndex}:${item.candidate}"
        if (!addedIceKeys.add(key)) return
        val candidate = IceCandidate(item.sdpMid, item.sdpMLineIndex, item.candidate)
        if (remoteDescriptionSet) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingIceCandidates.add(candidate)
        }
    }

    private fun flushPendingIce() {
        pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun toggleMute() {
        val nextMuted = !_isMuted.value
        _isMuted.value = nextMuted
        localAudioTrack?.setEnabled(!nextMuted)
    }

    fun toggleCamera() {
        val nextEnabled = !_isCameraEnabled.value
        _isCameraEnabled.value = nextEnabled
        localVideoTrack?.setEnabled(nextEnabled)
    }

    fun switchCamera() {
        runCatching { videoCapturer?.switchCamera(null) }
    }

    fun toggleSpeaker() {
        val nextSpeaker = !_isSpeakerOn.value
        setSpeakerphone(nextSpeaker)
    }

    private fun configureAudioForCall(defaultSpeaker: Boolean) {
        runCatching {
            previousAudioMode = audioManager.mode
            @Suppress("DEPRECATION")
            previousSpeakerState = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(defaultSpeaker)
        }
    }

    private fun setSpeakerphone(enabled: Boolean) {
        _isSpeakerOn.value = enabled
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (enabled) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
            }
        }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        stopRingtone()
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { localVideoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { localAudioTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { eglBase.release() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = previousSpeakerState
            }
            audioManager.mode = previousAudioMode
        }
        _localVideo.value = null
        _remoteVideo.value = null
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
