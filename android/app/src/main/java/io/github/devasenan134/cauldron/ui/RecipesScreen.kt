package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.devasenan134.cauldron.data.Facets
import io.github.devasenan134.cauldron.data.KcalRange
import io.github.devasenan134.cauldron.data.RecipeFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.RecipeSummary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun RecipesScreen(openRecipe: (Int) -> Unit, newRecipe: () -> Unit, importRecipe: () -> Unit) {
    val store = app().store
    var q by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(RecipeFilter()) }
    var filtering by remember { mutableStateOf(false) }
    val facets by store.facets.collectAsState()
    val list = store.recipes.collectAsState().value[store.recipesKey(filter)]

    LaunchedEffect(q) { delay(300); filter = filter.copy(q = q.trim()) }
    LaunchedEffect(Unit) { runCatching { store.loadFacets() } }
    val (load, retry) = cached(list, filter) { store.loadRecipes(filter) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = screenPadding(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding()) {
                    HeaderRow("Recipes", (load as? Load.Ready)?.value?.size?.let { "$it recipes" } ?: "Loading…")
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SearchPill(q, { q = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        BadgedBox(badge = { if (filter.count > 0) Badge(containerColor = C.ink, contentColor = C.bg) { Text("${filter.count}") } }) {
                            FloatingCircle({ filtering = true }, 54.dp) { Icon(Icons.Default.Tune, "Filters", tint = C.ink) }
                        }
                    }
                    QuickRow(filter, facets?.categories.orEmpty()) { filter = it }
                }
            }
            when (load) {
                Load.Loading -> items(6) { RecipeCardSkeleton() }
                is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { Loaded(load, retry) {} }
                is Load.Ready -> {
                    if (load.value.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        if (filter.prep && filter.count == 1 && filter.q.isEmpty())
                            Empty("🫙", "No prepped ingredients yet", "Mark a recipe like cooked rice, pickled onions or a sauce as a prepped ingredient (on its page), and other recipes can use it by weight.")
                        else if (filter.mine && filter.count == 1 && filter.q.isEmpty())
                            Empty("🧑‍🍳", "No recipes of your own yet", "Tap + to write one: ingredients, steps and a photo, like the rest of the library.")
                        else Empty("🔍", "No recipes found", "Try another word, or fewer filters.")
                    }
                    items(load.value, key = { it.id }) { RecipeCard(it, Modifier.animateItem()) { openRecipe(it.id) } }
                }
            }
        }
        // Import from a video (above) and write your own (below)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = LocalBottomSpace.current + 88.dp).size(46.dp)
                .shadow(10.dp, CircleShape).clip(CircleShape).background(C.surface).pressable(importRecipe, 0.9f),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Link, "Import from a video", tint = C.ink, modifier = Modifier.size(24.dp)) }
        // New recipe
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = LocalBottomSpace.current + 16.dp).size(58.dp)
                .shadow(12.dp, CircleShape).clip(CircleShape).background(C.ink).pressable(newRecipe, 0.9f),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, "New recipe", tint = C.bg, modifier = Modifier.size(28.dp)) }
    }

    if (filtering) FilterSheet(filter, facets, onDismiss = { filtering = false }) { filter = it.copy(q = filter.q) }
}

/** Under the search: My recipes, then the meals as quick chips. */
@Composable
private fun QuickRow(filter: RecipeFilter, categories: List<String>, onChange: (RecipeFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
        item { Chip("All", selected = filter.count == 0) { onChange(RecipeFilter(q = filter.q, sort = filter.sort)) } }
        item { Chip("My recipes", selected = filter.mine) { onChange(filter.copy(mine = !filter.mine)) } }
        item { Chip("🫙 Prepped", selected = filter.prep) { onChange(filter.copy(prep = !filter.prep)) } }
        items(categories) { c ->
            Chip(c, selected = c in filter.categories) { onChange(filter.copy(categories = filter.categories.toggle(c))) }
        }
    }
}

fun <T> Set<T>.toggle(x: T) = if (x in this) this - x else this + x

