package io.github.devasenan134.cauldron.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.devasenan134.cauldron.data.Feedback
import kotlinx.coroutines.launch

private const val ISSUES_URL = "https://github.com/devasenan134/cauldron/issues/new/choose"

/** Settings → Feedback: report a bug or ask for a feature (the app version and device ride along). */
@Composable
fun FeedbackForm(owner: Boolean) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("bug") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    LaunchedEffect(owner) { if (!owner) mine = runCatching { app.api.feedback() }.getOrDefault(emptyList()) }

    fun send() = scope.launch {
        busy = true; message = null
        val meta = mapOf(
            "platform" to "android",
            "version" to "app ${app.updates.currentVersion}",
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        message = try {
            app.api.sendFeedback(type, title.trim(), body.trim(), meta)
            title = ""; body = ""
            if (!owner) mine = runCatching { app.api.feedback() }.getOrDefault(mine)
            "Thanks! It's been sent."
        } catch (e: Exception) { e.friendly() }
        busy = false
    }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.surfaceAlt).padding(4.dp)) {
        listOf("bug" to "Report a bug", "feature" to "Request a feature").forEach { (value, label) ->
            val selected = type == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selected) C.surface else Color.Transparent)
                    .pressable({ type = value; message = null }, 0.95f).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) C.ink else C.muted) }
        }
    }
    OutlinedTextField(title, { title = it.take(200); message = null }, singleLine = true, enabled = !busy,
        placeholder = { Text(if (type == "bug") "What went wrong, in a few words" else "What would you like Cauldron to do?") },
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
    OutlinedTextField(body, { body = it.take(5000); message = null }, minLines = 3, enabled = !busy,
        placeholder = { Text(if (type == "bug") "What did you do, what happened, and what did you expect?" else "Tell me more: how would you use it?") },
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { send() }, enabled = title.isNotBlank() && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo)) { Text(if (busy) "Sending…" else "Send", fontWeight = FontWeight.Bold) }
        TextButton(onClick = { openUrl(context, ISSUES_URL) }) { Text("Or open an issue on GitHub", color = C.goText) }
    }
    message?.let { Text(it, color = if (it.startsWith("Thanks")) C.goText else C.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
    Text("The app's version and your phone's model and Android version are attached, to help find the problem.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    if (!owner && mine.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = C.line)
        Text("You've sent", fontWeight = FontWeight.SemiBold)
        mine.take(5).forEach { f ->
            Row(Modifier.padding(top = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${if (f.type == "bug") "🐞" else "💡"} ${f.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(if (f.status == "done") "Done" else "Open", style = MaterialTheme.typography.bodySmall,
                    color = if (f.status == "done") C.goText else C.muted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The owner's view: everyone's reports, open ones first, to mark done. */
@Composable
fun FeedbackInbox() {
    val app = app()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Feedback>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        items = try { app.api.feedback() } catch (e: Exception) { error = e.friendly(); items }
    }
    LaunchedEffect(Unit) { load() }

    val list = items
    when {
        list == null -> Text(error ?: "Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.isEmpty() -> Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Column {
            Text("${list.count { it.status == "open" }} open · ${list.size} in all", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(color = C.line)
                Column(Modifier.padding(vertical = 10.dp).alpha(if (f.status == "done") 0.6f else 1f)) {
                    Text("${if (f.type == "bug") "🐞" else "💡"} ${f.title}", fontWeight = FontWeight.SemiBold)
                    if (f.body.isNotBlank()) Text(f.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    Text(listOfNotNull(f.userName?.ifBlank { null }, f.userEmail, f.createdAt.take(10), f.meta["version"], f.meta["device"],
                        f.meta["android"]?.let { "Android $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching { app.api.setFeedbackStatus(f.id, if (f.status == "done") "open" else "done") }
                                .onFailure { error = it.friendly() }
                            load()
                        }
                    }, modifier = Modifier.padding(top = 6.dp)) { Text(if (f.status == "done") "Reopen" else "Mark done", color = C.ink) }
                }
            }
            error?.let { Text(it, color = C.danger, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
