package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.UserAvatar
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HubScreen(
    state: MeskotDataState,
    lang: AppLang,
    onSetLang: (AppLang) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenReels: () -> Unit,
    onOpenCreator: () -> Unit,
    onOpenAds: () -> Unit,
    onOpenMarket: () -> Unit,
    onUpdateProfile: (String, String, String) -> Unit,
    onLogout: () -> Unit
) {
    val me = state.currentUser ?: return
    var showEditProfile by remember { mutableStateOf(false) }
    val numFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("hub_screen"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Meskot Royal Passport Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotDarkAction),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(MeskotDarkAction, Color(0xFF2B2020), MeskotGoldDeep.copy(alpha = 0.85f)))
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenProfile(me.uid) }
                    ) {
                        UserAvatar(me.name, me.avatarColorHex, size = 56.dp, showOnlineDot = true)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "✦ ${MeskotStrings.t(lang, "royalPassport")}",
                                color = Color(0xFFFFC199),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(me.name, color = Color.White, fontFamily = FrauncesFontFamily, style = MaterialTheme.typography.titleLarge)
                                if (me.isVerified) {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(Icons.Filled.Verified, null, tint = Color(0xFFFFC199), modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(MeskotStrings.t(lang, "viewProfile"), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PassportStat("CIRCLE", "${state.friendIds.size}")
                        PassportStat("STARS", "${me.stars} ★")
                        PassportStat("WALLET", "ETB ${numFormat.format(me.walletEtb)}")
                    }
                }
            }
        }

        // Executive Suite (Commerce & Creator Lounge)
        item {
            Text("EXECUTIVE SUITE · የንግድ ማዕከል", style = MaterialTheme.typography.labelSmall, color = MeskotGoldDeep, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SuiteTile(Icons.Outlined.Storefront, MeskotStrings.t(lang, "marketplace"), "Bazaar", Modifier.weight(1f), onOpenMarket)
                SuiteTile(Icons.Outlined.Campaign, MeskotStrings.t(lang, "adsManager"), "${state.adCampaigns.size} Active", Modifier.weight(1f), onOpenAds)
                SuiteTile(Icons.Outlined.AutoGraph, MeskotStrings.t(lang, "creatorStudio"), "Chapa", Modifier.weight(1f), onOpenCreator)
            }
        }

        // Community Portals Grid
        item {
            Text("MESKOT PORTALS · የማህበረሰብ መስኮቶች", style = MaterialTheme.typography.labelSmall, color = MeskotGoldDeep, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PortalCard(Icons.Outlined.Groups, MeskotStrings.t(lang, "navGroups"), "${state.groups.size} circles", Modifier.weight(1f), onOpenGroups)
                    PortalCard(Icons.Outlined.PhotoLibrary, MeskotStrings.t(lang, "myAlbums"), "${state.albums.size} albums", Modifier.weight(1f), onOpenPhotos)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PortalCard(Icons.Outlined.PlayCircle, MeskotStrings.t(lang, "reels"), "${state.reels.size} clips", Modifier.weight(1f), onOpenReels)
                    PortalCard(Icons.Outlined.BookmarkBorder, MeskotStrings.t(lang, "saved"), "${state.savedPostIds.size} saved", Modifier.weight(1f), onOpenSaved)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val unread = state.notifications.count { !it.isRead }
                    PortalCard(Icons.Outlined.Notifications, MeskotStrings.t(lang, "notifications"), if (unread > 0) "$unread unread" else "All caught up", Modifier.weight(1f), onOpenNotifications)
                    PortalCard(Icons.Outlined.Person, MeskotStrings.t(lang, "editProfile"), me.location, Modifier.weight(1f)) { showEditProfile = true }
                }
            }
        }

        // Language & Session Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeskotCard),
                border = BorderStroke(1.dp, MeskotLine)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(MeskotStrings.t(lang, "languageLbl"), style = MaterialTheme.typography.titleSmall)
                        Surface(shape = RoundedCornerShape(20.dp), color = MeskotPaper2, border = BorderStroke(1.dp, MeskotLine)) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                AppLang.entries.forEach { opt ->
                                    val selected = lang == opt
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) MeskotDarkAction else Color.Transparent)
                                            .clickable { onSetLang(opt) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                            .testTag("hub_lang_${opt.code}")
                                    ) {
                                        Text(opt.badge, color = if (selected) Color.White else MeskotMuted, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MeskotLine)

                    OutlinedButton(
                        onClick = onLogout,
                        border = BorderStroke(1.dp, MeskotCross.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("hub_logout_button")
                    ) {
                        Icon(Icons.Outlined.Logout, null, tint = MeskotCross, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(MeskotStrings.t(lang, "logout"), color = MeskotCross, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            user = me,
            lang = lang,
            onDismiss = { showEditProfile = false },
            onSave = onUpdateProfile
        )
    }
}

@Composable
private fun PassportStat(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SuiteTile(icon: ImageVector, title: String, chip: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeskotCard),
        border = BorderStroke(1.dp, MeskotLine)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = title, tint = MeskotGold, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Surface(color = MeskotPaper2, shape = RoundedCornerShape(8.dp)) {
                Text(chip, style = MaterialTheme.typography.labelSmall, color = MeskotMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun PortalCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeskotCard),
        border = BorderStroke(1.dp, MeskotLine)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = title, tint = MeskotDarkAction, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MeskotMuted)
        }
    }
}
