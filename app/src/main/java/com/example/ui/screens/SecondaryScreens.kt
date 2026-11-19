package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.PostItem
import com.example.model.ReactionType
import com.example.ui.components.PostCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeAgo
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun NotificationsScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    LaunchedEffect(Unit) { onMarkAllRead() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(MeskotStrings.t(lang, "notifications"), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(10.dp))
        if (state.notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MeskotStrings.t(lang, "noNotifs"), color = MeskotMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.notifications, key = { it.id }) { n ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenProfile(n.actorId) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MeskotCard),
                        border = BorderStroke(if (!n.isRead) 1.5.dp else 1.dp, if (!n.isRead) MeskotGold else MeskotLine)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(n.actorName, state.users[n.actorId]?.avatarColorHex ?: 0xFFC93E03, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${n.actorName} ${if (lang == AppLang.AM) n.messageAm else n.messageEn}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(formatTimeAgo(n.timestampMs, lang), style = MaterialTheme.typography.labelSmall, color = MeskotMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedPostsScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
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
    val savedPosts = state.posts.filter { it.id in state.savedPostIds }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(MeskotStrings.t(lang, "saved"), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(10.dp))
        if (savedPosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MeskotStrings.t(lang, "noSaved"), color = MeskotMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        currentUserId = me.uid,
                        authorProfile = state.users[post.authorId],
                        isSaved = true,
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
    }
}

@Composable
fun PhotosAlbumsScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onCreateAlbum: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(MeskotStrings.t(lang, "myAlbums"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)) {
                Text(MeskotStrings.t(lang, "createAlbum"))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MeskotStrings.t(lang, "noAlbumsYet"), color = MeskotMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.albums, key = { it.id }) { album ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MeskotCard),
                        border = BorderStroke(1.dp, MeskotLine)
                    ) {
                        Column {
                            val coverRes = album.photos.firstOrNull()
                            if (coverRes != null) {
                                Image(
                                    painter = painterResource(coverRes),
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(136.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(Brush.linearGradient(listOf(MeskotGold, MeskotCross))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(34.dp))
                                }
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(album.title, style = MaterialTheme.typography.titleSmall)
                                Text("${album.photos.size + album.photoUris.size} items", style = MaterialTheme.typography.labelSmall, color = MeskotMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(MeskotStrings.t(lang, "createAlbum"), fontFamily = FrauncesFontFamily) },
            text = {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(MeskotStrings.t(lang, "albumTitle")) }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = { onCreateAlbum(title); showCreate = false }, enabled = title.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)) {
                    Text(MeskotStrings.t(lang, "create"))
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(MeskotStrings.t(lang, "cancel")) } }
        )
    }
}

@Composable
fun ReelsScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onCreateReel: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onAddComment: (String, String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(MeskotStrings.t(lang, "reels"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)) {
                Text(MeskotStrings.t(lang, "createReel"))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.reels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MeskotStrings.t(lang, "noReelsYet"), color = MeskotMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(state.reels, key = { it.id }) { reel ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(340.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val coverRes = reel.coverRes
                            if (coverRes != null) {
                                Image(
                                    painter = painterResource(coverRes),
                                    contentDescription = reel.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        Brush.linearGradient(listOf(MeskotDarkAction, MeskotCross, MeskotGoldDeep))
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                                )
                            )
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(0.78f)
                            ) {
                                Text(reel.authorName, color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(reel.caption, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(onClick = { onToggleLike(reel.id) }) {
                                    Icon(
                                        if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like reel",
                                        tint = if (reel.isLiked) MeskotGold else Color.White
                                    )
                                }
                                Text("${reel.likes}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var caption by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(MeskotStrings.t(lang, "createReel"), fontFamily = FrauncesFontFamily) },
            text = {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(MeskotStrings.t(lang, "reelCaption")) },
                    minLines = 2
                )
            },
            confirmButton = {
                Button(
                    onClick = { onCreateReel(caption); showCreate = false },
                    enabled = caption.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) {
                    Text(MeskotStrings.t(lang, "post"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }
}

@Composable
fun CreatorStudioScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit
) {
    val me = state.currentUser ?: return
    val myPosts = state.posts.filter { it.authorId == me.uid }
    val totalReactions = myPosts.sumOf { it.reactions.size }
    val totalComments = myPosts.sumOf { it.comments.size }
    val numFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(MeskotStrings.t(lang, "creatorStudio"), style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotDarkAction)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("WALLET BALANCE · CHAPA PAY", color = Color(0xFFFFC199), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("ETB ${numFormat.format(me.walletEtb)}", color = Color.White, fontFamily = FrauncesFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${me.stars} Stars · Payment gateway TODO", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Posts", "${myPosts.size}", Modifier.weight(1f))
                StatCard("Reactions", "$totalReactions", Modifier.weight(1f))
                StatCard("Comments", "$totalComments", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AdsManagerScreen(
    state: MeskotDataState,
    lang: AppLang,
    onBack: () -> Unit,
    onCreateCampaign: (String, String, Double, Int) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    val numFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(MeskotStrings.t(lang, "adsManager"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)) {
                    Text("+ Create Ad")
                }
            }
        }
        item {
            val totalImp = state.adCampaigns.sumOf { it.impressions }
            val totalClicks = state.adCampaigns.sumOf { it.clicks }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Impressions", numFormat.format(totalImp), Modifier.weight(1f))
                StatCard("Clicks", numFormat.format(totalClicks), Modifier.weight(1f))
                StatCard("Active", "${state.adCampaigns.count { it.active }}", Modifier.weight(1f))
            }
        }
        items(state.adCampaigns, key = { it.id }) { ad ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotCard),
                border = BorderStroke(1.dp, MeskotLine)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(ad.title, style = MaterialTheme.typography.titleSmall)
                    Text("${ad.objective} · ${ad.days} days · ETB ${numFormat.format(ad.budgetEtb)}", style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
                    Spacer(Modifier.height(6.dp))
                    Text("${numFormat.format(ad.impressions)} impressions · ${numFormat.format(ad.clicks)} clicks", style = MaterialTheme.typography.labelMedium, color = MeskotGoldDeep)
                }
            }
        }
    }

    if (showCreate) {
        var title by remember { mutableStateOf("") }
        var budget by remember { mutableStateOf("1500") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Ad Campaign", fontFamily = FrauncesFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Campaign title") }, singleLine = true)
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it },
                        label = { Text("Budget (ETB)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onCreateCampaign(title, "Boost Meskot content", budget.toDoubleOrNull() ?: 1500.0, 7); showCreate = false },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text("Launch") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(MeskotStrings.t(lang, "cancel")) } }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeskotCard),
        border = BorderStroke(1.dp, MeskotLine)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = FrauncesFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MeskotGoldDeep)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MeskotMuted, textAlign = TextAlign.Center)
        }
    }
}
