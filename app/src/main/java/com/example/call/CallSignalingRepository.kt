package com.example.call

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.CallSession
import com.example.model.IceCandidateItem
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Dual Firestore + Realtime Database signaling layer for peer-to-peer WebRTC
 * audio and video calls on meskot-b1a69.
 */
class CallSignalingRepository(
    private val db: FirebaseFirestore,
    private val rtdb: FirebaseDatabase = Firebase.database("https://meskot-b1a69-default-rtdb.firebaseio.com")
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private fun parseCallDoc(doc: DocumentSnapshot): CallSession? {
        if (!doc.exists()) return null
        val callerId = doc.getString("callerId") ?: return null
        val calleeId = doc.getString("calleeId") ?: return null
        val offerMap = doc.get("offer") as? Map<*, *>
        val answerMap = doc.get("answer") as? Map<*, *>
        val createdTs = try {
            doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        } catch (_: Exception) {
            null
        } ?: (doc.get("createdAtMs") as? Number)?.toLong() ?: System.currentTimeMillis()
        return CallSession(
            id = doc.id,
            convoId = doc.getString("convoId") ?: "",
            callerId = callerId,
            callerName = doc.getString("callerName") ?: "Member",
            calleeId = calleeId,
            calleeName = doc.getString("calleeName") ?: "Member",
            callType = (doc.getString("callType") ?: "audio").lowercase(),
            status = (doc.getString("status") ?: "ringing").lowercase(),
            offerSdp = (offerMap?.get("sdp") as? String) ?: doc.getString("offerSdp"),
            answerSdp = (answerMap?.get("sdp") as? String) ?: doc.getString("answerSdp"),
            createdAtMs = createdTs
        )
    }

    private fun parseRtdbCall(snap: DataSnapshot): CallSession? {
        val id = snap.key ?: return null
        val callerId = snap.child("callerId").getValue(String::class.java) ?: return null
        val calleeId = snap.child("calleeId").getValue(String::class.java) ?: return null
        return CallSession(
            id = id,
            convoId = snap.child("convoId").getValue(String::class.java) ?: "",
            callerId = callerId,
            callerName = snap.child("callerName").getValue(String::class.java) ?: "Member",
            calleeId = calleeId,
            calleeName = snap.child("calleeName").getValue(String::class.java) ?: "Member",
            callType = (snap.child("callType").getValue(String::class.java) ?: "audio").lowercase(),
            status = (snap.child("status").getValue(String::class.java) ?: "ringing").lowercase(),
            offerSdp = snap.child("offer").child("sdp").getValue(String::class.java)
                ?: snap.child("offerSdp").getValue(String::class.java),
            answerSdp = snap.child("answer").child("sdp").getValue(String::class.java)
                ?: snap.child("answerSdp").getValue(String::class.java),
            createdAtMs = (snap.child("createdAtMs").value as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    /**
     * Observes incoming ringing calls directed to [uid] across both Firestore and RTDB.
     * Ignores stale calls older than 90 seconds.
     */
    fun observeIncomingCalls(uid: String): Flow<CallSession?> {
        val fsFlow = db.collection("calls")
            .whereEqualTo("calleeId", uid)
            .whereEqualTo("status", "ringing")
            .snapshots()
            .map { snap -> snap.documents.mapNotNull(::parseCallDoc) }
            .catch { emit(emptyList()) }

        val rtdbFlow = callbackFlow<List<CallSession>> {
            val ref = rtdb.getReference("calls").orderByChild("calleeId").equalTo(uid)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull(::parseRtdbCall)
                        .filter { it.status == "ringing" }
                    trySend(list)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }.catch { emit(emptyList()) }

        return combine(fsFlow, rtdbFlow) { fs, rt ->
            val cutoff = System.currentTimeMillis() - 90_000L
            (fs + rt)
                .associateBy { it.id }
                .values
                .filter { it.status == "ringing" && it.createdAtMs >= cutoff }
                .maxByOrNull { it.createdAtMs }
        }
    }

    fun observeCall(callId: String): Flow<CallSession?> {
        val fsFlow = db.collection("calls").document(callId)
            .snapshots()
            .map { parseCallDoc(it) }
            .catch { emit(null) }

        val rtdbFlow = callbackFlow<CallSession?> {
            val ref = rtdb.getReference("calls").child(callId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(if (snapshot.exists()) parseRtdbCall(snapshot) else null)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(null)
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }.catch { emit(null) }

        return combine(fsFlow, rtdbFlow) { fs, rt ->
            when {
                fs == null -> rt
                rt == null -> fs
                else -> fs.copy(
                    status = if (rt.status in setOf("ended", "declined", "missed")) rt.status
                    else if (fs.status in setOf("ended", "declined", "missed")) fs.status
                    else if (rt.status == "accepted" || rt.status == "connected") rt.status
                    else fs.status,
                    offerSdp = fs.offerSdp ?: rt.offerSdp,
                    answerSdp = fs.answerSdp ?: rt.answerSdp
                )
            }
        }
    }

    fun observeIceCandidates(callId: String, collectionName: String): Flow<List<IceCandidateItem>> {
        val fsFlow = db.collection("calls").document(callId).collection(collectionName)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val candidate = doc.getString("candidate") ?: return@mapNotNull null
                    IceCandidateItem(
                        id = doc.id,
                        sdpMid = doc.getString("sdpMid") ?: "",
                        sdpMLineIndex = (doc.get("sdpMLineIndex") as? Number)?.toInt() ?: 0,
                        candidate = candidate
                    )
                }
            }
            .catch { emit(emptyList()) }

        val rtdbFlow = callbackFlow<List<IceCandidateItem>> {
            val ref = rtdb.getReference("calls").child(callId).child(collectionName)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = snapshot.children.mapNotNull { c ->
                        val cand = c.child("candidate").getValue(String::class.java) ?: return@mapNotNull null
                        IceCandidateItem(
                            id = c.key ?: UUID.randomUUID().toString(),
                            sdpMid = c.child("sdpMid").getValue(String::class.java) ?: "",
                            sdpMLineIndex = (c.child("sdpMLineIndex").value as? Number)?.toInt() ?: 0,
                            candidate = cand
                        )
                    }
                    trySend(items)
                }

                override fun onCancelled(error: DatabaseError) {
                    trySend(emptyList())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }.catch { emit(emptyList()) }

        return combine(fsFlow, rtdbFlow) { fs, rt ->
            (fs + rt).associateBy { "${it.sdpMid}:${it.sdpMLineIndex}:${it.candidate}" }.values.toList()
        }
    }

    suspend fun createOutgoingCall(
        convoId: String,
        callerId: String,
        callerName: String,
        calleeId: String,
        calleeName: String,
        callType: String
    ): CallSession {
        val callId = db.collection("calls").document().id
        val now = System.currentTimeMillis()
        val session = CallSession(
            id = callId,
            convoId = convoId,
            callerId = callerId,
            callerName = callerName,
            calleeId = calleeId,
            calleeName = calleeName,
            callType = callType.lowercase(),
            status = "ringing",
            createdAtMs = now
        )
        val payload = mapOf(
            "convoId" to convoId,
            "callerId" to callerId,
            "callerName" to callerName,
            "calleeId" to calleeId,
            "calleeName" to calleeName,
            "participantIds" to listOf(callerId, calleeId),
            "callType" to session.callType,
            "status" to "ringing",
            "createdAtMs" to now,
            "createdAt" to FieldValue.serverTimestamp()
        )
        runCatching { db.collection("calls").document(callId).set(payload).await() }
            .onFailure { Log.w("CallSignaling", "Firestore createCall failed: ${it.message}") }
        runCatching {
            rtdb.getReference("calls").child(callId).setValue(
                payload - "createdAt"
            )
        }
        return session
    }

    suspend fun setOffer(callId: String, sdp: String) {
        val offerObj = mapOf("type" to "offer", "sdp" to sdp)
        runCatching {
            db.collection("calls").document(callId)
                .update(mapOf("offer" to offerObj, "offerSdp" to sdp))
                .await()
        }
        runCatching {
            rtdb.getReference("calls").child(callId)
                .updateChildren(mapOf("offer" to offerObj, "offerSdp" to sdp))
        }
    }

    suspend fun setAnswer(callId: String, sdp: String) {
        val answerObj = mapOf("type" to "answer", "sdp" to sdp)
        val updates = mapOf(
            "answer" to answerObj,
            "answerSdp" to sdp,
            "status" to "accepted",
            "answeredAtMs" to System.currentTimeMillis()
        )
        runCatching { db.collection("calls").document(callId).update(updates).await() }
        runCatching { rtdb.getReference("calls").child(callId).updateChildren(updates) }
    }

    suspend fun addIceCandidate(callId: String, collectionName: String, candidate: IceCandidateItem) {
        val map = mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.candidate
        )
        runCatching {
            db.collection("calls").document(callId).collection(collectionName).add(map).await()
        }
        runCatching {
            rtdb.getReference("calls").child(callId).child(collectionName).push().setValue(map)
        }
    }

    suspend fun updateCallStatus(callId: String, status: String) {
        val updates = mutableMapOf<String, Any>("status" to status)
        if (status in setOf("ended", "declined", "missed")) {
            updates["endedAtMs"] = System.currentTimeMillis()
        }
        runCatching { db.collection("calls").document(callId).update(updates).await() }
        runCatching { rtdb.getReference("calls").child(callId).updateChildren(updates) }
    }
}
