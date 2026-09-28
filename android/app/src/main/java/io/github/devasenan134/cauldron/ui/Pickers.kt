package io.github.devasenan134.cauldron.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pick a day from the next two weeks, or the queue (null). [wrap] lays them out in rows instead of one scrolling line. */
@Composable
fun DayChips(selected: String?, onPick: (String?) -> Unit, from: String = today(), days: Int = 14, allowQueue: Boolean = true, wrap: Boolean = false) {
    val options = buildList<String?> {
        if (allowQueue) add(null)
        (0 until days).forEach { add(addDays(from, it.toLong())) }
        // Keep an already-chosen day outside the window pickable.
        if (selected != null && selected !in this) add(0, selected)
    }
    if (wrap) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
            options.forEach { Chip(dayChipLabel(it), it == selected) { onPick(it) } }
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(options) { Chip(dayChipLabel(it), it == selected) { onPick(it) } }
        }
    }
}

fun dayChipLabel(day: String?): String = when (day) {
    null -> "Queue"
    today() -> "Today"
    addDays(today(), 1) -> "Tomorrow"
    else -> "${weekdayShort(day)} ${monthDay(day)}"
}

/** "−  3  +", with the number rolling up or down as it changes. */
@Composable
fun Stepper(
    value: Double, onChange: (Double) -> Unit, step: Double = 1.0, min: Double = step,
    label: String? = null, tint: Color = Ink, big: Boolean = false, showLabel: Boolean = false,
) {
    val tick = rememberTick()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showLabel && label != null) Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        val canLess = value - step >= min - 1e-9
        RoundButton(Icons.Default.Remove, "Decrease ${label ?: "amount"}", tint, big, canLess) { tick(); onChange(value - step) }
        AnimatedContent(
            value,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(180)) { if (up) it else -it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(180)) { if (up) -it else it } + fadeOut(tween(120)))
            },
            label = "stepper",
        ) { v ->
            Text(num(v), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (big) 28.sp else 16.sp, color = tint,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = if (big) 48.dp else 30.dp))
        }
        RoundButton(Icons.Default.Add, "Increase ${label ?: "amount"}", tint, big) { tick(); onChange(value + step) }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, tint: Color, big: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val size = if (big) 40.dp else 30.dp
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (enabled) 0.1f else 0.04f))
            .then(if (enabled) Modifier.pressable(onClick, 0.85f) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(if (big) 22.dp else 18.dp))
    }
}
