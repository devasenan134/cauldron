package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.Food
import io.github.devasenan134.cauldron.data.Ingredient
import io.github.devasenan134.cauldron.data.RecipeDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

private val SOURCE_NOTE = mapOf(
    "given" to "weight from the recipe",
    "parts" to "weight worked out from parts",
    "portion" to "weight from a USDA portion",
    "estimate" to "estimated",
    "manual" to "set by hand",
)

@Composable
fun RecipeScreen(id: Int, back: () -> Unit, openPlan: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var load by remember { mutableStateOf<Load<RecipeDetail>>(Load.Loading) }
    var attempt by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Ingredient?>(null) }
    var pickingFood by remember { mutableStateOf<Ingredient?>(null) }

    LaunchedEffect(id, attempt) {
        load = try { Load.Ready(app.api.recipe(id)) } catch (e: Exception) { Load.Failed(e.friendly()) }
    }
    fun patch(ing: Ingredient, body: JsonObject) = scope.launch {
        try { load = Load.Ready(app.api.patchIngredient(ing.id, body)) } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
    }

    val title = (load as? Load.Ready)?.value?.title ?: ""
    Scaffold(topBar = { TopBar(title, back = back) }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = Cream) { padding ->
        Loaded(load, onRetry = { attempt++ }) { r ->
            val groups = r.ingredients.groupBy { it.group.orEmpty() }
            LazyColumn(Modifier.padding(padding)) {
                item { Header(r) }
                item { NutritionCard(r) }
                item {
                    AddToPlan(r) { msg, ok ->
                        scope.launch {
                            val res = snackbar.showSnackbar(msg, actionLabel = if (ok) "Open plan" else null)
                            if (res == androidx.compose.material3.SnackbarResult.ActionPerformed) openPlan()
                        }
                    }
                }
                item {
                    Text("Ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp))
                    if (r.canEdit) Text("Tap a weight to correct it, or a food to change what it's counted as.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
                groups.forEach { (group, ings) ->
                    if (group.isNotEmpty()) item { SectionLabel(group, Modifier.padding(horizontal = 16.dp)) }
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column {
                                ings.forEachIndexed { i, ing ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    IngredientRow(ing, r.canEdit, onGrams = { editing = ing }, onFood = { pickingFood = ing })
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
                }
                items(r.steps, key = { it.id }) { s ->
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row {
                                Text("${r.steps.indexOf(s) + 1}", color = Ember, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                Text(s.title, fontWeight = FontWeight.SemiBold)
                            }
                            Text(s.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editing?.let { ing ->
        GramsDialog(ing, onDismiss = { editing = null }) { grams ->
            editing = null
            patch(ing, buildJsonObject { put("grams", grams?.let { JsonPrimitive(it) } ?: JsonNull) })
        }
    }
    pickingFood?.let { ing ->
        FoodSheet(ing.name, onDismiss = { pickingFood = null }) { food ->
            pickingFood = null
            patch(ing, buildJsonObject { put("food_id", food.id) })
        }
    }
}

@Composable
private fun Header(r: RecipeDetail) {
    val context = LocalContext.current
    Column {
        AsyncImage(model = thumb(r.imageUrl, 900, 675), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(16.dp)) {
            Text(r.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                r.yieldText?.let { Pill(it) }
                r.totalMinutes?.let { Pill("$it min") }
                r.cuisine?.let { Pill(it) }
                r.category?.let { Pill(it) }
                r.tags.forEach { Pill(it) }
            }
            if (r.description.isNotBlank()) Text(r.description, modifier = Modifier.padding(top = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                r.videoUrl?.let { url ->
                    TextButton(onClick = { openUrl(context, url) }) {
                        Icon(Icons.Default.PlayArrow, null, tint = Ember); Text("Watch video", color = Ember)
                    }
                }
                r.sourceUrl?.let { url ->
                    TextButton(onClick = { openUrl(context, url) }) {
                        Text("Original recipe" + (r.author?.let { " by $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionCard(r: RecipeDetail) {
    val n = r.nutrition
    val m = n.perServing ?: n.total
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text((if (n.perServing != null) "Per serving (recipe makes ${num(r.servings ?: 1.0)})" else "Whole recipe").uppercase(),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Stat("Calories", "${m.kcal.roundToInt()}", strong = true)
                Stat("Protein", "${m.protein.roundToInt()} g")
                Stat("Carbs", "${m.carbs.roundToInt()} g")
                Stat("Fat", "${m.fat.roundToInt()} g")
            }
            val small = MaterialTheme.typography.bodySmall
            if (n.perServing != null) Text("Whole recipe: ${kcal(n.total.kcal)}", style = small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            if (n.leftOut.isNotEmpty()) Text("Not counted (no amount): ${n.leftOut.joinToString()}", style = small, color = Amber, modifier = Modifier.padding(top = 4.dp))
            if (n.estimated.isNotEmpty()) Text("Estimated amounts: ${n.estimated.joinToString()}", style = small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            r.sourceNutrition?.calories?.let { Text("Cook Well lists ${it.roundToInt()} kcal.", style = small, color = Stone.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, value: String, strong: Boolean = false) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = if (strong) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = if (strong) Ember else Ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddToPlan(r: RecipeDetail, done: (String, Boolean) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf<String?>(today()) }
    var cook by remember(r.id) { mutableStateOf(r.servings ?: 1.0) }
    var eat by remember { mutableStateOf(1.0) }
    var busy by remember { mutableStateOf(false) }
    val extra = cook - eat

    Column(Modifier.padding(top = 20.dp)) {
        Text("Add to plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        DayChips(day, { day = it })
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Stepper(cook, { cook = it; if (eat > it) eat = it }, label = "cook")
            Spacer(Modifier.width(16.dp))
            Stepper(eat, { eat = it; if (cook < it) cook = it }, step = 0.5, min = 0.5, label = "eat")
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            app.api.addEntry(buildJsonObject {
                                put("day", day?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("recipe_id", r.id)
                                put("servings", eat)
                                // Cooking more than you eat makes a batch; the rest goes in the fridge.
                                put("cook_portions", if (extra > 0) JsonPrimitive(cook) else JsonNull)
                            })
                            done("Added to ${dayChipLabel(day).lowercase().let { if (day == null) "the queue" else it }}", true)
                        } catch (e: Exception) {
                            done(e.friendly(), false)
                        } finally { busy = false }
                    }
                },
            ) { Text("Add") }
        }
        Text(
            if (extra > 0) "Batch: ${num(extra)} ${if (extra == 1.0) "portion goes" else "portions go"} in the fridge for later."
            else "Portions, one serving each. Cook more than you eat to batch cook.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun IngredientRow(ing: Ingredient, editable: Boolean, onGrams: () -> Unit, onFood: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append(ing.name)
                    if (ing.note.isNotBlank()) append(", ${ing.note}")
                },
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            val gramsText = ing.grams?.let { "${it.roundToInt()} g" } ?: if (editable) "+ g" else "— g"
            Text(
                gramsText,
                color = when {
                    ing.grams == null -> Amber
                    ing.gramsSource == "estimate" -> Stone
                    else -> Ink
                },
                fontStyle = if (ing.gramsSource == "estimate") FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).then(if (editable) Modifier.clickable(onClick = onGrams) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(ing.nutrition?.kcal?.roundToInt()?.toString() ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        }
        if (ing.label.isNotBlank()) Text(ing.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            ing.foodName ?: "no food linked",
            style = MaterialTheme.typography.bodySmall, color = Stone.copy(alpha = 0.7f),
            modifier = if (editable) Modifier.clickable(onClick = onFood) else Modifier,
        )
    }
}

@Composable
private fun GramsDialog(ing: Ingredient, onDismiss: () -> Unit, onSave: (Double?) -> Unit) {
    var text by remember { mutableStateOf(ing.grams?.let { num(Math.round(it * 10) / 10.0) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ing.name) },
        text = {
            Column {
                ing.gramsSource?.let { Text(SOURCE_NOTE[it] ?: it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (ing.label.isNotBlank()) Text("Recipe says: ${ing.label}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("Grams (empty = don't count)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.trim().replace(',', '.')
                if (v.isEmpty()) onSave(null) else v.toDoubleOrNull()?.let(onSave)
            }) { Text("Save", color = Ember) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSheet(initial: String, onDismiss: () -> Unit, onPick: (Food) -> Unit) {
    val app = app()
    var q by remember { mutableStateOf(initial) }
    var foods by remember { mutableStateOf<List<Food>?>(null) }
    LaunchedEffect(q) {
        delay(250)
        foods = if (q.isBlank()) emptyList() else runCatching { app.api.foods(q) }.getOrNull()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        OutlinedTextField(q, { q = it }, label = { Text("Search foods") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        LazyColumn(Modifier.padding(top = 8.dp)) {
            items(foods.orEmpty(), key = { it.id }) { f ->
                Row(Modifier.fillMaxWidth().clickable { onPick(f) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(f.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${f.kcal.roundToInt()} kcal/100 g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (foods?.isEmpty() == true && q.isNotBlank()) item {
                Text("No match. Try fewer words.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
