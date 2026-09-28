package io.github.devasenan134.cauldron.ui

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

    fun act(msg: String?, block: suspend () -> Unit) = scope.launch {
        try { block(); msg?.let { launch { snackbar.showSnackbar(it) } } } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        store.refreshPlans()
    }

    Box(Modifier.fillMaxSize()) {
        val cooked = batches.orEmpty().filter { it.day != null && it.day <= today() }
        val coming = batches.orEmpty().filter { it.day == null || it.day > today() }
        val portions = cooked.sumOf { it.portionsLeft ?: 0.0 }
        Column {
            ScreenHeader("Fridge", subtitle = if (cooked.isEmpty()) "Batch cooking" else "${plural(portions, "portion")} ready to eat")
            Loaded(load, retry) { _ ->
                PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; runCatching { store.loadBatches() }; refreshing = false } }) {
                    LazyColumn(contentPadding = screenPadding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        if (cooked.isEmpty()) item {
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
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomSpace.current + 16.dp))
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
