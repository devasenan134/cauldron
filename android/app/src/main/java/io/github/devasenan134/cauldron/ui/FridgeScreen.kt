package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.PlanEntry
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Cooked food keeps about 3–4 days in the fridge.
private const val EAT_SOON_DAYS = 3

@Composable
fun FridgeScreen(openRecipe: (Int) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var load by remember { mutableStateOf<Load<List<PlanEntry>>>(Load.Loading) }
    var attempt by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var tossing by remember { mutableStateOf<PlanEntry?>(null) }

    suspend fun reload() {
        load = try { Load.Ready(app.api.batches()) } catch (e: Exception) { if (load is Load.Ready) { snackbar.showSnackbar(e.friendly()); load } else Load.Failed(e.friendly()) }
    }
    LaunchedEffect(attempt) { reload() }
    fun act(msg: String?, block: suspend () -> Unit) = scope.launch {
        try { block(); msg?.let { launch { snackbar.showSnackbar(it) } } } catch (e: Exception) { snackbar.showSnackbar(e.friendly()) }
        reload()
    }

    Scaffold(topBar = { TopBar("Fridge") }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = Cream) { padding ->
        Loaded(load, onRetry = { attempt++ }) { batches ->
            val cooked = batches.filter { it.day != null && it.day <= today() }
            val coming = batches.filter { it.day == null || it.day > today() }
            PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; reload(); refreshing = false } }, modifier = Modifier.padding(padding)) {
                LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("In the fridge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            val total = cooked.sumOf { it.portionsLeft ?: 0.0 }
                            if (cooked.isNotEmpty()) Text("  ${num(total)} ${if (total == 1.0) "portion" else "portions"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Batches you've cooked with portions left over (after the leftovers you've already planned).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (cooked.isEmpty()) item {
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Text("Nothing yet. Plan a meal that cooks more than you eat (or use Batch cook on a planned meal), and once its day comes the rest shows up here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        }
                    }
                    items(cooked, key = { it.id }) { b ->
                        BatchCard(b, upcoming = false, open = { b.recipeId?.let(openRecipe) },
                            onAte = { act("Logged 1 portion for today") {
                                app.api.addEntry(buildJsonObject { put("day", today()); put("leftover_of", b.id); put("servings", 1.0) })
                            } },
                            onToss = { tossing = b })
                    }
                    if (coming.isNotEmpty()) {
                        item {
                            Spacer(Modifier.size(8.dp))
                            Text("Coming up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Batches on the plan that haven't been cooked yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(coming, key = { it.id }) { b -> BatchCard(b, upcoming = true, open = { b.recipeId?.let(openRecipe) }, onAte = {}, onToss = {}) }
                    }
                }
            }
        }
    }

    tossing?.let { b ->
        AlertDialog(
            onDismissRequest = { tossing = null },
            title = { Text("Toss the rest?") },
            text = { Text("Mark the ${num(b.portionsLeft ?: 0.0)} portions of ${b.title} left in the fridge as thrown away.") },
            confirmButton = {
                TextButton(onClick = {
                    tossing = null
                    act(null) { app.api.updateEntry(b.id, buildJsonObject { put("discarded", b.discarded + (b.portionsLeft ?: 0.0)) }) }
                }) { Text("Toss", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { tossing = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun BatchCard(b: PlanEntry, upcoming: Boolean, open: () -> Unit, onAte: () -> Unit, onToss: () -> Unit) {
    val age = b.day?.let { daysAgo(it) }
    val old = !upcoming && age != null && age >= EAT_SOON_DAYS
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (old) Amber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(thumb(b.imageUrl, 200), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = open))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(b.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = open))
                Row {
                    Text(
                        when {
                            upcoming -> b.day?.let { "cooking ${weekdayShort(it)} ${monthDay(it)}" } ?: "in the queue"
                            age == 0L -> "cooked today"
                            age == 1L -> "cooked yesterday"
                            else -> "cooked $age days ago"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (old) Text(" · eat soon", style = MaterialTheme.typography.bodySmall, color = Amber, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                    Text(num(b.portionsLeft ?: 0.0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("  of ${num(b.cookPortions ?: 0.0)} left · ${kcal(b.kcalPerServing)} each",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (!upcoming) Row(Modifier.padding(top = 6.dp)) {
                    Button(onClick = onAte, colors = ButtonDefaults.buttonColors(containerColor = Ink), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Ate 1 today") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onToss, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Toss the rest", color = Ink) }
                }
            }
        }
    }
}
