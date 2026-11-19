package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.PostItem
import com.example.model.ReactionType
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.PostCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    uid: String,
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onUpdateProfile: (String, String, String) -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onUnfriend: (String) -> Unit,
    onReaction: (String, ReactionType) -> Unit,
    onAddComment: (String, String) -> Unit,
    onAddReply: (String, String, String) -> Unit,
    onShare: (PostItem) -> Unit,
    onToggleSave: (String) -> Unit,
    onEditPost: (String, String) -> Unit,
    onDeletePost: (String) -> Unit,
    onHidePost: (String) -> Unit,
    onReportPost: (String) -> Unit
) {
    val me = state.currentUser ?: return
    val profile = state.users[uid] ?: me
    val isMe = profile.uid == me.uid
    val isFriend = profile.uid in state.friendIds
    val hasOutgoing = state.friendRequests.any { it.fromUid == me.uid && it.toUid == profile.uid }

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Posts, 1=Photos
    var showEditDialog by remember { mutableStateOf(false) }

    val userPosts = remember(state.posts, profile.uid) {
        state.posts.filter { it.authorId == profile.uid && it.groupId == null }
    }
    val photoPosts = remember(userPosts) { userPosts.filter { it.imageRes != null } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(profile.name, style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotCard),
                border = BorderStroke(1.dp, MeskotLine)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .background(Brush.horizontalGradient(listOf(MeskotGold.copy(alpha = 0.25f), MeskotCross.copy(alpha = 0.2f))))
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserAvatar(profile.name, profile.avatarColorHex, size = 80.dp, showOnlineDot = profile.isOnline)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                            if (profile.isVerified) {
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Filled.Verified, null, tint = MeskotGold, modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Outlined.LocationOn, null, tint = MeskotMuted, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(profile.location, style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
                        }
                        Text(
                            text = profile.bio.ifBlank { MeskotStrings.t(lang, "noBioYet") },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeskotMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            if (isMe) {
                                Button(
                                    onClick = { showEditDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeskotDarkAction),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(MeskotStrings.t(lang, "editProfile")) }
                            } else {
                                Button(
                                    onClick = { onOpenChat(profile.uid) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                                    modifier = Modifier.weight(1f)
                                ) { Text(MeskotStrings.t(lang, "messageBtn")) }
                                OutlinedButton(
                                    onClick = {
                                        if (isFriend) onUnfriend(profile.uid)
                                        else if (!hasOutgoing) onSendFriendRequest(profile.uid)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        when {
                                            isFriend -> MeskotStrings.t(lang, "friendsLabel")
                                            hasOutgoing -> MeskotStrings.t(lang, "requested")
                                            else -> MeskotStrings.t(lang, "addFriend")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MeskotCard,
                modifier = Modifier.padding(horizontal = 14.dp).clip(RoundedCornerShape(12.dp))
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Posts (${userPosts.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Photos (${photoPosts.size})") })
            }
        }

        if (selectedTab == 0) {
            if (userPosts.isEmpty()) {
                item {
                    Text(
                        "No posts yet.",
                        color = MeskotMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            } else {
                items(userPosts, key = { it.id }) { post ->
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        PostCard(
                            post = post,
                            currentUserId = me.uid,
                            authorProfile = profile,
                            isSaved = post.id in state.savedPostIds,
                            lang = lang,
                            onAuthorClick = {},
                            onReaction = onReaction,
                            onAddComment = onAddComment,
                            onAddReply = onAddReply,
                            onShare = onShare,
                            onToggleSave = onToggleSave,
                            onEditPost = onEditPost,
                            onDeletePost = onDeletePost,
                            onHidePost = onHidePost,
                            onReportPost = onReportPost
                        )
                    }
                }
            }
        } else {
            if (photoPosts.isEmpty()) {
                item {
                    Text("No photos yet.", color = MeskotMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            } else {
                items(photoPosts, key = { "photo_${it.id}" }) { post ->
                    post.imageRes?.let { resId ->
                        Image(
                            painter = painterResource(resId),
                            contentDescription = post.text,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && isMe) {
        EditProfileDialog(
            user = profile,
            lang = lang,
            onDismiss = { showEditDialog = false },
            onSave = onUpdateProfile
        )
    }
}
