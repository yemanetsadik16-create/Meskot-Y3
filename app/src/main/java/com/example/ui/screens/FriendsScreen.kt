package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.UserProfile
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*

@Composable
fun FriendsScreen(
    state: MeskotDataState,
    lang: AppLang,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onCancelRequest: (String) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onUnfriend: (String) -> Unit
) {
    val me = state.currentUser ?: return
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Discover, 1=Requests, 2=My Friends
    var query by remember { mutableStateOf("") }

    val incomingReqs = state.friendRequests.filter { it.toUid == me.uid }
    val outgoingReqUids = state.friendRequests.filter { it.fromUid == me.uid }.map { it.toUid }.toSet()
    val allOtherUsers = state.users.values.filter { it.uid != me.uid }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Segmented Tabs
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MeskotCard,
            border = BorderStroke(1.dp, MeskotLine)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val tabs = listOf(
                    MeskotStrings.t(lang, "tabDiscover"),
                    "${MeskotStrings.t(lang, "tabRequests")}${if (incomingReqs.isNotEmpty()) " (${incomingReqs.size})" else ""}",
                    MeskotStrings.t(lang, "tabMyFriends")
                )
                tabs.forEachIndexed { idx, label ->
                    val active = selectedTab == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { selectedTab = idx }
                            .testTag("friends_tab_$idx"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MeskotDarkAction else Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) Color.White else MeskotMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(MeskotStrings.t(lang, "searchPeople")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MeskotMuted) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MeskotCard,
                    unfocusedContainerColor = MeskotCard
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("friends_search_input")
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    val filtered = allOtherUsers.filter {
                        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.location.contains(query, ignoreCase = true)
                    }
                    items(filtered, key = { it.uid }) { user ->
                        val isFriend = user.uid in state.friendIds
                        val isOutgoing = user.uid in outgoingReqUids
                        val isIncoming = incomingReqs.any { it.fromUid == user.uid }
                        PersonRowCard(
                            user = user,
                            onProfileClick = { onOpenProfile(user.uid) },
                            actions = {
                                when {
                                    isFriend -> {
                                        OutlinedButton(onClick = { onOpenChat(user.uid) }, modifier = Modifier.weight(1f)) {
                                            Text(MeskotStrings.t(lang, "messageBtn"))
                                        }
                                        TextButton(onClick = { onUnfriend(user.uid) }) {
                                            Text(MeskotStrings.t(lang, "unfriend"), color = MeskotCross)
                                        }
                                    }
                                    isIncoming -> {
                                        Button(
                                            onClick = { onAcceptRequest(user.uid) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                                            modifier = Modifier.weight(1f)
                                        ) { Text(MeskotStrings.t(lang, "accept")) }
                                        OutlinedButton(onClick = { onDeclineRequest(user.uid) }, modifier = Modifier.weight(1f)) {
                                            Text(MeskotStrings.t(lang, "decline"))
                                        }
                                    }
                                    isOutgoing -> {
                                        OutlinedButton(onClick = { onCancelRequest(user.uid) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("${MeskotStrings.t(lang, "requested")} · ${MeskotStrings.t(lang, "cancelReq")}")
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = { onSendRequest(user.uid) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MeskotDarkAction),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text(MeskotStrings.t(lang, "addFriend")) }
                                    }
                                }
                            }
                        )
                    }
                }
                1 -> {
                    if (incomingReqs.isEmpty()) {
                        item {
                            Text(
                                MeskotStrings.t(lang, "noRequests"),
                                color = MeskotMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                        }
                    } else {
                        items(incomingReqs, key = { it.id }) { req ->
                            val sender = state.users[req.fromUid] ?: return@items
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MeskotCard),
                                border = BorderStroke(1.dp, MeskotLine)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    UserAvatar(sender.name, sender.avatarColorHex, size = 72.dp, showOnlineDot = sender.isOnline, onClick = { onOpenProfile(sender.uid) })
                                    Spacer(Modifier.height(10.dp))
                                    Text(sender.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(MeskotStrings.t(lang, "incomingSub"), style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
                                    Spacer(Modifier.height(14.dp))
                                    Button(
                                        onClick = { onAcceptRequest(sender.uid) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) { Text(MeskotStrings.t(lang, "accept"), fontWeight = FontWeight.Bold) }
                                    Spacer(Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = { onDeclineRequest(sender.uid) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) { Text(MeskotStrings.t(lang, "decline")) }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val friends = state.friendIds.mapNotNull { state.users[it] }
                    if (friends.isEmpty()) {
                        item {
                            Text(
                                MeskotStrings.t(lang, "noFriendsYet"),
                                color = MeskotMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
                            )
                        }
                    } else {
                        items(friends, key = { it.uid }) { friend ->
                            PersonRowCard(
                                user = friend,
                                onProfileClick = { onOpenProfile(friend.uid) },
                                actions = {
                                    Button(
                                        onClick = { onOpenChat(friend.uid) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeskotDarkAction),
                                        modifier = Modifier.weight(1f)
                                    ) { Text(MeskotStrings.t(lang, "messageBtn")) }
                                    OutlinedButton(onClick = { onUnfriend(friend.uid) }) {
                                        Text(MeskotStrings.t(lang, "unfriend"), color = MeskotCross)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRowCard(
    user: UserProfile,
    onProfileClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeskotCard),
        border = BorderStroke(1.dp, MeskotLine)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            UserAvatar(user.name, user.avatarColorHex, size = 48.dp, showOnlineDot = user.isOnline, onClick = onProfileClick)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clickable(onClick = onProfileClick)
                )
                Text(user.location, style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
                if (user.bio.isNotBlank()) {
                    Text(user.bio, style = MaterialTheme.typography.bodySmall, color = MeskotMuted, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}
