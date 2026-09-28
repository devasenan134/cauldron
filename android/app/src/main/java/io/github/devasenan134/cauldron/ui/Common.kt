package io.github.devasenan134.cauldron.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.CauldronApp
import io.github.devasenan134.cauldron.data.ApiException
import io.github.devasenan134.cauldron.data.SignedOutException
import java.io.IOException
import kotlin.math.roundToInt

@Composable
fun app(): CauldronApp = LocalContext.current.applicationContext as CauldronApp

/** What a screen shows while its data loads, fails, or is there. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

fun Throwable.friendly(): String = when (this) {
    is SignedOutException -> "Signed out"
    is ApiException -> message ?: "Server error"
    is IOException -> "Can't reach Cauldron. Check your connection."
    else -> message ?: toString()
}

/**
 * Shows [value] (cached) at once and refreshes it whenever [keys] change. Only when nothing is
 * cached yet does the screen show loading or the error.
 */
@Composable
fun <T> cached(value: T?, vararg keys: Any?, refresh: suspend () -> Unit): Pair<Load<T>, () -> Unit> {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(*keys, attempt) {
        error = null
        try { refresh() } catch (e: Exception) { error = e.friendly() }
    }
    val load = when {
        value != null -> Load.Ready(value)
        error != null -> Load.Failed(error!!)
        else -> Load.Loading
    }
    return load to { attempt++ }
}

@Composable
fun <T> Loaded(
    load: Load<T>,
    onRetry: () -> Unit,
    loading: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.goText) }
    },
    content: @Composable (T) -> Unit,
) = when (load) {
    Load.Loading -> loading()
    is Load.Failed -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🍳", fontSize = 40.sp)
            Text(load.message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = onRetry) { Text("Try again", color = C.goText, fontWeight = FontWeight.SemiBold) }
        }
    }
    is Load.Ready -> content(load.value)
}

/** Sanity CDN images are huge; ask for a resized one. */
fun thumb(url: String?, w: Int, h: Int = w): String? =
    if (url != null && "cdn.sanity.io" in url) "$url?w=$w&h=$h&fit=crop&auto=format" else url

fun kcal(n: Double?): String = if (n == null) "—" else "${n.roundToInt()} kcal"

/** 1.0 -> "1", 1.5 -> "1.5" */
fun num(n: Double): String = if (n % 1.0 == 0.0) n.toInt().toString() else "%.1f".format(n).trimEnd('0').trimEnd('.')

fun plural(n: Double, one: String, many: String = one + "s") = "${num(n)} ${if (n == 1.0) one else many}"

@Composable
fun Pill(text: String, color: Color = C.ink, background: Color = MaterialTheme.colorScheme.surfaceVariant, modifier: Modifier = Modifier) =
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    )

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) =
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }

/** Big friendly empty state. */
@Composable
fun Empty(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) =
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        action()
    }

fun openUrl(context: Context, url: String) = CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
