package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pick a day from the next two weeks, or the queue (null). [wrap] lays them out in rows instead of one scrolling line. */
@Composable
fun DayChips(selected: String?, onPick: (String?) -> Unit, from: String = today(), days: Int = 14, allowQueue: Boolean = true, wrap: Boolean = false) {
    val options = buildList<String?> {
        if (allowQueue) add(null)
        (0 until days).forEach { add(addDays(from, it.toLong())) }
        // Keep an already-chosen day outside the window pickable.
        if (selected != null && selected !in this) add(0, selected)
    }
    val chip: @Composable (String?) -> Unit = { day ->
        FilterChip(
            selected = day == selected, onClick = { onPick(day) },
            label = { Text(dayChipLabel(day)) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Cream),
        )
    }
    if (wrap) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp)) { options.forEach { chip(it) } }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(options) { chip(it) }
        }
    }
}

fun dayChipLabel(day: String?): String = when (day) {
    null -> "Queue"
    today() -> "Today"
    addDays(today(), 1) -> "Tomorrow"
    else -> "${weekdayShort(day)} ${monthDay(day)}"
}

/** "−  3  +" */
@Composable
fun Stepper(value: Double, onChange: (Double) -> Unit, step: Double = 1.0, min: Double = step, label: String? = null, tint: Color = Ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        label?.let { Text(it, color = tint, modifier = Modifier.padding(end = 4.dp)) }
        SmallRound(Icons.Default.Remove, "Decrease ${label ?: "amount"}", tint, enabled = value - step >= min - 1e-9) { onChange(value - step) }
        Text(num(value), fontWeight = FontWeight.SemiBold, color = tint, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 32.dp))
        SmallRound(Icons.Default.Add, "Increase ${label ?: "amount"}", tint) { onChange(value + step) }
    }
}

@Composable
private fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp).border(1.dp, tint.copy(alpha = if (enabled) 0.4f else 0.15f), CircleShape).padding(3.dp))
    }
}
