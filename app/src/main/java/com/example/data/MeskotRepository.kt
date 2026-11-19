package com.example.data

import android.content.Context
import com.example.R
import com.example.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class MeskotRepository(
    private val db: FirebaseFirestore
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

    private fun DocumentSnapshot.estimatedMillis(field: String): Long =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: System.currentTimeMillis()

    // ==================== Profile Bootstrap ====================

    suspend fun ensureUserDocuments(
        uid: String,
        displayName: String,
        email: String,
        initialLocation: String = ""
    ): Result<Unit> = runCatching {
        val publicRef = db.collection("public_profiles").document(uid)
        val privateRef = db.collection("users").document(uid)

        val publicSnap = try {
            publicRef.get().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.GET, "public_profiles/$uid")
        }

        if (!publicSnap.exists()) {
            val safeName = displayName.trim().ifBlank { "Meskot Member" }.take(100)
            val safeLoc = initialLocation.trim().ifBlank { "Addis Ababa, Ethiopia" }.take(120)
            val payload = mapOf(
                "displayName" to safeName,
                "bio" to "",
                "location" to safeLoc,
                "avatarColorHex" to pickAvatarColor(uid),
                "online" to true,
                "verified" to false,
                "stars" to 0,
                "visibility" to "PUBLIC",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                publicRef.set(payload).await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.CREATE, "public_profiles/$uid")
            }
        } else {
            try {
                publicRef.update(
                    mapOf(
                        "online" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            }
        }

        val privateSnap = try {
            privateRef.get().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.GET, "users/$uid")
        }

        if (!privateSnap.exists()) {
            val payload = mapOf(
                "email" to email.take(200),
                "walletEtb" to 0.0,
                "friendIds" to emptyList<String>(),
                "savedPostIds" to emptyList<String>(),
                "hiddenPostIds" to emptyList<String>(),
                "savedListingIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                privateRef.set(payload).await()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.CREATE, "users/$uid")
            }
        }
    }

    suspend fun setOnlineStatus(uid: String, online: Boolean): Result<Unit> = runCatching {
        try {
            db.collection("public_profiles").document(uid)
                .update(
                    mapOf(
                        "online" to online,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
        }
    }

    suspend fun updateProfile(uid: String, name: String, bio: String, location: String): Result<Unit> = runCatching {
        val cleanName = name.trim().take(100)
        if (cleanName.isBlank()) throw IllegalArgumentException("Name cannot be blank.")
        try {
            db.collection("public_profiles").document(uid)
                .update(
                    mapOf(
                        "displayName" to cleanName,
                        "bio" to bio.trim().take(400),
                        "location" to location.trim().ifBlank { "Addis Ababa, Ethiopia" }.take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
        }
    }

    // ==================== Reactive Firestore Observers ====================

    fun observePublicProfiles(): Flow<Map<String, UserProfile>> =
        db.collection("public_profiles")
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(PublicProfileDoc::class.java) ?: return@mapNotNull null
                    UserProfile(
                        uid = doc.id,
                        name = d.displayName,
                        email = "",
                        bio = d.bio,
                        location = d.location,
                        avatarColorHex = d.avatarColorHex,
                        isOnline = d.online,
                        isVerified = d.verified,
                        isAdmin = false,
                        stars = d.stars,
                        walletEtb = 0.0
                    )
                }.associateBy { it.uid }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "public_profiles")
            }

    fun observePrivateProfile(uid: String): Flow<UserPrivateProfileDoc?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { doc ->
                if (!doc.exists()) null else doc.toObject(UserPrivateProfileDoc::class.java)
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.GET, "users/$uid")
            }

    fun observePublicPosts(): Flow<List<PostItem>> =
        db.collection("posts")
            .whereEqualTo("privacy", "PUBLIC")
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull(::parsePostDoc) }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "posts")
            }

    fun observeMyPosts(uid: String): Flow<List<PostItem>> =
        db.collection("posts")
            .whereEqualTo("authorId", uid)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull(::parsePostDoc) }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "posts")
            }

    @Suppress("UNCHECKED_CAST")
    private fun parsePostDoc(doc: DocumentSnapshot): PostItem? {
        val authorId = doc.getString("authorId") ?: return null
        val authorName = doc.getString("authorName") ?: ""
        val text = doc.getString("text") ?: ""
        val privacy = PostPrivacy.fromName(doc.getString("privacy"))
        val bgColorIndex = (doc.getLong("bgColorIndex") ?: 0L).toInt()
        val imageUri = doc.getString("imageUri")
        val groupId = doc.getString("groupId")
        val shareCount = (doc.getLong("shareCount") ?: 0L).toInt()
        val timestampMs = doc.estimatedMillis("createdAt")

        val rawReactions = doc.get("reactions") as? Map<String, Any?> ?: emptyMap()
        val reactions = rawReactions.mapNotNull { (k, v) ->
            val rt = ReactionType.fromNameOrNull(v as? String)
            if (rt != null) k to rt else null
        }.toMap()

        val rawComments = doc.get("comments") as? List<Map<String, Any?>> ?: emptyList()
        val comments = rawComments.mapNotNull { cm ->
            val cid = cm["id"] as? String ?: return@mapNotNull null
            val cAuthorId = cm["authorId"] as? String ?: ""
            val cAuthorName = cm["authorName"] as? String ?: ""
            val cText = cm["text"] as? String ?: ""
            val cTs = (cm["timestampMs"] as? Number)?.toLong() ?: timestampMs
            val rawReplies = cm["replies"] as? List<Map<String, Any?>> ?: emptyList()
            val replies = rawReplies.mapNotNull { rm ->
                val rid = rm["id"] as? String ?: return@mapNotNull null
                CommentReply(
                    id = rid,
                    authorId = rm["authorId"] as? String ?: "",
                    authorName = rm["authorName"] as? String ?: "",
                    text = rm["text"] as? String ?: "",
                    timestampMs = (rm["timestampMs"] as? Number)?.toLong() ?: cTs
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

        val sharedOriginalId = doc.getString("sharedPostOriginalId")
        val sharedRef = if (!sharedOriginalId.isNullOrBlank()) {
            SharedPostRef(
                originalPostId = sharedOriginalId,
                authorName = doc.getString("sharedPostAuthor") ?: "",
                text = doc.getString("sharedPostText") ?: ""
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

    fun observeStories(): Flow<List<StoryItem>> =
        db.collection("stories")
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(StoryDoc::class.java) ?: return@mapNotNull null
                    StoryItem(
                        id = doc.id,
                        authorId = d.authorId,
                        authorName = d.authorName,
                        caption = d.caption,
                        imageRes = null,
                        bgColorHex = d.bgColorHex,
                        isLive = d.live,
                        timestampMs = doc.estimatedMillis("createdAt")
                    )
                }.sortedByDescending { it.timestampMs }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "stories")
            }

    fun observeFriendRequests(uid: String): Flow<List<FriendRequest>> =
        db.collection("friend_requests")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(FriendRequestDoc::class.java) ?: return@mapNotNull null
                    FriendRequest(
                        id = doc.id,
                        fromUid = d.fromUid,
                        toUid = d.toUid,
                        timestampMs = doc.estimatedMillis("createdAt")
                    )
                }.sortedByDescending { it.timestampMs }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "friend_requests")
            }

    fun observeConversations(uid: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(ConversationDoc::class.java) ?: return@mapNotNull null
                    Conversation(
                        id = doc.id,
                        participantIds = d.participantIds,
                        messages = emptyList(),
                        lastMessage = d.lastMessage,
                        updatedAtMs = doc.estimatedMillis("updatedAt"),
                        unreadCount = 0
                    )
                }.sortedByDescending { it.updatedAtMs }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "conversations")
            }

    fun observeMessages(convoId: String, uid: String): Flow<List<ChatMessage>> =
        db.collection("conversations").document(convoId)
            .collection("messages")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(ChatMessageDoc::class.java) ?: return@mapNotNull null
                    ChatMessage(
                        id = doc.id,
                        senderId = d.senderId,
                        text = d.text,
                        timestampMs = doc.estimatedMillis("createdAt"),
                        isEdited = d.edited
                    )
                }.sortedBy { it.timestampMs }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "conversations/$convoId/messages")
            }

    fun observeGroups(): Flow<List<GroupItem>> =
        db.collection("groups")
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(GroupDoc::class.java) ?: return@mapNotNull null
                    GroupItem(
                        id = doc.id,
                        name = d.name,
                        description = d.description,
                        coverColorHex = d.coverColorHex,
                        memberIds = d.memberIds.toSet(),
                        creatorId = d.creatorId
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "groups")
            }

    fun observeListings(): Flow<List<MarketListing>> =
        db.collection("listings")
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(MarketListingDoc::class.java) ?: return@mapNotNull null
                    MarketListing(
                        id = doc.id,
                        sellerId = d.sellerId,
                        sellerName = d.sellerName,
                        title = d.title,
                        priceEtb = d.priceEtb,
                        category = d.category,
                        city = d.city,
                        description = d.description,
                        imageRes = null,
                        timestampMs = doc.estimatedMillis("createdAt")
                    )
                }.sortedByDescending { it.timestampMs }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "listings")
            }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        db.collection("users").document(uid)
            .collection("notifications")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(NotificationDoc::class.java) ?: return@mapNotNull null
                    NotificationItem(
                        id = doc.id,
                        actorId = d.actorId,
                        actorName = d.actorName,
                        type = d.type,
                        targetId = d.targetId,
                        messageEn = d.messageEn,
                        messageAm = d.messageAm,
                        timestampMs = doc.estimatedMillis("createdAt"),
                        isRead = d.read
                    )
                }.sortedByDescending { it.timestampMs }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "users/$uid/notifications")
            }

    fun observeAlbums(uid: String): Flow<List<PhotoAlbum>> =
        db.collection("users").document(uid)
            .collection("albums")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(PhotoAlbumDoc::class.java) ?: return@mapNotNull null
                    PhotoAlbum(
                        id = doc.id,
                        ownerId = d.ownerId,
                        title = d.title,
                        photos = emptyList(),
                        photoUris = d.photoUris
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "users/$uid/albums")
            }

    fun observeReels(currentUid: String): Flow<List<ReelItem>> =
        db.collection("reels")
            .whereEqualTo("visibility", "PUBLIC")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(ReelDoc::class.java) ?: return@mapNotNull null
                    ReelItem(
                        id = doc.id,
                        authorId = d.authorId,
                        authorName = d.authorName,
                        caption = d.caption,
                        coverRes = null,
                        likes = d.likedBy.size,
                        isLiked = currentUid in d.likedBy,
                        comments = d.comments
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "reels")
            }

    fun observeAdCampaigns(uid: String): Flow<List<AdCampaign>> =
        db.collection("users").document(uid)
            .collection("ad_campaigns")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val d = doc.toObject(AdCampaignDoc::class.java) ?: return@mapNotNull null
                    AdCampaign(
                        id = doc.id,
                        title = d.title,
                        objective = d.objective,
                        budgetEtb = d.budgetEtb,
                        days = d.days,
                        impressions = d.impressions,
                        clicks = d.clicks,
                        active = d.active
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "users/$uid/ad_campaigns")
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
                .update(
                    mapOf(
                        "savedPostIds" to next.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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
                .update(
                    mapOf(
                        "hiddenPostIds" to next.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.CREATE, "friend_requests/$reqId")
        }
    }

    suspend fun cancelFriendRequest(fromUid: String, toUid: String): Result<Unit> = runCatching {
        val reqId = "${fromUid}_${toUid}"
        try {
            db.collection("friend_requests").document(reqId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "friend_requests/$reqId")
        }
    }

    suspend fun acceptFriendRequest(myUid: String, myFriends: Set<String>, fromUid: String): Result<Unit> = runCatching {
        val reqId = "${fromUid}_${myUid}"
        val updatedFriends = (myFriends + fromUid).toList()
        try {
            db.collection("friend_requests").document(reqId).delete().await()
            db.collection("users").document(myUid)
                .update(
                    mapOf(
                        "friendIds" to updatedFriends,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.WRITE, "users/$myUid")
        }
    }

    suspend fun declineFriendRequest(myUid: String, fromUid: String): Result<Unit> = runCatching {
        val reqId = "${fromUid}_${myUid}"
        try {
            db.collection("friend_requests").document(reqId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, "friend_requests/$reqId")
        }
    }

    suspend fun unfriend(myUid: String, myFriends: Set<String>, targetUid: String): Result<Unit> = runCatching {
        val updated = (myFriends - targetUid).toList()
        try {
            db.collection("users").document(myUid)
                .update(
                    mapOf(
                        "friendIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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
                .update(
                    mapOf(
                        "lastMessage" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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
                .update(
                    mapOf(
                        "savedListingIds" to next.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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
}
