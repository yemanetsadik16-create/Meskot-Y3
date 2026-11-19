package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.*
import com.example.ui.theme.*

fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

fun formatTimeAgo(timestampMs: Long, lang: AppLang): String {
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> MeskotStrings.t(lang, "justNow")
        diffSec < 3600 -> "${diffSec / 60}${MeskotStrings.t(lang, "minAgo")}"
        diffSec < 86400 -> "${diffSec / 3600}${MeskotStrings.t(lang, "hrAgo")}"
        else -> "${diffSec / 86400}${MeskotStrings.t(lang, "dayAgo")}"
    }
}

@Composable
fun MeskotBrandMark(size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(Brush.linearGradient(listOf(MeskotGold, MeskotGoldDeep)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = 2.dp.toPx()
            val c = Color.White.copy(alpha = 0.72f)
            drawLine(c, Offset(this.size.width / 2f, 0f), Offset(this.size.width / 2f, this.size.height), stroke)
            drawLine(c, Offset(0f, this.size.height / 2f), Offset(this.size.width, this.size.height / 2f), stroke)
        }
    }
}

@Composable
fun UserAvatar(
    name: String,
    colorHex: Long = 0xFFC93E03,
    size: Dp = 40.dp,
    showOnlineDot: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(colorHex))
                .border(2.dp, MeskotLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsOf(name),
                color = Color.White,
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36f).sp
            )
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).coerceIn(10f, 14f).dp)
                    .clip(CircleShape)
                    .background(MeskotOnlineGreen)
                    .border(2.dp, MeskotCard, CircleShape)
            )
        }
    }
}

