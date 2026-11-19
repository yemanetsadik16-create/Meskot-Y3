package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MeskotDataState
import com.example.i18n.AppLang
import com.example.i18n.MeskotStrings
import com.example.model.MarketListing
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MarketScreen(
    state: MeskotDataState,
    lang: AppLang,
    onCreateListing: (title: String, priceEtb: Double, category: String, city: String, description: String) -> Unit,
    onToggleSaveListing: (String) -> Unit,
    onMessageSeller: (String) -> Unit
) {
    val categories = listOf("All", "Fashion", "Home", "Art", "Electronics", "Vehicles")
    val cities = listOf("All Cities", "Addis Ababa", "Mekelle", "Hawassa", "Bahir Dar", "Gondar")

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedCity by remember { mutableStateOf("All Cities") }
    var savedOnly by remember { mutableStateOf(false) }
    var showSellDialog by remember { mutableStateOf(false) }
    var showCityMenu by remember { mutableStateOf(false) }
    var detailListing by remember { mutableStateOf<MarketListing?>(null) }

    val filtered = remember(state.listings, selectedCategory, selectedCity, savedOnly, state.savedListingIds) {
        state.listings.filter { item ->
            (selectedCategory == "All" || item.category == selectedCategory) &&
                (selectedCity == "All Cities" || item.city == selectedCity) &&
                (!savedOnly || item.id in state.savedListingIds)
        }
    }

    val numFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        // Header + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(MeskotStrings.t(lang, "marketplace"), style = MaterialTheme.typography.headlineSmall)
                Text("Buy, Sell & Local Artisans", style = MaterialTheme.typography.bodySmall, color = MeskotMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = savedOnly,
                    onClick = { savedOnly = !savedOnly },
                    label = { Text("Saved") }
                )
                Button(
                    onClick = { showSellDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("market_sell_button")
                ) {
                    Text(MeskotStrings.t(lang, "sellItem"), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Category Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Today's picks + City Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${MeskotStrings.t(lang, "todaysPicks")} (${filtered.size})",
                style = MaterialTheme.typography.titleSmall
            )
            Box {
                TextButton(onClick = { showCityMenu = true }) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MeskotGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(selectedCity, color = MeskotGoldDeep, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = showCityMenu, onDismissRequest = { showCityMenu = false }) {
                    cities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = { selectedCity = city; showCityMenu = false }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(MeskotStrings.t(lang, "noListings"), color = MeskotMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { listing ->
                    val isSaved = listing.id in state.savedListingIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailListing = listing }
                            .testTag("listing_card_${listing.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MeskotCard),
                        border = BorderStroke(1.dp, MeskotLine)
                    ) {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().height(132.dp)) {
                                if (listing.imageRes != null) {
                                    Image(
                                        painter = painterResource(listing.imageRes),
                                        contentDescription = listing.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleSaveListing(listing.id) },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Save listing",
                                        tint = if (isSaved) MeskotGold else Color.White
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "ETB ${numFormat.format(listing.priceEtb)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MeskotGoldDeep,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    listing.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listing.city,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeskotMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detailListing?.let { item ->
        AlertDialog(
            onDismissRequest = { detailListing = null },
            title = { Text(item.title, fontFamily = FrauncesFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ETB ${numFormat.format(item.priceEtb)}", color = MeskotGoldDeep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${item.category} · ${item.city}", style = MaterialTheme.typography.labelMedium, color = MeskotMuted)
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                    Text("Seller: ${item.sellerName}", style = MaterialTheme.typography.labelSmall, color = MeskotMuted)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        detailListing = null
                        if (item.sellerId != state.currentUser?.uid) onMessageSeller(item.sellerId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "messageBtn")) }
            },
            dismissButton = {
                TextButton(onClick = { detailListing = null }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }

    if (showSellDialog) {
        var title by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Fashion") }
        var city by remember { mutableStateOf("Addis Ababa") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSellDialog = false },
            title = { Text("Create Listing", fontFamily = FrauncesFontFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price (ETB)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateListing(title, price.toDoubleOrNull() ?: 1000.0, category, city, desc)
                        showSellDialog = false
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeskotGold)
                ) { Text(MeskotStrings.t(lang, "post")) }
            },
            dismissButton = {
                TextButton(onClick = { showSellDialog = false }) { Text(MeskotStrings.t(lang, "cancel")) }
            }
        )
    }
}
