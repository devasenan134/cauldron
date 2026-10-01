package io.github.devasenan134.cauldron.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.devasenan134.cauldron.CauldronApp
import io.github.devasenan134.cauldron.data.MEALS
import io.github.devasenan134.cauldron.data.MEAL_EMOJI
import io.github.devasenan134.cauldron.data.MEAL_LABEL
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

private const val QUEUE_PAGE = 0

@Composable
fun PlannerScreen(openRecipe: (Int) -> Unit, openGrocery: () -> Unit) {
    val app = app()
    val store = app.store
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var week by rememberSaveable { mutableStateOf(weekStart(today())) }
    val plan = store.plans.collectAsState().value[week]
    val (load, retry) = cached(plan, week) { store.loadPlan(week) }
    var refreshing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) }
    var addingMeal by remember { mutableStateOf("dinner") }
    var addingOpen by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<PlanEntry?>(null) }
    var portioning by remember { mutableStateOf<PlanEntry?>(null) }
    var groceryDialog by remember { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf("day") } // "day" or "week"
    var noting by remember { mutableStateOf<NoteTarget?>(null) }

    val days = (0L until 7L).map { addDays(week, it) }
    // Page 0 is the queue; pages 1..7 are the days.
    val startPage = days.indexOf(today()).takeIf { it >= 0 }?.plus(1) ?: 1
    val pager = rememberPagerState(initialPage = startPage) { 8 }
    LaunchedEffect(week) { pager.scrollToPage(days.indexOf(today()).takeIf { it >= 0 }?.plus(1) ?: 1) }

    /** Run a change on the server, then bring every cached week and the fridge up to date. */
    fun act(block: suspend () -> Unit) = scope.launch {
        try { block() } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        store.refreshPlans()
    }
    val log = rememberMealLog { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val actions = PlanActions(app, week, ::act, onMove = { moving = it }, onPortions = { portioning = it }, openRecipe = openRecipe, onEditNote = { noting = NoteTarget(it.day, it.meal, it) }, log = log)
    val onAdd = { day: String?, meal: String -> adding = day; addingMeal = meal; addingOpen = true }
    val onNote = { day: String?, meal: String -> noting = NoteTarget(day, meal, null) }
    val weekKcal = plan?.days?.values?.flatten()?.sumOf { it.kcal ?: 0.0 } ?: 0.0

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Plan", subtitle = "Week of ${monthDay(week)} · ${"%,d".format(weekKcal.roundToInt())} kcal") {
                IconButton(onClick = { groceryDialog = true }) { Icon(Icons.Default.AddShoppingCart, "Make grocery list") }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Segmented(listOf("day" to "Day", "week" to "Week"), view) { view = it }
            }
            WeekStrip(week, plan, if (view == "day") pager.currentPage else -1, onWeek = { week = it },
                onPage = { view = "day"; scope.launch { pager.animateScrollToPage(it) } })
            Loaded(load, retry) { p ->
                PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; runCatching { store.loadPlan(week) }; refreshing = false } }) {
                    if (view == "week") {
                        WeekList(p, days, actions,
                            openDay = { i -> view = "day"; scope.launch { pager.scrollToPage(i) } },
                            onAdd = onAdd, onNote = onNote)
                    } else HorizontalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        val day = if (page == QUEUE_PAGE) null else days[page - 1]
                        val entries = if (day == null) p.queue else p.days[day].orEmpty()
                        DayPage(day, entries, actions, onAdd = { meal -> onAdd(day, meal) }, onNote = { meal -> onNote(day, meal) })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomSpace.current + 16.dp))
    }

    noting?.let { t ->
        NoteDialog(t.entry?.title ?: "", isNew = t.entry == null, onDismiss = { noting = null }, onDelete = t.entry?.let { e -> { noting = null; actions.remove(e) } }) { text ->
            noting = null
            val e = t.entry
            if (e == null) act { app.api.addEntry(buildJsonObject { put("day", t.day?.let { JsonPrimitive(it) } ?: JsonNull); put("meal", t.meal); put("title", text) }) }
            else {
                app.store.editEntry(e.id) { it.copy(title = text) }
                act { app.api.updateEntry(e.id, buildJsonObject { put("title", text) }) }
            }
        }
    }
    log.Dialogs()
    if (addingOpen) AddSheet(adding, addingMeal, onDismiss = { addingOpen = false }) { body ->
        addingOpen = false
        act { app.api.addEntry(body) }
    }
    moving?.let { entry ->
        MoveSheet(entry, week, onDismiss = { moving = null }) { day, meal ->
            moving = null
            // Show it in its new place right away.
            store.editPlan(week) { it.moveEntry(entry.id, day, meal) }
            act { app.api.updateEntry(entry.id, buildJsonObject { put("day", day?.let { JsonPrimitive(it) } ?: JsonNull); put("meal", meal) }) }
        }
    }
    portioning?.let { entry ->
        PortionsSheet(entry, onDismiss = { portioning = null }) { eat, cook ->
            portioning = null
            actions.setPortions(entry, eat, cook)
        }
    }
    if (groceryDialog) GroceryDialog(week, onDismiss = { groceryDialog = false }) { includeQueue ->
        groceryDialog = false
        scope.launch {
            try {
                app.grocery.replace(app.api.generateGrocery(week, addDays(week, 6), includeQueue))
                openGrocery()
            } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        }
    }
}

