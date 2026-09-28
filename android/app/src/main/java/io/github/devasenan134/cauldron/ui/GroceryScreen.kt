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
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.devasenan134.cauldron.data.GroceryTemplate
import io.github.devasenan134.cauldron.data.TemplateItem
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
fun GroceryScreen(openPlan: () -> Unit, back: () -> Unit) {
    val app = app()
    val repo = app.grocery
    val scope = rememberCoroutineScope()
    val items by repo.items.collectAsState()
    val pending by repo.pending.collectAsState()
    val online by repo.online.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var templatesOpen by remember { mutableStateOf(false) }
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
        ScreenHeader("Grocery", subtitle = if (items.isEmpty()) "Your list" else "${done.size} of ${items.size} in the cart", back = back) {
            TextButton(onClick = { templatesOpen = true }) {
                Icon(Icons.AutoMirrored.Outlined.ListAlt, null, tint = C.ink, modifier = Modifier.size(20.dp))
                Text("  Templates", color = C.ink, fontWeight = FontWeight.SemiBold)
            }
        }
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; sync(); refreshing = false } }) {
            LazyColumn(contentPadding = screenPadding(top = 4.dp), modifier = Modifier.fillMaxSize()) {
                if (items.isNotEmpty()) item(key = "progress") {
                    Box(Modifier.padding(bottom = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(C.line)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(50))
                            .background(C.go))
                    }
                }
                if (!online) item(key = "offline") {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).background(C.purpleBg, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudOff, null, tint = C.purpleFg)
                        Text(
                            if (pending > 0) "Offline · ${plural(pending.toDouble(), "change")} will sync when you're back online."
                            else "Offline · showing the list saved on this phone.",
                            color = C.purpleFg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                item(key = "add") { AddPill(name, { name = it }, ::addItem) }
                if (items.isEmpty()) item(key = "empty") {
                    Empty("🛒", "Your list is empty", "Plan some meals, then tap the cart button on the Plan tab to build the list.") {
                        TextButton(onClick = openPlan) { Text("Open the plan", color = C.goText, fontWeight = FontWeight.Bold) }
                    }
                }
                aisles.forEach { (aisle, group) ->
                    item(key = "aisle:$aisle") { ListHeader("${aisleEmoji(aisle)}  $aisle", group.size, Modifier.animateItem()) }
                    items(group, key = { it.id }) { item ->
                        ItemRow(item, Modifier.animateItem(), onToggle = { tick(); act { repo.check(item) } }, onRemove = { act { repo.remove(item) } })
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "done") {
                        ListHeader("In the cart", done.size, Modifier.animateItem()) {
                            TextButton(onClick = { act { repo.clearChecked() } }) { Text("Clear", color = C.ink, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    items(done, key = { it.id }) { item ->
                        ItemRow(item, Modifier.animateItem(), onToggle = { tick(); act { repo.check(item) } }, onRemove = { act { repo.remove(item) } })
                    }
                }
            }
        }
    }
    if (templatesOpen) TemplatesSheet(onDismiss = { templatesOpen = false }) { fresh -> scope.launch { repo.replace(fresh) } }
}

@Composable
private fun AddPill(value: String, onChange: (String) -> Unit, onAdd: () -> Unit) {
    Surface(color = C.surface, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(50), ambientColor = C.ink.copy(alpha = 0.2f), spotColor = C.ink.copy(alpha = 0.2f))) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("Add an item, e.g. “Paneer 200 g”", color = C.muted.copy(alpha = 0.8f))
                BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = C.ink), cursorBrush = SolidColor(C.goText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onAdd() }), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (value.isBlank()) C.line else C.ink).pressable(onAdd, 0.85f), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, "Add", tint = C.bg)
            }
        }
    }
}

/** "Dairy & Eggs · 3" with a hairline running to the edge. */
@Composable
private fun ListHeader(title: String, count: Int, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$title · $count", color = C.muted, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f).padding(start = 12.dp).height(1.dp).background(C.line))
        trailing()
    }
}

