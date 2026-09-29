package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.devasenan134.cauldron.data.ImportJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val STEPS = listOf("queued" to "Waiting its turn", "fetching" to "Reading the video's page",
    "reading" to "Watching the video and writing the recipe", "saving" to "Working out calories")

/**
 * Import a recipe from a YouTube video, Short or Instagram Reel. The server does the work; this
 * shows its progress and opens the recipe when it's ready. [initial] is a shared link, if any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(initial: String?, onDismiss: () -> Unit, openRecipe: (Int) -> Unit) {
    val app = app()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(initial ?: "") }
    var job by remember { mutableStateOf<ImportJob?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start(link: String) {
        error = null
        scope.launch {
            try {
                var j = app.api.startImport(link)
                job = j
                while (j.status !in setOf("done", "failed")) {
                    delay(1500)
                    j = app.api.importJob(j.id)
                    job = j
                }
                if (j.status == "done" && j.recipeId != null) {
                    app.store.recipesChanged()
                    openRecipe(j.recipeId)
                    onDismiss()
                } else error = j.message
            } catch (e: Exception) { error = e.friendly(); job = null }
        }
    }
    // A shared link starts right away.
    LaunchedEffect(Unit) { if (!initial.isNullOrBlank()) start(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Import a recipe", style = MaterialTheme.typography.headlineSmall)
            Text("From a YouTube video, a Short or an Instagram Reel. Cauldron watches it, reads the description for amounts and macros, and writes the recipe.",
                color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            val running = job != null && job?.status !in setOf("done", "failed")
            if (!running) {
                OutlinedTextField(url, { url = it }, placeholder = { Text("Paste a link") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                Row(Modifier.padding(top = 12.dp)) {
                    OutlinedButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) { Text("Paste", color = C.ink) }
                    Button(onClick = { start(url) }, enabled = url.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo),
                        modifier = Modifier.padding(start = 10.dp).weight(1f)) { Text("Import", fontWeight = FontWeight.Bold) }
                }
            } else {
                val idx = STEPS.indexOfFirst { it.first == job?.status }.coerceAtLeast(0)
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().background(C.surface, RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.go, strokeWidth = 2.5.dp)
                        Text(STEPS[idx].second + "…", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                    LinearProgressIndicator(progress = { (idx + 1) / (STEPS.size + 1f) }, color = C.go, trackColor = C.line,
                        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(6.dp))
                    Text("This takes about a minute. You can close this; it keeps going and shows up in My recipes.",
                        color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
            }
            error?.let { Text(it, color = C.danger, modifier = Modifier.padding(top = 14.dp)) }
        }
    }
}
