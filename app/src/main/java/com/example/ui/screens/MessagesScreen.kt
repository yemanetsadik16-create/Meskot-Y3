package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.ChatMessage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.*

@Composable
fun MessagesScreen(
    state: MeskotDataState,
    lang: AppLang,
    activeConvoId: String?,
    onSelectConvo: (String?) -> Unit,
    onStartChatWithUser: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onEditMessage: (String, String, String) -> Unit,
    onDeleteMessage: (String, String) -> Unit,
    onStartCall: (convoId: String, peerUid: String, peerName: String, callType: String) -> Unit
) {
    val me = state.currentUser ?: return
    val activeConvo = state.conversations.find { it.id == activeConvoId }

    if (activeConvo != null) {
        BackHandler { onSelectConvo(null) }
        val otherUid = activeConvo.participantIds.firstOrNull { it != me.uid } ?: me.uid
        val otherUser = state.users[otherUid]
        val otherDisplayName = otherUser?.name ?: "Member"
        ChatThreadView(
            convoId = activeConvo.id,
            myUid = me.uid,
            otherName = otherDisplayName,
            otherColor = otherUser?.avatarColorHex ?: 0xFFC93E03,
            isOnline = otherUser?.isOnline == true,
            messages = activeConvo.messages,
            lang = lang,
            onBack = { onSelectConvo(null) },
            onOpenProfile = { onOpenProfile(otherUid) },
            onSend = { onSendMessage(activeConvo.id, it) },
            onEdit = { msgId, txt -> onEditMessage(activeConvo.id, msgId, txt) },
            onDelete = { msgId -> onDeleteMessage(activeConvo.id, msgId) },
            onStartCall = { callType -> onStartCall(activeConvo.id, otherUid, otherDisplayName, callType) }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                MeskotStrings.t(lang, "navMessages"),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Online friends story strip
            val onlineFriends = state.friendIds.mapNotNull { state.users[it] }
            if (onlineFriends.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    items(onlineFriends, key = { it.uid }) { friend ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(64.dp).clickable { onStartChatWithUser(friend.uid) }
                        ) {
                            UserAvatar(friend.name, friend.avatarColorHex, size = 54.dp, showOnlineDot = friend.isOnline)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                friend.name.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = MeskotLine, modifier = Modifier.padding(bottom = 12.dp))
            }

            if (state.conversations.isEmpty()) {
                Text(
                    MeskotStrings.t(lang, "noConvos"),
                    color = MeskotMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.conversations, key = { it.id }) { convo ->
                        val otherUid = convo.participantIds.firstOrNull { it != me.uid } ?: me.uid
                        val other = state.users[otherUid]
                        val lastMsg = convo.messages.lastOrNull()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectConvo(convo.id) }
                                .testTag("convo_row_${convo.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeskotCard),
                            border = BorderStroke(1.dp, MeskotLine)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    name = other?.name ?: "Member",
                                    colorHex = other?.avatarColorHex ?: 0xFFC93E03,
                                    size = 46.dp,
                                    showOnlineDot = other?.isOnline == true
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(other?.name ?: "Member", style = MaterialTheme.typography.titleSmall)
                                    val previewText = lastMsg?.text ?: convo.lastMessage.ifBlank { "Say hello 👋" }
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MeskotMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val ts = lastMsg?.timestampMs ?: convo.updatedAtMs
                                    if (ts > 0L) {
                                        Text(formatTimeAgo(ts, lang), style = MaterialTheme.typography.labelSmall, color = MeskotMuted)
                                    }
                                    if (convo.unreadCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MeskotCross))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadView(
    convoId: String,
    myUid: String,
    otherName: String,
    otherColor: Long,
    isOnline: Boolean,
    messages: List<ChatMessage>,
    lang: AppLang,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onSend: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onStartCall: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var editingMsg by remember { mutableStateOf<ChatMessage?>(null) }

    val displayed = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MeskotPaper)) {
        // Chat Header
        Surface(color = MeskotCard, border = BorderStroke(1.dp, MeskotLine)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                UserAvatar(otherName, otherColor, size = 38.dp, showOnlineDot = isOnline, onClick = onOpenProfile)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                    Text(otherName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (isOnline) "Active now" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) MeskotOnlineGreen else MeskotMuted
                    )
                }
                IconButton(onClick = { searchOpen = !searchOpen }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search chat", tint = MeskotMuted)
                }
                IconButton(
                    onClick = { onStartCall("audio") },
                    modifier = Modifier.testTag("chat_audio_call_button")
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = "Audio call", tint = MeskotGold)
                }
                IconButton(
                    onClick = { onStartCall("video") },
                    modifier = Modifier.testTag("chat_video_call_button")
                ) {
                    Icon(Icons.Outlined.Videocam, contentDescription = "Video call", tint = MeskotGold)
                }
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search in this chat…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }

        // Messages List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayed, key = { it.id }) { msg ->
                val isMine = msg.senderId == myUid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMine) 16.dp else 4.dp,
                            bottomEnd = if (isMine) 4.dp else 16.dp
                        ),
                        color = if (isMine) MeskotDarkAction else MeskotCard,
                        border = if (isMine) null else BorderStroke(1.dp, MeskotLine),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clickable(enabled = isMine) { editingMsg = msg }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
                            Text(
                                text = msg.text,
                                color = if (isMine) Color.White else MeskotInk,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatTimeAgo(msg.timestampMs, lang) + if (msg.isEdited) " · edited" else "",
                                color = if (isMine) Color.White.copy(alpha = 0.65f) else MeskotMuted,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Input Bar
        Surface(color = MeskotCard, border = BorderStroke(1.dp, MeskotLine)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(MeskotStrings.t(lang, "typeMessage")) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f).testTag("chat_input_$convoId")
                )
                FilledIconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSend(draft)
                            draft = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MeskotGold),
                    modifier = Modifier.size(48.dp).testTag("chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }

    editingMsg?.let { msg ->
        var edited by remember { mutableStateOf(msg.text) }
        AlertDialog(
            onDismissRequest = { editingMsg = null },
            title = { Text("Message", fontFamily = FrauncesFontFamily) },
            text = {
                OutlinedTextField(value = edited, onValueChange = { edited = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(
                    onClick = { onEdit(msg.id, edited); editingMsg = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "save")) }
            },
            dismissButton = {
                TextButton(onClick = { onDelete(msg.id); editingMsg = null }) {
                    Text(MeskotStrings.t(lang, "deletePost"), color = MeskotCross)
                }
            }
        )
    }
}
