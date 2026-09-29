package io.github.devasenan134.cauldron.ui

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.clickable

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ModalBottomSheet

import androidx.compose.material3.OutlinedTextField

import androidx.compose.runtime.LaunchedEffect

import io.github.devasenan134.cauldron.data.PrepStock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.PlanEntry
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.ceil
import kotlin.math.roundToInt

// Cooked food keeps about 3–4 days in the fridge.
private const val EAT_SOON_DAYS = 3

@Composable
fun FridgeScreen(openRecipe: (Int) -> Unit) {
    val app = app()
    val store = app.store
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val batches by store.batches.collectAsState()
    val (load, retry) = cached(batches, LocalRefresh.current) { store.loadBatches() }
    var refreshing by remember { mutableStateOf(false) }
    var tossing by remember { mutableStateOf<PlanEntry?>(null) }
    val stock by store.prepStock.collectAsState()
    LaunchedEffect(LocalRefresh.current) { runCatching { store.loadPrepStock() } }
    var using by remember { mutableStateOf<PrepStock?>(null) }
    var finishing by remember { mutableStateOf<PrepStock?>(null) }
    var addingPrep by remember { mutableStateOf(false) }
    fun takeOut(p: PrepStock, grams: Double) {
        store.editPrepStock { l -> l.map { if (it.entryId == p.entryId) it.copy(gramsNow = (it.gramsNow - grams).coerceAtLeast(0.0), gramsLeft = (it.gramsLeft - grams).coerceAtLeast(0.0)) else it } }
        scope.launch {
            try { app.api.updateEntry(p.entryId, buildJsonObject { put("discarded", p.discarded + grams) }) } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
            store.refreshPlans()
        }
    }

    fun act(msg: String?, block: suspend () -> Unit) = scope.launch {
        try { block(); msg?.let { launch { snackbar.showSnackbar(it) } } } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        store.refreshPlans()
    }

    Box(Modifier.fillMaxSize()) {
        val cooked = batches.orEmpty().filter { it.day != null && it.day <= today() }
        val coming = batches.orEmpty().filter { it.day == null || it.day > today() }
        val portions = cooked.sumOf { it.portionsLeft ?: 0.0 }
        val preps = stock.orEmpty().filter { it.day != null && it.day <= today() }
        val prepsLater = stock.orEmpty().filter { it.day == null || it.day > today() }
        Column {
            ScreenHeader("Fridge", subtitle = listOfNotNull(
                if (cooked.isNotEmpty()) "${plural(portions, "portion")} ready to eat" else null,
                if (preps.isNotEmpty()) plural(preps.size.toDouble(), "prepped ingredient") else null,
            ).joinToString(" · ").ifEmpty { "Batch cooking and prep" })
            Loaded(load, retry) { _ ->
                PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; runCatching { store.loadBatches() }; runCatching { store.loadPrepStock() }; refreshing = false } }) {
                    LazyColumn(contentPadding = screenPadding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        if (cooked.isEmpty() && preps.isEmpty()) item {
                            Empty("🥡", "Nothing in the fridge", "Plan a meal that cooks more than you eat (or use Batch cook on a planned meal). Once its day comes, the rest shows up here.")
                        }
                        items(cooked, key = { it.id }) { b ->
                            BatchCard(b, Modifier.animateItem(), open = { b.recipeId?.let(openRecipe) },
                                onAte = {
                                    store.editEntry(b.id) { it.copy(portionsLeft = (it.portionsLeft ?: 0.0) - 1) }
                                    act("Logged 1 portion for today") {
                                        app.api.addEntry(buildJsonObject { put("day", today()); put("leftover_of", b.id); put("servings", 1.0) })
                                    }
                                },
                                onToss = { tossing = b })
                        }
                        if (coming.isNotEmpty()) {
                            item { SectionLabel("Coming up") }
                            items(coming, key = { it.id }) { b -> ComingRow(b, Modifier.animateItem()) { b.recipeId?.let(openRecipe) } }
                        }
                        item {
                            SectionLabel("🫙 Prepped") {
                                TextButton(onClick = { addingPrep = true }) { Text("+ Add", color = C.ink, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        if (stock != null && preps.isEmpty()) item {
                            Text("No prepped ingredients in the fridge. Plan a prep (cooked rice, pickled onions, a sauce) from its recipe page, or add what you already have.",
                                color = C.muted, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(20.dp)).padding(16.dp))
                        }
                        items(preps, key = { "p${it.entryId}" }) { p ->
                            PrepStockCard(p, later = false, modifier = Modifier.animateItem(), open = { openRecipe(p.recipeId) },
                                onUse = { using = p }, onFinish = { finishing = p })
                        }
                        items(prepsLater, key = { "p${it.entryId}" }) { p ->
                            PrepStockCard(p, later = true, modifier = Modifier.animateItem(), open = { openRecipe(p.recipeId) }, onUse = {}, onFinish = {})
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomSpace.current + 16.dp))
    }

    using?.let { p ->
        WeightDialog("Used some ${p.title.lowercase()}?", "How much did you use outside the plan? It comes off what's in the fridge.", null,
            onDismiss = { using = null }) { g -> using = null; if (g != null) takeOut(p, g) }
    }
    finishing?.let { p ->
        AlertDialog(
            onDismissRequest = { finishing = null },
            title = { Text("Finished?") },
            text = { Text("Mark the ${p.gramsNow.roundToInt()} g of ${p.title} in the fridge as used up or thrown away." +
                if (p.uses.any { it.day == null || it.day >= today() }) " Meals planned with it will buy its ingredients instead." else "") },
            confirmButton = { TextButton(onClick = { finishing = null; takeOut(p, p.gramsNow) }) { Text("Finished", color = C.danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { finishing = null }) { Text("Keep") } },
        )
    }
    if (addingPrep) AddPrepSheet(onDismiss = { addingPrep = false }) { recipeId, grams ->
        addingPrep = false
        act("Added to the fridge") {
            app.api.addEntry(buildJsonObject { put("day", today()); put("recipe_id", recipeId); grams?.let { put("made_grams", it) } })
        }
    }

    tossing?.let { b ->
        AlertDialog(
            onDismissRequest = { tossing = null },
            title = { Text("Toss the rest?") },
            text = { Text("Mark the ${plural(b.portionsLeft ?: 0.0, "portion")} of ${b.title} left in the fridge as thrown away.") },
            confirmButton = {
                TextButton(onClick = {
                    tossing = null
                    val gone = b.portionsLeft ?: 0.0
                    store.editEntry(b.id) { it.copy(portionsLeft = 0.0, discarded = it.discarded + gone) }
                    act(null) { app.api.updateEntry(b.id, buildJsonObject { put("discarded", b.discarded + gone) }) }
                }) { Text("Toss", color = C.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { tossing = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun BatchCard(b: PlanEntry, modifier: Modifier, open: () -> Unit, onAte: () -> Unit, onToss: () -> Unit) {
    val age = daysAgo(b.day!!)
    val old = age >= EAT_SOON_DAYS
    val left = b.portionsLeft ?: 0.0
    val made = b.cookPortions ?: 0.0
    Surface(color = C.surface, shape = RoundedCornerShape(28.dp), modifier = modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(170.dp).pressable(open, 0.99f)) {
                AsyncImage(thumb(b.imageUrl, 900, 500), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(C.line))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                Pill(
                    if (old) "⏰ Eat soon · ${age}d" else when (age) { 0L -> "Cooked today"; 1L -> "Cooked yesterday"; else -> "Cooked $age days ago" },
                    color = if (old) Color.White else Color.Black, background = if (old) C.pinkFg else Color.White, modifier = Modifier.padding(14.dp),
                )
                Text(b.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(num(left), style = MaterialTheme.typography.displaySmall)
                    Text("  of ${num(made)} left · ${kcal(b.kcalPerServing)} each", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                PortionDots(left, made)
                Row(Modifier.padding(top = 14.dp)) {
                    Button(onClick = onAte, enabled = left > 0, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo), modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Ate one", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = onToss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Toss the rest", color = C.ink, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** One dot per portion: filled while it's still in the fridge. */
@Composable
private fun PortionDots(left: Double, made: Double) {
    val total = ceil(made).roundToInt().coerceIn(0, 24)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        repeat(total) { i ->
            val full = i < left
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (full) C.go else Color.Transparent)
                .border(2.dp, if (full) C.go else C.line, CircleShape))
        }
    }
}

@Composable
private fun ComingRow(b: PlanEntry, modifier: Modifier, open: () -> Unit) {
    Surface(color = C.surface, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth().pressable(open, 0.98f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(thumb(b.imageUrl, 140), null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(C.line))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(b.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(b.day?.let { "Cooking ${weekdayShort(it)} ${monthDay(it)}" } ?: "In the queue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(num(b.portionsLeft ?: 0.0), style = MaterialTheme.typography.titleLarge, color = C.purpleFg)
                Text("for later", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A prepped ingredient in the fridge: grams in it now, and the planned meals that will use some. */
@Composable
private fun PrepStockCard(p: PrepStock, later: Boolean, modifier: Modifier, open: () -> Unit, onUse: () -> Unit, onFinish: () -> Unit) {
    val age = p.day?.let { daysAgo(it) } ?: 0L
    val old = !later && age >= EAT_SOON_DAYS
    val share = if (p.madeGrams > 0) (p.gramsNow / p.madeGrams).toFloat().coerceIn(0f, 1f) else 0f
    val planned = if (p.madeGrams > 0) ((p.gramsNow - p.gramsLeft) / p.madeGrams).toFloat().coerceIn(0f, share) else 0f
    val upcoming = p.uses.filter { it.day == null || it.day >= today() }
    Surface(color = C.surface, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (later) 0.8f else 1f }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(C.goSoft).pressable(open), contentAlignment = Alignment.Center) {
                    if (p.imageUrl != null) AsyncImage(thumb(p.imageUrl, 140), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("🫙", fontSize = 24.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = open))
                    Text(
                        if (later) p.day?.let { "Making ${weekdayShort(it)} ${monthDay(it)}" } ?: "In the queue"
                        else if (old) "⏰ Use soon · made ${age}d ago" else when (age) { 0L -> "Made today"; 1L -> "Made yesterday"; else -> "Made $age days ago" },
                        color = if (old) C.pinkFg else C.muted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${(if (later) p.madeGrams else p.gramsNow).roundToInt()} g", style = MaterialTheme.typography.headlineSmall)
                    Text(if (later) "to make" else "of ${p.madeGrams.roundToInt()} g", color = C.muted, fontSize = 11.sp)
                }
            }
            // Green: spare; lighter: set aside for planned meals.
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(C.surfaceAlt)) {
                if (share - planned > 0f) Box(Modifier.weight(share - planned).fillMaxHeight().background(C.go))
                if (planned > 0f) Box(Modifier.weight(planned).fillMaxHeight().background(C.go.copy(alpha = 0.35f)))
                if (share < 1f) Spacer(Modifier.weight(1f - share))
            }
            if (upcoming.isNotEmpty()) Text(
                "Planned: " + upcoming.joinToString { "${it.grams.roundToInt()} g for ${it.title}" + (it.day?.let { d -> if (d == today()) " (today)" else " (${weekdayShort(d)})" } ?: "") } +
                    " · ${p.gramsLeft.roundToInt()} g spare",
                color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
            p.kcalPer100g?.let { Text("${kcal(it)} per 100 g", color = C.faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp)) }
            if (!later) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUse, modifier = Modifier.weight(1f)) { Text("Used some", color = C.ink, fontWeight = FontWeight.SemiBold) }
                OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finished", color = C.ink, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Put a prep you already have in the fridge (made today, by weight). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPrepSheet(onDismiss: () -> Unit, onAdd: (Int, Double?) -> Unit) {
    val app = app()
    var preps by remember { mutableStateOf<List<io.github.devasenan134.cauldron.data.RecipeSummary>?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var grams by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { preps = runCatching { app.api.preps() }.getOrDefault(emptyList()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Already made some?", style = MaterialTheme.typography.headlineSmall)
            Text("It goes in the fridge as made today.", color = C.muted)
            preps?.forEach { r ->
                val on = picked == r.id
                Text("🫙  ${r.title}", fontWeight = FontWeight.SemiBold, color = if (on) C.bg else C.ink,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (on) C.ink else C.surface)
                        .pressable({ picked = r.id }, 0.98f).padding(14.dp))
            }
            if (preps?.isEmpty() == true) Text("No prepped ingredients yet: switch it on from a recipe's page.", color = C.muted, modifier = Modifier.padding(top = 12.dp))
            if (picked != null) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = grams, onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("How much (blank: whole recipe)") },
                    singleLine = true, suffix = { Text("g") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Button(onClick = { onAdd(picked!!, grams.toDoubleOrNull()?.takeIf { it > 0 }) }, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo),
                    modifier = Modifier.padding(start = 8.dp)) { Text("Add", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
