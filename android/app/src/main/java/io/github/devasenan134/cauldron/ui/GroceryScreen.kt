package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.devasenan134.cauldron.data.GroceryItem
import kotlinx.coroutines.launch
import java.io.IOException

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
    var amount by remember { mutableStateOf("") }

    suspend fun sync() = try { repo.sync() } catch (_: IOException) { }
    LaunchedEffect(Unit) { sync() }
    fun act(block: suspend () -> Unit) = scope.launch { block() }
    fun addItem() {
        if (name.isBlank()) return
        val n = name; val a = amount
        name = ""; amount = ""
        act { repo.add(n, a) }
    }

    val toBuy = items.filter { !it.checked }
    val done = items.filter { it.checked }
    val aisles = toBuy.groupBy { it.aisle }

    Scaffold(topBar = { TopBar("Grocery list") }, containerColor = Cream) { padding ->
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; sync(); refreshing = false } }, modifier = Modifier.padding(padding)) {
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
                if (!online) item {
                    Surface(color = AmberSoft, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = Amber)
                            Text(
                                if (pending > 0) "Offline: $pending ${if (pending == 1) "change" else "changes"} will sync when you're back online."
                                else "Offline: showing the list saved on this phone.",
                                color = Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        OutlinedTextField(name, { name = it }, placeholder = { Text("Add an item") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addItem() }))
                        OutlinedTextField(amount, { amount = it }, placeholder = { Text("Qty") }, singleLine = true, modifier = Modifier.width(80.dp).padding(start = 6.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addItem() }))
                        FilledIconButton(onClick = ::addItem, enabled = name.isNotBlank(), modifier = Modifier.padding(start = 6.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Ink, contentColor = Cream)) { Icon(Icons.Default.Add, "Add") }
                    }
                    Text("${toBuy.size} to buy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (items.isEmpty()) item {
                    Column(Modifier.padding(top = 24.dp)) {
                        Text("Empty. Plan some meals, then make the grocery list from the Plan tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = openPlan) { Text("Open the plan", color = Ember) }
                    }
                }
                aisles.forEach { (aisle, group) ->
                    item(key = "aisle:$aisle") { SectionLabel(aisle) }
                    item(key = "list:$aisle") { ItemCard(group, { act { repo.check(it) } }, { act { repo.remove(it) } }) }
                }
                if (done.isNotEmpty()) {
                    item(key = "done") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                            SectionLabel("In the cart (${done.size})", Modifier.weight(1f))
                            TextButton(onClick = { act { repo.clearChecked() } }) { Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    item(key = "done-list") { ItemCard(done, { act { repo.check(it) } }, { act { repo.remove(it) } }, faded = true) }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(items: List<GroceryItem>, onToggle: (GroceryItem) -> Unit, onRemove: (GroceryItem) -> Unit, faded: Boolean = false) {
    Surface(color = if (faded) Color.White.copy(alpha = 0.6f) else Color.White, shape = RoundedCornerShape(12.dp)) {
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().clickable { onToggle(item) }.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.checked, { onToggle(item) }, colors = CheckboxDefaults.colors(checkedColor = Ember))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Row {
                            Text(item.name, fontWeight = FontWeight.Medium, textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) Stone else Ink)
                            if (item.amount.isNotBlank()) Text("  ${item.amount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.sources.isNotEmpty()) Text(item.sources.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = Stone.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onRemove(item) }) { Icon(Icons.Default.Close, "Remove", tint = Stone.copy(alpha = 0.5f)) }
                }
            }
        }
    }
}