private val SORTS = listOf("title" to "A–Z", "quickest" to "Quickest", "lowest_kcal" to "Fewest calories",
    "highest_protein" to "Most protein", "newest" to "Newest")
private val TIMES = listOf(15, 30, 45, 60)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(initial: RecipeFilter, facets: Facets?, onDismiss: () -> Unit, onApply: (RecipeFilter) -> Unit) {
    var f by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.bg) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { f = RecipeFilter(q = f.q) }) { Text("Reset", color = C.muted, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            FilterGroup("Sort by") {
                SORTS.forEach { (value, label) -> Chip(label, f.sort == value) { f = f.copy(sort = value) } }
            }
            facets?.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
                FilterGroup("Meal") { cats.forEach { c -> Chip(c, c in f.categories) { f = f.copy(categories = f.categories.toggle(c)) } } }
            }
            FilterGroup("Ready in") {
                TIMES.forEach { m -> Chip("≤ $m min", f.maxMinutes == m) { f = f.copy(maxMinutes = if (f.maxMinutes == m) null else m) } }
            }
            FilterGroup("Calories per serving") {
                KcalRange.entries.forEach { r -> Chip(r.label, f.kcal == r) { f = f.copy(kcal = if (f.kcal == r) null else r) } }
            }
            facets?.tagGroups?.forEach { g ->
                FilterGroup(g.name) { g.tags.forEach { t -> Chip(t, t in f.tags) { f = f.copy(tags = f.tags.toggle(t)) } } }
            }
            facets?.cuisines?.takeIf { it.isNotEmpty() }?.let { cs ->
                FilterGroup("Cuisine") { cs.forEach { c -> Chip(c, c in f.cuisines) { f = f.copy(cuisines = f.cuisines.toggle(c)) } } }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { onApply(f); onDismiss() },
            colors = ButtonDefaults.buttonColors(containerColor = C.ink, contentColor = C.bg),
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(54.dp),
        ) { Text(if (f.count == 0) "Show all recipes" else "Show recipes · ${f.count} filter${if (f.count == 1) "" else "s"}", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(title.uppercase(), color = C.muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.8.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
fun HeaderRow(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun SearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search recipes or ingredients") {
    Surface(color = C.surface, shape = RoundedCornerShape(50), modifier = modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(50), ambientColor = C.ink.copy(alpha = 0.2f), spotColor = C.ink.copy(alpha = 0.2f))) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp).height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = C.muted)
            Box(Modifier.weight(1f).padding(start = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, color = C.muted.copy(alpha = 0.8f))
                BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = C.ink), cursorBrush = SolidColor(C.goText), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear search", tint = C.muted) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) C.ink else C.surface)
            .border(1.dp, if (selected) C.ink else C.line, RoundedCornerShape(50)).pressable(onClick, 0.93f).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(Icons.Default.Public, null, tint = if (selected) C.bg else C.muted, modifier = Modifier.size(16.dp).padding(end = 0.dp))
        Text(text, color = if (selected) C.bg else C.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = if (icon) 6.dp else 0.dp))
    }
}

@Composable
fun RecipeCard(r: RecipeSummary, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(C.line)) {
            AsyncImage(thumb(r.imageUrl, 480, 480), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            r.kcalPerServing?.let {
                Text(
                    "${it.roundToInt()} kcal", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (r.isPrep) Text("🫙 Prep", color = C.onGo, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(C.go, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, start = 2.dp))
        Text(listOfNotNull(r.totalMinutes?.let { "$it min" }, r.cuisine).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
    }
}

@Composable
private fun RecipeCardSkeleton() = Column {
    Shimmer(Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(24.dp))
    Shimmer(Modifier.padding(top = 10.dp).fillMaxWidth(0.8f).height(16.dp), RoundedCornerShape(6.dp))
    Shimmer(Modifier.padding(top = 6.dp).fillMaxWidth(0.4f).height(12.dp), RoundedCornerShape(6.dp))
}
