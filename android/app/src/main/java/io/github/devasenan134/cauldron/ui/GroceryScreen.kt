package io.github.devasenan134.cauldron.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.data.GroceryItem
import kotlinx.coroutines.launch
import java.io.IOException

private val AISLE_EMOJI = mapOf(
    "produce" to "🥬", "meat" to "🥩", "seafood" to "🐟", "dairy" to "🥚", "eggs" to "🥚", "bakery" to "🍞", "bread" to "🍞",
    "pantry" to "🥫", "spices" to "🧂", "frozen" to "🧊", "beverages" to "🥤", "drinks" to "🥤", "condiments" to "🫙",
    "oils" to "🫒", "canned" to "🥫", "grains" to "🌾", "baking" to "🧁", "snacks" to "🍿", "other" to "🛒",
)

/** Roughly the order you walk a store in. */
private val AISLE_ORDER = listOf("produce", "bakery", "bread", "meat", "seafood", "dairy", "eggs", "grains", "pantry", "canned", "oils",
    "condiments", "spices", "baking", "snacks", "beverages", "drinks", "frozen", "other")

private fun aisleRank(aisle: String) = AISLE_ORDER.indexOfFirst { aisle.lowercase().contains(it) }.let { if (it < 0) AISLE_ORDER.size else it }

private fun aisleEmoji(aisle: String) = AISLE_EMOJI.entries.firstOrNull { aisle.lowercase().contains(it.key) }?.value ?: "🛒"

@Composable
fun GroceryScreen(openPlan: () -> Unit) {
    val app = app()
    val repo = app.grocery
    val scope = rememberCoroutineScope()
    val items by repo.items.collectAsState()
    val pending by repo.pending.collectAsState()
    val online by repo.online.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val tick = rememberTick()

    suspend fun sync() = try { repo.sync() } catch (_: IOException) { }
    LaunchedEffect(Unit) { sync() }
    fun act(block: suspend () -> Unit) = scope.launch { block() }
    fun addItem() {
        if (name.isBlank()) return
        // "Paneer 200 g" -> name + amount: a trailing number (and unit) is the amount.
        val m = Regex("""^(.*?)\s+(\d[\d.,/]*\s*\p{L}{0,6})$""").find(name.trim())
        val (n, a) = if (m != null) m.groupValues[1] to m.groupValues[2] else name.trim() to ""
        name = ""
        act { repo.add(n, a) }
    }

    val toBuy = items.filter { !it.checked }
    val done = items.filter { it.checked }
    val aisles = toBuy.groupBy { it.aisle }.toSortedMap(compareBy<String> { aisleRank(it) }.thenBy { it })
    val progress by animateFloatAsState(if (items.isEmpty()) 0f else done.size.toFloat() / items.size, tween(500), label = "cart")

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Grocery", subtitle = if (items.isEmpty()) "Your list" else "${done.size} of ${items.size} in the cart")
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; sync(); refreshing = false } }) {
            LazyColumn(contentPadding = screenPadding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                if (items.isNotEmpty()) item(key = "progress") {
                    Box(Modifier.padding(bottom = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(EmberSoft)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFB923C), EmberBright))))
                    }
                }
                if (!online) item(key = "offline") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(AmberSoft, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, null, tint = Amber)
                        Text(
                            if (pending > 0) "Offline · ${plural(pending.toDouble(), "change")} will sync when you're back online."
                            else "Offline · showing the list saved on this phone.",
                            color = Amber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                item(key = "add") { AddPill(name, { name = it }, ::addItem) }
                if (items.isEmpty()) item(key = "empty") {
                    Empty("🛒", "Your list is empty", "Plan some meals, then tap the cart button on the Plan tab to build the list.") {
                        TextButton(onClick = openPlan) { Text("Open the plan", color = Ember, fontWeight = FontWeight.Bold) }
                    }
                }
                aisles.forEach { (aisle, group) ->
                    item(key = "aisle:$aisle") {
                        Text("${aisleEmoji(aisle)}  $aisle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.animateItem().padding(top = 14.dp, bottom = 4.dp, start = 4.dp))
                    }
                    items(group, key = { it.id }) { item ->
                        ItemRow(item, Modifier.animateItem(), onToggle = { tick(); act { repo.check(item) } }, onRemove = { act { repo.remove(item) } })
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done") {
                        Row(Modifier.animateItem().padding(top = 18.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✅  In the cart", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { act { repo.clearChecked() } }) { Text("Clear", color = Ember, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    items(done, key = { it.id }) { item ->
                        ItemRow(item, Modifier.animateItem(), onToggle = { tick(); act { repo.check(item) } }, onRemove = { act { repo.remove(item) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPill(value: String, onChange: (String) -> Unit, onAdd: () -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(50), ambientColor = Ink.copy(alpha = 0.2f), spotColor = Ink.copy(alpha = 0.2f))) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("Add an item, e.g. “Paneer 200 g”", color = Stone.copy(alpha = 0.8f))
                BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Ink), cursorBrush = SolidColor(Ember),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onAdd() }), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (value.isBlank()) StoneLight else EmberBright).pressable(onAdd, 0.85f), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Add", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ItemRow(item: GroceryItem, modifier: Modifier, onToggle: () -> Unit, onRemove: () -> Unit) {
    val checked = item.checked
    val box by animateColorAsState(if (checked) EmberBright else Color.Transparent, label = "check")
    Surface(color = if (checked) Paper.copy(alpha = 0.55f) else Paper, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.pressable(onToggle, 0.98f).semantics { role = Role.Checkbox; stateDescription = if (checked) "in the cart" else "to buy" }
                .padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(box).border(2.dp, if (checked) EmberBright else Color(0xFFD6D3D1), CircleShape), contentAlignment = Alignment.Center) {
                if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, textDecoration = if (checked) TextDecoration.LineThrough else null,
                        color = if (checked) Stone else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.amount.isNotBlank()) Text("  ${item.amount}", color = if (checked) Stone.copy(alpha = 0.6f) else Ember, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                if (item.sources.isNotEmpty() && !checked) Text(item.sources.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = Stone.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(36.dp).clip(CircleShape).pressable(onRemove, 0.85f), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "Remove ${item.name}", tint = Stone.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
