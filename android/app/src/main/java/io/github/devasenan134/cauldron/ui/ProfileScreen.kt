package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.Catalog
import io.github.devasenan134.cauldron.data.Cooked
import io.github.devasenan134.cauldron.data.FolderSummary
import io.github.devasenan134.cauldron.data.Profile
import io.github.devasenan134.cauldron.data.RecipeSummary
import io.github.devasenan134.cauldron.data.Session
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Your profile, like Cook Well's: who you are, how you've been cooking, then Cooked and Catalog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(openRecipe: (Int) -> Unit, openFolder: (Int) -> Unit, openIngredients: () -> Unit = {}) {
    val app = app()
    val me = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me
    val update by app.updates.available.collectAsState()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var cooked by remember { mutableStateOf<List<Cooked>?>(null) }
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var tab by rememberSaveable { mutableStateOf("cooked") }
    var shelf by rememberSaveable { mutableStateOf("mine") } // mine | favorites | folders
    var newFolder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val open = LocalOpenSettings.current

    suspend fun load() {
        runCatching { profile = app.api.profile() }
        runCatching { cooked = app.api.cooked() }
        runCatching { catalog = app.api.catalog() }
    }
    LaunchedEffect(LocalRefresh.current) { load() }

    Column(Modifier.fillMaxSize()) {
    // The same header as every other tab (it keeps clear of the status bar).
    ScreenHeader("Profile", subtitle = "Your cooking and catalog") {
        // Ingredients and their macros (edit a food and every recipe follows).
        Row(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(C.surface).border(1.dp, C.line, RoundedCornerShape(50))
            .pressable(openIngredients).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🥕 Ingredients", fontWeight = FontWeight.SemiBold, color = C.ink)
        }
        BadgedBox(badge = { if (update != null) Badge(containerColor = C.go) }) {
            FloatingCircle(open) { Icon(Icons.Outlined.Settings, "Settings", tint = C.ink) }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding()) {
        // Who you are
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val initial = (me?.name?.ifBlank { null } ?: me?.email ?: "?").first().uppercaseChar().toString()
                Box(Modifier.size(88.dp).clip(CircleShape).background(C.goSoft), contentAlignment = Alignment.Center) {
                    Text(initial, color = C.goText, fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(me?.name?.ifBlank { null } ?: "You", style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Stat(profile?.cooked, "cooked", C.go)
                        Stat(profile?.mine, "my recipes", C.purpleFg)
                        Stat(profile?.favorites, "favorites", C.pinkFg)
                    }
                }
            }
            profile?.streak?.takeIf { it > 0 }?.let {
                Text("🔥 ${plural(it.toDouble(), "day")} in a row", fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(top = 14.dp).background(Color(0xFFF97316), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        // The cooking calendar
        item { profile?.let { CookCalendar(it.days, Modifier.padding(top = 20.dp)) } }
        // Your kitchen lately
        item {
            val p = profile
            if (p != null && (p.cuisines.isNotEmpty() || p.categories.isNotEmpty())) {
                Column(Modifier.padding(top = 18.dp).fillMaxWidth().border(1.dp, C.line, RoundedCornerShape(24.dp)).padding(18.dp)) {
                    Text("Your kitchen lately", style = MaterialTheme.typography.titleMedium)
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (p.cuisines + p.categories).forEach { pair ->
                            val name = pair[0].toString().trim('"'); val n = pair[1].toString()
                            Pill("$name · ${n}×", background = C.surfaceAlt)
                        }
                    }
                }
            }
        }
        // Cooked | Catalog
        item {
            Row(Modifier.padding(top = 22.dp, bottom = 8.dp)) { Segmented(listOf("cooked" to "Cooked", "catalog" to "Catalog"), tab) { tab = it } }
        }
        if (tab == "cooked") {
            val list = cooked
            if (list == null) item { Shimmer(Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(20.dp)) }
            else if (list.isEmpty()) item { Empty("🍳", "Nothing cooked yet", "Meals on your plan count as cooked once their day comes.") }
            else list.groupBy { it.day }.forEach { (day, meals) ->
                item(key = "d$day") {
                    Text(if (day == today()) "Today" else if (day == addDays(today(), -1)) "Yesterday" else "${weekdayLong(day)}, ${monthDay(day)}",
                        color = C.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                }
                items(meals, key = { "c${it.entryId}" }) { c -> RecipeRow(c.recipe, if (c.batch != null) "Batch · cooked ${num(c.batch)}" else plural(c.servings, "serving")) { openRecipe(c.recipe.id) } }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                    Chip("My recipes" + (catalog?.mine?.size?.let { " · $it" } ?: ""), shelf == "mine") { shelf = "mine" }
                    Chip("Favorites" + (catalog?.favorites?.size?.let { " · $it" } ?: ""), shelf == "favorites") { shelf = "favorites" }
                    Chip("Folders" + (catalog?.folders?.size?.let { " · $it" } ?: ""), shelf == "folders") { shelf = "folders" }
                }
            }
            val c = catalog
            when {
                c == null -> item { Shimmer(Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(20.dp)) }
                shelf == "mine" -> {
                    if (c.mine.isEmpty()) item { Empty("🧑‍🍳", "No recipes of your own yet", "Write one from Recipes (+), or open any recipe and tap “Make my version”.") }
                    items(c.mine, key = { "m${it.id}" }) { r -> RecipeRow(r, listOfNotNull(r.totalMinutes?.let { "$it min" }, r.kcalPerServing?.let { kcal(it) }).joinToString(" · ")) { openRecipe(r.id) } }
                }
                shelf == "favorites" -> {
                    if (c.favorites.isEmpty()) item { Empty("♡", "No favorites yet", "Tap the heart on any recipe to keep it here.") }
                    items(c.favorites, key = { "f${it.id}" }) { r -> RecipeRow(r, listOfNotNull(r.totalMinutes?.let { "$it min" }, r.kcalPerServing?.let { kcal(it) }).joinToString(" · ")) { openRecipe(r.id) } }
                }
                else -> {
                    item {
                        Row(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.5.dp, C.line, RoundedCornerShape(20.dp))
                            .pressable({ newFolder = true }, 0.98f).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = C.ink); Text("  New folder", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (c.folders.isEmpty()) item { Text("Folders keep recipes together: “Weeknight”, “For guests”… Add recipes to one from the recipe page.", color = C.muted, modifier = Modifier.padding(vertical = 12.dp)) }
                    items(c.folders.chunked(2), key = { row -> "fo" + row.joinToString { it.id.toString() } }) { row ->
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { f -> FolderTile(f, Modifier.weight(1f)) { openFolder(f.id) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    }

    if (newFolder) NameDialog("New folder", "e.g. Weeknight", "", onDismiss = { newFolder = false }) { name ->
        newFolder = false
        scope.launch { runCatching { app.api.createFolder(name) }; load() }
    }
}

@Composable
private fun Stat(n: Int?, label: String, dot: Color) = Column {
    Text(n?.toString() ?: "–", fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(" $label", color = C.muted, fontSize = 12.sp)
    }
}

/** Cook Well's grid: one square per day for the last weeks, darker the more you cooked. */
@Composable
private fun CookCalendar(days: Map<String, Int>, modifier: Modifier = Modifier, weeks: Int = 17) {
    val todayDate = LocalDate.now()
    val start = todayDate.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
    Column(modifier.fillMaxWidth().border(1.dp, C.line, RoundedCornerShape(24.dp)).padding(16.dp)) {
        Row {
            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DayOfWeek.entries.forEach { d ->
                    Box(Modifier.height(13.dp), contentAlignment = Alignment.CenterStart) {
                        Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = 9.sp, color = C.faint)
                    }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                (0 until weeks).forEach { w ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        (0 until 7).forEach { d ->
                            val date = start.plusWeeks(w.toLong()).plusDays(d.toLong())
                            val n = days[date.toString()] ?: 0
                            val color = when {
                                date.isAfter(todayDate) -> Color.Transparent
                                n == 0 -> C.surfaceAlt
                                n == 1 -> C.go.copy(alpha = 0.45f)
                                n == 2 -> C.go.copy(alpha = 0.7f)
                                else -> C.go
                            }
                            Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(color))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, weeks / 2, weeks - 1).forEach { w ->
                Text(start.plusWeeks(w.toLong()).month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 10.sp, color = C.faint)
            }
        }
    }
}

@Composable
fun RecipeRow(r: RecipeSummary, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick, 0.98f).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(C.surfaceAlt), contentAlignment = Alignment.Center) {
            if (r.imageUrl != null) AsyncImage(thumb(r.imageUrl, 160), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text("🍳", fontSize = 24.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = C.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FolderTile(f: FolderSummary, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.pressable(onClick, 0.97f)) {
        // A 2×2 mosaic of the folder's photos.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(C.surfaceAlt)) {
            if (f.covers.isEmpty()) Text("📁", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
            else Column {
                f.covers.take(4).chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f)) {
                        pair.forEach { url -> AsyncImage(thumb(url, 240), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize()) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(plural(f.count.toDouble(), "recipe"), color = C.muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** One folder: its recipes, with rename and delete. */
@Composable
fun FolderScreen(id: Int, back: () -> Unit, openRecipe: (Int) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf<io.github.devasenan134.cauldron.data.FolderDetail?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    suspend fun load() { try { folder = app.api.folder(id) } catch (e: Exception) { failed = e.friendly() } }
    LaunchedEffect(id) { load() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(folder?.name ?: "Folder", subtitle = folder?.let { plural(it.recipes.size.toDouble(), "recipe") }, back = back) {
            androidx.compose.material3.TextButton(onClick = { renaming = true }) { Text("Rename", color = C.ink) }
            androidx.compose.material3.TextButton(onClick = { deleting = true }) { Text("Delete", color = C.danger) }
        }
        LazyColumn(contentPadding = screenPadding(top = 8.dp)) {
            failed?.let { item { Text(it, color = C.danger) } }
            folder?.let { f ->
                if (f.recipes.isEmpty()) item { Empty("📁", "This folder is empty", "Open a recipe and tap the bookmark to add it here.") }
                items(f.recipes, key = { it.id }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { RecipeRow(r, listOfNotNull(r.totalMinutes?.let { "$it min" }, r.kcalPerServing?.let { "${it.roundToInt()} kcal" }).joinToString(" · ")) { openRecipe(r.id) } }
                        androidx.compose.material3.TextButton(onClick = { scope.launch { runCatching { app.api.setInFolder(id, r.id, false) }; load() } }) { Text("Remove", color = C.muted) }
                    }
                }
            }
        }
    }
    if (renaming) NameDialog("Rename folder", "Name", folder?.name ?: "", onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch { runCatching { app.api.renameFolder(id, name) }; load() }
    }
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete “${folder?.name}”?") },
        text = { Text("Only the folder goes; its recipes stay in your catalog.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { deleting = false; scope.launch { runCatching { app.api.deleteFolder(id) }; back() } }) { Text("Delete", color = C.danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text("Keep") } },
    )
}
