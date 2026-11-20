package com.example.data

import android.util.Log
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/**
 * Observes existing records stored in Firebase Realtime Database (meskot-b1a69-default-rtdb)
 * so legacy web-app data seamlessly merges with Cloud Firestore.
 */
class RealtimeDbSource(
    private val rtdb: FirebaseDatabase = Firebase.database("https://meskot-b1a69-default-rtdb.firebaseio.com")
) {
    private val palette = listOf(0xFFC93E03L, 0xFF8C2F39L, 0xFF1F232BL, 0xFF2D4373L, 0xFF1B5E42L, 0xFF6B3FA0L)
    private fun pickColor(id: String): Long = palette[kotlin.math.abs(id.hashCode()) % palette.size]

    private fun observeNode(path: String): Flow<DataSnapshot?> = callbackFlow {
        val ref = rtdb.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("RealtimeDbSource", "RTDB listener cancelled for $path: ${error.message}")
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    private fun DataSnapshot.str(vararg keys: String): String? {
        for (k in keys) {
            val v = child(k).value
            if (v is String && v.isNotBlank()) return v
        }
        return null
    }

    private fun DataSnapshot.longVal(vararg keys: String): Long? {
        for (k in keys) {
            val v = child(k).value
            if (v is Number) return v.toLong()
        }
        return null
    }

    private fun DataSnapshot.doubleVal(vararg keys: String): Double? {
        for (k in keys) {
            val v = child(k).value
            if (v is Number) return v.toDouble()
        }
        return null
    }

    private fun DataSnapshot.boolVal(vararg keys: String): Boolean? {
        for (k in keys) {
            val v = child(k).value
            if (v is Boolean) return v
        }
        return null
    }

    private fun DataSnapshot.strList(vararg keys: String): List<String> {
        for (k in keys) {
            val node = child(k)
            if (!node.exists()) continue
            val out = mutableListOf<String>()
            node.children.forEach { c ->
                when (val v = c.value) {
                    is String -> out.add(v)
                    is Boolean -> if (v && c.key != null) out.add(c.key!!)
                    else -> c.key?.let { out.add(it) }
                }
            }
            if (out.isNotEmpty()) return out
        }
        return emptyList()
    }

    fun observeUsers(): Flow<Map<String, UserProfile>> =
        observeNode("users").mapSnapshot(emptyMap()) { snap ->
            val map = mutableMapOf<String, UserProfile>()
            snap.children.forEach { c ->
                val uid = c.key ?: return@forEach
                val name = c.str("name", "displayName", "fullName", "username") ?: return@forEach
                map[uid] = UserProfile(
                    uid = uid,
                    name = name,
                    email = c.str("email") ?: "",
                    bio = c.str("bio", "about") ?: "",
                    location = c.str("location", "city") ?: "Addis Ababa, Ethiopia",
                    avatarColorHex = c.longVal("avatarColorHex") ?: pickColor(uid),
                    isOnline = c.boolVal("online", "isOnline") ?: false,
                    isVerified = c.boolVal("verified", "isVerified") ?: false,
                    isAdmin = c.boolVal("isAdmin") ?: false,
                    stars = (c.longVal("stars") ?: 0L).toInt(),
                    walletEtb = c.doubleVal("walletEtb") ?: 0.0
                )
            }
            map
        }

    fun observeFriendsAndFollowers(uid: String): Flow<Set<String>> =
        combine(
            observeNode("friends/$uid"),
            observeNode("followers/$uid"),
            observeNode("following/$uid"),
            observeNode("users/$uid/friends"),
            observeNode("users/$uid/followers"),
            observeNode("users/$uid/following")
        ) { snaps ->
            val ids = mutableSetOf<String>()
            snaps.filterNotNull().forEach { snap ->
                snap.children.forEach { c ->
                    when (val v = c.value) {
                        is String -> if (v.isNotBlank() && v != uid) ids.add(v)
                        is Boolean -> if (v && !c.key.isNullOrBlank() && c.key != uid) ids.add(c.key!!)
                        else -> if (!c.key.isNullOrBlank() && c.key != uid) ids.add(c.key!!)
                    }
                }
            }
            ids
        }

    fun observePosts(): Flow<List<PostItem>> =
        observeNode("posts").mapSnapshot(emptyList()) { snap ->
            val list = mutableListOf<PostItem>()
            snap.children.forEach { c ->
                val id = c.key ?: return@forEach
                val authorId = c.str("authorId", "uid", "userId", "authorUid") ?: return@forEach
                val authorName = c.str("authorName", "author", "name", "userName") ?: "Member"
                val text = c.str("text", "content", "body", "caption") ?: ""
                val privacyRaw = c.str("privacy", "visibility")?.lowercase()
                val privacy = when (privacyRaw) {
                    "friends", "friends_only" -> PostPrivacy.FRIENDS
                    "only_me", "onlyme", "private" -> PostPrivacy.ONLY_ME
                    else -> PostPrivacy.PUBLIC
                }
                val reactions = mutableMapOf<String, ReactionType>()
                c.child("reactions").children.forEach { r ->
                    val rUid = r.key ?: return@forEach
                    val rVal = r.value?.toString()?.trim() ?: return@forEach
                    ReactionType.entries.firstOrNull {
                        it.name.equals(rVal, ignoreCase = true) || it.emoji == rVal
                    }?.let { reactions[rUid] = it }
                }
                val comments = mutableListOf<PostComment>()
                c.child("comments").children.forEach { cm ->
                    val cid = cm.key ?: return@forEach
                    val cText = cm.str("text", "body", "message") ?: return@forEach
                    comments.add(
                        PostComment(
                            id = cid,
                            authorId = cm.str("authorId", "uid", "userId") ?: "",
                            authorName = cm.str("authorName", "author", "name") ?: "Member",
                            text = cText,
                            timestampMs = cm.longVal("timestampMs", "timestamp", "createdAt", "ts")
                                ?: System.currentTimeMillis()
                        )
                    )
                }
                list.add(
                    PostItem(
                        id = id,
                        authorId = authorId,
                        authorName = authorName,
                        text = text,
                        timestampMs = c.longVal("createdAt", "timestamp", "timestampMs", "ts")
                            ?: System.currentTimeMillis(),
                        privacy = privacy,
                        bgColorIndex = (c.longVal("bgColorIndex") ?: 0L).toInt(),
                        imageUri = c.str("imageUri", "imageUrl", "image", "photoUrl"),
                        groupId = c.str("groupId"),
                        reactions = reactions,
                        comments = comments,
                        shareCount = (c.longVal("shareCount", "shares") ?: 0L).toInt()
                    )
                )
            }
            list.sortedByDescending { it.timestampMs }
        }

    fun observeStories(): Flow<List<StoryItem>> =
        observeNode("stories").mapSnapshot(emptyList()) { snap ->
            snap.children.mapNotNull { c ->
                val id = c.key ?: return@mapNotNull null
                val authorId = c.str("authorId", "uid", "userId") ?: return@mapNotNull null
                StoryItem(
                    id = id,
                    authorId = authorId,
                    authorName = c.str("authorName", "author", "name") ?: "Member",
                    caption = c.str("caption", "text", "title") ?: "",
                    bgColorHex = c.longVal("bgColorHex") ?: 0xFFEE4C04L,
                    isLive = c.boolVal("live", "isLive") ?: false,
                    timestampMs = c.longVal("createdAt", "timestamp", "timestampMs") ?: System.currentTimeMillis()
                )
            }.sortedByDescending { it.timestampMs }
        }

    fun observeFriendRequests(uid: String): Flow<List<FriendRequest>> =
        combine(
            observeNode("friend_requests"),
            observeNode("friendRequests")
        ) { s1, s2 ->
            val out = mutableListOf<FriendRequest>()
            listOfNotNull(s1, s2).forEach { snap ->
                snap.children.forEach { c ->
                    val id = c.key ?: return@forEach
                    val fromUid = c.str("fromUid", "from", "senderId") ?: return@forEach
                    val toUid = c.str("toUid", "to", "receiverId") ?: return@forEach
                    if (fromUid == uid || toUid == uid) {
                        out.add(
                            FriendRequest(
                                id = id,
                                fromUid = fromUid,
                                toUid = toUid,
                                timestampMs = c.longVal("createdAt", "timestamp", "timestampMs")
                                    ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
            out.associateBy { "${it.fromUid}_${it.toUid}" }.values.sortedByDescending { it.timestampMs }
        }

    fun observeConversations(uid: String): Flow<List<Conversation>> =
        combine(
            observeNode("conversations"),
            observeNode("chats"),
            observeNode("messages")
        ) { convosSnap, chatsSnap, topMsgsSnap ->
            val map = linkedMapOf<String, Conversation>()

            fun parseConvoSnap(snap: DataSnapshot?) {
                snap?.children?.forEach { c ->
                    val id = c.key ?: return@forEach
                    val participants = c.strList("participantIds", "participants", "members", "users")
                        .ifEmpty { id.split("_").filter { it.isNotBlank() } }
                    if (uid !in participants) return@forEach
                    val msgs = parseMessagesChildren(c.child("messages"))
                    val lastText = c.str("lastMessage", "lastText", "preview")
                        ?: msgs.lastOrNull()?.text
                        ?: ""
                    val updated = c.longVal("updatedAt", "lastMessageAt", "createdAt", "timestamp")
                        ?: msgs.lastOrNull()?.timestampMs
                        ?: System.currentTimeMillis()
                    map[id] = Conversation(
                        id = id,
                        participantIds = participants,
                        messages = msgs,
                        lastMessage = lastText,
                        updatedAtMs = updated
                    )
                }
            }

            parseConvoSnap(convosSnap)
            parseConvoSnap(chatsSnap)

            // Also handle /messages/{convoId}/{msgId} or flat /messages/{msgId}
            topMsgsSnap?.children?.forEach { c ->
                val key = c.key ?: return@forEach
                val from = c.str("senderId", "from", "fromUid", "uid")
                val to = c.str("receiverId", "to", "toUid")
                if (from != null && to != null) {
                    if (from == uid || to == uid) {
                        val participants = listOf(from, to).sorted()
                        val cid = "${participants[0]}_${participants[1]}"
                        val msg = ChatMessage(
                            id = key,
                            senderId = from,
                            text = c.str("text", "message", "body", "content") ?: "",
                            timestampMs = c.longVal("createdAt", "timestamp", "timestampMs", "ts")
                                ?: System.currentTimeMillis(),
                            isEdited = c.boolVal("edited", "isEdited") ?: false
                        )
                        val existing = map[cid]
                        val allMsgs = ((existing?.messages ?: emptyList()) + msg)
                            .associateBy { it.id }.values.sortedBy { it.timestampMs }
                        map[cid] = Conversation(
                            id = cid,
                            participantIds = participants,
                            messages = allMsgs,
                            lastMessage = allMsgs.lastOrNull()?.text ?: "",
                            updatedAtMs = allMsgs.lastOrNull()?.timestampMs ?: msg.timestampMs
                        )
                    }
                } else {
                    // Nested /messages/{convoId}/{msgId}
                    val parts = key.split("_").filter { it.isNotBlank() }
                    if (uid in parts) {
                        val msgs = parseMessagesChildren(c)
                        if (msgs.isNotEmpty()) {
                            map[key] = Conversation(
                                id = key,
                                participantIds = parts,
                                messages = msgs,
                                lastMessage = msgs.last().text,
                                updatedAtMs = msgs.last().timestampMs
                            )
                        }
                    }
                }
            }

            map.values.sortedByDescending { it.updatedAtMs }
        }

    fun observeMessages(convoId: String): Flow<List<ChatMessage>> =
        combine(
            observeNode("conversations/$convoId/messages"),
            observeNode("chats/$convoId/messages"),
            observeNode("messages/$convoId")
        ) { s1, s2, s3 ->
            val all = mutableListOf<ChatMessage>()
            listOfNotNull(s1, s2, s3).forEach { all.addAll(parseMessagesChildren(it)) }
            all.associateBy { it.id }.values.sortedBy { it.timestampMs }
        }

    private fun parseMessagesChildren(node: DataSnapshot): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        node.children.forEach { m ->
            val mid = m.key ?: return@forEach
            val sender = m.str("senderId", "from", "fromUid", "uid") ?: return@forEach
            val text = m.str("text", "message", "body", "content") ?: ""
            list.add(
                ChatMessage(
                    id = mid,
                    senderId = sender,
                    text = text,
                    timestampMs = m.longVal("createdAt", "timestamp", "timestampMs", "ts")
                        ?: System.currentTimeMillis(),
                    isEdited = m.boolVal("edited", "isEdited") ?: false
                )
            )
        }
        return list.sortedBy { it.timestampMs }
    }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        combine(
            observeNode("notifications/$uid"),
            observeNode("notifications"),
            observeNode("users/$uid/notifications")
        ) { userNotifs, topNotifs, nestedNotifs ->
            val out = mutableListOf<NotificationItem>()
            fun parseNotif(c: DataSnapshot, requireRecipientMatch: Boolean) {
                val id = c.key ?: return
                val recipient = c.str("toUid", "userId", "recipientId", "to")
                if (requireRecipientMatch && recipient != uid) return
                val actorId = c.str("actorId", "fromUid", "from", "uid") ?: return
                val msg = c.str("messageEn", "message", "text", "body") ?: return
                out.add(
                    NotificationItem(
                        id = id,
                        actorId = actorId,
                        actorName = c.str("actorName", "fromName", "authorName", "name") ?: "Member",
                        type = c.str("type") ?: "info",
                        targetId = c.str("targetId", "postId"),
                        messageEn = msg,
                        messageAm = c.str("messageAm") ?: msg,
                        timestampMs = c.longVal("createdAt", "timestamp", "timestampMs", "ts")
                            ?: System.currentTimeMillis(),
                        isRead = c.boolVal("read", "isRead") ?: false
                    )
                )
            }
            userNotifs?.children?.forEach { parseNotif(it, requireRecipientMatch = false) }
            nestedNotifs?.children?.forEach { parseNotif(it, requireRecipientMatch = false) }
            topNotifs?.children?.forEach { parseNotif(it, requireRecipientMatch = true) }
            out.associateBy { it.id }.values.sortedByDescending { it.timestampMs }
        }

    fun observeGroups(): Flow<List<GroupItem>> =
        observeNode("groups").mapSnapshot(emptyList()) { snap ->
            snap.children.mapNotNull { c ->
                val id = c.key ?: return@mapNotNull null
                val name = c.str("name", "title") ?: return@mapNotNull null
                GroupItem(
                    id = id,
                    name = name,
                    description = c.str("description", "desc", "about") ?: "",
                    coverColorHex = c.longVal("coverColorHex") ?: pickColor(id),
                    memberIds = c.strList("memberIds", "members").toSet(),
                    creatorId = c.str("creatorId", "ownerId", "createdBy") ?: ""
                )
            }
        }

    fun observeListings(): Flow<List<MarketListing>> =
        combine(
            observeNode("listings"),
            observeNode("marketplace")
        ) { s1, s2 ->
            val out = mutableListOf<MarketListing>()
            listOfNotNull(s1, s2).forEach { snap ->
                snap.children.forEach { c ->
                    val id = c.key ?: return@forEach
                    val title = c.str("title", "name") ?: return@forEach
                    out.add(
                        MarketListing(
                            id = id,
                            sellerId = c.str("sellerId", "uid", "userId", "ownerId") ?: "",
                            sellerName = c.str("sellerName", "seller", "authorName") ?: "Seller",
                            title = title,
                            priceEtb = c.doubleVal("priceEtb", "price") ?: 0.0,
                            category = c.str("category") ?: "General",
                            city = c.str("city", "location") ?: "Addis Ababa",
                            description = c.str("description", "desc") ?: "",
                            timestampMs = c.longVal("createdAt", "timestamp", "timestampMs")
                                ?: System.currentTimeMillis()
                        )
                    )
                }
            }
            out.associateBy { it.id }.values.sortedByDescending { it.timestampMs }
        }

    fun observeAlbums(uid: String): Flow<List<PhotoAlbum>> =
        combine(
            observeNode("albums"),
            observeNode("users/$uid/albums")
        ) { top, nested ->
            val out = mutableListOf<PhotoAlbum>()
            nested?.children?.forEach { c ->
                val id = c.key ?: return@forEach
                val title = c.str("title", "name") ?: return@forEach
                out.add(PhotoAlbum(id = id, ownerId = uid, title = title, photoUris = c.strList("photoUris", "photos")))
            }
            top?.children?.forEach { c ->
                val id = c.key ?: return@forEach
                val owner = c.str("ownerId", "uid", "userId")
                if (owner != uid) return@forEach
                val title = c.str("title", "name") ?: return@forEach
                out.add(PhotoAlbum(id = id, ownerId = uid, title = title, photoUris = c.strList("photoUris", "photos")))
            }
            out.associateBy { it.id }.values.toList()
        }

    fun observeReels(currentUid: String): Flow<List<ReelItem>> =
        observeNode("reels").mapSnapshot(emptyList()) { snap ->
            snap.children.mapNotNull { c ->
                val id = c.key ?: return@mapNotNull null
                val caption = c.str("caption", "text", "title") ?: return@mapNotNull null
                val likedBy = c.strList("likedBy", "likes")
                ReelItem(
                    id = id,
                    authorId = c.str("authorId", "uid") ?: "",
                    authorName = c.str("authorName", "author", "name") ?: "Member",
                    caption = caption,
                    likes = likedBy.size,
                    isLiked = currentUid in likedBy,
                    comments = c.strList("comments")
                )
            }
        }

    fun observeLiveStreams(): Flow<List<LiveStreamItem>> =
        combine(
            observeNode("live_streams"),
            observeNode("lives"),
            observeNode("liveStreams"),
            observeNode("live")
        ) { s1, s2, s3, s4 ->
            val out = mutableListOf<LiveStreamItem>()
            listOfNotNull(s1, s2, s3, s4).forEach { snap ->
                snap.children.forEach { c ->
                    val id = c.key ?: return@forEach
                    val hostId = c.str("hostId", "authorId", "uid", "userId") ?: return@forEach
                    val status = c.str("status")?.lowercase()
                    val activeFlag = c.boolVal("isLive", "live", "active") ?: (status != "ended")
                    if (!activeFlag || status == "ended") return@forEach
                    val privacyRaw = c.str("privacy", "visibility")?.lowercase()
                    val privacy = when (privacyRaw) {
                        "friends", "friends_only" -> PostPrivacy.FRIENDS
                        "only_me", "onlyme", "private" -> PostPrivacy.ONLY_ME
                        else -> PostPrivacy.PUBLIC
                    }
                    out.add(
                        LiveStreamItem(
                            id = id,
                            hostId = hostId,
                            hostName = c.str("hostName", "authorName", "name", "displayName") ?: "Member",
                            title = c.str("title", "caption", "topic") ?: "Live on Meskot",
                            category = c.str("category", "topic") ?: "General",
                            privacy = privacy,
                            isLive = true,
                            viewerCount = (c.longVal("viewerCount", "viewers") ?: 1L).toInt().coerceAtLeast(1),
                            reactionsCount = (c.longVal("reactionsCount", "reactions", "likes") ?: 0L).toInt(),
                            starsEarned = (c.longVal("starsEarned", "stars") ?: 0L).toInt(),
                            startedAtMs = c.longVal("startedAt", "createdAt", "timestamp")
                                ?: System.currentTimeMillis(),
                            coverColorHex = c.longVal("coverColorHex", "bgColorHex") ?: 0xFFC93E03L
                        )
                    )
                }
            }
            out.associateBy { it.id }.values.sortedByDescending { it.startedAtMs }
        }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> =
        combine(
            observeNode("live_streams/$streamId/comments"),
            observeNode("lives/$streamId/comments"),
            observeNode("liveStreams/$streamId/comments")
        ) { s1, s2, s3 ->
            val out = mutableListOf<LiveComment>()
            listOfNotNull(s1, s2, s3).forEach { snap ->
                snap.children.forEach { c ->
                    val id = c.key ?: return@forEach
                    val text = c.str("text", "message", "body") ?: return@forEach
                    out.add(
                        LiveComment(
                            id = id,
                            authorId = c.str("authorId", "uid", "senderId") ?: "",
                            authorName = c.str("authorName", "senderName", "name") ?: "Viewer",
                            text = text,
                            isGift = c.boolVal("isGift", "gift") ?: false,
                            giftEmoji = c.str("giftEmoji", "emoji"),
                            timestampMs = c.longVal("timestamp", "createdAt", "ts")
                                ?: System.currentTimeMillis()
                        )
                    )
                }
            }
            out.associateBy { it.id }.values.sortedBy { it.timestampMs }
        }

    fun writeLiveStream(stream: LiveStreamItem) {
        runCatching {
            rtdb.reference.child("live_streams").child(stream.id).setValue(
                mapOf(
                    "hostId" to stream.hostId,
                    "hostName" to stream.hostName,
                    "title" to stream.title,
                    "category" to stream.category,
                    "privacy" to stream.privacy.name,
                    "isLive" to true,
                    "live" to true,
                    "status" to "live",
                    "viewerCount" to stream.viewerCount,
                    "reactionsCount" to stream.reactionsCount,
                    "starsEarned" to stream.starsEarned,
                    "startedAt" to stream.startedAtMs,
                    "coverColorHex" to stream.coverColorHex
                )
            )
        }
    }

    fun endLiveStream(streamId: String) {
        runCatching {
            rtdb.reference.child("live_streams").child(streamId).updateChildren(
                mapOf(
                    "isLive" to false,
                    "live" to false,
                    "status" to "ended",
                    "endedAt" to System.currentTimeMillis()
                )
            )
        }
    }

    fun pushLiveComment(streamId: String, comment: LiveComment) {
        runCatching {
            rtdb.reference.child("live_streams").child(streamId).child("comments").child(comment.id).setValue(
                mapOf(
                    "authorId" to comment.authorId,
                    "authorName" to comment.authorName,
                    "text" to comment.text,
                    "isGift" to comment.isGift,
                    "giftEmoji" to comment.giftEmoji,
                    "timestamp" to comment.timestampMs
                )
            )
        }
    }

    private fun <T> Flow<DataSnapshot?>.mapSnapshot(defaultValue: T, transform: (DataSnapshot) -> T): Flow<T> =
        kotlinx.coroutines.flow.flow {
            collect { snap ->
                emit(if (snap != null) transform(snap) else defaultValue)
            }
        }
}
