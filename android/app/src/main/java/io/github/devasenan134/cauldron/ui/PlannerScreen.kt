package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.devasenan134.cauldron.data.Plan
import io.github.devasenan134.cauldron.data.PlanEntry
import io.github.devasenan134.cauldron.data.RecipeSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** Where a new meal goes: a day, or the queue (day = null). */
private data class Slot(val day: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(openRecipe: (Int) -> Unit, openGrocery: () -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var start by rememberSaveable { mutableStateOf(weekStart(today())) }
    var load by remember { mutableStateOf<Load<Plan>>(Load.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf<Slot?>(null) }
    var moving by remember { mutableStateOf<PlanEntry?>(null) }
    var groceryDialog by remember { mutableStateOf(false) }

    suspend fun reload() {
        load = try { Load.Ready(app.api.plan(start)) } catch (e: Exception) { if (load is Load.Ready) { snackbar.showSnackbar(e.friendly()); load } else Load.Failed(e.friendly()) }
    }
    LaunchedEffect(start, attempt) { reload() }
    /** Run a change, then show the fresh plan (or say what went wrong). */
    fun act(block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        reload()
    }

    val plan = (load as? Load.Ready)?.value
    val weekKcal = plan?.days?.values?.flatten()?.sumOf { it.kcal ?: 0.0 } ?: 0.0

    Scaffold(
        topBar = {
            TopBar("Plan") {
                IconButton(onClick = { groceryDialog = true }) { Icon(Icons.Default.AddShoppingCart, "Make grocery list") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Cream,
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { start = addDays(start, -7) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Week of ${monthDay(start)}", fontWeight = FontWeight.SemiBold)
                    Text("${weekKcal.roundToInt()} kcal planned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (start != weekStart(today())) TextButton(onClick = { start = weekStart(today()) }) { Text("This week", color = Ember) }
                IconButton(onClick = { start = addDays(start, 7) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week") }
            }
            Loaded(load, onRetry = { attempt++ }) { p ->
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } }) {
                    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(key = "queue") {
                            DayCard("Queue", "planned, no day yet", null, p.queue, highlight = false, onAdd = { adding = Slot(null) },
                                entryActions = { entryActions(it, p.queue, ::act, app, openRecipe, onMove = { moving = it }) })
                        }
                        items(p.days.keys.sorted(), key = { it }) { day ->
                            val list = p.days[day].orEmpty()
                            DayCard(weekdayLong(day), monthDay(day), list.sumOf { it.kcal ?: 0.0 }, list, highlight = day == today(),
                                onAdd = { adding = Slot(day) },
                                entryActions = { entryActions(it, list, ::act, app, openRecipe, onMove = { moving = it }) })
                        }
                    }
                }
            }
        }
    }

    adding?.let { slot ->
        AddSheet(slot.day, onDismiss = { adding = null }) { body ->
            adding = null
            act { app.api.addEntry(body) }
        }
    }
    moving?.let { entry ->
        MoveSheet(entry, start, onDismiss = { moving = null }) { day ->
            moving = null
            act { app.api.updateEntry(entry.id, buildJsonObject { put("day", day?.let { JsonPrimitive(it) } ?: JsonNull) }) }
        }
    }
    if (groceryDialog) {
        var includeQueue by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { groceryDialog = false },
            title = { Text("Make grocery list") },
            text = {
                Column {
                    Text("Rebuild the list from this week's meals (${monthDay(start)} – ${monthDay(addDays(start, 6))}). Items you added by hand stay.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { includeQueue = !includeQueue }) {
                        Checkbox(includeQueue, { includeQueue = it }, colors = CheckboxDefaults.colors(checkedColor = Ember))
                        Text("Include the queue")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    groceryDialog = false
                    scope.launch {
                        try {
                            app.grocery.replace(app.api.generateGrocery(start, addDays(start, 6), includeQueue))
                            openGrocery()
                        } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
                    }
                }) { Text("Make list", color = Ember) }
            },
            dismissButton = { TextButton(onClick = { groceryDialog = false }) { Text("Cancel") } },
        )
    }
}

/** What an entry card can do; kept apart so DayCard stays about layout. */
private class EntryActions(
    val setServings: (Double) -> Unit,
    val setCook: (Double?) -> Unit,
    val moveUp: (() -> Unit)?,
    val moveDown: (() -> Unit)?,
    val move: () -> Unit,
    val remove: () -> Unit,
    val open: (() -> Unit)?,
)

private fun entryActions(
    e: PlanEntry, list: List<PlanEntry>, act: (suspend () -> Unit) -> Unit,
    app: io.github.devasenan134.cauldron.CauldronApp, openRecipe: (Int) -> Unit, onMove: (PlanEntry) -> Unit,
): EntryActions {
    val i = list.indexOfFirst { it.id == e.id }
    fun patch(body: JsonObject) = act { app.api.updateEntry(e.id, body) }
    fun position(p: Int) = patch(buildJsonObject { put("day", e.day?.let { JsonPrimitive(it) } ?: JsonNull); put("position", p) })
    return EntryActions(
        setServings = { patch(buildJsonObject { put("servings", it) }) },
        setCook = { c -> patch(buildJsonObject { put("cook_portions", c?.let { JsonPrimitive(it) } ?: JsonNull) }) },
        moveUp = if (i > 0) ({ position(i - 1) }) else null,
        moveDown = if (i < list.lastIndex) ({ position(i + 1) }) else null,
        move = { onMove(e) },
        remove = { act { app.api.deleteEntry(e.id) } },
        open = e.recipeId?.let { id -> { openRecipe(id) } },
    )
}

@Composable
private fun DayCard(
    title: String, subtitle: String, total: Double?, entries: List<PlanEntry>, highlight: Boolean,
    onAdd: () -> Unit, entryActions: (PlanEntry) -> EntryActions,
) {
    Surface(
        color = Color.White, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) Ink else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(" $subtitle", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                if (total != null && total > 0) Text("${total.roundToInt()}", color = Ember, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add a meal") }
            }
            if (entries.isEmpty()) Text("Nothing planned", style = MaterialTheme.typography.bodySmall, color = Stone.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp))
            entries.forEach { e -> EntryRow(e, entryActions(e)) }
        }
    }
}