@Composable
private fun ItemRow(item: GroceryItem, modifier: Modifier, onToggle: () -> Unit, onRemove: () -> Unit) {
    val checked = item.checked
    val box by animateColorAsState(if (checked) C.ink else Color.Transparent, label = "check")
    Row(
        modifier.fillMaxWidth().pressable(onToggle, 0.98f).semantics { role = Role.Checkbox; stateDescription = if (checked) "in the cart" else "to buy" }
            .padding(start = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontSize = 17.sp, textDecoration = if (checked) TextDecoration.LineThrough else null,
                    color = if (checked) C.faint else C.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.amount.isNotBlank()) Text("  ${item.amount}", color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.sources.isNotEmpty() && !checked) Text(item.sources.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = C.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).pressable(onRemove, 0.85f), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Remove ${item.name}", tint = C.faint.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
        Box(Modifier.padding(start = 4.dp).size(26.dp).clip(CircleShape).background(box).border(1.5.dp, if (checked) C.ink else C.faint, CircleShape), contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Default.Check, null, tint = C.bg, modifier = Modifier.size(16.dp))
        }
    }
}


/** Saved lists ("Weekly basics"): add one to your list in a tap, or save the current list as one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesSheet(onDismiss: () -> Unit, onApplied: (List<GroceryItem>) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<GroceryTemplate>?>(null) }
    var editing by remember { mutableStateOf<GroceryTemplate?>(null) }
    var creating by remember { mutableStateOf(false) }
    var savingList by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun load() { templates = runCatching { app.api.templates() }.getOrElse { message = it.friendly(); emptyList() } }
    LaunchedEffect(Unit) { load() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Templates", style = MaterialTheme.typography.headlineSmall)
            Text("Lists you buy again and again. Add one to your grocery list in a tap.", color = C.muted, style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = C.goText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)) }
            when (val list = templates) {
                null -> CircularProgressIndicator(color = C.go, modifier = Modifier.padding(24.dp))
                else -> {
                    if (list.isEmpty()) Text("No templates yet.", color = C.muted, modifier = Modifier.padding(vertical = 16.dp))
                    list.forEach { t ->
                        Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface).padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).pressable({ editing = t }, 0.98f)) {
                                Text(t.name, fontWeight = FontWeight.SemiBold)
                                Text(t.items.joinToString(", ") { it.name }.ifEmpty { "Empty" }, color = C.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(onClick = {
                                scope.launch {
                                    try { onApplied(app.api.applyTemplate(t.id)); message = "Added “${t.name}” to your list" } catch (e: Exception) { message = e.friendly() }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = C.ink, contentColor = C.bg), modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null, tint = C.ink); Text(" New template", color = C.ink) }
                OutlinedButton(onClick = { savingList = true }) { Text("Save my list as one", color = C.ink) }
            }
        }
    }
    if (creating || editing != null) TemplateEditor(editing, onDismiss = { creating = false; editing = null }, onSave = { name, items ->
        scope.launch {
            try { app.api.saveTemplate(editing?.id, name, items); creating = false; editing = null; load() } catch (e: Exception) { message = e.friendly() }
        }
    }, onDelete = editing?.let { t -> {
        scope.launch { runCatching { app.api.deleteTemplate(t.id) }; editing = null; load() }
    } })
    if (savingList) NameDialog("Save my list as a template", "e.g. Weekly basics", "", onDismiss = { savingList = false }) { name ->
        savingList = false
        scope.launch { try { app.api.templateFromList(name); message = "Saved “$name”"; load() } catch (e: Exception) { message = e.friendly() } }
    }
}

/** Name plus one item per line ("Milk 1 L"). */
@Composable
private fun TemplateEditor(t: GroceryTemplate?, onDismiss: () -> Unit, onSave: (String, List<TemplateItem>) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(t?.name ?: "") }
    var lines by remember { mutableStateOf(t?.items?.joinToString("\n") { listOf(it.name, it.amount).filter { s -> s.isNotBlank() }.joinToString(" ") } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t == null) "New template" else "Edit template") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lines, { lines = it }, label = { Text("Items, one per line") }, placeholder = { Text("Milk 1 L\nEggs 12\nBananas") },
                    minLines = 5, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete template", color = C.danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val items = lines.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                    val m = Regex("""^(.*?)\s+(\d[\d.,/]*\s*\p{L}{0,6})$""").find(line)
                    if (m != null) TemplateItem(m.groupValues[1], m.groupValues[2]) else TemplateItem(line)
                }
                if (name.isNotBlank()) onSave(name.trim(), items)
            }) { Text("Save", color = C.goText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(title: String, hint: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save", color = C.goText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
