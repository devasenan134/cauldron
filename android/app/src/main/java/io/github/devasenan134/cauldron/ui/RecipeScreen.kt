package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.border
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
fun RecipeScreen(id: Int, back: () -> Unit, openPlan: () -> Unit, edit: () -> Unit, openRecipe: (Int) -> Unit = {}, editNew: (Int) -> Unit = {}) {
    val app = app()
    val store = app.store
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val recipe = store.recipe.collectAsState().value[id]
    val (load, retry) = cached(recipe, id) { store.loadRecipe(id) }
    var editing by remember { mutableStateOf<Ingredient?>(null) }
    var pickingFood by remember { mutableStateOf<Ingredient?>(null) }
    var adding by remember { mutableStateOf(false) }
    var foldering by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    fun toggleFavorite(r: RecipeDetail) {
        store.putRecipe(r.copy(favorite = !r.favorite)) // at once
        scope.launch { runCatching { app.api.setFavorite(r.id, !r.favorite) }.onFailure { store.putRecipe(r); snackbar.showSnackbar(it.friendly()) } }
    }
    fun makeVersion(r: RecipeDetail) {
        copying = true
        scope.launch {
            try { val newId = app.api.makeVariation(r.id); store.recipesChanged(); editNew(newId) }
            catch (e: Exception) { snackbar.showSnackbar(e.friendly()) } finally { copying = false }
        }
    }
    val list = rememberLazyListState()

    fun patch(ing: Ingredient, body: JsonObject) = scope.launch {
        try { store.putRecipe(app.api.patchIngredient(ing.id, body)) } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
    }

    Box(Modifier.fillMaxSize()) {
        Loaded(load, retry, loading = { RecipeSkeleton() }) { r ->
            val groups = r.ingredients.groupBy { it.group.orEmpty() }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item {
                    // No photo (a recipe of your own, say): a short tinted header instead of a big blank.
                    if (r.imageUrl == null) Box(Modifier.fillMaxWidth().height(200.dp).background(C.goSoft), contentAlignment = Alignment.Center) {
                        Text("🍳", fontSize = 64.sp)
                    } else Box(Modifier.fillMaxWidth().height(HERO).clip(RoundedCornerShape(0.dp))) {
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
                        Modifier.offset(y = (-32).dp).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(C.bg).padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        Text(r.title, style = MaterialTheme.typography.headlineMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                            r.totalMinutes?.let { Meta(Icons.Default.Schedule, "$it min") }
                            (r.yieldText ?: r.servings?.let { plural(it, "serving") })?.let { Meta(Icons.Default.Restaurant, it) }
                            r.cuisine?.let { Pill(it, background = C.surface) }
                            r.category?.let { Pill(it, background = C.surface) }
                        }
                        if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 14.dp))
                        if (r.notes.isNotBlank()) Text("📝  ${r.notes}", style = MaterialTheme.typography.bodyMedium, color = C.muted,
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().background(C.surfaceAlt, RoundedCornerShape(14.dp)).padding(12.dp))
                        Links(r)
                        // Variations: where this came from, your versions of it, and a button to make one.
                        r.parentTitle?.let { t ->
                            Text("↳ Your version of $t", color = C.purpleFg, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(50)).background(C.purpleBg)
                                    .pressable({ r.parentId?.let(openRecipe) }, 0.97f).padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        if (r.variations.isNotEmpty()) FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.variations.forEach { v -> Chip("✎ ${v.title}", false) { openRecipe(v.id) } }
                        }
                        OutlinedButton(onClick = { makeVersion(r) }, enabled = !copying, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.Outlined.ContentCopy, null, tint = C.ink, modifier = Modifier.size(18.dp))
                            Text(if (copying) "  Copying…" else "  Make my version", color = C.ink, fontWeight = FontWeight.SemiBold)
                        }
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
                                color = C.goText, letterSpacing = 1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp, start = 4.dp))
                            Surface(color = C.surface, shape = RoundedCornerShape(20.dp)) {
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
                        Box(Modifier.size(34.dp).clip(CircleShape).background(C.ink), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = C.bg, fontFamily = Display, fontWeight = FontWeight.Bold)
                        }
                        Surface(color = C.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
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
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, C.bg, C.bg)))
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Button(
                    onClick = { adding = true }, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo),
                    modifier = Modifier.fillMaxWidth().height(58.dp).shadow(12.dp, RoundedCornerShape(50), spotColor = C.ink),
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
            Modifier.fillMaxWidth().background(C.bg.copy(alpha = barAlpha)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingCircle(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.ink) }
            Text(recipe?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp).graphicsLayer { alpha = barAlpha })
            // Your own recipes can be rewritten.
            recipe?.let { r ->
                FloatingCircle({ toggleFavorite(r) }) {
                    Icon(if (r.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (r.favorite) "Unfavorite" else "Favorite",
                        tint = if (r.favorite) C.pinkFg else C.ink)
                }
                Spacer(Modifier.width(8.dp))
                FloatingCircle({ foldering = true }) {
                    Icon(if (r.folderIds.isNotEmpty()) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Save to a folder", tint = C.ink)
                }
                // Your own recipes can be rewritten.
                if (r.isMine) { Spacer(Modifier.width(8.dp)); FloatingCircle(edit) { Icon(Icons.Default.Edit, "Edit recipe", tint = C.ink) } }
            }
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
    if (foldering) recipe?.let { r -> FolderSheet(r, onDismiss = { foldering = false }) { store.putRecipe(it) } }
}

/** Save a recipe to your folders (tick them), or make a new folder for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSheet(r: RecipeDetail, onDismiss: () -> Unit, changed: (RecipeDetail) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<io.github.devasenan134.cauldron.data.FolderSummary>?>(null) }
    var inIds by remember { mutableStateOf(r.folderIds.toSet()) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { folders = runCatching { app.api.catalog().folders }.getOrDefault(emptyList()) }
    fun set(folderId: Int, on: Boolean) {
        inIds = if (on) inIds + folderId else inIds - folderId
        changed(r.copy(folderIds = inIds.toList()))
        scope.launch { runCatching { app.api.setInFolder(folderId, r.id, on) } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Save to a folder", style = MaterialTheme.typography.headlineSmall)
            Text(r.title, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            folders?.forEach { f ->
                val on = f.id in inIds
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(C.surface).pressable({ set(f.id, !on) }, 0.98f).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("📁  ${f.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) C.ink else Color.Transparent).border(1.5.dp, if (on) C.ink else C.faint, CircleShape),
                        contentAlignment = Alignment.Center) { if (on) Icon(Icons.Default.Check, null, tint = C.bg, modifier = Modifier.size(16.dp)) }
                }
            }
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Add, null, tint = C.ink); Text(" New folder", color = C.ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (creating) NameDialog("New folder", "e.g. Weeknight", "", onDismiss = { creating = false }) { name ->
        creating = false
        scope.launch {
            runCatching { app.api.createFolder(name) }.onSuccess { f -> folders = folders.orEmpty() + f; set(f.id, true) }
        }
    }
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.background(C.surface, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.goText, modifier = Modifier.size(15.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 5.dp))
    }
}

@Composable
private fun Links(r: RecipeDetail) {
    val context = LocalContext.current
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        r.videoUrl?.let { url ->
            Row(Modifier.clip(RoundedCornerShape(50)).background(C.goSoft).pressable({ openUrl(context, url) }).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = C.goText, modifier = Modifier.size(20.dp))
                Text("Watch video", color = C.goText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
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
    Surface(color = C.surface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, C.line), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text((if (n.perServing != null) "Per serving · recipe makes ${num(r.servings ?: 1.0)}" else "Whole recipe").uppercase(),
                color = C.muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.6.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${m.kcal.roundToInt()}", color = C.ink, style = MaterialTheme.typography.displaySmall)
                Text(" kcal", color = C.muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            // Where the calories come from: protein and carbs 4 kcal/g, fat 9.
            val p = m.protein * 4; val c = m.carbs * 4; val f = m.fat * 9
            val total = (p + c + f).takeIf { it > 0 } ?: 1.0
            if (p + c + f == 0.0) {
                Text(if (r.ingredients.isEmpty()) "Add ingredients to see calories." else "No ingredient has a weight yet: tap one to add it.",
                    color = C.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            } else Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))) {
                Box(Modifier.weight((p / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(ProteinColor))
                Box(Modifier.weight((c / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(CarbsColor))
                Box(Modifier.weight((f / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(FatColor))
            }
            if (p + c + f > 0) Row(Modifier.padding(top = 14.dp)) {
                Macro("Protein", m.protein, ProteinColor, Modifier.weight(1f))
                Macro("Carbs", m.carbs, CarbsColor, Modifier.weight(1f))
                Macro("Fat", m.fat, FatColor, Modifier.weight(1f))
            }
            val small = MaterialTheme.typography.bodySmall
            if (n.perServing != null) Text("Whole recipe: ${kcal(n.total.kcal)}", style = small, color = C.muted, modifier = Modifier.padding(top = 14.dp))
            if (n.leftOut.isNotEmpty()) Text("Not counted (no amount): ${n.leftOut.joinToString()}", style = small, color = C.pinkFg, modifier = Modifier.padding(top = 4.dp))
            if (n.estimated.isNotEmpty()) Text("Estimated: ${n.estimated.joinToString()}", style = small, color = C.faint, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Column(Modifier.padding(start = 8.dp)) {
            Text("${grams.roundToInt()} g", color = C.ink, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(label, color = C.muted, fontSize = 12.sp)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.bg) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Add to plan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            Text(r.title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            Text("When", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            DayChips(day, { tick(); day = it }, wrap = true, days = 10)
            Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepperCard("Cook", "portions", cook, { cook = it; if (eat > it) eat = it }, Modifier.weight(1f))
                StepperCard("Eat now", "portions", eat, { eat = it; if (cook < it) cook = it }, Modifier.weight(1f), step = 0.5, min = 0.5)
            }
            Surface(color = if (extra > 0) C.purpleBg else C.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Text(
                    if (extra > 0) "🥡  Batch cook: ${plural(extra, "portion")} go${if (extra == 1.0) "es" else ""} in the fridge for later."
                    else "Cook more than you eat to batch cook, and the rest goes in the fridge.",
                    color = if (extra > 0) C.purpleFg else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (extra > 0) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(14.dp),
                )
            }
            Button(
                enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = C.ink),
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
    Surface(color = C.surface, shape = RoundedCornerShape(20.dp), modifier = modifier) {
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
            Text(ing.foodName ?: "no food linked", style = MaterialTheme.typography.labelSmall, color = C.muted.copy(alpha = 0.7f),
                modifier = if (editable) Modifier.clickable(onClick = onFood) else Modifier)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                ing.grams?.let { "${it.roundToInt()} g" } ?: if (editable) "+ g" else "—",
                color = when { ing.grams == null -> C.purpleFg; ing.gramsSource == "estimate" -> C.muted; else -> C.ink },
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
            }) { Text("Save", color = C.goText) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.bg) {
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