// --- local (optimistic) plan edits

private fun Plan.mapEntries(f: (List<PlanEntry>) -> List<PlanEntry>) = Plan(days.mapValues { f(it.value) }, f(queue))

private fun Plan.moveEntry(id: Int, day: String?, meal: String): Plan {
    val entry = (days.values.flatten() + queue).firstOrNull { it.id == id } ?: return this
    val without = mapEntries { l -> l.filter { it.id != id } }
    val moved = entry.copy(day = day, meal = meal)
    return if (day == null) Plan(without.days, without.queue + moved)
    else Plan(without.days.mapValues { (d, l) -> if (d == day) l + moved else l }, without.queue)
}

/** What a meal card can do. Changes show at once and are then sent to the server. */
private class PlanActions(
    val app: CauldronApp, val week: String, val act: (suspend () -> Unit) -> Unit,
    val onMove: (PlanEntry) -> Unit, val onPortions: (PlanEntry) -> Unit, val openRecipe: (Int) -> Unit, val onEditNote: (PlanEntry) -> Unit,
    val log: MealLog,
) {
    private fun send(e: PlanEntry, body: JsonObject) = act { app.api.updateEntry(e.id, body) }

    fun setServings(e: PlanEntry, s: Double) {
        val delta = s - e.servings
        app.store.editEntry(e.id) {
            it.copy(servings = s, kcal = it.kcalPerServing?.let { k -> k * s }, portionsLeft = it.portionsLeft?.minus(delta))
        }
        send(e, buildJsonObject { put("servings", s) })
    }

    fun setCook(e: PlanEntry, c: Double?) {
        app.store.editEntry(e.id) {
            it.copy(cookPortions = c, portionsLeft = c?.let { new -> (it.portionsLeft ?: (new - it.servings)) + (new - (it.cookPortions ?: new)) })
        }
        send(e, buildJsonObject { put("cook_portions", c?.let { JsonPrimitive(it) } ?: JsonNull) })
    }

    /** Eating and cooking portions in one go (one request, so the server checks them together).
     *  cook: null leaves a plain meal plain; a number makes or keeps it a batch. */
    fun setPortions(e: PlanEntry, eat: Double, cook: Double?) {
        if (eat == e.servings && cook == e.cookPortions) return
        app.store.editEntry(e.id) {
            val was = it.cookPortions
            val left = when {
                cook == null -> it.portionsLeft  // a leftover or a plain meal: the server says what changed
                was == null -> cook - eat  // a new batch
                else -> (it.portionsLeft ?: (was - it.servings)) + (cook - was) - (eat - it.servings)
            }
            it.copy(servings = eat, kcal = it.kcalPerServing?.let { k -> k * eat }, cookPortions = cook, portionsLeft = left)
        }
        send(e, buildJsonObject {
            if (eat != e.servings) put("servings", eat)
            if (cook != e.cookPortions) put("cook_portions", cook?.let { JsonPrimitive(it) } ?: JsonNull)
        })
    }

    fun setMade(e: PlanEntry, g: Double) {
        app.store.editEntry(e.id) { it.copy(madeGrams = g, gramsLeft = (it.gramsLeft ?: 0.0) + g - (it.madeGrams ?: 0.0)) }
        send(e, buildJsonObject { put("made_grams", g) })
    }

    fun reorder(e: PlanEntry, position: Int) = send(e, buildJsonObject { put("day", e.day?.let { JsonPrimitive(it) } ?: JsonNull); put("position", position) })

    fun remove(e: PlanEntry) {
        app.store.editPlan(week) { p -> p.mapEntries { l -> l.filter { it.id != e.id && it.leftoverOf != e.id } } }
        act { app.api.deleteEntry(e.id) }
    }
}