@Composable
fun PostCard(
    post: PostItem,
    currentUserId: String,
    authorProfile: UserProfile?,
    isSaved: Boolean,
    lang: AppLang,
    onAuthorClick: (String) -> Unit,
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
    var showReactionPicker by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var replyingToCommentId by remember { mutableStateOf<String?>(null) }
    var replyDraft by remember { mutableStateOf("") }
    var expandedText by remember { mutableStateOf(false) }

    val myReaction = post.reactions[currentUserId]
    val isMine = post.authorId == currentUserId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeskotCard),
        border = BorderStroke(1.dp, MeskotLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Signature Meskot royal arch accent stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(MeskotGold, MeskotCross, MeskotGoldDeep)))
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = post.authorName,
                    colorHex = authorProfile?.avatarColorHex ?: 0xFFC93E03,
                    size = 40.dp,
                    showOnlineDot = authorProfile?.isOnline == true,
                    onClick = { onAuthorClick(post.authorId) }
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).clickable { onAuthorClick(post.authorId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MeskotInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (authorProfile?.isVerified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Verified",
                                tint = MeskotGold,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = formatTimeAgo(post.timestampMs, lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MeskotMuted
                        )
                        Text("·", color = MeskotMuted, style = MaterialTheme.typography.bodySmall)
                        Icon(
                            imageVector = when (post.privacy) {
                                PostPrivacy.PUBLIC -> Icons.Outlined.Public
                                PostPrivacy.FRIENDS -> Icons.Outlined.Group
                                PostPrivacy.ONLY_ME -> Icons.Outlined.Lock
                            },
                            contentDescription = MeskotStrings.t(lang, post.privacy.labelKey),
                            tint = MeskotMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("post_menu_${post.id}")
                    ) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "Post options", tint = MeskotMuted)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(MeskotStrings.t(lang, if (isSaved) "unsavePostAction" else "savePostAction")) },
                            leadingIcon = { Icon(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                            onClick = { showMenu = false; onToggleSave(post.id) }
                        )
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text(MeskotStrings.t(lang, "editPostAction")) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { showMenu = false; showEditDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(MeskotStrings.t(lang, "deletePost"), color = MeskotCross) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MeskotCross) },
                                onClick = { showMenu = false; onDeletePost(post.id) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(MeskotStrings.t(lang, "dontWantSee")) },
                                leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                                onClick = { showMenu = false; onHidePost(post.id) }
                            )
                            DropdownMenuItem(
                                text = { Text(MeskotStrings.t(lang, "reportPostAction"), color = MeskotCross) },
                                leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = MeskotCross) },
                                onClick = { showMenu = false; onReportPost(post.id) }
                            )
                        }
                    }
                }
            }

            // Shared Post tag
            if (post.sharedPost != null) {
                Text(
                    text = MeskotStrings.t(lang, "sharedAPost"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MeskotMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Body Text (either colored card or standard)
            if (post.text.isNotBlank()) {
                val bgSwatch = PostBgSwatches.getOrElse(post.bgColorIndex) { Color.Transparent }
                if (bgSwatch != Color.Transparent && post.imageRes == null && post.imageUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgSwatch)
                            .defaultMinSize(minHeight = 160.dp)
                            .padding(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.text,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                    }
                } else {
                    val isLong = post.text.length > 150
                    Text(
                        text = post.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MeskotInk,
                        maxLines = if (!isLong || expandedText) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    if (isLong) {
                        Text(
                            text = MeskotStrings.t(lang, if (expandedText) "seeLess" else "seeMore"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MeskotGoldDeep,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clickable { expandedText = !expandedText }
                        )
                    }
                }
            }

            // Embedded Shared Post Box
            post.sharedPost?.let { sp ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MeskotPaper,
                    border = BorderStroke(1.dp, MeskotLine)
                ) {
                    Column {
                        Text(
                            text = sp.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                        )
                        if (sp.text.isNotBlank()) {
                            Text(
                                text = sp.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeskotInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        if (sp.imageRes != null) {
                            Image(
                                painter = painterResource(sp.imageRes),
                                contentDescription = "Shared photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(180.dp)
                            )
                        }
                    }
                }
            }

            // Post Image
            if (post.imageUri != null) {
                AsyncImage(
                    model = post.imageUri,
                    contentDescription = "Post media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 180.dp, max = 340.dp)
                )
            } else if (post.imageRes != null) {
                Image(
                    painter = painterResource(post.imageRes),
                    contentDescription = "Post photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(250.dp)
                )
            }

            // Reaction Summary Row
            if (post.reactions.isNotEmpty() || post.comments.isNotEmpty() || post.shareCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val topEmojis = post.reactions.values.groupingBy { it }.eachCount()
                            .entries.sortedByDescending { it.value }.take(3).map { it.key.emoji }
                        if (topEmojis.isNotEmpty()) {
                            Text(topEmojis.joinToString(""), fontSize = 14.sp)
                            Text("${post.reactions.size}", style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (post.comments.isNotEmpty()) {
                            Text(
                                "${post.comments.size} ${MeskotStrings.t(lang, "comments")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeskotMuted,
                                modifier = Modifier.clickable { showComments = !showComments }
                            )
                        }
                        if (post.shareCount > 0) {
                            Text(
                                "${post.shareCount} ${MeskotStrings.t(lang, "share")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeskotMuted
                            )
                        }
                    }
                }
            }

            // Reaction Picker Bar (when expanded)
            if (showReactionPicker) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MeskotCard,
                    border = BorderStroke(1.dp, MeskotLine),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReactionType.entries.forEach { r ->
                            TextButton(
                                onClick = {
                                    onReaction(post.id, r)
                                    showReactionPicker = false
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                            ) {
                                Text(r.emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MeskotLine)

            // Action Buttons (React / Comment / Share)
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        if (showReactionPicker) showReactionPicker = false
                        else onReaction(post.id, myReaction ?: ReactionType.LIKE)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("post_react_${post.id}")
                ) {
                    Text(
                        text = if (myReaction != null) "${myReaction.emoji} ${MeskotStrings.t(lang, myReaction.labelKey)}"
                        else "👍 ${MeskotStrings.t(lang, "reactLike")}",
                        color = if (myReaction != null) MeskotGold else MeskotMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(
                    onClick = { showReactionPicker = !showReactionPicker },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.AddReaction, contentDescription = "Pick reaction", tint = MeskotMuted, modifier = Modifier.size(18.dp))
                }
                TextButton(
                    onClick = { showComments = !showComments },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("post_comment_${post.id}")
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeskotMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(MeskotStrings.t(lang, "comment"), color = MeskotMuted, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = { onShare(post) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("post_share_${post.id}")
                ) {
                    Icon(Icons.Outlined.Share, null, tint = MeskotMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(MeskotStrings.t(lang, "share"), color = MeskotMuted, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Expandable Comments & Replies
            if (showComments) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeskotPaper)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    post.comments.forEach { comment ->
                        Column {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MeskotCard,
                                border = BorderStroke(1.dp, MeskotLine)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(comment.authorName, style = MaterialTheme.typography.labelMedium, color = MeskotInk)
                                        Text(formatTimeAgo(comment.timestampMs, lang), style = MaterialTheme.typography.labelSmall, color = MeskotMuted)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = MeskotInk)
                                }
                            }
                            Text(
                                text = MeskotStrings.t(lang, "reply"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MeskotGoldDeep,
                                modifier = Modifier
                                    .padding(start = 10.dp, top = 4.dp)
                                    .clickable {
                                        replyingToCommentId = if (replyingToCommentId == comment.id) null else comment.id
                                    }
                            )
                            comment.replies.forEach { reply ->
                                Surface(
                                    modifier = Modifier.padding(start = 24.dp, top = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MeskotCard,
                                    border = BorderStroke(1.dp, MeskotLine)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(reply.authorName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(reply.text, style = MaterialTheme.typography.bodySmall, color = MeskotInk)
                                    }
                                }
                            }
                            if (replyingToCommentId == comment.id) {
                                Row(
                                    modifier = Modifier.padding(start = 24.dp, top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = replyDraft,
                                        onValueChange = { replyDraft = it },
                                        placeholder = { Text(MeskotStrings.t(lang, "writeReply"), style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        if (replyDraft.isNotBlank()) {
                                            onAddReply(post.id, comment.id, replyDraft)
                                            replyDraft = ""
                                            replyingToCommentId = null
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply", tint = MeskotGold)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commentDraft,
                            onValueChange = { commentDraft = it },
                            placeholder = { Text(MeskotStrings.t(lang, "writeComment"), style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f).testTag("comment_input_${post.id}")
                        )
                        FilledIconButton(
                            onClick = {
                                if (commentDraft.isNotBlank()) {
                                    onAddComment(post.id, commentDraft)
                                    commentDraft = ""
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MeskotDarkAction),
                            modifier = Modifier.size(48.dp).testTag("comment_send_${post.id}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = MeskotStrings.t(lang, "send"), tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        var editedText by remember { mutableStateOf(post.text) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(MeskotStrings.t(lang, "editPostAction"), fontFamily = FrauncesFontFamily) },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEditPost(post.id, editedText); showEditDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "save")) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }
}

@Composable
fun FullPostComposerDialog(
    user: UserProfile,
    lang: AppLang,
    groupName: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (text: String, privacy: PostPrivacy, bgColorIndex: Int, imageUri: String?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PostPrivacy.PUBLIC) }
    var bgIndex by remember { mutableIntStateOf(0) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPrivacyPicker by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            bgIndex = 0
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MeskotPaper
        ) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = groupName ?: MeskotStrings.t(lang, "createPost"),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Button(
                        onClick = {
                            onSubmit(text, privacy, bgIndex, selectedImageUri?.toString())
                            onDismiss()
                        },
                        enabled = text.isNotBlank() || selectedImageUri != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                        modifier = Modifier.testTag("composer_post_button")
                    ) {
                        Text(MeskotStrings.t(lang, "post"))
                    }
                }
                HorizontalDivider(color = MeskotLine)

                // Profile + Privacy Pill
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(user.name, user.avatarColorHex, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = { showPrivacyPicker = true },
                            label = { Text(MeskotStrings.t(lang, privacy.labelKey), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    when (privacy) {
                                        PostPrivacy.PUBLIC -> Icons.Outlined.Public
                                        PostPrivacy.FRIENDS -> Icons.Outlined.Group
                                        PostPrivacy.ONLY_ME -> Icons.Outlined.Lock
                                    },
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                // Composer Input
                val activeBg = PostBgSwatches.getOrElse(bgIndex) { Color.Transparent }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (activeBg == Color.Transparent) Color.Transparent else activeBg)
                        .padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = {
                            Text(
                                MeskotStrings.t(lang, "composerPh"),
                                color = if (activeBg != Color.Transparent) Color.White.copy(alpha = 0.8f) else MeskotMuted,
                                fontSize = 18.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = if (activeBg != Color.Transparent) 22.sp else 18.sp,
                            color = if (activeBg != Color.Transparent) Color.White else MeskotInk,
                            textAlign = if (activeBg != Color.Transparent) TextAlign.Center else TextAlign.Start
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxSize().testTag("composer_text_input")
                    )
                }

                // Selected Photo Preview
                selectedImageUri?.let { uri ->
                    Box(modifier = Modifier.padding(16.dp).height(140.dp).clip(RoundedCornerShape(12.dp))) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White)
                        }
                    }
                }

                // Background Swatches (when no photo selected)
                if (selectedImageUri == null) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(PostBgSwatches) { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (color == Color.Transparent) MeskotPaper2 else color)
                                    .border(
                                        width = if (bgIndex == idx) 2.5.dp else 1.dp,
                                        color = if (bgIndex == idx) MeskotGold else MeskotLine,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { bgIndex = idx },
                                contentAlignment = Alignment.Center
                            ) {
                                if (idx == 0) Text("Aa", style = MaterialTheme.typography.labelSmall, color = MeskotInk)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MeskotLine)

                // Add Photo Button (uses Android Photo Picker — zero storage permissions)
                TextButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth().padding(12.dp).height(48.dp)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = MeskotGold)
                    Spacer(Modifier.width(8.dp))
                    Text(MeskotStrings.t(lang, "btnPhoto"), color = MeskotInk, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showPrivacyPicker) {
        AlertDialog(
            onDismissRequest = { showPrivacyPicker = false },
            title = { Text(MeskotStrings.t(lang, "whoCanSee"), fontFamily = FrauncesFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PostPrivacy.entries.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { privacy = opt; showPrivacyPicker = false }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = privacy == opt, onClick = { privacy = opt; showPrivacyPicker = false })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(MeskotStrings.t(lang, opt.labelKey), style = MaterialTheme.typography.titleSmall)
                                Text(MeskotStrings.t(lang, opt.subKey), style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPicker = false }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }
}

@Composable
fun EditProfileDialog(
    user: UserProfile,
    lang: AppLang,
    onDismiss: () -> Unit,
    onSave: (name: String, bio: String, location: String) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var bio by remember { mutableStateOf(user.bio) }
    var location by remember { mutableStateOf(user.location) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MeskotStrings.t(lang, "editProfile"), fontFamily = FrauncesFontFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(MeskotStrings.t(lang, "fullName")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(MeskotStrings.t(lang, "bio")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, bio, location); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
            ) { Text(MeskotStrings.t(lang, "save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(MeskotStrings.t(lang, "cancel")) }
        }
    )
}
