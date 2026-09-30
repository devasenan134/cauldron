package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.LibraryRecipe
import io.github.devasenan134.cauldron.data.RecipeFilter
import io.github.devasenan134.cauldron.data.RecipeSummary
import kotlinx.coroutines.launch

private val LIBRARY_TABS = listOf(
    Triple("everyone", "Everyone", "Every user sees these, from the day they sign up. Hide one and nobody sees it; it stays here to show again."),
    Triple("guests", "Guests", "Only you and the guests in CAULDRON_ALLOWED_EMAILS see these (the Cook Well recipes). They never go to everyone."),
)

/** Settings → Recipe libraries (the owner only): the recipes everyone gets, and the ones for guests. */
@Composable
fun LibrariesScreen(back: () -> Unit, openRecipe: (Int) -> Unit, edit: (Int) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("everyone") }
    var q by remember { mutableStateOf("") }
    var recipes by remember { mutableStateOf<List<LibraryRecipe>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    suspend fun load() {
        error = null
        recipes = try { app.api.library(tab) } catch (e: Exception) { error = e.friendly(); recipes }
    }
    // Again after coming back from the editor (LocalRefresh changes when a page is closed).
    LaunchedEffect(tab, LocalRefresh.current) { load() }
    fun changed() = scope.launch { app.store.recipesChanged(); load() }

    val shown = recipes.orEmpty().filter { it.title.contains(q.trim(), ignoreCase = true) }
    val hidden = recipes.orEmpty().count { it.hidden }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Recipe libraries", subtitle = recipes?.let { "${it.size} recipe${if (it.size == 1) "" else "s"}" + if (hidden > 0) " · $hidden hidden" else "" }, back = back) {
            FloatingCircle({ adding = true }) { Icon(Icons.Default.Add, "Add one of your recipes", tint = C.ink) }
        }
        LazyColumn(contentPadding = screenPadding(top = 4.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.surfaceAlt).padding(4.dp)) {
                    LIBRARY_TABS.forEach { (name, label, _) ->
                        val selected = tab == name
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selected) C.surface else Color.Transparent)
                                .pressable({ if (tab != name) { tab = name; recipes = null; q = "" } }, 0.95f).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) C.ink else C.muted) }
                    }
                }
                Text(LIBRARY_TABS.first { it.first == tab }.third, color = C.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                SearchPill(q, { q = it }, Modifier.padding(top = 12.dp, bottom = 8.dp), placeholder = "Search this library")
                error?.let { Text(it, color = C.danger, modifier = Modifier.padding(vertical = 8.dp)) }
                if (recipes == null && error == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.goText)
                }
                if (recipes != null && shown.isEmpty()) Text(if (q.isBlank()) "This library is empty." else "Nothing matches.",
                    color = C.muted, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(shown, key = { it.id }) { r ->
                LibraryRow(r, open = { openRecipe(r.id) }, edit = { edit(r.id) }, changed = { changed() })
                HorizontalDivider(color = C.line)
            }
        }
    }
    if (adding) AddToLibraryDialog(tab, onDismiss = { adding = false }, onAdded = { adding = false; changed() })
}

@Composable
private fun LibraryRow(r: LibraryRecipe, open: () -> Unit, edit: () -> Unit, changed: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun act(block: suspend () -> Unit) = scope.launch {
        busy = true; error = null
        try { block(); changed() } catch (e: Exception) { error = e.friendly() }
        busy = false
    }
    Column(Modifier.padding(vertical = 10.dp).alpha(if (r.hidden) 0.6f else 1f)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = open), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(C.goSoft), contentAlignment = Alignment.Center) {
                if (r.imageUrl != null) AsyncImage(thumb(r.imageUrl, 160, 160), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("🍳", fontSize = 24.sp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(kcal(r.kcalPerServing), r.cuisine).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = C.muted)
            }
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (r.hidden) Pill("Hidden", color = C.bg, background = C.ink, modifier = Modifier.align(Alignment.CenterVertically))
            if (r.edited) Pill("Edited here", color = C.blueFg, background = C.blueBg, modifier = Modifier.align(Alignment.CenterVertically))
            if (r.yours) Pill("Yours", color = C.goText, background = C.goSoft, modifier = Modifier.align(Alignment.CenterVertically))
            OutlinedButton(onClick = edit, enabled = !busy) { Text("Edit", color = C.ink) }
            OutlinedButton(onClick = { act { app.api.setLibraryHidden(r.id, !r.hidden) } }, enabled = !busy) { Text(if (r.hidden) "Show" else "Hide", color = C.ink) }
            if (r.yours) OutlinedButton(onClick = { act { app.api.takeBackFromLibrary(r.id) } }, enabled = !busy) { Text("Back to my recipes", color = C.ink) }
        }
        error?.let { Text(it, color = C.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Pick one of your own recipes to put in the library. */
@Composable
private fun AddToLibraryDialog(library: String, onDismiss: () -> Unit, onAdded: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var mine by remember { mutableStateOf<List<RecipeSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mine = runCatching { app.api.recipes(RecipeFilter(mine = true)) }.getOrElse { error = it.friendly(); emptyList() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to ${if (library == "everyone") "Everyone" else "Guests"}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("It moves out of your recipes into the library, and you can take it back later." +
                    if (library == "everyone") " Imported recipes and versions of Cook Well recipes are other people's work, so they can't go here." else "",
                    style = MaterialTheme.typography.bodySmall, color = C.muted)
                error?.let { Text(it, color = C.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                val list = mine
                when {
                    list == null -> CircularProgressIndicator(color = C.goText, modifier = Modifier.padding(top = 16.dp))
                    list.isEmpty() -> Text("No recipes of your own yet.", color = C.muted, modifier = Modifier.padding(top = 12.dp))
                    else -> list.forEach { r ->
                        Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy) {
                                busy = true; error = null
                                scope.launch {
                                    try { app.api.addToLibrary(library, r.id); onAdded() } catch (e: Exception) { error = e.friendly() }
                                    busy = false
                                }
                            }.padding(vertical = 10.dp, horizontal = 4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
