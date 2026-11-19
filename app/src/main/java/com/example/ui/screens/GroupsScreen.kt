package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.PostItem
import com.example.model.PostPrivacy
import com.example.model.ReactionType
import com.example.ui.components.FullPostComposerDialog
import com.example.ui.components.PostCard
import com.example.ui.components.initialsOf
import com.example.ui.theme.*

@Composable
fun GroupsScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onCreateGroup: (String, String) -> Unit,
    onToggleMembership: (String) -> Unit,
    onCreateGroupPost: (String, String, PostPrivacy, Int, String?) -> Unit,
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
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Discover, 1=My Groups
    var query by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val selectedGroup = state.groups.find { it.id == selectedGroupId }

    if (selectedGroup != null) {
        BackHandler { selectedGroupId = null }
        val isMember = me.uid in selectedGroup.memberIds
        val groupPosts = state.posts.filter { it.groupId == selectedGroup.id }
        var showComposer by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedGroupId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to groups")
                    }
                    Text(selectedGroup.name, style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MeskotCard),
                    border = BorderStroke(1.dp, MeskotLine)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(76.dp).background(Color(selectedGroup.coverColorHex)))
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(selectedGroup.name, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(selectedGroup.description, style = MaterialTheme.typography.bodyMedium, color = MeskotMuted, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text("${selectedGroup.memberIds.size} ${MeskotStrings.t(lang, "members")}", style = MaterialTheme.typography.labelMedium, color = MeskotGoldDeep)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onToggleMembership(selectedGroup.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isMember) MeskotPaper2 else MeskotGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    MeskotStrings.t(lang, if (isMember) "leave" else "join"),
                                    color = if (isMember) MeskotInk else Color.White
                                )
                            }
                        }
                    }
                }
            }
            item {
                if (isMember) {
                    Button(
                        onClick = { showComposer = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MeskotDarkAction),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(MeskotStrings.t(lang, "whatsHappening")) }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeskotCard,
                        border = BorderStroke(1.dp, MeskotLine),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            MeskotStrings.t(lang, "joinToPost"),
                            color = MeskotMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            items(groupPosts, key = { it.id }) { post ->
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

        if (showComposer) {
            FullPostComposerDialog(
                user = me,
                lang = lang,
                groupName = selectedGroup.name,
                onDismiss = { showComposer = false },
                onSubmit = { text, privacy, bg, uri ->
                    onCreateGroupPost(selectedGroup.id, text, privacy, bg, uri)
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(MeskotStrings.t(lang, "navGroups"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "createGroup")) }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text(MeskotStrings.t(lang, "tabDiscover")) })
                FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text(MeskotStrings.t(lang, "tabMyGroups")) })
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(MeskotStrings.t(lang, "searchGroups")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MeskotMuted) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            val filtered = state.groups.filter { g ->
                (selectedTab == 0 || me.uid in g.memberIds) &&
                    (query.isBlank() || g.name.contains(query, ignoreCase = true) || g.description.contains(query, ignoreCase = true))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { g ->
                    val isMember = me.uid in g.memberIds
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedGroupId = g.id },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MeskotCard),
                        border = BorderStroke(1.dp, MeskotLine)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(g.coverColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initialsOf(g.name), color = Color.White, fontFamily = FrauncesFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.titleSmall)
                                Text(g.description, style = MaterialTheme.typography.bodySmall, color = MeskotMuted, maxLines = 2)
                                Text("${g.memberIds.size} ${MeskotStrings.t(lang, "members")}", style = MaterialTheme.typography.labelSmall, color = MeskotGoldDeep)
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { onToggleMembership(g.id) }) {
                                Text(MeskotStrings.t(lang, if (isMember) "leave" else "join"))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(MeskotStrings.t(lang, "createGroup"), fontFamily = FrauncesFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(MeskotStrings.t(lang, "groupName")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(MeskotStrings.t(lang, "groupDesc")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCreateGroup(name, desc); showCreateDialog = false },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "create")) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }
}