@Composable
private fun EntryRow(e: PlanEntry, a: EntryActions) {
    var menu by remember { mutableStateOf(false) }
    val bg = when { e.isLeftover -> SkySoft; e.isBatch -> AmberSoft; else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) }
    Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 6.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(thumb(e.imageUrl, 96), null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp).then(if (a.open != null) Modifier.clickable { a.open.invoke() } else Modifier)) {
                    if (e.isLeftover) Text("LEFTOVERS", color = Sky, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    if (e.isBatch) Text("BATCH", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    Text(e.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "Options for ${e.title}") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move to another day") }, onClick = { menu = false; a.move() })
                        a.moveUp?.let { DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; it() }) }
                        a.moveDown?.let { DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; it() }) }
                        if (!e.isLeftover && e.recipeId != null) {
                            if (e.isBatch) DropdownMenuItem(text = { Text("Not a batch") }, onClick = { menu = false; a.setCook(null) })
                            else DropdownMenuItem(text = { Text("Batch cook") }, onClick = { menu = false; a.setCook(e.servings + 3) })
                        }
                        DropdownMenuItem(
                            text = { Text(if (e.isBatch) "Remove (and its leftovers)" else "Remove", color = Danger) },
                            onClick = { menu = false; a.remove() },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Stepper(e.servings, a.setServings, step = 0.5, min = 0.5, label = "eat")
                Spacer(Modifier.weight(1f))
                e.kcal?.let { Text(kcal(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (e.isBatch) Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(e.cookPortions!!, { a.setCook(it) }, min = e.servings, label = "cook", tint = Amber)
                Spacer(Modifier.weight(1f))
                val left = e.portionsLeft ?: 0.0
                Text("${num(left)} left", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (left < 0) Danger else Amber)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(day: String?, onDismiss: () -> Unit, onAdd: (JsonObject) -> Unit) {
    val app = app()
    var tab by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var recipes by remember { mutableStateOf<List<RecipeSummary>?>(null) }
    var batches by remember { mutableStateOf<List<PlanEntry>?>(null) }
    LaunchedEffect(q) { delay(250); recipes = runCatching { app.api.recipes(q.trim()) }.getOrNull() }
    LaunchedEffect(Unit) { batches = runCatching { app.api.batches() }.getOrNull() }
    val dayJson = day?.let { JsonPrimitive(it) } ?: JsonNull

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Text("Add to ${dayChipLabel(day).let { if (day == null) "the queue" else it }}", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        SecondaryTabRow(tab, containerColor = Cream, indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = Ember) }) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Recipes") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Fridge" + (batches?.size?.takeIf { it > 0 }?.let { " · $it" } ?: "")) })
        }
        if (tab == 0) {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Find a recipe") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp))
            LazyColumn(Modifier.height(420.dp)) {
                items(recipes.orEmpty(), key = { it.id }) { r ->
                    PickRow(r.imageUrl, r.title, r.kcalPerServing?.let { "${it.roundToInt()}" }) {
                        onAdd(buildJsonObject { put("day", dayJson); put("recipe_id", r.id); put("servings", 1.0) })
                    }
                }
            }
        } else {
            LazyColumn(Modifier.height(420.dp)) {
                if (batches?.isEmpty() == true) item {
                    Text("No batches with portions left. Plan a meal that cooks more than you eat, or use Batch cook on a meal.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(batches.orEmpty(), key = { it.id }) { b ->
                    PickRow(b.imageUrl, b.title, "${num(b.portionsLeft ?: 0.0)} left", sub = b.day?.let { "cooked ${monthDay(it)}" } ?: "not scheduled") {
                        onAdd(buildJsonObject { put("day", dayJson); put("leftover_of", b.id); put("servings", 1.0) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(image: String?, title: String, trailing: String?, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(thumb(image, 96), null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(entry: PlanEntry, week: String, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Text("Move “${entry.title}”", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        DayChips(entry.day, onPick, from = week, days = 14, wrap = true)
        Spacer(Modifier.height(40.dp))
    }
}