@Composable
private fun WeekStrip(week: String, plan: Plan?, page: Int, onWeek: (String) -> Unit, onPage: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onWeek(addDays(week, -7)) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week", tint = C.muted) }
            Text("${monthDay(week)} – ${monthDay(addDays(week, 6))}", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onWeek(addDays(week, 7)) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week", tint = C.muted) }
            Spacer(Modifier.weight(1f))
            if (weekStart(today()) != week) TextButton(onClick = { onWeek(weekStart(today())) }) { Text("Today", color = C.goText, fontWeight = FontWeight.SemiBold) }
            Chip("Queue · ${plan?.queue?.size ?: 0}", page == QUEUE_PAGE) { onPage(QUEUE_PAGE) }
        }
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L until 7L).map { addDays(week, it) }.forEachIndexed { i, d ->
                DayPill(weekdayShort(d), d.takeLast(2).trimStart('0'), page == i + 1, today = d == today(),
                    dot = plan?.days?.get(d)?.isNotEmpty() == true, modifier = Modifier.weight(1f)) { onPage(i + 1) }
            }
        }
    }
}

@Composable
private fun DayPill(top: String, big: String, selected: Boolean, today: Boolean, dot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.ink else C.surface, label = "day")
    val fg = if (selected) C.bg else C.ink
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg)
            .then(if (today && !selected) Modifier.border(2.dp, C.go, RoundedCornerShape(16.dp)) else Modifier)
            .pressable(onClick, 0.9f).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(top.take(3), fontSize = 11.sp, color = fg.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, maxLines = 1)
        Text(big, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg)
        Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (dot) C.go else Color.Transparent))
    }
}

@Composable
private fun DayPage(day: String?, entries: List<PlanEntry>, actions: PlanActions, onAdd: (String) -> Unit, onNote: (String) -> Unit) {
    val total = dayKcal(entries)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (day == null) "Queue" else if (day == today()) "Today" else weekdayLong(day), style = MaterialTheme.typography.headlineSmall)
                    Text(if (day == null) "Planned, no day yet" else monthDay(day), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (total > 0) Column(horizontalAlignment = Alignment.End) {
                    Text("%,d".format(total.roundToInt()), style = MaterialTheme.typography.headlineSmall, color = C.goText)
                    Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // A day has breakfast, lunch and dinner panels; the queue is one list.
        val groups = if (day == null) listOf("" to entries) else MEALS.map { m -> m to entries.filter { it.meal == m } }
        groups.forEach { (meal, list) ->
            if (meal.isNotEmpty()) item(key = "h:$meal") {
                MealHeader(meal, dayKcal(list), Modifier.animateItem().padding(top = 4.dp), onAdd = { onAdd(meal) }, onNote = { onNote(meal) })
            }
            items(list, key = { it.id }) { e ->
                val i = list.indexOf(e)
                if (e.isNote) NoteCard(e, actions, canUp = i > 0, canDown = i < list.lastIndex, index = i, modifier = Modifier.animateItem())
                else MealCard(e, actions, canUp = i > 0, canDown = i < list.lastIndex, index = i, modifier = Modifier.animateItem())
            }
            if (meal.isNotEmpty() && list.isEmpty()) item(key = "e:$meal") {
                Row(Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, C.line, RoundedCornerShape(18.dp))
                    .pressable({ onAdd(meal) }, 0.98f).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Nothing planned", color = C.faint, modifier = Modifier.weight(1f))
                    if (day != null && day <= today()) LogPill("Ate out", C.danger, Color.Transparent, border = true) { actions.log.outEmpty(day, meal) }
                }
            }
        }
        if (day == null) item(key = "add") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { AddMealButton { onAdd("dinner") } }
                AddNoteButton { onNote("dinner") }
            }
        }
    }
}

