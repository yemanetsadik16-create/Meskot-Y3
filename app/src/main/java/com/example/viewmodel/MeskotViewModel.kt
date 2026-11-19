package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MeskotDataState
import com.example.data.MeskotRepository
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MeskotViewModel(
    private val repository: MeskotRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String,
    private val initialLocation: String = ""
) : ViewModel() {

    private val _dataState = MutableStateFlow(MeskotDataState(isLoading = true))
    val dataState: StateFlow<MeskotDataState> = _dataState.asStateFlow()

    private val _activeConvoId = MutableStateFlow<String?>(null)
    val activeConvoId: StateFlow<String?> = _activeConvoId.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var currentLang: AppLang = AppLang.EN

    private var publicProfilesCache: Map<String, UserProfile> = emptyMap()
    private var privateProfileCache: UserPrivateProfileDoc? = null
    private var publicPostsCache: List<PostItem> = emptyList()
    private var myPostsCache: List<PostItem> = emptyList()
    private var conversationsCache: List<Conversation> = emptyList()
    private var activeMessagesCache: List<ChatMessage> = emptyList()

    init {
        viewModelScope.launch {
            repository.ensureUserDocuments(currentUserId, initialDisplayName, initialEmail, initialLocation)
                .onFailure { e ->
                    _dataState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
        observeStreams()
    }

    fun updateLanguageCache(lang: AppLang) {
        currentLang = lang
    }

    private fun t(key: String): String = MeskotStrings.t(currentLang, key)

    private fun handleStreamError(e: Throwable) {
        val firestoreEx = generateSequence(e) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (e is CancellationException && firestoreEx == null) throw e
        _dataState.update {
            it.copy(
                isLoading = false,
                errorMessage = firestoreEx?.message ?: e.message ?: "Failed to sync with server."
            )
        }
    }

    private fun rebuildUserState() {
        val priv = privateProfileCache
        val pub = publicProfilesCache[currentUserId]
        val me = UserProfile(
            uid = currentUserId,
            name = pub?.name?.ifBlank { initialDisplayName } ?: initialDisplayName.ifBlank { "Meskot Member" },
            email = priv?.email ?: initialEmail,
            bio = pub?.bio ?: "",
            location = pub?.location ?: "Addis Ababa, Ethiopia",
            avatarColorHex = pub?.avatarColorHex ?: 0xFFC93E03L,
            isOnline = pub?.isOnline ?: true,
            isVerified = pub?.isVerified ?: false,
            isAdmin = false,
            stars = pub?.stars ?: 0,
            walletEtb = priv?.walletEtb ?: 0.0
        )
        val mergedUsers = publicProfilesCache.toMutableMap().apply { put(currentUserId, me) }
        _dataState.update {
            it.copy(
                currentUser = me,
                users = mergedUsers,
                friendIds = priv?.friendIds?.toSet() ?: emptySet(),
                savedPostIds = priv?.savedPostIds?.toSet() ?: emptySet(),
                hiddenPostIds = priv?.hiddenPostIds?.toSet() ?: emptySet(),
                savedListingIds = priv?.savedListingIds?.toSet() ?: emptySet(),
                isLoading = false,
                errorMessage = null
            )
        }
    }

    private fun rebuildPosts() {
        val combined = (publicPostsCache + myPostsCache)
            .associateBy { it.id }
            .values
            .sortedByDescending { it.timestampMs }
        _dataState.update { it.copy(posts = combined) }
    }

    private fun rebuildConversations() {
        val activeId = _activeConvoId.value
        val merged = conversationsCache.map { convo ->
            if (convo.id == activeId) convo.copy(messages = activeMessagesCache) else convo
        }
        _dataState.update { it.copy(conversations = merged) }
    }

    private fun observeStreams() {
        viewModelScope.launch {
            repository.observePublicProfiles()
                .catch { handleStreamError(it) }
                .collect { profiles ->
                    publicProfilesCache = profiles
                    rebuildUserState()
                }
        }
        viewModelScope.launch {
            repository.observePrivateProfile(currentUserId)
                .catch { handleStreamError(it) }
                .collect { priv ->
                    privateProfileCache = priv
                    rebuildUserState()
                }
        }
        viewModelScope.launch {
            repository.observePublicPosts()
                .catch { handleStreamError(it) }
                .collect { posts ->
                    publicPostsCache = posts
                    rebuildPosts()
                }
        }
        viewModelScope.launch {
            repository.observeMyPosts(currentUserId)
                .catch { handleStreamError(it) }
                .collect { posts ->
                    myPostsCache = posts
                    rebuildPosts()
                }
        }
        viewModelScope.launch {
            repository.observeStories()
                .catch { handleStreamError(it) }
                .collect { stories -> _dataState.update { it.copy(stories = stories) } }
        }
        viewModelScope.launch {
            repository.observeFriendRequests(currentUserId)
                .catch { handleStreamError(it) }
                .collect { reqs -> _dataState.update { it.copy(friendRequests = reqs) } }
        }
        viewModelScope.launch {
            repository.observeConversations(currentUserId)
                .catch { handleStreamError(it) }
                .collect { convos ->
                    conversationsCache = convos
                    rebuildConversations()
                }
        }
        viewModelScope.launch {
            _activeConvoId
                .flatMapLatest { cid ->
                    if (cid == null) flowOf(emptyList())
                    else repository.observeMessages(cid, currentUserId)
                }
                .catch { handleStreamError(it) }
                .collect { msgs ->
                    activeMessagesCache = msgs
                    rebuildConversations()
                }
        }
        viewModelScope.launch {
            repository.observeGroups()
                .catch { handleStreamError(it) }
                .collect { groups -> _dataState.update { it.copy(groups = groups) } }
        }
        viewModelScope.launch {
            repository.observeListings()
                .catch { handleStreamError(it) }
                .collect { listings -> _dataState.update { it.copy(listings = listings) } }
        }
        viewModelScope.launch {
            repository.observeNotifications(currentUserId)
                .catch { handleStreamError(it) }
                .collect { notifs -> _dataState.update { it.copy(notifications = notifs) } }
        }
        viewModelScope.launch {
            repository.observeAlbums(currentUserId)
                .catch { handleStreamError(it) }
                .collect { albums -> _dataState.update { it.copy(albums = albums) } }
        }
        viewModelScope.launch {
            repository.observeReels(currentUserId)
                .catch { handleStreamError(it) }
                .collect { reels -> _dataState.update { it.copy(reels = reels) } }
        }
        viewModelScope.launch {
            repository.observeAdCampaigns(currentUserId)
                .catch { handleStreamError(it) }
                .collect { ads -> _dataState.update { it.copy(adCampaigns = ads) } }
        }
    }

    fun selectConversation(convoId: String?) {
        _activeConvoId.value = convoId
        if (convoId == null) {
            activeMessagesCache = emptyList()
            rebuildConversations()
        }
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    suspend fun markOfflineBeforeSignOut() {
        repository.setOnlineStatus(currentUserId, false)
    }

    fun updateProfile(name: String, bio: String, location: String) {
        viewModelScope.launch {
            repository.updateProfile(currentUserId, name, bio, location)
                .onSuccess { showToast(t("save") + " ✓") }
                .onFailure { showToast(it.message ?: "Update failed") }
        }
    }

    fun createPost(
        text: String,
        privacy: PostPrivacy = PostPrivacy.PUBLIC,
        bgColorIndex: Int = 0,
        imageUri: String? = null,
        groupId: String? = null
    ) {
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.createPost(currentUserId, myName, text, privacy, bgColorIndex, imageUri, groupId)
                .onSuccess { showToast(t("posted")) }
                .onFailure { showToast(it.message ?: "Could not publish post") }
        }
    }

    fun editPost(postId: String, newText: String) {
        viewModelScope.launch {
            repository.editPost(postId, newText)
                .onFailure { showToast(it.message ?: "Could not edit post") }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId)
                .onFailure { showToast(it.message ?: "Could not delete post") }
        }
    }

    fun toggleReaction(postId: String, reaction: ReactionType) {
        val post = _dataState.value.posts.find { it.id == postId } ?: return
        viewModelScope.launch {
            repository.toggleReaction(post, currentUserId, reaction)
                .onFailure { showToast(it.message ?: "Could not react") }
        }
    }

    fun addComment(postId: String, text: String) {
        val post = _dataState.value.posts.find { it.id == postId } ?: return
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.addComment(post, currentUserId, myName, text)
                .onFailure { showToast(it.message ?: "Could not comment") }
        }
    }

    fun addReply(postId: String, commentId: String, text: String) {
        val post = _dataState.value.posts.find { it.id == postId } ?: return
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.addReply(post, commentId, currentUserId, myName, text)
                .onFailure { showToast(it.message ?: "Could not reply") }
        }
    }

    fun sharePost(post: PostItem) {
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.sharePost(currentUserId, myName, post)
                .onSuccess { showToast(t("postShared")) }
                .onFailure { showToast(it.message ?: "Could not share post") }
        }
    }

    fun toggleSavePost(postId: String) {
        val currentSaved = _dataState.value.savedPostIds
        viewModelScope.launch {
            repository.toggleSavePost(currentUserId, currentSaved, postId)
        }
    }

    fun hidePost(postId: String) {
        val currentHidden = _dataState.value.hiddenPostIds
        viewModelScope.launch {
            repository.hidePost(currentUserId, currentHidden, postId)
                .onSuccess { showToast(t("postHidden")) }
        }
    }

    fun reportPost(postId: String) {
        val currentHidden = _dataState.value.hiddenPostIds
        viewModelScope.launch {
            repository.hidePost(currentUserId, currentHidden, postId)
                .onSuccess { showToast(t("reportSubmitted")) }
        }
    }

    fun addStory(caption: String, bgColorHex: Long) {
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.addStory(currentUserId, myName, caption, bgColorHex)
                .onSuccess { showToast(t("posted")) }
                .onFailure { showToast(it.message ?: "Could not add story") }
        }
    }

    fun sendFriendRequest(toUid: String) {
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.sendFriendRequest(currentUserId, myName, toUid)
        }
    }

    fun cancelFriendRequest(toUid: String) {
        viewModelScope.launch {
            repository.cancelFriendRequest(currentUserId, toUid)
        }
    }

    fun acceptFriendRequest(fromUid: String) {
        val currentFriends = _dataState.value.friendIds
        viewModelScope.launch {
            repository.acceptFriendRequest(currentUserId, currentFriends, fromUid)
        }
    }

    fun declineFriendRequest(fromUid: String) {
        viewModelScope.launch {
            repository.declineFriendRequest(currentUserId, fromUid)
        }
    }

    fun unfriend(uid: String) {
        val currentFriends = _dataState.value.friendIds
        viewModelScope.launch {
            repository.unfriend(currentUserId, currentFriends, uid)
        }
    }

    fun openOrCreateChat(otherUid: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            repository.getOrCreateConversation(currentUserId, otherUid)
                .onSuccess { cid ->
                    _activeConvoId.value = cid
                    onReady(cid)
                }
                .onFailure { showToast(it.message ?: "Could not open chat") }
        }
    }

    fun sendMessage(convoId: String, text: String) {
        val convo = _dataState.value.conversations.find { it.id == convoId }
        val participants = convo?.participantIds ?: return
        viewModelScope.launch {
            repository.sendMessage(convoId, participants, currentUserId, text)
                .onFailure { showToast(it.message ?: "Could not send message") }
        }
    }

    fun editMessage(convoId: String, msgId: String, text: String) {
        viewModelScope.launch {
            repository.editMessage(convoId, msgId, text)
        }
    }

    fun deleteMessage(convoId: String, msgId: String) {
        viewModelScope.launch {
            repository.deleteMessage(convoId, msgId)
        }
    }

    fun createGroup(name: String, description: String) {
        viewModelScope.launch {
            repository.createGroup(currentUserId, name, description)
                .onSuccess { showToast(t("posted")) }
        }
    }

    fun toggleGroupMembership(groupId: String) {
        val group = _dataState.value.groups.find { it.id == groupId } ?: return
        viewModelScope.launch {
            repository.toggleGroupMembership(group, currentUserId)
        }
    }

    fun createListing(title: String, priceEtb: Double, category: String, city: String, description: String) {
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.createListing(currentUserId, myName, title, priceEtb, category, city, description)
                .onSuccess { showToast(t("posted")) }
        }
    }

    fun toggleSaveListing(listingId: String) {
        val currentSaved = _dataState.value.savedListingIds
        viewModelScope.launch {
            repository.toggleSaveListing(currentUserId, currentSaved, listingId)
        }
    }

    fun markAllNotificationsRead() {
        val unreadIds = _dataState.value.notifications.filter { !it.isRead }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            repository.markAllNotificationsRead(currentUserId, unreadIds)
        }
    }

    fun createAlbum(title: String) {
        viewModelScope.launch {
            repository.createAlbum(currentUserId, title)
                .onSuccess { showToast(t("posted")) }
        }
    }

    fun createReel(caption: String) {
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.createReel(currentUserId, myName, caption)
                .onSuccess { showToast(t("posted")) }
        }
    }

    fun toggleReelLike(reelId: String) {
        val reel = _dataState.value.reels.find { it.id == reelId } ?: return
        viewModelScope.launch {
            repository.toggleReelLike(reelId, currentUserId, reel.isLiked)
        }
    }

    fun addReelComment(reelId: String, text: String) {
        val reel = _dataState.value.reels.find { it.id == reelId } ?: return
        val myName = _dataState.value.currentUser?.name ?: initialDisplayName
        viewModelScope.launch {
            repository.addReelComment(reel, myName, text)
        }
    }

    fun createAdCampaign(title: String, objective: String, budgetEtb: Double, days: Int) {
        viewModelScope.launch {
            repository.createAdCampaign(currentUserId, title, objective, budgetEtb, days)
                .onSuccess { showToast("Campaign launched") }
        }
    }

    class Factory(
        private val repository: MeskotRepository,
        private val currentUserId: String,
        private val initialDisplayName: String,
        private val initialEmail: String,
        private val initialLocation: String = ""
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeskotViewModel(repository, currentUserId, initialDisplayName, initialEmail, initialLocation) as T
        }
    }
}
