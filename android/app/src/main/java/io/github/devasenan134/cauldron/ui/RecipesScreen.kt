package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
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
fun RecipesScreen(openRecipe: (Int) -> Unit) {
    val store = app().store
    var q by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") } // q, once typing pauses
    var cuisine by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    val facets by store.facets.collectAsState()
    val list = store.recipes.collectAsState().value[store.recipesKey(query, cuisine, category)]

    LaunchedEffect(q) { delay(300); query = q.trim() }
    LaunchedEffect(Unit) { runCatching { store.loadFacets() } }
    val (load, retry) = cached(list, query, cuisine, category) { store.loadRecipes(query, cuisine, category) }

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
                SearchPill(q, { q = it }, Modifier.padding(top = 8.dp))
                CategoryRow(facets?.categories.orEmpty(), category, { category = it }, facets?.cuisines.orEmpty(), cuisine, { cuisine = it })
            }
        }
        when (load) {
            Load.Loading -> items(6) { RecipeCardSkeleton() }
            is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { Loaded(load, retry) {} }
            is Load.Ready -> {
                if (load.value.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Empty("🔍", "No recipes found", "Try another word, or clear the filters.")
                }
                items(load.value, key = { it.id }) { RecipeCard(it, Modifier.animateItem()) { openRecipe(it.id) } }
            }
        }
    }
}

@Composable
fun HeaderRow(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        Avatar()
    }
}

@Composable
fun SearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search recipes or ingredients") {
    Surface(color = Paper, shape = RoundedCornerShape(50), modifier = modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(50), ambientColor = Ink.copy(alpha = 0.2f), spotColor = Ink.copy(alpha = 0.2f))) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp).height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Stone)
            Box(Modifier.weight(1f).padding(start = 12.dp)) {
                if (value.isEmpty()) Text(placeholder, color = Stone.copy(alpha = 0.8f))
                BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Ink), cursorBrush = SolidColor(Ember), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear search", tint = Stone) }
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<String>, category: String, onCategory: (String) -> Unit,
    cuisines: List<String>, cuisine: String, onCuisine: (String) -> Unit,
) {
    var cuisineMenu by remember { mutableStateOf(false) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
        item {
            Box {
                Chip(cuisine.ifEmpty { "Cuisine" }, selected = cuisine.isNotEmpty(), icon = true) { cuisineMenu = true }
                DropdownMenu(cuisineMenu, onDismissRequest = { cuisineMenu = false }) {
                    DropdownMenuItem(text = { Text("All cuisines") }, onClick = { onCuisine(""); cuisineMenu = false })
                    cuisines.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onCuisine(c); cuisineMenu = false }) }
                }
            }
        }
        item { Chip("All", selected = category.isEmpty()) { onCategory("") } }
        items(categories) { c -> Chip(c, selected = c == category) { onCategory(if (c == category) "" else c) } }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Ink else Paper).pressable(onClick, 0.93f).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(Icons.Default.Public, null, tint = if (selected) Cream else Stone, modifier = Modifier.size(16.dp).padding(end = 0.dp))
        Text(text, color = if (selected) Cream else Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = if (icon) 6.dp else 0.dp))
    }
}

@Composable
private fun RecipeCard(r: RecipeSummary, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.pressable(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(StoneLight)) {
            AsyncImage(thumb(r.imageUrl, 480, 480), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            r.kcalPerServing?.let {
                Text(
                    "${it.roundToInt()} kcal", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
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