/** Planned calories; a meal you ate out instead counts what you logged for it. */
private fun dayKcal(entries: List<PlanEntry>) = entries.sumOf { (if (it.isOut) it.outKcal else it.kcal) ?: 0.0 }

/** "🍳 Breakfast · 420" with buttons to add a meal or a note to it. */
@Composable
private fun MealHeader(meal: String, total: Double, modifier: Modifier = Modifier, compact: Boolean = false, onAdd: () -> Unit, onNote: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${MEAL_EMOJI[meal]} ${MEAL_LABEL[meal]?.uppercase()}", color = C.muted, fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        if (total > 0) Text("  ${total.roundToInt()}", color = C.goText, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(32.dp).clip(CircleShape).pressable(onNote, 0.85f), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.EditNote, "Add a note to ${MEAL_LABEL[meal]}", tint = C.muted, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.size(32.dp).clip(CircleShape).pressable(onAdd, 0.85f), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, "Add to ${MEAL_LABEL[meal]}", tint = C.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Two or three options in a pill, the chosen one raised (like Settings → Theme). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(C.surfaceAlt).padding(3.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) C.surface else Color.Transparent)
                    .pressable({ onPick(value) }, 0.95f).padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) C.ink else C.muted, fontSize = 14.sp) }
        }
    }
}

/** The whole week at a glance: each day with its meals as compact rows. */
@Composable
private fun WeekList(p: Plan, days: List<String>, actions: PlanActions, openDay: (Int) -> Unit, onAdd: (String?, String) -> Unit, onNote: (String?, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(top = 4.dp)) {
        if (p.queue.isNotEmpty()) item(key = "queue") { WeekDay(null, p.queue, actions, { openDay(QUEUE_PAGE) }, { m -> onAdd(null, m) }, { m -> onNote(null, m) }) }
        days.forEachIndexed { i, d ->
            item(key = d) { WeekDay(d, p.days[d].orEmpty(), actions, { openDay(i + 1) }, { m -> onAdd(d, m) }, { m -> onNote(d, m) }) }
        }
    }
}

@Composable
private fun WeekDay(day: String?, entries: List<PlanEntry>, actions: PlanActions, open: () -> Unit, add: (String) -> Unit, note: (String) -> Unit) {
    val total = dayKcal(entries)
    val isToday = day == today()
    Column(Modifier.padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().pressable(open, 0.98f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (day == null) "Queue" else weekdayLong(day), style = MaterialTheme.typography.titleMedium, color = if (isToday) C.goText else C.ink)
            Text(if (day == null) "  no day yet" else "  ${monthDay(day)}", color = C.muted)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(1.dp).background(C.line))
            if (total > 0) Text("%,d".format(total.roundToInt()), fontFamily = Display, fontWeight = FontWeight.Bold, color = C.goText)
            if (day == null) {
                Box(Modifier.padding(start = 4.dp).size(32.dp).clip(CircleShape).pressable({ note("dinner") }, 0.85f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.EditNote, "Add a note", tint = C.muted, modifier = Modifier.size(20.dp))
                }
                Box(Modifier.size(32.dp).clip(CircleShape).pressable({ add("dinner") }, 0.85f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, "Add a meal", tint = C.muted, modifier = Modifier.size(20.dp))
                }
            }
        }
        val groups = if (day == null) listOf("" to entries) else MEALS.map { m -> m to entries.filter { it.meal == m } }
        groups.forEach { (meal, list) ->
            if (meal.isNotEmpty()) MealHeader(meal, dayKcal(list), Modifier.padding(top = 2.dp), compact = true, onAdd = { add(meal) }, onNote = { note(meal) })
            list.forEachIndexed { i, e ->
                if (e.isNote) NoteCard(e, actions, canUp = i > 0, canDown = i < list.lastIndex, index = i, modifier = Modifier.padding(vertical = 4.dp), compact = true)
                else CompactMeal(e, actions, canUp = i > 0, canDown = i < list.lastIndex, index = i)
            }
        }
    }
}

