package io.github.devasenan134.cauldron.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.devasenan134.cauldron.data.PlanEntry
import io.github.devasenan134.cauldron.data.Session
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen(openRecipe: (Int) -> Unit, openTab: (String) -> Unit) {
    val app = app()
    val store = app.store
    val week = weekStart(today())
    val plan = store.plans.collectAsState().value[week]
    val batches by store.batches.collectAsState()
    val grocery by app.grocery.items.collectAsState()
    val goal by app.prefs.kcalGoal.collectAsState()
    val me = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me
    var editingGoal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { store.loadPlan(week) }
        runCatching { store.loadBatches() }
    }
    // Tomorrow may be next week.
    val tomorrow = addDays(today(), 1)
    val nextWeek = weekStart(tomorrow)
    val nextPlan = store.plans.collectAsState().value[nextWeek]
    LaunchedEffect(nextWeek) { if (nextWeek != week) runCatching { store.loadPlan(nextWeek) } }

    val todays = plan?.days?.get(today()).orEmpty()
    val tomorrows = (if (nextWeek == week) plan else nextPlan)?.days?.get(tomorrow).orEmpty()
    val eaten = todays.sumOf { it.kcal ?: 0.0 }
    val oldBatches = batches.orEmpty().filter { b -> b.day != null && b.day <= today() }
    val firstName = me?.name?.substringBefore(' ')?.ifBlank { null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(horizontal = 0.dp)) {
        item { ScreenHeader(greeting() + (firstName?.let { ", $it" } ?: ""), subtitle = "${weekdayLong(today())}, ${monthDay(today())}") }

        item {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                when {
                    plan == null -> Shimmer(Modifier.fillMaxWidth().height(260.dp), RoundedCornerShape(28.dp))
                    todays.isNotEmpty() -> Hero(todays.first(), "TODAY", more = todays.size - 1) { todays.first().recipeId?.let(openRecipe) }
                    tomorrows.isNotEmpty() -> Hero(tomorrows.first(), "TOMORROW", more = tomorrows.size - 1) { tomorrows.first().recipeId?.let(openRecipe) }
                    else -> EmptyHero { openTab("recipes") }
                }
            }
        }

        item { CaloriesCard(eaten, goal, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { editingGoal = true } }

        if (todays.size > 1) item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Today's meals")
                Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(vertical = 6.dp)) { todays.forEach { MealRow(it) { it.recipeId?.let(openRecipe) } } }
                }
            }
        }

        if (oldBatches.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("In the fridge") { TextButton(onClick = { openTab("fridge") }) { Text("See all", color = Ember) } }
                oldBatches.take(3).forEach { b -> FridgeAlert(b) { openTab("fridge") } }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val toBuy = grocery.count { !it.checked }
                Tile("🛒", if (toBuy == 0) "All bought" else "$toBuy to buy", "Grocery list", Modifier.weight(1f)) { openTab("grocery") }
                val weekKcal = plan?.days?.values?.flatten()?.sumOf { it.kcal ?: 0.0 } ?: 0.0
                val meals = plan?.days?.values?.sumOf { it.size } ?: 0
                Tile("📅", "$meals meals", "${"%,d".format(weekKcal.roundToInt())} kcal this week", Modifier.weight(1f)) { openTab("plan") }
            }
        }

        if (todays.isNotEmpty() && tomorrows.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel("Tomorrow")
                Surface(color = Paper, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(vertical = 6.dp)) { tomorrows.forEach { MealRow(it) { it.recipeId?.let(openRecipe) } } }
                }
            }
        }
    }

    if (editingGoal) GoalDialog(goal, onDismiss = { editingGoal = false }) { app.prefs.setKcalGoal(it); editingGoal = false }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun Hero(e: PlanEntry, label: String, more: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(28.dp)).background(Ink).pressable(onClick, 0.98f)) {
        AsyncImage(thumb(e.imageUrl, 900, 700), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(label, color = Color.White, background = EmberBright)
            if (e.isLeftover) Pill("LEFTOVERS", color = Ink, background = Color.White)
            if (e.isBatch) Pill("BATCH · ${num(e.cookPortions ?: 0.0)}", color = Ink, background = Color.White)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(e.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                e.kcal?.let { Text(kcal(it), color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold) }
                if (e.servings != 1.0) Text("  ·  ${plural(e.servings, "serving")}", color = Color.White.copy(alpha = 0.8f))
                if (more > 0) Text("  ·  +$more more", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun EmptyHero(onFind: () -> Unit) {
    Surface(color = Ink, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("🍲", fontSize = 40.sp)
            Text("Nothing planned today", color = Cream, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text("Pick something tasty and put it on the plan.", color = Cream.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
            Button(onClick = onFind, colors = ButtonDefaults.buttonColors(containerColor = EmberBright), modifier = Modifier.padding(top = 16.dp)) {
                Text("Find a recipe", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CaloriesCard(eaten: Double, goal: Int, modifier: Modifier, onEditGoal: () -> Unit) {
    val shown by animateIntAsState(eaten.roundToInt(), tween(700), label = "kcal")
    val fraction by animateFloatAsState((eaten / goal).toFloat().coerceIn(0f, 1f), tween(900), label = "bar")
    val over = eaten > goal
    Surface(color = Paper, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().pressable(onEditGoal, 0.98f)) {
        Column(Modifier.padding(20.dp)) {
            Text("Today's calories", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(shown), style = MaterialTheme.typography.displaySmall, color = if (over) Danger else Ink)
                Text("  / ${"%,d".format(goal)} kcal", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(EmberSoft)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFB923C), if (over) Danger else EmberBright))))
            }
            val left = goal - eaten
            Text(
                if (left >= 0) "${"%,d".format(left.roundToInt())} kcal left in your plan" else "${"%,d".format((-left).roundToInt())} kcal over your goal",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MealRow(e: PlanEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick, 0.98f).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(thumb(e.imageUrl, 120), null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when { e.isLeftover -> "Leftovers"; e.isBatch -> "Batch · cook ${num(e.cookPortions ?: 0.0)}"; else -> plural(e.servings, "serving") },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        e.kcal?.let { Text("${it.roundToInt()}", fontFamily = Display, fontWeight = FontWeight.Bold, color = Ember) }
    }
}

@Composable
private fun FridgeAlert(b: PlanEntry, onClick: () -> Unit) {
    val age = daysAgo(b.day!!)
    val soon = age >= 3
    Surface(color = if (soon) AmberSoft else Paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).pressable(onClick, 0.98f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(thumb(b.imageUrl, 120), null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(b.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (soon) "Cooked $age days ago · eat soon" else if (age == 0L) "Cooked today" else "Cooked ${plural(age.toDouble(), "day")} ago",
                    style = MaterialTheme.typography.bodySmall, color = if (soon) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (soon) FontWeight.SemiBold else FontWeight.Normal)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(num(b.portionsLeft ?: 0.0), style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text("left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Tile(emoji: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(22.dp), modifier = modifier.pressable(onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GoalDialog(goal: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(goal.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily calorie goal") },
        text = {
            OutlinedTextField(text, { text = it.filter(Char::isDigit).take(5) }, singleLine = true, suffix = { Text("kcal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        },
        confirmButton = { TextButton(onClick = { text.toIntOrNull()?.takeIf { it in 500..10000 }?.let(onSave) }) { Text("Save", color = Ember) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
