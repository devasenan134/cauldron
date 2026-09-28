package io.github.devasenan134.cauldron.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.CauldronApp
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
    is io.github.devasenan134.cauldron.data.ApiException -> message ?: "Server error"
    is IOException -> "Can't reach Cauldron. Check your connection."
    else -> message ?: toString()
}

@Composable
fun <T> Loaded(load: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) = when (load) {
    Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ember) }
    is Load.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(load.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("Try again", color = Ember) }
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

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, background: Color = MaterialTheme.colorScheme.surfaceVariant) =
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
)

fun openUrl(context: Context, url: String) = CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