@Composable
private fun CompactMeal(e: PlanEntry, a: PlanActions, canUp: Boolean, canDown: Boolean, index: Int) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(thumb(e.imageUrl, 140), null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(C.surfaceAlt)
                .then(if (e.recipeId != null) Modifier.pressable({ a.openRecipe(e.recipeId) }) else Modifier))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    e.isOut -> Pill("Ate out" + (e.outKcal?.let { " · ${it.roundToInt()}" } ?: ""), color = Color.White, background = C.danger)
                    e.status == "eaten" -> Pill("✓ Eaten", color = C.onGo, background = C.go)
                    e.isPrep -> Pill("🫙 Prep · ${(e.madeGrams ?: 0.0).roundToInt()} g", color = C.goText, background = C.goSoft)
                    e.isLeftover -> Pill("Leftovers", color = C.blueFg, background = C.blueBg)
                    e.isBatch -> Pill("Batch · ${num(e.cookPortions ?: 0.0)}", color = C.purpleFg, background = C.purpleBg)
                }
                if (!e.isPrep) Text((if (e.isLeftover || e.isBatch || e.status != null) "  " else "") + listOfNotNull(
                    if (e.servings != 1.0) plural(e.servings, "serving") else null, e.kcal?.let { kcal(it) }).joinToString(" · "),
                    color = C.muted, style = MaterialTheme.typography.bodySmall)
            }
            if (e.short.isNotEmpty()) Text("Needs ${shortText(e)}: buying the ingredients", color = C.pinkFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${e.title}", tint = C.muted) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (e.loggable()) LogMenuItems(e, a) { menu = false }
                DropdownMenuItem(text = { Text("Move to another day") }, onClick = { menu = false; a.onMove(e) })
                if (e.hasPortions()) DropdownMenuItem(text = { Text("Change portions") }, onClick = { menu = false; a.onPortions(e) })
                if (canUp) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; a.reorder(e, index - 1) })
                if (canDown) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; a.reorder(e, index + 1) })
                if (!e.isPrep) DropdownMenuItem(text = { Text("Eat one more") }, onClick = { menu = false; a.setServings(e, e.servings + 1) })
                if (!e.isPrep && e.servings > 1) DropdownMenuItem(text = { Text("Eat one less") }, onClick = { menu = false; a.setServings(e, e.servings - 1) })
                if (!e.isLeftover && !e.isPrep && e.recipeId != null) {
                    if (e.isBatch) DropdownMenuItem(text = { Text("Not a batch") }, onClick = { menu = false; a.setCook(e, null) })
                    else DropdownMenuItem(text = { Text("Batch cook") }, onClick = { menu = false; a.setCook(e, e.servings + 3) })
                }
                DropdownMenuItem(text = { Text(if (e.isBatch) "Remove (and its leftovers)" else "Remove", color = C.danger) }, onClick = { menu = false; a.remove(e) })
            }
        }
    }
}

/** Log it from a card's menu (the week view has no room for the buttons). */
@Composable
private fun LogMenuItems(e: PlanEntry, a: PlanActions, close: () -> Unit) {
    when (e.status) {
        null -> {
            if (!e.isNote) DropdownMenuItem(text = { Text("✓ Ate it") }, onClick = { close(); a.log.eaten(e) })
            DropdownMenuItem(text = { Text("Ate out", color = C.danger) }, onClick = { close(); a.log.out(e) })
        }
        else -> DropdownMenuItem(text = { Text("Undo the log") }, onClick = { close(); a.log.undo(e) })
    }
}

/** Where a new note goes, or the note being edited. */
private data class NoteTarget(val day: String?, val meal: String, val entry: PlanEntry?)

