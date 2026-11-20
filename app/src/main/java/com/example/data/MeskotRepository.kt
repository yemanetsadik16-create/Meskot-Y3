package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class MeskotDataState(
    val currentUser: UserProfile? = null,
    val users: Map<String, UserProfile> = emptyMap(),
    val posts: List<PostItem> = emptyList(),
    val stories: List<StoryItem> = emptyList(),
    val friendIds: Set<String> = emptySet(),
    val friendRequests: List<FriendRequest> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val groups: List<GroupItem> = emptyList(),
    val listings: List<MarketListing> = emptyList(),
    val savedPostIds: Set<String> = emptySet(),
    val hiddenPostIds: Set<String> = emptySet(),
    val savedListingIds: Set<String> = emptySet(),
    val notifications: List<NotificationItem> = emptyList(),
    val albums: List<PhotoAlbum> = emptyList(),
    val reels: List<ReelItem> = emptyList(),
    val adCampaigns: List<AdCampaign> = emptyList(),
    val liveStreams: List<LiveStreamItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MeskotRepository(
    private val db: FirebaseFirestore,
    private val rtdbSource: RealtimeDbSource = RealtimeDbSource()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    )

    private val avatarPalette = listOf(
        0xFFC93E03L,
        0xFF8C2F39L,
        0xFF1F232BL,
        0xFF2D4373L,
        0xFF1B5E42L,
        0xFF6B3FA0L
    )

    private fun pickAvatarColor(uid: String): Long =
        avatarPalette[kotlin.math.abs(uid.hashCode()) % avatarPalette.size]

    private fun DocumentSnapshot.flexibleMillis(vararg fields: String): Long {
        for (field in fields) {
            val ts = try {
                getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } catch (_: Exception) {
                null
            }
            if (ts != null) return ts.toDate().time
            val raw = get(field)
            if (raw is Number) return raw.toLong()
            if (raw is Timestamp) return raw.toDate().time
        }
        return System.currentTimeMillis()
    }

    private fun DocumentSnapshot.firstString(vararg fields: String): String? {
        for (field in fields) {
            val v = get(field) as? String
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.firstStringList(vararg fields: String): List<String> {
        for (field in fields) {
            val v = get(field)
            if (v is List<*>) return v.mapNotNull { it as? String }
            if (v is Map<*, *>) return v.keys.mapNotNull { it as? String }
        }
        return emptyList()
    }

    private fun parsePrivacy(raw: String?): PostPrivacy {
        return when (raw?.trim()?.lowercase()) {
            "friends", "friends_only" -> PostPrivacy.FRIENDS
            "only_me", "onlyme", "private", "me" -> PostPrivacy.ONLY_ME
            else -> PostPrivacy.PUBLIC
        }
    }

    private fun parseReaction(raw: Any?): ReactionType? {
        val s = (raw as? String)?.trim() ?: return null
        return ReactionType.entries.firstOrNull {
            it.name.equals(s, ignoreCase = true) || it.emoji == s
        }
    }

    private fun parseUserDoc(doc: DocumentSnapshot): UserProfile? {
        val name = doc.firstString("name", "displayName", "fullName", "username")
            ?: return null
        val email = doc.firstString("email") ?: ""
        val bio = doc.firstString("bio", "about") ?: ""
        val location = doc.firstString("location", "city") ?: "Addis Ababa, Ethiopia"
        val avatarColor = (doc.get("avatarColorHex") as? Number)?.toLong() ?: pickAvatarColor(doc.id)
        val online = (doc.get("online") as? Boolean) ?: (doc.get("isOnline") as? Boolean) ?: false
        val verified = (doc.get("verified") as? Boolean) ?: (doc.get("isVerified") as? Boolean) ?: false
        val stars = (doc.get("stars") as? Number)?.toInt() ?: 0
        val walletEtb = (doc.get("walletEtb") as? Number)?.toDouble() ?: 0.0
        return UserProfile(
            uid = doc.id,
            name = name,
            email = email,
            bio = bio,
            location = location,
            avatarColorHex = avatarColor,
            isOnline = online,
            isVerified = verified,
            isAdmin = (doc.get("isAdmin") as? Boolean) ?: false,
            stars = stars,
            walletEtb = walletEtb
        )
    }

    // ==================== Profile Bootstrap ====================

    suspend fun ensureUserDocuments(
        uid: String,
        displayName: String,
        email: String,
        initialLocation: String = ""
    ): Result<Unit> = runCatching {
        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("public_profiles").document(uid)

        val userSnap = runCatching { userRef.get().await() }.getOrNull()
        val existingName = userSnap?.firstString("name", "displayName", "fullName")
        val safeName = (existingName ?: displayName).trim().ifBlank { "Meskot Member" }.take(100)
        val safeLoc = userSnap?.firstString("location", "city")
            ?: initialLocation.trim().ifBlank { "Addis Ababa, Ethiopia" }.take(120)

        if (userSnap == null || !userSnap.exists()) {
            val payload = mapOf(
                "name" to safeName,
                "displayName" to safeName,
                "email" to email.take(200),
                "bio" to "",
                "location" to safeLoc,
                "avatarColorHex" to pickAvatarColor(uid),
                "online" to true,
                "verified" to false,
                "stars" to 0,
                "walletEtb" to 0.0,
                "friendIds" to emptyList<String>(),
                "savedPostIds" to emptyList<String>(),
                "hiddenPostIds" to emptyList<String>(),
                "savedListingIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            runCatching { userRef.set(payload, SetOptions.merge()).await() }
        } else {
            runCatching {
                userRef.set(
                    mapOf(
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
        }

        // Also keep public_profiles in sync (best-effort in case backend rules only use /users)
        runCatching {
            val pubSnap = publicRef.get().await()
            if (!pubSnap.exists()) {
                publicRef.set(
                    mapOf(
                        "displayName" to safeName,
                        "name" to safeName,
                        "bio" to (userSnap?.firstString("bio", "about") ?: ""),
                        "location" to safeLoc,
                        "avatarColorHex" to pickAvatarColor(uid),
                        "online" to true,
                        "verified" to false,
                        "stars" to 0,
                        "visibility" to "PUBLIC",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            } else {
                publicRef.set(
                    mapOf("online" to true, "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                ).await()
            }
        }
    }

    suspend fun setOnlineStatus(uid: String, online: Boolean): Result<Unit> = runCatching {
        runCatching {
            db.collection("users").document(uid)
                .set(mapOf("online" to online, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        }
        runCatching {
            db.collection("public_profiles").document(uid)
                .set(mapOf("online" to online, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        }
    }

    suspend fun updateProfile(uid: String, name: String, bio: String, location: String): Result<Unit> = runCatching {
        val cleanName = name.trim().take(100)
        if (cleanName.isBlank()) throw IllegalArgumentException("Name cannot be blank.")
        val cleanBio = bio.trim().take(400)
        val cleanLoc = location.trim().ifBlank { "Addis Ababa, Ethiopia" }.take(120)
        val updates = mapOf(
            "name" to cleanName,
            "displayName" to cleanName,
            "bio" to cleanBio,
            "location" to cleanLoc,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("users").document(uid).set(updates, SetOptions.merge()).await()
            runCatching {
                db.collection("public_profiles").document(uid).set(updates, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        }
    }

    // ==================== Reactive Firestore Observers ====================

    fun observeAllUsers(): Flow<Map<String, UserProfile>> {
        val usersFlow = db.collection("users")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull(::parseUserDoc).associateBy { it.uid } }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w("MeskotRepo", "users collection listener failed: ${e.message}")
                emit(emptyMap())
            }

        val publicProfilesFlow = db.collection("public_profiles")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull(::parseUserDoc).associateBy { it.uid } }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w("MeskotRepo", "public_profiles listener failed: ${e.message}")
                emit(emptyMap())
            }

        return combine(publicProfilesFlow, usersFlow, rtdbSource.observeUsers()) { pub, usr, rtdbUsers ->
            val merged = rtdbUsers.toMutableMap()
            pub.forEach { (uid, profile) -> merged[uid] = profile }
            usr.forEach { (uid, profile) ->
                val existing = merged[uid]
                merged[uid] = if (existing == null) {
                    profile
                } else {
                    profile.copy(
                        name = profile.name.ifBlank { existing.name },
                        bio = profile.bio.ifBlank { existing.bio },
                        location = profile.location.ifBlank { existing.location }
                    )
                }
            }
            merged
        }
    }

    fun observePrivateProfile(uid: String): Flow<UserPrivateProfileDoc?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { doc ->
                if (!doc.exists()) {
                    null
                } else {
                    UserPrivateProfileDoc(
                        id = doc.id,
                        email = doc.firstString("email") ?: "",
                        walletEtb = (doc.get("walletEtb") as? Number)?.toDouble() ?: 0.0,
                        friendIds = doc.firstStringList("friendIds", "friends"),
                        savedPostIds = doc.firstStringList("savedPostIds", "savedPosts"),
                        hiddenPostIds = doc.firstStringList("hiddenPostIds", "hiddenPosts"),
                        savedListingIds = doc.firstStringList("savedListingIds", "savedListings")
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w("MeskotRepo", "private profile listener failed: ${e.message}")
                emit(null)
            }

    fun observePosts(): Flow<List<PostItem>> {
        val fsFlow = db.collection("posts")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull(::parsePostDoc) }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w("MeskotRepo", "posts listener failed: ${e.message}")
                emit(emptyList())
            }
        return combine(fsFlow, rtdbSource.observePosts()) { fs, rtdb ->
            (rtdb + fs).associateBy { it.id }.values.sortedByDescending { it.timestampMs }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePostDoc(doc: DocumentSnapshot): PostItem? {
        val authorId = doc.firstString("authorId", "uid", "userId", "authorUid") ?: return null
        val authorName = doc.firstString("authorName", "author", "name", "userName", "displayName") ?: "Member"
        val text = doc.firstString("text", "content", "body", "caption") ?: ""
        val privacy = parsePrivacy(doc.firstString("privacy", "visibility"))
        val bgColorIndex = (doc.get("bgColorIndex") as? Number)?.toInt() ?: 0
        val imageUri = doc.firstString("imageUri", "imageUrl", "image", "photoUrl", "mediaUrl")
        val groupId = doc.firstString("groupId")
        val shareCount = (doc.get("shareCount") as? Number)?.toInt()
            ?: (doc.get("shares") as? Number)?.toInt()
            ?: 0
        val timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs", "ts")

        val rawReactions = doc.get("reactions") as? Map<String, Any?> ?: emptyMap()
        val reactions = rawReactions.mapNotNull { (k, v) ->
            val rt = parseReaction(v)
            if (rt != null) k to rt else null
        }.toMap()

        val rawComments = doc.get("comments") as? List<Map<String, Any?>> ?: emptyList()
        val comments = rawComments.mapIndexedNotNull { idx, cm ->
            val cid = (cm["id"] as? String) ?: "c_${doc.id}_$idx"
            val cAuthorId = (cm["authorId"] as? String)
                ?: (cm["uid"] as? String)
                ?: (cm["userId"] as? String)
                ?: ""
            val cAuthorName = (cm["authorName"] as? String)
                ?: (cm["author"] as? String)
                ?: (cm["name"] as? String)
                ?: "Member"
            val cText = (cm["text"] as? String) ?: (cm["body"] as? String) ?: ""
            val cTs = (cm["timestampMs"] as? Number)?.toLong()
                ?: (cm["timestamp"] as? Number)?.toLong()
                ?: (cm["createdAt"] as? Timestamp)?.toDate()?.time
                ?: timestampMs
            val rawReplies = cm["replies"] as? List<Map<String, Any?>> ?: emptyList()
            val replies = rawReplies.mapIndexedNotNull { rIdx, rm ->
                val rid = (rm["id"] as? String) ?: "r_${cid}_$rIdx"
                CommentReply(
                    id = rid,
                    authorId = (rm["authorId"] as? String) ?: (rm["uid"] as? String) ?: "",
                    authorName = (rm["authorName"] as? String) ?: (rm["author"] as? String) ?: (rm["name"] as? String) ?: "Member",
                    text = (rm["text"] as? String) ?: (rm["body"] as? String) ?: "",
                    timestampMs = (rm["timestampMs"] as? Number)?.toLong()
                        ?: (rm["timestamp"] as? Number)?.toLong()
                        ?: (rm["createdAt"] as? Timestamp)?.toDate()?.time
                        ?: cTs
                )
            }
            PostComment(
                id = cid,
                authorId = cAuthorId,
                authorName = cAuthorName,
                text = cText,
                timestampMs = cTs,
                replies = replies
            )
        }

        val sharedOriginalId = doc.firstString("sharedPostOriginalId", "sharedFromId")
        val sharedRef = if (!sharedOriginalId.isNullOrBlank()) {
            SharedPostRef(
                originalPostId = sharedOriginalId,
                authorName = doc.firstString("sharedPostAuthor") ?: "",
                text = doc.firstString("sharedPostText") ?: ""
            )
        } else null

        return PostItem(
            id = doc.id,
            authorId = authorId,
            authorName = authorName,
            text = text,
            timestampMs = timestampMs,
            privacy = privacy,
            bgColorIndex = bgColorIndex,
            imageRes = null,
            imageUri = imageUri,
            groupId = groupId,
            reactions = reactions,
            comments = comments,
            sharedPost = sharedRef,
            shareCount = shareCount
        )
    }

    fun observeStories(): Flow<List<StoryItem>> {
        val fsFlow = db.collection("stories")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val authorId = doc.firstString("authorId", "uid", "userId") ?: return@mapNotNull null
                    StoryItem(
                        id = doc.id,
                        authorId = authorId,
                        authorName = doc.firstString("authorName", "author", "name") ?: "Member",
                        caption = doc.firstString("caption", "text", "title") ?: "",
                        imageRes = null,
                        bgColorHex = (doc.get("bgColorHex") as? Number)?.toLong() ?: 0xFFEE4C04L,
                        isLive = (doc.get("live") as? Boolean) ?: (doc.get("isLive") as? Boolean) ?: false,
                        timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs")
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w("MeskotRepo", "stories listener failed: ${e.message}")
                emit(emptyList())
            }
        return combine(fsFlow, rtdbSource.observeStories()) { fs, rtdb ->
            (rtdb + fs).associateBy { it.id }.values.sortedByDescending { it.timestampMs }
        }
    }

    fun observeFriendsAndFollowers(uid: String): Flow<Set<String>> {
        val flows = listOf(
            db.collection("users").document(uid).collection("friends").snapshots()
                .map { s -> s.documents.map { it.id }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("users").document(uid).collection("followers").snapshots()
                .map { s -> s.documents.map { it.id }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("users").document(uid).collection("following").snapshots()
                .map { s -> s.documents.map { it.id }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("followers").whereEqualTo("followerId", uid).snapshots()
                .map { s -> s.documents.mapNotNull { it.firstString("followingId", "targetId", "toUid", "to") }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("followers").whereEqualTo("followingId", uid).snapshots()
                .map { s -> s.documents.mapNotNull { it.firstString("followerId", "fromUid", "from", "uid") }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("follows").whereEqualTo("followerId", uid).snapshots()
                .map { s -> s.documents.mapNotNull { it.firstString("followingId", "targetId", "toUid", "to") }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("follows").whereEqualTo("followingId", uid).snapshots()
                .map { s -> s.documents.mapNotNull { it.firstString("followerId", "fromUid", "from", "uid") }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("friends").whereArrayContains("users", uid).snapshots()
                .map { s -> s.documents.flatMap { it.firstStringList("users") }.filter { it != uid }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            db.collection("friendships").whereArrayContains("users", uid).snapshots()
                .map { s -> s.documents.flatMap { it.firstStringList("users") }.filter { it != uid }.toSet() }
                .catch { e -> if (e is CancellationException) throw e; emit(emptySet()) },
            rtdbSource.observeFriendsAndFollowers(uid)
        )
        return combine(flows) { sets ->
            val all = mutableSetOf<String>()
            sets.forEach { all.addAll(it) }
            all.remove(uid)
            all
        }
    }

    fun observeFriendRequests(uid: String): Flow<List<FriendRequest>> {
        fun parseList(docs: List<DocumentSnapshot>): List<FriendRequest> =
            docs.mapNotNull { doc ->
                val fromUid = doc.firstString("fromUid", "from", "senderId", "uid") ?: return@mapNotNull null
                val toUid = doc.firstString("toUid", "to", "receiverId", "targetUid") ?: return@mapNotNull null
                if (fromUid != uid && toUid != uid) return@mapNotNull null
                FriendRequest(
                    id = doc.id,
                    fromUid = fromUid,
                    toUid = toUid,
                    timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs", "ts")
                )
            }

        fun safeQuery(query: com.google.firebase.firestore.Query): Flow<List<FriendRequest>> =
            query.snapshots()
                .map { parseList(it.documents) }
                .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        val flows = listOf(
            safeQuery(db.collection("friend_requests").whereArrayContains("participantUids", uid)),
            safeQuery(db.collection("friend_requests").whereEqualTo("toUid", uid)),
            safeQuery(db.collection("friend_requests").whereEqualTo("fromUid", uid)),
            safeQuery(db.collection("friend_requests").whereEqualTo("to", uid)),
            safeQuery(db.collection("friend_requests").whereEqualTo("from", uid)),
            safeQuery(db.collection("friendRequests").whereEqualTo("toUid", uid)),
            safeQuery(db.collection("friendRequests").whereEqualTo("fromUid", uid)),
            safeQuery(db.collection("friendRequests").whereEqualTo("to", uid)),
            safeQuery(db.collection("friendRequests").whereEqualTo("from", uid)),
            rtdbSource.observeFriendRequests(uid)
        )

        return combine(flows) { lists ->
            lists.flatMap { it }
                .associateBy { "${it.fromUid}_${it.toUid}" }
                .values
                .sortedByDescending { it.timestampMs }
        }
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> {
        fun parseConvo(doc: DocumentSnapshot): Conversation? {
            val participants = doc.firstStringList("participantIds", "participants", "members", "users", "uids", "participantUids")
                .ifEmpty {
                    listOfNotNull(
                        doc.firstString("user1", "from", "fromUid", "senderId"),
                        doc.firstString("user2", "to", "toUid", "receiverId")
                    )
                }
                .ifEmpty { doc.id.split("_").filter { it.isNotBlank() } }
            if (uid !in participants) return null
            return Conversation(
                id = doc.id,
                participantIds = participants,
                messages = emptyList(),
                lastMessage = doc.firstString("lastMessage", "lastText", "preview", "text") ?: "",
                updatedAtMs = doc.flexibleMillis("updatedAt", "lastMessageAt", "createdAt", "timestamp", "ts"),
                unreadCount = (doc.get("unreadCount") as? Number)?.toInt() ?: 0
            )
        }

        fun safeConvoQuery(query: com.google.firebase.firestore.Query): Flow<List<Conversation>> =
            query.snapshots()
                .map { it.documents.mapNotNull(::parseConvo) }
                .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        fun safeTopMsgQuery(query: com.google.firebase.firestore.Query): Flow<List<Conversation>> =
            query.snapshots()
                .map { snap ->
                    val grouped = linkedMapOf<String, MutableList<ChatMessage>>()
                    val partMap = linkedMapOf<String, List<String>>()
                    snap.documents.forEach { doc ->
                        val from = doc.firstString("senderId", "from", "fromUid", "uid") ?: return@forEach
                        val to = doc.firstString("receiverId", "to", "toUid") ?: return@forEach
                        if (from != uid && to != uid) return@forEach
                        val parts = listOf(from, to).sorted()
                        val cid = doc.firstString("conversationId", "chatId", "convoId") ?: "${parts[0]}_${parts[1]}"
                        partMap[cid] = parts
                        grouped.getOrPut(cid) { mutableListOf() }.add(
                            ChatMessage(
                                id = doc.id,
                                senderId = from,
                                text = doc.firstString("text", "message", "body", "content") ?: "",
                                timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs", "ts"),
                                isEdited = (doc.get("edited") as? Boolean) ?: (doc.get("isEdited") as? Boolean) ?: false
                            )
                        )
                    }
                    grouped.map { (cid, msgs) ->
                        val sorted = msgs.sortedBy { it.timestampMs }
                        Conversation(
                            id = cid,
                            participantIds = partMap[cid] ?: emptyList(),
                            messages = sorted,
                            lastMessage = sorted.lastOrNull()?.text ?: "",
                            updatedAtMs = sorted.lastOrNull()?.timestampMs ?: 0L
                        )
                    }
                }
                .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        val flows = listOf(
            safeConvoQuery(db.collection("conversations").whereArrayContains("participantIds", uid)),
            safeConvoQuery(db.collection("conversations").whereArrayContains("participants", uid)),
            safeConvoQuery(db.collection("conversations").whereArrayContains("members", uid)),
            safeConvoQuery(db.collection("conversations").whereArrayContains("users", uid)),
            safeConvoQuery(db.collection("chats").whereArrayContains("participantIds", uid)),
            safeConvoQuery(db.collection("chats").whereArrayContains("participants", uid)),
            safeConvoQuery(db.collection("chats").whereArrayContains("members", uid)),
            safeConvoQuery(db.collection("chats").whereArrayContains("users", uid)),
            safeTopMsgQuery(db.collection("messages").whereEqualTo("from", uid)),
            safeTopMsgQuery(db.collection("messages").whereEqualTo("to", uid)),
            safeTopMsgQuery(db.collection("messages").whereEqualTo("senderId", uid)),
            safeTopMsgQuery(db.collection("messages").whereEqualTo("receiverId", uid)),
            rtdbSource.observeConversations(uid)
        )

        return combine(flows) { lists ->
            val merged = linkedMapOf<String, Conversation>()
            lists.flatMap { it }.forEach { convo ->
                val existing = merged[convo.id]
                merged[convo.id] = if (existing == null) {
                    convo
                } else {
                    val combinedMsgs = (existing.messages + convo.messages)
                        .associateBy { it.id }.values.sortedBy { it.timestampMs }
                    convo.copy(
                        messages = combinedMsgs,
                        lastMessage = convo.lastMessage.ifBlank { existing.lastMessage },
                        updatedAtMs = maxOf(convo.updatedAtMs, existing.updatedAtMs)
                    )
                }
            }
            merged.values.sortedByDescending { it.updatedAtMs }
        }
    }

    fun observeMessages(convoId: String): Flow<List<ChatMessage>> {
        fun parseMsg(doc: DocumentSnapshot): ChatMessage? {
            val senderId = doc.firstString("senderId", "from", "fromUid", "uid") ?: return null
            val text = doc.firstString("text", "message", "body", "content") ?: ""
            return ChatMessage(
                id = doc.id,
                senderId = senderId,
                text = text,
                timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs", "ts"),
                isEdited = (doc.get("edited") as? Boolean) ?: (doc.get("isEdited") as? Boolean) ?: false
            )
        }

        fun safeMsgQuery(query: com.google.firebase.firestore.Query): Flow<List<ChatMessage>> =
            query.snapshots()
                .map { it.documents.mapNotNull(::parseMsg) }
                .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        val flows = listOf(
            safeMsgQuery(db.collection("conversations").document(convoId).collection("messages")),
            safeMsgQuery(db.collection("chats").document(convoId).collection("messages")),
            safeMsgQuery(db.collection("messages").whereEqualTo("conversationId", convoId)),
            safeMsgQuery(db.collection("messages").whereEqualTo("chatId", convoId)),
            safeMsgQuery(db.collection("messages").whereEqualTo("convoId", convoId)),
            rtdbSource.observeMessages(convoId)
        )

        return combine(flows) { lists ->
            lists.flatMap { it }.associateBy { it.id }.values.sortedBy { it.timestampMs }
        }
    }

    fun observeGroups(): Flow<List<GroupItem>> {
        val fsFlow = db.collection("groups")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val name = doc.firstString("name", "title") ?: return@mapNotNull null
                    GroupItem(
                        id = doc.id,
                        name = name,
                        description = doc.firstString("description", "desc", "about") ?: "",
                        coverColorHex = (doc.get("coverColorHex") as? Number)?.toLong() ?: pickAvatarColor(doc.id),
                        memberIds = doc.firstStringList("memberIds", "members").toSet(),
                        creatorId = doc.firstString("creatorId", "ownerId", "createdBy") ?: ""
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                Log.w("MeskotRepo", "groups listener failed: ${e.message}")
                emit(emptyList())
            }
        return combine(fsFlow, rtdbSource.observeGroups()) { fs, rtdb ->
            (rtdb + fs).associateBy { it.id }.values.toList()
        }
    }

    fun observeListings(): Flow<List<MarketListing>> {
        fun parseListing(doc: DocumentSnapshot): MarketListing? {
            val title = doc.firstString("title", "name") ?: return null
            return MarketListing(
                id = doc.id,
                sellerId = doc.firstString("sellerId", "uid", "userId", "ownerId") ?: "",
                sellerName = doc.firstString("sellerName", "seller", "authorName", "name") ?: "Seller",
                title = title,
                priceEtb = (doc.get("priceEtb") as? Number)?.toDouble()
                    ?: (doc.get("price") as? Number)?.toDouble()
                    ?: 0.0,
                category = doc.firstString("category") ?: "General",
                city = doc.firstString("city", "location") ?: "Addis Ababa",
                description = doc.firstString("description", "desc") ?: "",
                imageRes = null,
                timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs")
            )
        }

        val listingsFlow = db.collection("listings").snapshots()
            .map { it.documents.mapNotNull(::parseListing) }
            .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        val marketFlow = db.collection("marketplace").snapshots()
            .map { it.documents.mapNotNull(::parseListing) }
            .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        return combine(listingsFlow, marketFlow, rtdbSource.observeListings()) { a, b, c ->
            (c + b + a).associateBy { it.id }.values.sortedByDescending { it.timestampMs }
        }
    }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> {
        fun parseNotif(doc: DocumentSnapshot): NotificationItem? {
            val type = doc.firstString("type") ?: "info"
            val msg = doc.firstString("messageEn", "message", "text", "body", "title", "content")
                ?: when (type.lowercase()) {
                    "like", "react" -> "reacted to your post"
                    "comment" -> "commented on your post"
                    "friend_req", "friend_request" -> "sent you a friend request"
                    "friend_accept" -> "accepted your friend request"
                    "follow" -> "started following you"
                    "message" -> "sent you a message"
                    else -> "interacted with you"
                }
            return NotificationItem(
                id = doc.id,
                actorId = doc.firstString("actorId", "fromUid", "from", "senderId") ?: "",
                actorName = doc.firstString("actorName", "fromName", "authorName", "senderName", "name") ?: "Member",
                type = type,
                targetId = doc.firstString("targetId", "postId", "convoId"),
                messageEn = msg,
                messageAm = doc.firstString("messageAm") ?: msg,
                timestampMs = doc.flexibleMillis("createdAt", "timestamp", "timestampMs", "ts"),
                isRead = (doc.get("read") as? Boolean) ?: (doc.get("isRead") as? Boolean) ?: false
            )
        }

        fun safeNotifQuery(query: com.google.firebase.firestore.Query): Flow<List<NotificationItem>> =
            query.snapshots()
                .map { snap -> snap.documents.mapNotNull(::parseNotif) }
                .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        val flows = listOf(
            safeNotifQuery(db.collection("users").document(uid).collection("notifications")),
            safeNotifQuery(db.collection("notifications").whereEqualTo("toUid", uid)),
            safeNotifQuery(db.collection("notifications").whereEqualTo("to", uid)),
            safeNotifQuery(db.collection("notifications").whereEqualTo("userId", uid)),
            safeNotifQuery(db.collection("notifications").whereEqualTo("uid", uid)),
            safeNotifQuery(db.collection("notifications").whereEqualTo("recipientId", uid)),
            rtdbSource.observeNotifications(uid)
        )

        return combine(flows) { lists ->
            lists.flatMap { it }.associateBy { it.id }.values.sortedByDescending { it.timestampMs }
        }
    }

    fun observeAlbums(uid: String): Flow<List<PhotoAlbum>> {
        val nestedFlow = db.collection("users").document(uid)
            .collection("albums")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val title = doc.firstString("title", "name") ?: return@mapNotNull null
                    PhotoAlbum(
                        id = doc.id,
                        ownerId = doc.firstString("ownerId", "uid") ?: uid,
                        title = title,
                        photos = emptyList(),
                        photoUris = doc.firstStringList("photoUris", "photos")
                    )
                }
            }
            .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        val topFlow = db.collection("albums")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val owner = doc.firstString("ownerId", "uid", "userId")
                    if (owner != uid) return@mapNotNull null
                    val title = doc.firstString("title", "name") ?: return@mapNotNull null
                    PhotoAlbum(
                        id = doc.id,
                        ownerId = uid,
                        title = title,
                        photos = emptyList(),
                        photoUris = doc.firstStringList("photoUris", "photos")
                    )
                }
            }
            .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        return combine(nestedFlow, topFlow, rtdbSource.observeAlbums(uid)) { a, b, c ->
            (c + b + a).associateBy { it.id }.values.toList()
        }
    }

    fun observeReels(currentUid: String): Flow<List<ReelItem>> {
        val fsFlow = db.collection("reels")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val caption = doc.firstString("caption", "text", "title") ?: return@mapNotNull null
                    val likedBy = doc.firstStringList("likedBy", "likes")
                    ReelItem(
                        id = doc.id,
                        authorId = doc.firstString("authorId", "uid") ?: "",
                        authorName = doc.firstString("authorName", "author", "name") ?: "Member",
                        caption = caption,
                        coverRes = null,
                        likes = likedBy.size,
                        isLiked = currentUid in likedBy,
                        comments = doc.firstStringList("comments")
                    )
                }
            }
            .catch { e -> if (e is CancellationException) throw e; emit(emptyList()) }

        return combine(fsFlow, rtdbSource.observeReels(currentUid)) { fs, rtdb ->
            (rtdb + fs).associateBy { it.id }.values.toList()
        }
    }

    fun observeAdCampaigns(uid: String): Flow<List<AdCampaign>> =
        db.collection("users").document(uid)
            .collection("ad_campaigns")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val title = doc.firstString("title", "name") ?: return@mapNotNull null
                    AdCampaign(
                        id = doc.id,
                        title = title,
                        objective = doc.firstString("objective") ?: "Engagement",
                        budgetEtb = (doc.get("budgetEtb") as? Number)?.toDouble() ?: 0.0,
                        days = (doc.get("days") as? Number)?.toInt() ?: 7,
                        impressions = (doc.get("impressions") as? Number)?.toInt() ?: 0,
                        clicks = (doc.get("clicks") as? Number)?.toInt() ?: 0,
                        active = (doc.get("active") as? Boolean) ?: true
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(emptyList())
            }

    // ==================== Mutations ====================

    suspend fun createPost(
        authorId: String,
        authorName: String,
        text: String,
        privacy: PostPrivacy = PostPrivacy.PUBLIC,
        bgColorIndex: Int = 0,
        imageUri: String? = null,
        groupId: String? = null
    ): Result<String> = runCatching {
        val docRef = db.collection("posts").document()
        val payload = mapOf<String, Any?>(
            "authorId" to authorId,
            "uid" to authorId,
            "authorName" to authorName.ifBlank { "Meskot Member" }.take(100),
            "text" to text.trim().take(4000),
            "privacy" to privacy.name,
            "bgColorIndex" to bgColorIndex.coerceIn(0, 10),
            "imageUri" to imageUri?.take(2000),
            "groupId" to groupId?.take(128),
            "reactions" to emptyMap<String, String>(),
            "comments" to emptyList<Map<String, Any>>(),
            "shareCount" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "posts/${docRef.id}")
        }
        docRef.id
    }

    suspend fun editPost(postId: String, newText: String): Result<Unit> = runCatching {
        try {
            db.collection("posts").document(postId)
                .update(
                    mapOf(
                        "text" to newText.trim().take(4000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "posts/$postId")
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        try {
            db.collection("posts").document(postId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "posts/$postId")
        }
    }

    suspend fun toggleReaction(post: PostItem, uid: String, reaction: ReactionType): Result<Unit> = runCatching {
        val updated = post.reactions.toMutableMap()
        if (updated[uid] == reaction) updated.remove(uid) else updated[uid] = reaction
        val rawMap = updated.mapValues { it.value.name }
        try {
            db.collection("posts").document(post.id)
                .update(
                    mapOf(
                        "reactions" to rawMap,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "posts/${post.id}")
        }
    }

    suspend fun addComment(post: PostItem, authorId: String, authorName: String, text: String): Result<Unit> = runCatching {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@runCatching
        val newComment = PostComment(
            id = "c_${UUID.randomUUID().toString().take(8)}",
            authorId = authorId,
            authorName = authorName.ifBlank { "Meskot Member" },
            text = cleanText,
            timestampMs = System.currentTimeMillis()
        )
        val updatedComments = (post.comments + newComment).map(::commentToMap)
        try {
            db.collection("posts").document(post.id)
                .update(
                    mapOf(
                        "comments" to updatedComments,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "posts/${post.id}")
        }
    }

    suspend fun addReply(
        post: PostItem,
        commentId: String,
        authorId: String,
        authorName: String,
        text: String
    ): Result<Unit> = runCatching {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@runCatching
        val newReply = CommentReply(
            id = "r_${UUID.randomUUID().toString().take(8)}",
            authorId = authorId,
            authorName = authorName.ifBlank { "Meskot Member" },
            text = cleanText,
            timestampMs = System.currentTimeMillis()
        )
        val updatedComments = post.comments.map { c ->
            if (c.id == commentId) c.copy(replies = c.replies + newReply) else c
        }.map(::commentToMap)
        try {
            db.collection("posts").document(post.id)
                .update(
                    mapOf(
                        "comments" to updatedComments,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "posts/${post.id}")
        }
    }

    private fun commentToMap(c: PostComment): Map<String, Any> = mapOf(
        "id" to c.id,
        "authorId" to c.authorId,
        "authorName" to c.authorName,
        "text" to c.text,
        "timestampMs" to c.timestampMs,
        "replies" to c.replies.map { r ->
            mapOf(
                "id" to r.id,
                "authorId" to r.authorId,
                "authorName" to r.authorName,
                "text" to r.text,
                "timestampMs" to r.timestampMs
            )
        }
    )

    suspend fun sharePost(authorId: String, authorName: String, original: PostItem): Result<Unit> = runCatching {
        val docRef = db.collection("posts").document()
        val payload = mapOf(
            "authorId" to authorId,
            "uid" to authorId,
            "authorName" to authorName.ifBlank { "Meskot Member" }.take(100),
            "text" to "",
            "privacy" to PostPrivacy.PUBLIC.name,
            "bgColorIndex" to 0,
            "reactions" to emptyMap<String, String>(),
            "comments" to emptyList<Map<String, Any>>(),
            "sharedPostOriginalId" to original.id,
            "sharedPostAuthor" to original.authorName.take(100),
            "sharedPostText" to original.text.take(4000),
            "shareCount" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            if (original.privacy != PostPrivacy.ONLY_ME) {
                db.collection("posts").document(original.id)
                    .update(
                        mapOf(
                            "shareCount" to (original.shareCount + 1),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "posts/${docRef.id}")
        }
    }

    suspend fun toggleSavePost(uid: String, currentSaved: Set<String>, postId: String): Result<Unit> = runCatching {
        val next = if (postId in currentSaved) currentSaved - postId else currentSaved + postId
        try {
            db.collection("users").document(uid)
                .set(
                    mapOf(
                        "savedPostIds" to next.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        }
    }

    suspend fun hidePost(uid: String, currentHidden: Set<String>, postId: String): Result<Unit> = runCatching {
        val next = currentHidden + postId
        try {
            db.collection("users").document(uid)
                .set(
                    mapOf(
                        "hiddenPostIds" to next.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        }
    }

    suspend fun addStory(
        authorId: String,
        authorName: String,
        caption: String,
        bgColorHex: Long
    ): Result<Unit> = runCatching {
        val clean = caption.trim().take(300)
        if (clean.isBlank()) return@runCatching
        val docRef = db.collection("stories").document()
        val payload = mapOf(
            "authorId" to authorId,
            "uid" to authorId,
            "authorName" to authorName.ifBlank { "Meskot Member" }.take(100),
            "caption" to clean,
            "bgColorHex" to bgColorHex,
            "live" to false,
            "visibility" to "PUBLIC",
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "stories/${docRef.id}")
        }
    }

    suspend fun sendFriendRequest(fromUid: String, fromName: String, toUid: String): Result<Unit> = runCatching {
        if (fromUid == toUid) return@runCatching
        val reqId = "${fromUid}_${toUid}"
        val payload = mapOf(
            "fromUid" to fromUid,
            "toUid" to toUid,
            "participantUids" to listOf(fromUid, toUid),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("friend_requests").document(reqId).set(payload).await()
            runCatching {
                val notifRef = db.collection("users").document(toUid).collection("notifications").document()
                notifRef.set(
                    mapOf(
                        "actorId" to fromUid,
                        "actorName" to fromName.ifBlank { "Meskot Member" }.take(100),
                        "type" to "friend_req",
                        "targetId" to fromUid,
                        "messageEn" to "sent you a friend request",
                        "messageAm" to "የጓደኝነት ጥያቄ ላከልዎት",
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "friend_requests/$reqId")
        }
    }

    suspend fun cancelFriendRequest(fromUid: String, toUid: String): Result<Unit> = runCatching {
        val reqId = "${fromUid}_${toUid}"
        runCatching { db.collection("friend_requests").document(reqId).delete().await() }
        runCatching { db.collection("friendRequests").document(reqId).delete().await() }
    }

    suspend fun acceptFriendRequest(myUid: String, myFriends: Set<String>, fromUid: String): Result<Unit> = runCatching {
        val reqId = "${fromUid}_${myUid}"
        val updatedFriends = (myFriends + fromUid).toList()
        runCatching { db.collection("friend_requests").document(reqId).delete().await() }
        runCatching { db.collection("friendRequests").document(reqId).delete().await() }
        try {
            db.collection("users").document(myUid)
                .set(
                    mapOf(
                        "friendIds" to updatedFriends,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "users/$myUid")
        }
    }

    suspend fun declineFriendRequest(myUid: String, fromUid: String): Result<Unit> = runCatching {
        val reqId = "${fromUid}_${myUid}"
        runCatching { db.collection("friend_requests").document(reqId).delete().await() }
        runCatching { db.collection("friendRequests").document(reqId).delete().await() }
    }

    suspend fun unfriend(myUid: String, myFriends: Set<String>, targetUid: String): Result<Unit> = runCatching {
        val updated = (myFriends - targetUid).toList()
        try {
            db.collection("users").document(myUid)
                .set(
                    mapOf(
                        "friendIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/$myUid")
        }
    }

    suspend fun getOrCreateConversation(myUid: String, otherUid: String): Result<String> = runCatching {
        val participants = listOf(myUid, otherUid).sorted()
        val convoId = "${participants[0]}_${participants[1]}"
        val ref = db.collection("conversations").document(convoId)
        try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "participantIds" to participants,
                        "participants" to participants,
                        "lastMessage" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "conversations/$convoId")
        }
        convoId
    }

    suspend fun sendMessage(
        convoId: String,
        participantIds: List<String>,
        senderId: String,
        text: String
    ): Result<Unit> = runCatching {
        val clean = text.trim().take(2000)
        if (clean.isBlank()) return@runCatching
        val msgRef = db.collection("conversations").document(convoId).collection("messages").document()
        try {
            msgRef.set(
                mapOf(
                    "senderId" to senderId,
                    "participantIds" to participantIds,
                    "text" to clean,
                    "edited" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("conversations").document(convoId)
                .set(
                    mapOf(
                        "participantIds" to participantIds,
                        "participants" to participantIds,
                        "lastMessage" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "conversations/$convoId/messages/${msgRef.id}")
        }
    }

    suspend fun editMessage(convoId: String, msgId: String, newText: String): Result<Unit> = runCatching {
        val clean = newText.trim().take(2000)
        if (clean.isBlank()) return@runCatching
        try {
            db.collection("conversations").document(convoId)
                .collection("messages").document(msgId)
                .update(
                    mapOf(
                        "text" to clean,
                        "edited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$convoId/messages/$msgId")
        }
    }

    suspend fun deleteMessage(convoId: String, msgId: String): Result<Unit> = runCatching {
        try {
            db.collection("conversations").document(convoId)
                .collection("messages").document(msgId)
                .delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "conversations/$convoId/messages/$msgId")
        }
    }

    suspend fun createGroup(creatorId: String, name: String, description: String): Result<Unit> = runCatching {
        val cleanName = name.trim().take(120)
        if (cleanName.isBlank()) return@runCatching
        val docRef = db.collection("groups").document()
        try {
            docRef.set(
                mapOf(
                    "name" to cleanName,
                    "description" to description.trim().take(600),
                    "coverColorHex" to pickAvatarColor(docRef.id),
                    "creatorId" to creatorId,
                    "memberIds" to listOf(creatorId),
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "groups/${docRef.id}")
        }
    }

    suspend fun toggleGroupMembership(group: GroupItem, uid: String): Result<Unit> = runCatching {
        val updated = if (uid in group.memberIds) group.memberIds - uid else group.memberIds + uid
        try {
            db.collection("groups").document(group.id)
                .update(
                    mapOf(
                        "memberIds" to updated.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}")
        }
    }

    suspend fun createListing(
        sellerId: String,
        sellerName: String,
        title: String,
        priceEtb: Double,
        category: String,
        city: String,
        description: String
    ): Result<Unit> = runCatching {
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.isBlank()) return@runCatching
        val docRef = db.collection("listings").document()
        try {
            docRef.set(
                mapOf(
                    "sellerId" to sellerId,
                    "sellerName" to sellerName.ifBlank { "Meskot Member" }.take(100),
                    "title" to cleanTitle,
                    "priceEtb" to priceEtb.coerceAtLeast(0.0),
                    "category" to category.trim().ifBlank { "General" }.take(60),
                    "city" to city.trim().ifBlank { "Addis Ababa" }.take(80),
                    "description" to description.trim().take(2000),
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "listings/${docRef.id}")
        }
    }

    suspend fun toggleSaveListing(uid: String, currentSaved: Set<String>, listingId: String): Result<Unit> = runCatching {
        val next = if (listingId in currentSaved) currentSaved - listingId else currentSaved + listingId
        try {
            db.collection("users").document(uid)
                .set(
                    mapOf(
                        "savedListingIds" to next.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        }
    }

    suspend fun markAllNotificationsRead(uid: String, unreadIds: List<String>): Result<Unit> = runCatching {
        if (unreadIds.isEmpty()) return@runCatching
        try {
            val batch = db.batch()
            unreadIds.forEach { nid ->
                val ref = db.collection("users").document(uid).collection("notifications").document(nid)
                batch.update(ref, "read", true)
            }
            batch.commit().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications")
        }
    }

    suspend fun createAlbum(ownerId: String, title: String): Result<Unit> = runCatching {
        val clean = title.trim().take(120)
        if (clean.isBlank()) return@runCatching
        val docRef = db.collection("users").document(ownerId).collection("albums").document()
        try {
            docRef.set(
                mapOf(
                    "ownerId" to ownerId,
                    "title" to clean,
                    "photoUris" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "users/$ownerId/albums/${docRef.id}")
        }
    }

    suspend fun createReel(authorId: String, authorName: String, caption: String): Result<Unit> = runCatching {
        val clean = caption.trim().take(400)
        if (clean.isBlank()) return@runCatching
        val docRef = db.collection("reels").document()
        try {
            docRef.set(
                mapOf(
                    "authorId" to authorId,
                    "authorName" to authorName.ifBlank { "Meskot Member" }.take(100),
                    "caption" to clean,
                    "likedBy" to emptyList<String>(),
                    "comments" to emptyList<String>(),
                    "visibility" to "PUBLIC",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "reels/${docRef.id}")
        }
    }

    suspend fun toggleReelLike(reelId: String, uid: String, isCurrentlyLiked: Boolean): Result<Unit> = runCatching {
        try {
            db.collection("reels").document(reelId)
                .update(
                    mapOf(
                        "likedBy" to if (isCurrentlyLiked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "reels/$reelId")
        }
    }

    suspend fun addReelComment(reel: ReelItem, authorName: String, text: String): Result<Unit> = runCatching {
        val clean = text.trim()
        if (clean.isBlank()) return@runCatching
        val updated = reel.comments + "$authorName: $clean"
        try {
            db.collection("reels").document(reel.id)
                .update(
                    mapOf(
                        "comments" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "reels/${reel.id}")
        }
    }

    suspend fun createAdCampaign(
        ownerId: String,
        title: String,
        objective: String,
        budgetEtb: Double,
        days: Int
    ): Result<Unit> = runCatching {
        val cleanTitle = title.trim().take(140)
        if (cleanTitle.isBlank()) return@runCatching
        val docRef = db.collection("users").document(ownerId).collection("ad_campaigns").document()
        try {
            docRef.set(
                mapOf(
                    "ownerId" to ownerId,
                    "title" to cleanTitle,
                    "objective" to objective.trim().ifBlank { "Engagement" }.take(80),
                    "budgetEtb" to budgetEtb.coerceAtLeast(0.0),
                    "days" to days.coerceIn(1, 365),
                    "impressions" to 0,
                    "clicks" to 0,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "users/$ownerId/ad_campaigns/${docRef.id}")
        }
    }

    // ==================== Live Streaming ====================

    private fun parseLiveStreamDoc(doc: DocumentSnapshot): LiveStreamItem? {
        val hostId = doc.firstString("hostId", "authorId", "uid", "userId") ?: return null
        val status = doc.firstString("status")?.lowercase()
        val isLiveFlag = (doc.get("isLive") as? Boolean)
            ?: (doc.get("live") as? Boolean)
            ?: (doc.get("active") as? Boolean)
            ?: (status != "ended")
        if (!isLiveFlag || status == "ended") return null
        return LiveStreamItem(
            id = doc.id,
            hostId = hostId,
            hostName = doc.firstString("hostName", "authorName", "name", "displayName") ?: "Member",
            title = doc.firstString("title", "caption", "topic") ?: "Live on Meskot",
            category = doc.firstString("category") ?: "General",
            privacy = parsePrivacy(doc.firstString("privacy", "visibility")),
            isLive = true,
            viewerCount = ((doc.get("viewerCount") as? Number) ?: (doc.get("viewers") as? Number) ?: 1).toInt().coerceAtLeast(1),
            reactionsCount = ((doc.get("reactionsCount") as? Number) ?: (doc.get("reactions") as? Number) ?: 0).toInt(),
            starsEarned = ((doc.get("starsEarned") as? Number) ?: (doc.get("stars") as? Number) ?: 0).toInt(),
            startedAtMs = doc.flexibleMillis("startedAt", "createdAt", "timestamp"),
            coverColorHex = (doc.get("coverColorHex") as? Number)?.toLong()
                ?: (doc.get("bgColorHex") as? Number)?.toLong()
                ?: pickAvatarColor(hostId)
        )
    }

    fun observeLiveStreams(): Flow<List<LiveStreamItem>> {
        val fs1 = db.collection("live_streams").snapshots()
            .map { snap -> snap.documents.mapNotNull(::parseLiveStreamDoc) }
            .catch { emit(emptyList()) }
        val fs2 = db.collection("lives").snapshots()
            .map { snap -> snap.documents.mapNotNull(::parseLiveStreamDoc) }
            .catch { emit(emptyList()) }
        val fs3 = db.collection("liveStreams").snapshots()
            .map { snap -> snap.documents.mapNotNull(::parseLiveStreamDoc) }
            .catch { emit(emptyList()) }
        val rtdb = rtdbSource.observeLiveStreams().catch { emit(emptyList()) }
        return combine(fs1, fs2, fs3, rtdb) { a, b, c, d ->
            (a + b + c + d)
                .associateBy { it.id }
                .values
                .filter { it.isLive }
                .sortedByDescending { it.startedAtMs }
        }
    }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> {
        fun parseCommentDoc(doc: DocumentSnapshot): LiveComment? {
            val text = doc.firstString("text", "message", "body") ?: return null
            return LiveComment(
                id = doc.id,
                authorId = doc.firstString("authorId", "uid", "senderId") ?: "",
                authorName = doc.firstString("authorName", "senderName", "name") ?: "Viewer",
                text = text,
                isGift = (doc.get("isGift") as? Boolean) ?: (doc.get("gift") as? Boolean) ?: false,
                giftEmoji = doc.firstString("giftEmoji", "emoji"),
                timestampMs = doc.flexibleMillis("createdAt", "timestamp", "ts")
            )
        }
        val fs1 = db.collection("live_streams").document(streamId).collection("comments").snapshots()
            .map { snap -> snap.documents.mapNotNull(::parseCommentDoc) }
            .catch { emit(emptyList()) }
        val fs2 = db.collection("lives").document(streamId).collection("comments").snapshots()
            .map { snap -> snap.documents.mapNotNull(::parseCommentDoc) }
            .catch { emit(emptyList()) }
        val rtdb = rtdbSource.observeLiveComments(streamId).catch { emit(emptyList()) }
        return combine(fs1, fs2, rtdb) { a, b, c ->
            (a + b + c).associateBy { it.id }.values.sortedBy { it.timestampMs }
        }
    }

    suspend fun startLiveStream(
        hostId: String,
        hostName: String,
        title: String,
        category: String,
        privacy: PostPrivacy
    ): Result<LiveStreamItem> = runCatching {
        val cleanTitle = title.trim().ifBlank { "$hostName is Live on Meskot" }.take(140)
        val docRef = db.collection("live_streams").document()
        val now = System.currentTimeMillis()
        val color = pickAvatarColor(hostId)
        val item = LiveStreamItem(
            id = docRef.id,
            hostId = hostId,
            hostName = hostName,
            title = cleanTitle,
            category = category.ifBlank { "General" },
            privacy = privacy,
            isLive = true,
            viewerCount = 1,
            reactionsCount = 0,
            starsEarned = 0,
            startedAtMs = now,
            coverColorHex = color
        )
        runCatching {
            docRef.set(
                mapOf(
                    "hostId" to hostId,
                    "hostName" to hostName,
                    "title" to cleanTitle,
                    "category" to item.category,
                    "privacy" to privacy.name,
                    "visibility" to privacy.name,
                    "isLive" to true,
                    "live" to true,
                    "status" to "live",
                    "viewerCount" to 1,
                    "reactionsCount" to 0,
                    "starsEarned" to 0,
                    "coverColorHex" to color,
                    "startedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        // Also publish a LIVE story tile so it appears immediately in the Feed's LIVE strip
        runCatching {
            db.collection("stories").document(docRef.id).set(
                mapOf(
                    "authorId" to hostId,
                    "authorName" to hostName,
                    "caption" to cleanTitle,
                    "bgColorHex" to color,
                    "live" to true,
                    "isLive" to true,
                    "visibility" to privacy.name,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        rtdbSource.writeLiveStream(item)
        item
    }

    suspend fun endLiveStream(streamId: String): Result<Unit> = runCatching {
        runCatching {
            db.collection("live_streams").document(streamId).update(
                mapOf(
                    "isLive" to false,
                    "live" to false,
                    "status" to "ended",
                    "endedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        runCatching {
            db.collection("stories").document(streamId).update(
                mapOf("live" to false, "isLive" to false)
            ).await()
        }
        rtdbSource.endLiveStream(streamId)
    }

    suspend fun adjustLiveViewerCount(streamId: String, delta: Long): Result<Unit> = runCatching {
        runCatching {
            db.collection("live_streams").document(streamId)
                .update("viewerCount", FieldValue.increment(delta))
                .await()
        }
    }

    suspend fun sendLiveComment(
        streamId: String,
        authorId: String,
        authorName: String,
        text: String,
        isGift: Boolean = false,
        giftEmoji: String? = null
    ): Result<Unit> = runCatching {
        val clean = text.trim().take(300)
        if (clean.isBlank()) return@runCatching
        val docRef = db.collection("live_streams").document(streamId).collection("comments").document()
        val comment = LiveComment(
            id = docRef.id,
            authorId = authorId,
            authorName = authorName,
            text = clean,
            isGift = isGift,
            giftEmoji = giftEmoji,
            timestampMs = System.currentTimeMillis()
        )
        runCatching {
            docRef.set(
                mapOf(
                    "authorId" to authorId,
                    "authorName" to authorName,
                    "text" to clean,
                    "isGift" to isGift,
                    "giftEmoji" to giftEmoji,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        rtdbSource.pushLiveComment(streamId, comment)
    }

    suspend fun sendLiveReaction(
        streamId: String,
        hostId: String,
        starsAmount: Int = 0
    ): Result<Unit> = runCatching {
        runCatching {
            val updates = mutableMapOf<String, Any>(
                "reactionsCount" to FieldValue.increment(1L)
            )
            if (starsAmount > 0) {
                updates["starsEarned"] = FieldValue.increment(starsAmount.toLong())
            }
            db.collection("live_streams").document(streamId).update(updates).await()
        }
        if (starsAmount > 0 && hostId.isNotBlank()) {
            runCatching {
                db.collection("public_profiles").document(hostId)
                    .update("stars", FieldValue.increment(starsAmount.toLong()))
                    .await()
            }
        }
    }
}
