package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    "given" to "Weight from the recipe",
    "parts" to "Weight worked out from parts",
    "portion" to "Weight from a USDA portion",
    "estimate" to "Estimated",
    "manual" to "Set by hand",
)

private val HERO = 380.dp

@Composable
fun RecipeScreen(id: Int, back: () -> Unit, openPlan: () -> Unit) {
    val app = app()
    val store = app.store
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val recipe = store.recipe.collectAsState().value[id]
    val (load, retry) = cached(recipe, id) { store.loadRecipe(id) }
    var editing by remember { mutableStateOf<Ingredient?>(null) }
    var pickingFood by remember { mutableStateOf<Ingredient?>(null) }
    var adding by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun patch(ing: Ingredient, body: JsonObject) = scope.launch {
        try { store.putRecipe(app.api.patchIngredient(ing.id, body)) } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
    }

    Box(Modifier.fillMaxSize()) {
        Loaded(load, retry, loading = { RecipeSkeleton() }) { r ->
            val groups = r.ingredients.groupBy { it.group.orEmpty() }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item {
                    Box(Modifier.fillMaxWidth().height(HERO).clip(RoundedCornerShape(0.dp))) {
                        AsyncImage(
                            thumb(r.imageUrl, 1000, 1000), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                // Parallax: the photo scrolls at half speed.
                                if (list.firstVisibleItemIndex == 0) translationY = list.firstVisibleItemScrollOffset * 0.5f
                            },
                        )
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
                    }
                }
                item {
                    Column(
                        Modifier.offset(y = (-32).dp).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Cream).padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Text(r.title, style = MaterialTheme.typography.headlineMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            r.totalMinutes?.let { Meta(Icons.Default.Schedule, "$it min") }
                            (r.yieldText ?: r.servings?.let { plural(it, "serving") })?.let { Meta(Icons.Default.Restaurant, it) }
                            r.cuisine?.let { Pill(it, background = Paper) }
                            r.category?.let { Pill(it, background = Paper) }
                        }
                        if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
                        Links(r)
                        NutritionCard(r)
                    }
                }
                item {
                    Column(Modifier.offset(y = (-32).dp).padding(horizontal = 20.dp)) {
                        SectionLabel("Ingredients") { Text("${r.ingredients.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                        if (r.canEdit) Text("Tap a weight to correct it, or a food to change what it's counted as.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        groups.forEach { (group, ings) ->
                            if (group.isNotEmpty()) Text(group.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = Ember, letterSpacing = 1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp))
                            Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
                                Column {
                                    ings.forEachIndexed { i, ing ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 14.dp))
                                        IngredientRow(ing, r.canEdit, onGrams = { editing = ing }, onFood = { pickingFood = ing })
                                    }
                                }
                            }
                        }
                        SectionLabel("Steps")
                    }
                }
                itemsIndexed(r.steps, key = { _, s -> s.id }) { i, s ->
                    Row(Modifier.offset(y = (-32).dp).padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(EmberBright), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold)
                        }
                        Surface(color = Paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                if (s.title.isNotBlank()) Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text(s.text, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, modifier = Modifier.padding(top = if (s.title.isNotBlank()) 6.dp else 0.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
            // Sticky "Add to plan"
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Cream, Cream)))
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Button(
                    onClick = { adding = true }, colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    modifier = Modifier.fillMaxWidth().height(58.dp).shadow(12.dp, RoundedCornerShape(50), spotColor = Ink),
                ) { Text("Add to plan", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
            if (adding) AddToPlanSheet(r, onDismiss = { adding = false }) { msg, ok ->
                adding = false
                scope.launch {
                    val res = snackbar.showSnackbar(msg, actionLabel = if (ok) "Open plan" else null)
                    if (res == SnackbarResult.ActionPerformed) openPlan()
                }
            }
        }
        // Over the photo: just a round back button. Scrolled past it: a solid bar with the title.
        val solid by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 600 } }
        val barAlpha by animateFloatAsState(if (solid) 1f else 0f, label = "bar")
        Row(
            Modifier.fillMaxWidth().background(Cream.copy(alpha = barAlpha)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)).pressable(back, 0.9f),
                contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
            Text(recipe?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).graphicsLayer { alpha = barAlpha })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 80.dp))
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
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.background(Paper, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ember, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun Links(r: RecipeDetail) {
    val context = LocalContext.current
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        r.videoUrl?.let { url ->
            Row(Modifier.clip(RoundedCornerShape(50)).background(EmberSoft).pressable({ openUrl(context, url) }).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Ember, modifier = Modifier.size(20.dp))
                Text("Watch video", color = Ember, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
        }
        r.sourceUrl?.let { url ->
            Text("Original" + (r.author?.let { " by $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).clickable { openUrl(context, url) })
        }
    }
}

@Composable
private fun NutritionCard(r: RecipeDetail) {
    val n = r.nutrition
    val m = n.perServing ?: n.total
    Surface(color = Ink, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(if (n.perServing != null) "Per serving · recipe makes ${num(r.servings ?: 1.0)}" else "Whole recipe",
                color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${m.kcal.roundToInt()}", color = Color.White, style = MaterialTheme.typography.displaySmall)
                Text(" kcal", color = Cream.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 8.dp))
            }
            // Where the calories come from: protein and carbs 4 kcal/g, fat 9.
            val p = m.protein * 4; val c = m.carbs * 4; val f = m.fat * 9
            val total = (p + c + f).takeIf { it > 0 } ?: 1.0
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) {
                Box(Modifier.weight((p / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF60A5FA)))
                Box(Modifier.weight((c / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFFFBBF24)))
                Box(Modifier.weight((f / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFFFB923C)))
            }
            Row(Modifier.padding(top = 12.dp)) {
                Macro("Protein", m.protein, Color(0xFF60A5FA), Modifier.weight(1f))
                Macro("Carbs", m.carbs, Color(0xFFFBBF24), Modifier.weight(1f))
                Macro("Fat", m.fat, Color(0xFFFB923C), Modifier.weight(1f))
            }
            val small = MaterialTheme.typography.bodySmall
            if (n.perServing != null) Text("Whole recipe: ${kcal(n.total.kcal)}", style = small, color = Cream.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
            if (n.leftOut.isNotEmpty()) Text("Not counted (no amount): ${n.leftOut.joinToString()}", style = small, color = Color(0xFFFDBA74), modifier = Modifier.padding(top = 4.dp))
            if (n.estimated.isNotEmpty()) Text("Estimated: ${n.estimated.joinToString()}", style = small, color = Cream.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Column(Modifier.padding(start = 8.dp)) {
            Text("${grams.roundToInt()} g", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(label, color = Cream.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlanSheet(r: RecipeDetail, onDismiss: () -> Unit, done: (String, Boolean) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val tick = rememberTick()
    var day by remember { mutableStateOf<String?>(today()) }
    var cook by remember(r.id) { mutableStateOf(r.servings ?: 1.0) }
    var eat by remember { mutableStateOf(1.0) }
    var busy by remember { mutableStateOf(false) }
    val extra = cook - eat

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Add to plan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            Text(r.title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            Text("When", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            DayChips(day, { tick(); day = it }, wrap = true, days = 10)
            Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepperCard("Cook", "portions", cook, { cook = it; if (eat > it) eat = it }, Modifier.weight(1f))
                StepperCard("Eat now", "portions", eat, { eat = it; if (cook < it) cook = it }, Modifier.weight(1f), step = 0.5, min = 0.5)
            }
            Surface(color = if (extra > 0) AmberSoft else Paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Text(
                    if (extra > 0) "🥡  Batch cook: ${plural(extra, "portion")} go${if (extra == 1.0) "es" else ""} in the fridge for later."
                    else "Cook more than you eat to batch cook, and the rest goes in the fridge.",
                    color = if (extra > 0) Amber else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (extra > 0) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(14.dp),
                )
            }
            Button(
                enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Ink),
                modifier = Modifier.padding(20.dp).fillMaxWidth().height(56.dp),
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
                            app.store.refreshPlans()
                            done("Added to ${if (day == null) "the queue" else dayChipLabel(day).lowercase()}", true)
                        } catch (e: Exception) {
                            done(e.friendly(), false)
                        } finally { busy = false }
                    }
                },
            ) { Text(if (day == null) "Add to queue" else "Add to ${dayChipLabel(day)}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun StepperCard(label: String, unit: String, value: Double, onChange: (Double) -> Unit, modifier: Modifier, step: Double = 1.0, min: Double = step) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper(value, onChange, step = step, min = min, big = true, label = label.lowercase())
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IngredientRow(ing: Ingredient, editable: Boolean, onGrams: () -> Unit, onFood: () -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(buildString { append(ing.name); if (ing.note.isNotBlank()) append(", ${ing.note}") }, fontWeight = FontWeight.SemiBold)
            if (ing.label.isNotBlank()) Text(ing.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ing.foodName ?: "no food linked", style = MaterialTheme.typography.labelSmall, color = Stone.copy(alpha = 0.7f),
                modifier = if (editable) Modifier.clickable(onClick = onFood) else Modifier)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                ing.grams?.let { "${it.roundToInt()} g" } ?: if (editable) "+ g" else "—",
                color = when { ing.grams == null -> Amber; ing.gramsSource == "estimate" -> Stone; else -> Ink },
                fontWeight = FontWeight.SemiBold,
                fontStyle = if (ing.gramsSource == "estimate") FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (editable) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .then(if (editable) Modifier.clickable(onClick = onGrams) else Modifier).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Text(ing.nutrition?.kcal?.roundToInt()?.let { "$it kcal" } ?: "", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RecipeSkeleton() = Column {
    Shimmer(Modifier.fillMaxWidth().height(HERO), RoundedCornerShape(0.dp))
    Column(Modifier.padding(20.dp)) {
        Shimmer(Modifier.fillMaxWidth(0.85f).height(30.dp), RoundedCornerShape(8.dp))
        Shimmer(Modifier.padding(top = 12.dp).fillMaxWidth(0.5f).height(20.dp), RoundedCornerShape(8.dp))
        Shimmer(Modifier.padding(top = 24.dp).fillMaxWidth().height(160.dp), RoundedCornerShape(24.dp))
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
                    value = text, onValueChange = { text = it }, label = { Text("Grams (empty = don't count)") }, singleLine = true, suffix = { Text("g") },
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
        Text("What is it counted as?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        SearchPill(q, { q = it }, Modifier.padding(20.dp), placeholder = "Search foods")
        LazyColumn(Modifier.height(460.dp)) {
            items(foods.orEmpty(), key = { it.id }) { f ->
                Row(Modifier.fillMaxWidth().clickable { onPick(f) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(f.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${f.kcal.roundToInt()} kcal/100 g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (foods?.isEmpty() == true && q.isNotBlank()) item {
                Text("No match. Try fewer words.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