@Composable
private fun AddNoteButton(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(22.dp))
            .pressable(onClick, 0.97f).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EditNote, null, tint = C.muted)
        Text("Note", color = C.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

/** A note on the plan ("Dinner at Priya's", "Defrost the chicken"): tap to edit. */
@Composable
private fun NoteCard(e: PlanEntry, a: PlanActions, canUp: Boolean, canDown: Boolean, index: Int, modifier: Modifier, compact: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(if (compact) 14.dp else 20.dp)).background(if (e.isOut) C.danger.copy(alpha = 0.12f) else C.yellowBg)
            .pressable({ if (e.isOut) a.log.out(e) else a.onEditNote(e) }, 0.98f).padding(start = 14.dp, top = if (compact) 8.dp else 12.dp, bottom = if (compact) 8.dp else 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (e.isOut) Text("🍽") else Icon(Icons.Outlined.EditNote, null, tint = C.yellowFg)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(e.title + (if (e.isOut) e.outKcal?.let { " · ${it.roundToInt()} kcal" } ?: "" else ""), color = if (e.isOut) C.danger else C.ink,
                style = MaterialTheme.typography.bodyLarge, fontWeight = if (e.isOut) FontWeight.SemiBold else FontWeight.Normal)
            if (!compact && e.loggable() && !e.isOut) LogButtons(e, a.log, Modifier.padding(top = 8.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for note", tint = C.muted) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (e.loggable()) LogMenuItems(e, a) { menu = false }
                if (!e.isOut) DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; a.onEditNote(e) })
                DropdownMenuItem(text = { Text("Move to another day") }, onClick = { menu = false; a.onMove(e) })
                if (canUp) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; a.reorder(e, index - 1) })
                if (canDown) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; a.reorder(e, index + 1) })
                DropdownMenuItem(text = { Text("Remove", color = C.danger) }, onClick = { menu = false; a.remove(e) })
            }
        }
    }
}

@Composable
private fun NoteDialog(initial: String, isNew: Boolean, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a note" else "Note") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, placeholder = { Text("e.g. Dinner at Priya's, defrost the chicken") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove note", color = C.danger) }
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save", color = C.goText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddMealButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(22.dp))
            .pressable(onClick, 0.97f).padding(18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = C.muted)
        Text("Add a meal", color = C.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun shortText(e: PlanEntry) = e.short.joinToString { "${it.grams.roundToInt()} g ${it.title.lowercase()}" }

/** Nothing planned makes enough of a prep this meal uses: the grocery list buys its ingredients. */
@Composable
private fun Shortfall(e: PlanEntry) {
    if (e.short.isEmpty()) return
    Text("Needs ${shortText(e)}: nothing planned makes enough, so the grocery list buys the ingredients.",
        color = C.pinkFg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(C.pinkBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
}

/** Making a prepped ingredient: by weight, and it all goes to the fridge. */
@Composable
private fun PrepCard(e: PlanEntry, a: PlanActions, canUp: Boolean, canDown: Boolean, index: Int, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    var weighing by remember { mutableStateOf(false) }
    Surface(color = C.goSoft, contentColor = C.ink, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(thumb(e.imageUrl, 200), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(C.surface)
                        .then(if (e.recipeId != null) Modifier.pressable({ a.openRecipe(e.recipeId) }) else Modifier))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("🫙 PREP", color = C.goText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = if (e.recipeId != null) Modifier.clickable { a.openRecipe(e.recipeId) } else Modifier)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${e.title}") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move to another day") }, onClick = { menu = false; a.onMove(e) })
                        if (canUp) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; a.reorder(e, index - 1) })
                        if (canDown) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; a.reorder(e, index + 1) })
                        DropdownMenuItem(text = { Text("Remove", color = C.danger) }, onClick = { menu = false; a.remove(e) })
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("makes ${(e.madeGrams ?: 0.0).roundToInt()} g", fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.surface).clickable { weighing = true }.padding(horizontal = 12.dp, vertical = 8.dp))
                Spacer(Modifier.weight(1f))
                val left = e.gramsLeft ?: 0.0
                Text(if (left < 1) "all used by your plan" else "${left.roundToInt()} g spare", color = if (left < 1) C.muted else C.goText,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
            Shortfall(e)
        }
    }
    if (weighing) WeightDialog("How much are you making?", "Meals that use ${e.title.lowercase()} take from this, oldest first.", e.madeGrams,
        onDismiss = { weighing = false }) { g -> weighing = false; if (g != null) a.setMade(e, g) }
}

