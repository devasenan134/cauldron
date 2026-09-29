package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.data.Food
import io.github.devasenan134.cauldron.data.FoodIn
import io.github.devasenan134.cauldron.data.Unlinked
import kotlinx.coroutines.launch
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/** Every food your recipes use, with its macros per 100 g; edit one and every recipe follows. */
@Composable
fun IngredientsScreen(back: () -> Unit, openFood: (Int?) -> Unit, openRecipe: (Int) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var foods by remember { mutableStateOf<List<Food>?>(null) }
    var unlinked by remember { mutableStateOf<List<Unlinked>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var show by remember { mutableStateOf("all") } // all | yours | unlinked
    var assigning by remember { mutableStateOf<Unlinked?>(null) }
    suspend fun load() {
        try { foods = app.api.foodLibrary(); failed = null } catch (e: Exception) { failed = e.friendly() }
        runCatching { unlinked = app.api.unlinked() }
    }
    LaunchedEffect(LocalRefresh.current) { load() }

    val words = q.lowercase().split(" ").filter { it.isNotBlank() }
    fun matches(f: Food) = words.all { w -> f.name.lowercase().contains(w) || f.brand.lowercase().contains(w) || f.names.any { it.contains(w) } }
    val rows = foods.orEmpty().filter { (show != "yours" || it.edited || it.own) && matches(it) }
    val yours = foods.orEmpty().count { it.edited || it.own }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Ingredients", subtitle = foods?.let { "${it.size} foods · macros per 100 g" }, back = back) {
                FloatingCircle({ openFood(null) }) { Icon(Icons.Default.Add, "Add a food", tint = C.ink) }
            }
            LazyColumn(contentPadding = screenPadding(top = 4.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Text("Change a food's macros (say, the brand of curd you buy) and every recipe that uses it follows. Only you see your changes.",
                        color = C.muted, style = MaterialTheme.typography.bodyMedium)
                    SearchPill(q, { q = it }, Modifier.padding(top = 12.dp), placeholder = "Search ingredients")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        item { Chip("All", show == "all") { show = "all" } }
                        item { Chip("Edited by you" + if (yours > 0) " · $yours" else "", show == "yours") { show = "yours" } }
                        item { Chip("Not counted" + (unlinked?.size?.takeIf { it > 0 }?.let { " · $it" } ?: ""), show == "unlinked") { show = "unlinked" } }
                    }
                }
                failed?.let { item { Text(it, color = C.danger) } }
                if (show == "unlinked") {
                    val list = unlinked.orEmpty().filter { u -> words.all { u.name.lowercase().contains(it) } }
                    if (unlinked != null && list.isEmpty()) item { Empty("✅", "Everything is counted", "Every ingredient in the recipes you can edit has a food (or is a prep).") }
                    else if (list.isNotEmpty()) item {
                        Text("These have no food, so they add no calories. Pick one and it's used everywhere the ingredient appears.",
                            color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items(list, key = { "u" + it.name }) { u ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(C.surface).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${u.name} · ${plural(u.count.toDouble(), "time")}", fontWeight = FontWeight.SemiBold)
                                Text("in " + u.recipes.joinToString { it.title }, color = C.muted, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { u.recipes.firstOrNull()?.let { openRecipe(it.id) } })
                            }
                            TextButton(onClick = { assigning = u }) { Text("Pick a food", color = C.goText, fontWeight = FontWeight.Bold) }
                        }
                    }
                } else {
                    if (foods != null && rows.isEmpty()) item {
                        if (show == "yours" && q.isBlank()) Empty("🥕", "Nothing edited yet", "Open a food to set your own macros and notes, or add a food of your own from its label.")
                        else Empty("🔍", "No ingredient found", "Try another word, or add it as your own food (+).")
                    }
                    if (rows.isNotEmpty()) item {
                        Surface(color = C.surface, shape = RoundedCornerShape(20.dp)) {
                            Column {
                                rows.take(300).forEachIndexed { i, f ->
                                    if (i > 0) HorizontalDivider(color = C.line, modifier = Modifier.padding(horizontal = 14.dp))
                                    FoodRow(f) { openFood(f.id) }
                                }
                            }
                        }
                        if (rows.size > 300) Text("Showing 300 of ${rows.size}: search to narrow it down.", color = C.muted, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
    assigning?.let { u ->
        FoodSheet(u.name, onDismiss = { assigning = null }) { food ->
            assigning = null
            scope.launch {
                try { app.api.assignFood(food.id, u.name); app.store.recipesChanged(); load(); snackbar.showSnackbar("${u.name} now counts as ${food.name}") }
                catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
            }
        }
    }
}

@Composable
private fun FoodRow(f: Food, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name + if (f.brand.isNotBlank()) " · ${f.brand}" else "", fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (f.edited || f.own) Pill(if (f.own) "your food" else "edited", color = C.goText, background = C.goSoft, modifier = Modifier.padding(start = 6.dp))
            }
            Text(listOfNotNull(if (f.recipes > 0) plural(f.recipes.toDouble(), "recipe") else "not used yet",
                f.names.takeIf { it.isNotEmpty() }?.let { "as ${it.take(3).joinToString()}" }, f.notes.takeIf { it.isNotBlank() }?.let { "📝 $it" }).joinToString(" · "),
                color = C.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text("${f.kcal.roundToInt()} kcal", fontFamily = Display, fontWeight = FontWeight.Bold)
            Text("P ${num(f.protein)} · C ${num(f.carbs)} · F ${num(f.fat)}", color = C.muted, fontSize = 11.sp)
        }
    }
}

private val FIELDS = listOf("kcal" to ("Calories" to "kcal"), "protein" to ("Protein" to "g"), "carbs" to ("Carbs" to "g"), "fat" to ("Fat" to "g"),
    "fiber" to ("Fiber" to "g"), "sugar" to ("Sugar" to "g"), "sodium_mg" to ("Sodium" to "mg"))
private val OPTIONAL = setOf("fiber", "sugar", "sodium_mg")

private fun Food.value(key: String): Double? = when (key) {
    "kcal" -> kcal; "protein" -> protein; "carbs" -> carbs; "fat" -> fat; "fiber" -> fiber; "sugar" -> sugar; else -> sodiumMg
}

/** One food: its macros per 100 g (yours if you've changed them), notes, and where it's used. [id] null = a new food. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodScreen(id: Int?, back: () -> Unit, openRecipe: (Int) -> Unit, created: (Int) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var food by remember { mutableStateOf<Food?>(null) }
    var loaded by remember { mutableStateOf(id == null) }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val values = remember { mutableStateOf(FIELDS.associate { it.first to "" }) }
    // Labels often give values per serving: scale them to 100 g on save.
    var per by remember { mutableStateOf("100") }
    var saving by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun fill(f: Food) {
        food = f; name = f.name; brand = f.brand; notes = f.notes
        values.value = FIELDS.associate { (k, _) -> k to (f.value(k)?.let { num(it) } ?: "") }
        per = "100"
    }
    LaunchedEffect(id) {
        if (id != null) try { fill(app.api.food(id)); loaded = true } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
    }
    val scale = 100 / (per.toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0)
    fun v(k: String) = values.value[k]!!.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * scale * 100) / 100.0 }
    fun body() = FoodIn(name.trim(), brand.trim(), notes.trim(), v("kcal") ?: 0.0, v("protein") ?: 0.0, v("fat") ?: 0.0, v("carbs") ?: 0.0,
        v("fiber"), v("sugar"), v("sodium_mg"))
    fun changed() = food?.let { f -> name != f.name || brand != f.brand || notes != f.notes || per != "100" ||
        FIELDS.any { (k, _) -> values.value[k] != (f.value(k)?.let { num(it) } ?: "") } } ?: true
    fun save() {
        if (name.isBlank()) { scope.launch { snackbar.showSnackbar("Give it a name") }; return }
        saving = true
        scope.launch {
            try {
                val f = if (id == null) app.api.addFood(body()) else app.api.saveFood(id, body())
                app.store.recipesChanged(); app.store.refreshPlans()
                if (id == null) created(f.id) else { fill(f); snackbar.showSnackbar("Saved: every recipe with it is updated") }
            } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) } finally { saving = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(if (id == null) "New food" else food?.name ?: "Food", back = back) {
                Button(onClick = ::save, enabled = !saving && loaded && changed(), colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo)) {
                    Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight.Bold)
                }
            }
            if (loaded) LazyColumn(contentPadding = screenPadding(top = 4.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    val f = food
                    Text(
                        when {
                            f == null -> "A product off its label, or anything the search doesn't have. Pick it for an ingredient from the recipe page (⇄)."
                            f.own -> "A food you added."
                            f.edited -> "Your version: it replaces the standard values in all your recipes."
                            else -> "Standard values (${if (f.source.startsWith("usda")) "USDA" else "built in"}). Saving makes your own version, used in all your recipes."
                        } + (f?.recipes?.takeIf { it > 0 }?.let { " Used in ${plural(it.toDouble(), "recipe")}." } ?: ""),
                        color = C.muted, style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(color = C.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            OutlinedTextField(notes, { notes = it }, label = { Text("Notes: where you buy it, which pack…") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                    }
                    Surface(color = C.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Nutrition", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text("per ", color = C.muted)
                                OutlinedTextField(per, { per = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true, suffix = { Text("g") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(110.dp))
                            }
                            if (per != "100" && (per.toDoubleOrNull() ?: 0.0) > 0) Text("Type the label's numbers for $per g; they're saved per 100 g (× ${num(scale)}).",
                                color = C.goText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(C.goSoft, RoundedCornerShape(12.dp)).padding(10.dp))
                            FIELDS.chunked(2).forEach { pair -> Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (k, lu) ->
                                    val standard = food?.default?.get(k)?.jsonPrimitive?.doubleOrNull
                                    Column(Modifier.weight(1f)) {
                                        OutlinedTextField(values.value[k]!!, { t -> values.value = values.value + (k to t.filter { c -> c.isDigit() || c == '.' || c == ',' }) },
                                            label = { Text(lu.first) }, suffix = { Text(lu.second) }, singleLine = true, placeholder = { Text(if (k in OPTIONAL) "—" else "0") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                                        if (standard != null && num(standard) != values.value[k]) Text("standard ${num(standard)}", color = C.faint, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            } }
                            val check = ((v("protein") ?: 0.0) + (v("carbs") ?: 0.0)) * 4 + (v("fat") ?: 0.0) * 9
                            Text("From the macros: ${check.roundToInt()} kcal per 100 g (protein and carbs 4 kcal/g, fat 9).", color = C.muted,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                    food?.takeIf { it.usedIn.isNotEmpty() }?.let { f ->
                        Surface(color = C.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Used in", style = MaterialTheme.typography.titleLarge)
                                if (f.names.isNotEmpty()) Text("as ${f.names.joinToString()}", color = C.muted, style = MaterialTheme.typography.bodySmall)
                                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    f.usedIn.take(40).forEach { r -> Chip(r.title, false) { openRecipe(r.id) } }
                                }
                                if (f.usedIn.size > 40) Text("and ${f.usedIn.size - 40} more", color = C.muted, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    food?.takeIf { it.edited || it.own }?.let { f ->
                        TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 12.dp)) {
                            Text(if (f.own) "Delete this food" else "↺ Back to the standard values", color = C.danger, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
    if (confirmReset) food?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(if (f.own) "Delete ${f.name}?" else "Back to the standard values?") },
            text = { Text(if (f.own) "Ingredients using it won't be counted." else "All your recipes go back to the standard macros for this food.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        try {
                            app.api.resetFood(f.id); app.store.recipesChanged(); app.store.refreshPlans()
                            if (f.own) back() else fill(app.api.food(f.id))
                        } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
                    }
                }) { Text(if (f.own) "Delete" else "Reset", color = C.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Keep") } },
        )
    }
}
