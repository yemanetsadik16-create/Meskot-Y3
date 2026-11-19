package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.*
import com.example.ui.components.FullPostComposerDialog
import com.example.ui.components.PostCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.*

@Composable
fun FeedScreen(
    state: MeskotDataState,
    lang: AppLang,
    onOpenProfile: (String) -> Unit,
    onOpenReels: () -> Unit,
    onCreatePost: (String, PostPrivacy, Int, String?) -> Unit,
    onReaction: (String, ReactionType) -> Unit,
    onAddComment: (String, String) -> Unit,
    onAddReply: (String, String, String) -> Unit,
    onShare: (PostItem) -> Unit,
    onToggleSave: (String) -> Unit,
    onEditPost: (String, String) -> Unit,
    onDeletePost: (String) -> Unit,
    onHidePost: (String) -> Unit,
    onReportPost: (String) -> Unit,
    onAddStory: (String, Long) -> Unit
) {
    val me = state.currentUser ?: return
    var showComposer by remember { mutableStateOf(false) }
    var showStoryCreator by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<StoryItem?>(null) }

    val feedPosts = remember(state.posts, state.hiddenPostIds, state.friendIds, me.uid) {
        state.posts.filter { p ->
            p.groupId == null &&
                p.id !in state.hiddenPostIds &&
                when (p.privacy) {
                    PostPrivacy.PUBLIC -> true
                    PostPrivacy.FRIENDS -> p.authorId == me.uid || p.authorId in state.friendIds
                    PostPrivacy.ONLY_ME -> p.authorId == me.uid
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("feed_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Meskot Stories Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotCard),
                border = BorderStroke(1.dp, MeskotLine)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MeskotGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            MeskotStrings.t(lang, "storiesTitle"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        val liveCount = state.stories.count { it.isLive }
                        Surface(
                            color = MeskotCross.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "$liveCount LIVE",
                                color = MeskotCross,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { showStoryCreator = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("add_moment_button")
                        ) {
                            Text(MeskotStrings.t(lang, "addMoment"), color = MeskotGoldDeep, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.stories, key = { it.id }) { story ->
                            StoryCardTile(story = story, onClick = { viewingStory = story })
                        }
                    }
                }
            }
        }

        // Royal Composer Bar
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotCard),
                border = BorderStroke(1.dp, MeskotLine)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(me.name, me.avatarColorHex, size = 40.dp, onClick = { onOpenProfile(me.uid) })
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { showComposer = true }
                                .testTag("feed_composer_trigger"),
                            shape = RoundedCornerShape(22.dp),
                            color = MeskotPaper2
                        ) {
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(MeskotStrings.t(lang, "composerPh"), color = MeskotMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MeskotLine)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ComposerShortcut(Icons.Outlined.RadioButtonChecked, MeskotStrings.t(lang, "btnLive"), MeskotCross) {
                            showStoryCreator = true
                        }
                        ComposerShortcut(Icons.Outlined.CameraAlt, MeskotStrings.t(lang, "btnStory"), MeskotGold) {
                            showStoryCreator = true
                        }
                        ComposerShortcut(Icons.Outlined.PlayCircle, MeskotStrings.t(lang, "btnReel"), MeskotDarkAction) {
                            onOpenReels()
                        }
                        ComposerShortcut(Icons.Outlined.PhotoLibrary, MeskotStrings.t(lang, "btnPhoto"), MeskotAcceptGreen) {
                            showComposer = true
                        }
                    }
                }
            }
        }

        // Feed Posts
        if (feedPosts.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🪟", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(MeskotStrings.t(lang, "emptyFeed"), color = MeskotMuted, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(feedPosts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    currentUserId = me.uid,
                    authorProfile = state.users[post.authorId],
                    isSaved = post.id in state.savedPostIds,
                    lang = lang,
                    onAuthorClick = onOpenProfile,
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

    if (showComposer) {
        FullPostComposerDialog(
            user = me,
            lang = lang,
            onDismiss = { showComposer = false },
            onSubmit = { text, privacy, bgIdx, uri -> onCreatePost(text, privacy, bgIdx, uri) }
        )
    }

    if (showStoryCreator) {
        var caption by remember { mutableStateOf("") }
        var selectedColor by remember { mutableLongStateOf(0xFFEE4C04L) }
        val colors = listOf(0xFFEE4C04L, 0xFF8C2F39L, 0xFF1F232BL, 0xFF1B5E42L, 0xFF2D4373L)
        AlertDialog(
            onDismissRequest = { showStoryCreator = false },
            title = { Text(MeskotStrings.t(lang, "addMoment"), fontFamily = FrauncesFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        placeholder = { Text(MeskotStrings.t(lang, "composerPh")) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(if (selectedColor == hex) 2.5.dp else 1.dp, if (selectedColor == hex) MeskotInk else MeskotLine, CircleShape)
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAddStory(caption, selectedColor); showStoryCreator = false },
                    enabled = caption.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "post")) }
            },
            dismissButton = {
                TextButton(onClick = { showStoryCreator = false }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }

    viewingStory?.let { story ->
        Dialog(onDismissRequest = { viewingStory = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color(story.bgColorHex))) {
                    if (story.imageRes != null) {
                        Image(
                            painter = painterResource(story.imageRes),
                            contentDescription = story.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp).align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(story.authorName, size = 36.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(formatTimeAgo(story.timestampMs, lang), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { viewingStory = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close story", tint = Color.White)
                        }
                    }
                    Text(
                        text = story.caption,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryCardTile(story: StoryItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(story.bgColorHex))
            .border(if (story.isLive) 2.dp else 1.dp, if (story.isLive) MeskotGold else MeskotLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        if (story.imageRes != null) {
            Image(
                painter = painterResource(story.imageRes),
                contentDescription = story.authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))
                )
            )
        }
        if (story.isLive) {
            Surface(
                color = MeskotCross,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            ) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        Text(
            text = story.authorName,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
    }
}

@Composable
private fun ComposerShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        modifier = Modifier.height(44.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = MeskotInk, style = MaterialTheme.typography.labelSmall)
    }
}