@Composable
private fun MealCard(e: PlanEntry, a: PlanActions, canUp: Boolean, canDown: Boolean, index: Int, modifier: Modifier) {
    if (e.isPrep) return PrepCard(e, a, canUp, canDown, index, modifier)
    var menu by remember { mutableStateOf(false) }
    val accent = when { e.isOut -> C.danger; e.isLeftover -> C.blueFg; e.isBatch -> C.purpleFg; else -> C.ink }
    Surface(color = if (e.isOut) C.danger.copy(alpha = 0.10f) else C.surface, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(thumb(e.imageUrl, 200), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(C.line)
                        .then(if (e.recipeId != null) Modifier.pressable({ a.openRecipe(e.recipeId) }) else Modifier))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    if (e.isOut || e.isLeftover || e.isBatch) Text(if (e.isOut) "ATE OUT · IN THE FRIDGE" else if (e.isLeftover) "LEFTOVERS" else "BATCH COOK", color = accent, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = if (e.recipeId != null) Modifier.clickable { a.openRecipe(e.recipeId) } else Modifier)
                    e.kcal?.let { Text(kcal(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${e.title}") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Move to another day") }, onClick = { menu = false; a.onMove(e) })
                        if (e.hasPortions()) DropdownMenuItem(text = { Text("Change portions") }, onClick = { menu = false; a.onPortions(e) })
                        if (canUp) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; a.reorder(e, index - 1) })
                        if (canDown) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; a.reorder(e, index + 1) })
                        if (!e.isLeftover && !e.isOut && e.recipeId != null) {
                            if (e.isBatch) DropdownMenuItem(text = { Text("Not a batch") }, onClick = { menu = false; a.setCook(e, null) })
                            else DropdownMenuItem(text = { Text("Batch cook") }, onClick = { menu = false; a.setCook(e, e.servings + 3) })
                        }
                        DropdownMenuItem(text = { Text(if (e.isBatch) "Remove (and its leftovers)" else "Remove", color = C.danger) },
                            onClick = { menu = false; a.remove(e) })
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stepper(e.servings, { a.setServings(e, it) }, step = 0.5, min = 0.5, label = "eat", showLabel = true)
                Spacer(Modifier.weight(1f))
                if (e.isBatch) Stepper(e.cookPortions!!, { a.setCook(e, it) }, min = e.servings, label = "cook", showLabel = true, tint = C.purpleFg)
            }
            if (e.isBatch) {
                val left = e.portionsLeft ?: 0.0
                Text(
                    if (left < 0) "${num(-left)} more planned than cooked" else "${plural(left, "portion")} left for later",
                    color = if (left < 0) C.danger else C.purpleFg, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(C.purpleBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Shortfall(e)
            if (e.loggable()) LogButtons(e, a.log, Modifier.padding(top = 10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(day: String?, initialMeal: String, onDismiss: () -> Unit, onAdd: (JsonObject) -> Unit) {
    val app = app()
    var tab by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var recipes by remember { mutableStateOf<List<RecipeSummary>?>(null) }
    val batches by app.store.batches.collectAsState()
    LaunchedEffect(q) { delay(250); recipes = runCatching { app.api.recipes(q.trim()) }.getOrNull() }
    LaunchedEffect(Unit) { runCatching { app.store.loadBatches() } }
    val dayJson = day?.let { JsonPrimitive(it) } ?: JsonNull
    var meal by remember { mutableStateOf(initialMeal) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.bg) {
        Text("Add to ${if (day == null) "the queue" else dayChipLabel(day)}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        if (day != null) Row(Modifier.padding(start = 20.dp, top = 10.dp)) { Segmented(MEALS.map { it to MEAL_LABEL.getValue(it) }, meal) { meal = it } }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Recipes", tab == 0) { tab = 0 }
            Chip("From the fridge" + (batches?.size?.takeIf { it > 0 }?.let { " · $it" } ?: ""), tab == 1) { tab = 1 }
        }
        if (tab == 0) {
            SearchPill(q, { q = it }, Modifier.padding(horizontal = 20.dp), placeholder = "Find a recipe")
            LazyColumn(Modifier.height(480.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(recipes.orEmpty(), key = { it.id }) { r ->
                    PickRow(r.imageUrl, r.title, r.kcalPerServing?.let { "${it.roundToInt()} kcal" }) {
                        onAdd(buildJsonObject { put("day", dayJson); put("meal", meal); put("recipe_id", r.id); put("servings", 1.0) })
                    }
                }
            }
        } else {
            LazyColumn(Modifier.height(540.dp)) {
                if (batches?.isEmpty() == true) item {
                    Empty("🥡", "Fridge is empty", "Plan a meal that cooks more than you eat, and the rest shows up here.")
                }
                items(batches.orEmpty(), key = { it.id }) { b ->
                    PickRow(b.imageUrl, b.title, "${num(b.portionsLeft ?: 0.0)} left", sub = b.day?.let { "cooked ${monthDay(it)}" } ?: "not scheduled") {
                        onAdd(buildJsonObject { put("day", dayJson); put("meal", meal); put("leftover_of", b.id); put("servings", 1.0) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(image: String?, title: String, trailing: String?, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick, 0.98f).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(thumb(image, 120), null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(C.line))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.Add, null, tint = C.go, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(entry: PlanEntry, week: String, onDismiss: () -> Unit, onPick: (String?, String) -> Unit) {
    var meal by remember { mutableStateOf(entry.meal) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.bg) {
        Text("Move to…", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Text(entry.title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
        Row(Modifier.padding(start = 20.dp, top = 12.dp)) { Segmented(MEALS.map { it to MEAL_LABEL.getValue(it) }, meal) { meal = it } }
        Spacer(Modifier.height(16.dp))
        DayChips(entry.day, { onPick(it, meal) }, from = week, days = 14, wrap = true)
        Spacer(Modifier.height(40.dp))
    }
}

/** A recipe or leftovers on the plan (not a note, a prep made by weight, or a meal eaten out). */
private fun PlanEntry.hasPortions() = !isNote && !isPrep && !isOut && (recipeId != null || isLeftover)

/** How many portions you eat at this meal and, for a recipe, how many you cook: cooking more than you
 *  eat makes it a batch, and the rest goes to the fridge for later meals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortionsSheet(entry: PlanEntry, onDismiss: () -> Unit, onSave: (eat: Double, cook: Double?) -> Unit) {
    var eat by remember { mutableStateOf(entry.servings) }
    var cook by remember { mutableStateOf(entry.cookPortions ?: entry.servings) }
    val canCook = !entry.isLeftover && entry.recipeId != null
    // Cooking only what you eat keeps a plain meal plain; a batch stays a batch ("Not a batch" in the menu
    // undoes it, and takes its leftovers with it).
    val cookOut = if (!canCook || (entry.cookPortions == null && cook <= eat)) null else maxOf(cook, eat)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.bg) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Portions", style = MaterialTheme.typography.headlineSmall)
            Text(entry.title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepperCard("Eat", if (eat == 1.0) "portion" else "portions", eat, { eat = it; if (cook < it) cook = it }, Modifier.weight(1f), step = 0.5, min = 0.5)
                if (canCook) StepperCard("Cook", if (cook == 1.0) "portion" else "portions", cook, { cook = it }, Modifier.weight(1f), min = eat)
            }
            val left = (cookOut ?: 0.0) - eat
            Text(
                when {
                    entry.isLeftover -> "Taken from the batch in the fridge."
                    !canCook -> ""
                    cookOut == null -> "Cook more than you eat to batch cook: the rest goes to the fridge."
                    left > 0 -> "Batch cook: ${plural(left, "portion")} to the fridge for later."
                    else -> "Batch cook, nothing left over for later."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp),
            )
            Button(
                onClick = { onSave(eat, cookOut) },
                colors = ButtonDefaults.buttonColors(containerColor = C.ink, contentColor = C.bg),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp).height(52.dp),
            ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun GroceryDialog(week: String, onDismiss: () -> Unit, onMake: (Boolean) -> Unit) {
    var includeQueue by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make grocery list") },
        text = {
            Column {
                Text("Build the list from this week's meals (${monthDay(week)} – ${monthDay(addDays(week, 6))}). Items you added by hand stay.")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).clickable { includeQueue = !includeQueue }) {
                    Checkbox(includeQueue, { includeQueue = it }, colors = CheckboxDefaults.colors(checkedColor = C.go))
                    Text("Include the queue")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMake(includeQueue) }) { Text("Make list", color = C.goText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
