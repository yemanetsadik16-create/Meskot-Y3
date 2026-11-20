package com.example.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ReactionType(val emoji: String, val labelKey: String) {
    LIKE("👍", "reactLike"),
    LOVE("❤️", "reactLove"),
    HAHA("😆", "reactHaha"),
    WOW("😮", "reactWow"),
    SAD("😢", "reactSad"),
    ANGRY("😡", "reactAngry");

    companion object {
        fun fromNameOrNull(raw: String?): ReactionType? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class PostPrivacy(val labelKey: String, val subKey: String) {
    PUBLIC("public", "publicSub"),
    FRIENDS("friendsLabel", "friendsSub"),
    ONLY_ME("onlyMe", "onlyMeSub");

    companion object {
        fun fromName(raw: String?): PostPrivacy =
            entries.firstOrNull { it.name == raw } ?: PUBLIC
    }
}

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val bio: String = "",
    val location: String = "Addis Ababa, Ethiopia",
    val avatarColorHex: Long = 0xFFC93E03,
    val isOnline: Boolean = false,
    val isVerified: Boolean = false,
    val isAdmin: Boolean = false,
    val stars: Int = 0,
    val walletEtb: Double = 0.0
)

data class StoryItem(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    @DrawableRes val imageRes: Int? = null,
    val bgColorHex: Long = 0xFFEE4C04,
    val isLive: Boolean = false,
    val timestampMs: Long = 0L
)

data class CommentReply(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val timestampMs: Long = 0L,
    val likedBy: Set<String> = emptySet()
)

data class PostComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val timestampMs: Long = 0L,
    val likedBy: Set<String> = emptySet(),
    val replies: List<CommentReply> = emptyList()
)

data class SharedPostRef(
    val originalPostId: String = "",
    val authorName: String = "",
    val text: String = "",
    @DrawableRes val imageRes: Int? = null
)

data class PostItem(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val timestampMs: Long = 0L,
    val privacy: PostPrivacy = PostPrivacy.PUBLIC,
    val bgColorIndex: Int = 0,
    @DrawableRes val imageRes: Int? = null,
    val imageUri: String? = null,
    val groupId: String? = null,
    val reactions: Map<String, ReactionType> = emptyMap(),
    val comments: List<PostComment> = emptyList(),
    val sharedPost: SharedPostRef? = null,
    val shareCount: Int = 0
)

data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val timestampMs: Long = 0L
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestampMs: Long = 0L,
    val isEdited: Boolean = false,
    val isCallLog: Boolean = false,
    val callType: String? = null
)

data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val lastMessage: String = "",
    val updatedAtMs: Long = 0L,
    val unreadCount: Int = 0
)

data class GroupItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val coverColorHex: Long = 0xFFC93E03,
    val memberIds: Set<String> = emptySet(),
    val creatorId: String = ""
)

data class MarketListing(
    val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val title: String = "",
    val priceEtb: Double = 0.0,
    val category: String = "",
    val city: String = "",
    val description: String = "",
    @DrawableRes val imageRes: Int? = null,
    val timestampMs: Long = 0L
)

data class NotificationItem(
    val id: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val type: String = "",
    val targetId: String? = null,
    val messageEn: String = "",
    val messageAm: String = "",
    val timestampMs: Long = 0L,
    val isRead: Boolean = false
)

data class PhotoAlbum(
    val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val photos: List<Int> = emptyList(),
    val photoUris: List<String> = emptyList()
)

data class ReelItem(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    @DrawableRes val coverRes: Int? = null,
    val likes: Int = 0,
    val isLiked: Boolean = false,
    val comments: List<String> = emptyList()
)

data class AdCampaign(
    val id: String = "",
    val title: String = "",
    val objective: String = "",
    val budgetEtb: Double = 0.0,
    val days: Int = 7,
    val impressions: Int = 0,
    val clicks: Int = 0,
    val active: Boolean = true
)

data class LiveStreamItem(
    val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val category: String = "General",
    val privacy: PostPrivacy = PostPrivacy.PUBLIC,
    val isLive: Boolean = true,
    val viewerCount: Int = 1,
    val reactionsCount: Int = 0,
    val starsEarned: Int = 0,
    val startedAtMs: Long = 0L,
    val endedAtMs: Long? = null,
    val coverColorHex: Long = 0xFFC93E03
)

data class LiveComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val isGift: Boolean = false,
    val giftEmoji: String? = null,
    val timestampMs: Long = 0L
)

data class CallSession(
    val id: String = "",
    val convoId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val callType: String = "audio", // "audio" or "video"
    val status: String = "ringing", // "ringing", "accepted", "connected", "ended", "declined", "missed"
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val createdAtMs: Long = 0L
)

data class IceCandidateItem(
    val id: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)

// ==================== Firestore Document DTOs ====================

@Keep
@IgnoreExtraProperties
data class PublicProfileDoc(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val bio: String = "",
    val location: String = "Addis Ababa, Ethiopia",
    val avatarColorHex: Long = 0xFFC93E03,
    val online: Boolean = false,
    val verified: Boolean = false,
    val stars: Int = 0,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPrivateProfileDoc(
    @DocumentId val id: String = "",
    val email: String = "",
    val walletEtb: Double = 0.0,
    val friendIds: List<String> = emptyList(),
    val savedPostIds: List<String> = emptyList(),
    val hiddenPostIds: List<String> = emptyList(),
    val savedListingIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryDoc(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    val bgColorHex: Long = 0xFFEE4C04,
    val live: Boolean = false,
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequestDoc(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConversationDoc(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessageDoc(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    val edited: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val coverColorHex: Long = 0xFFC93E03,
    val creatorId: String = "",
    val memberIds: List<String> = emptyList(),
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MarketListingDoc(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val title: String = "",
    val priceEtb: Double = 0.0,
    val category: String = "",
    val city: String = "",
    val description: String = "",
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NotificationDoc(
    @DocumentId val id: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val type: String = "",
    val targetId: String? = null,
    val messageEn: String = "",
    val messageAm: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PhotoAlbumDoc(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val photoUris: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReelDoc(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val caption: String = "",
    val likedBy: List<String> = emptyList(),
    val comments: List<String> = emptyList(),
    val visibility: String = "PUBLIC",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdCampaignDoc(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val objective: String = "",
    val budgetEtb: Double = 0.0,
    val days: Int = 7,
    val impressions: Int = 0,
    val clicks: Int = 0,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)
