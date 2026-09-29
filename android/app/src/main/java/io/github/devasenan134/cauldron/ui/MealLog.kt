package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.CauldronApp
import io.github.devasenan134.cauldron.data.MEAL_LABEL
import io.github.devasenan134.cauldron.data.PlanEntry
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** The meal it's time for (or next): what a new plan entry for today defaults to. */
fun mealNow(): String = when (java.time.LocalTime.now().hour) { in 0..10 -> "breakfast"; in 11..15 -> "lunch"; else -> "dinner" }

/** Meals you can log: planned for today or earlier, and not making a prep. */
fun PlanEntry.loggable() = !isPrep && day != null && day <= today()

/** Where "Ate out" is being logged: a planned meal, or a meal with nothing planned. */
private data class OutTarget(val day: String, val meal: String, val entry: PlanEntry?)

/**
 * Logging meals: eaten (counts toward the day's calories), eaten out (what was planned goes to
 * the fridge; you can say roughly how many kcal you had), or undo. Shows at once, then syncs.
 */
class MealLog(private val app: CauldronApp, private val onError: (String) -> Unit) {
    private var asking by mutableStateOf<OutTarget?>(null)

    private fun send(block: suspend () -> Unit) = app.scope.launch {
        try { block() } catch (e: Exception) { onError(e.friendly()) }
        app.store.refreshPlans()
    }

    private fun patch(e: PlanEntry, status: String?, outKcal: Double? = null) {
        app.store.editEntry(e.id) {
            it.copy(status = status, outKcal = if (status == "out") outKcal else null,
                eatenKcal = when (status) { "out" -> outKcal; "eaten" -> it.kcal; else -> null })
        }
        send {
            app.api.updateEntry(e.id, buildJsonObject {
                put("status", status?.let { JsonPrimitive(it) } ?: JsonNull)
                if (status == "out") put("out_kcal", outKcal?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        }
    }

    fun eaten(e: PlanEntry) = patch(e, "eaten")
    fun undo(e: PlanEntry) = patch(e, null)
    fun out(e: PlanEntry) { asking = OutTarget(e.day ?: today(), e.meal, e) }
    fun outEmpty(day: String, meal: String) { asking = OutTarget(day, meal, null) }

    private fun confirmOut(t: OutTarget, kcal: Double?) {
        asking = null
        if (t.entry != null) patch(t.entry, "out", kcal)
        else send {
            app.api.addEntry(buildJsonObject {
                put("day", t.day); put("meal", t.meal); put("status", "out")
                put("out_kcal", kcal?.let { JsonPrimitive(it) } ?: JsonNull)
            })
        }
    }

    @Composable
    fun Dialogs() {
        val t = asking ?: return
        var text by remember(t) { mutableStateOf(t.entry?.outKcal?.roundToInt()?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("Ate out · ${MEAL_LABEL[t.meal] ?: t.meal}") },
            text = {
                Column {
                    Text(
                        if (t.entry?.recipeId != null || t.entry?.isLeftover == true) "${t.entry.title} goes to the fridge for another day. Roughly how many kcal did you eat?"
                        else "Roughly how many kcal did you eat? Leave it blank if you don't know.",
                        style = MaterialTheme.typography.bodySmall, color = C.muted,
                    )
                    OutlinedTextField(text, { text = it.filter { c -> c.isDigit() }.take(5) }, singleLine = true, suffix = { Text("kcal") },
                        placeholder = { Text("optional") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { confirmOut(t, text.toDoubleOrNull()?.takeIf { it > 0 }) }) { Text("Log it", color = C.danger, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun rememberMealLog(onError: (String) -> Unit = {}): MealLog {
    val app = app()
    return remember(app) { MealLog(app, onError) }
}

/** ✓ Ate it / Ate out, or what was logged (tap to undo). */
@Composable
fun LogButtons(e: PlanEntry, log: MealLog, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (e.status) {
            "eaten" -> LogPill("✓ Eaten", C.onGo, C.go) { log.undo(e) }
            "out" -> {
                LogPill("Ate out" + (e.outKcal?.let { " · ${it.roundToInt()}" } ?: ""), Color.White, C.danger) { log.out(e) }
                LogPill("Undo", C.muted, Color.Transparent) { log.undo(e) }
            }
            else -> {
                if (!e.isNote) LogPill("✓ Ate it", C.bg, C.ink) { log.eaten(e) }
                LogPill("Ate out", C.danger, Color.Transparent, border = true) { log.out(e) }
            }
        }
    }
}

@Composable
fun LogPill(text: String, color: Color, background: Color, border: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background)
            .then(if (border) Modifier.border(1.dp, C.danger.copy(alpha = 0.5f), RoundedCornerShape(50)) else Modifier)
            .pressable(onClick, 0.92f).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
