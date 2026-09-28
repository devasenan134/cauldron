package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.Facets
import io.github.devasenan134.cauldron.data.RecipeSummary
import kotlinx.coroutines.delay

@Composable
fun RecipesScreen(openRecipe: (Int) -> Unit) {
    val app = app()
    var q by rememberSaveable { mutableStateOf("") }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var facets by remember { mutableStateOf<Facets?>(null) }
    var load by remember { mutableStateOf<Load<List<RecipeSummary>>>(Load.Loading) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { facets = runCatching { app.api.facets() }.getOrNull() }
    LaunchedEffect(q, cuisine, category, attempt) {
        delay(250) // wait for typing to pause
        load = try { Load.Ready(app.api.recipes(q.trim(), cuisine, category)) } catch (e: Exception) { Load.Failed(e.friendly()) }
    }

    Scaffold(topBar = { TopBar("Recipes") }, containerColor = Cream) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("Search recipes or ingredients") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Clear") } },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ember, unfocusedContainerColor = androidx.compose.ui.graphics.Color.White, focusedContainerColor = androidx.compose.ui.graphics.Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FacetChip("All cuisines", cuisine, facets?.cuisines.orEmpty()) { cuisine = it }
                FacetChip("All meals", category, facets?.categories.orEmpty()) { category = it }
                Box(Modifier.weight(1f))
                (load as? Load.Ready)?.let {
                    Text("${it.value.size} recipes", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                }
            }
            Loaded(load, onRetry = { attempt++ }) { recipes ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(recipes, key = { it.id }) { RecipeCard(it) { openRecipe(it.id) } }
                }
            }
        }
    }
}

@Composable
private fun FacetChip(allLabel: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = value.isNotEmpty(), onClick = { open = true },
            label = { Text(value.ifEmpty { allLabel }) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmberSoft, selectedLabelColor = Ember, selectedTrailingIconColor = Ember),
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onPick(""); open = false })
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}

@Composable
private fun RecipeCard(r: RecipeSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        AsyncImage(
            model = thumb(r.imageUrl, 400, 300), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
        )
        Column(Modifier.padding(10.dp)) {
            Text(r.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                r.kcalPerServing?.let { Pill("${kcal(it)}/serving", color = Ember, background = EmberSoft) }
                r.totalMinutes?.let { Pill("$it min") }
            }
        }
    }
}
