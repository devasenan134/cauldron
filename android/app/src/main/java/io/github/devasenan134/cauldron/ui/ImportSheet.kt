package io.github.devasenan134.cauldron.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.devasenan134.cauldron.data.ImportJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The server says what it's doing in job.message; these are for when it hasn't yet.
private val STEPS = listOf("queued" to "Waiting its turn", "fetching" to "Opening the link",
    "reading" to "Writing the recipe", "saving" to "Working out calories")
private val FILE_TYPES = arrayOf("application/pdf", "image/*", "application/json", "application/yaml", "application/x-yaml",
    "text/*", "application/octet-stream")

/**
 * Import a recipe from a recipe page, a video (YouTube, Shorts, Reels) or a file (a PDF, a photo of
 * a recipe, a YAML/JSON recipe). The server does the work; this shows its progress and opens the
 * recipe when it's ready. [initial] is a shared link and [file] a shared file, if any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(initial: String?, file: Uri? = null, onDismiss: () -> Unit, openRecipe: (Int) -> Unit) {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(initial ?: "") }
    var job by remember { mutableStateOf<ImportJob?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start(link: String?, uri: Uri? = null) {
        error = null
        scope.launch {
            try {
                var j = if (uri != null) {
                    job = ImportJob(0, "", "queued", "Sending the file")
                    val (bytes, type, name) = withContext(Dispatchers.IO) { readFile(context, uri) }
                    app.api.importFile(bytes, type, name)
                } else app.api.startImport(link!!)
                job = j
                var consecutiveFails = 0
                while (j.status !in setOf("done", "failed")) {
                    delay(1500)
                    try {
                        j = app.api.importJob(j.id)
                        job = j
                        consecutiveFails = 0
                    } catch (e: Exception) {
                        consecutiveFails++
                        if (consecutiveFails >= 4) throw e
                    }
                }
                if (j.status == "done" && j.recipeId != null) {
                    app.store.recipesChanged()
                    delay(500)
                    openRecipe(j.recipeId)
                    onDismiss()
                } else error = j.message
            } catch (e: Exception) { error = e.friendly(); job = null }
        }
    }
    // A shared link or file starts right away.
    LaunchedEffect(Unit) { if (file != null) start(null, file) else if (!initial.isNullOrBlank()) start(initial) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) start(null, uri) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Import a recipe", style = MaterialTheme.typography.headlineSmall)
            Text("From a recipe website, a YouTube video, a Short or an Instagram post or Reel, or a file: a PDF, a photo of a recipe, or a YAML/JSON recipe. Cauldron reads it, keeps the amounts and macros it gives, and writes the recipe.",
                color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            val isDone = job?.status == "done"
            val running = job != null && job?.status !in setOf("done", "failed")
            if (!running && !isDone) {
                OutlinedTextField(url, { url = it }, placeholder = { Text("Paste a link") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                Row(Modifier.padding(top = 12.dp)) {
                    OutlinedButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) { Text("Paste", color = C.ink) }
                    Button(onClick = { start(url) }, enabled = url.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo),
                        modifier = Modifier.padding(start = 10.dp).weight(1f)) { Text("Import", fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(onClick = { pickFile.launch(FILE_TYPES) }, modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                    Text("📄  Choose a PDF, photo or recipe file", color = C.ink)
                }
            } else {
                val idx = STEPS.indexOfFirst { it.first == job?.status }.coerceAtLeast(0)
                val progress = when (job?.status) {
                    "done" -> 1f
                    "saving" -> 0.85f
                    "reading" -> 0.65f
                    "fetching" -> 0.35f
                    "queued" -> 0.15f
                    else -> 0.2f
                }
                val label = if (isDone) (job?.message?.ifBlank { null } ?: "Ready! Opening recipe") + "…"
                    else (job?.message?.ifBlank { null } ?: STEPS[idx].second) + "…"
                Column(Modifier.padding(top = 20.dp).fillMaxWidth().background(C.surface, RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.go, strokeWidth = 2.5.dp)
                        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                    LinearProgressIndicator(progress = { progress }, color = C.go, trackColor = C.line,
                        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().height(6.dp))
                    Text(if (isDone) "Opening recipe now…" else "This takes about a minute. You can close this; it keeps going and shows up in My recipes.",
                        color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
            }
            error?.let { Text(it, color = C.danger, modifier = Modifier.padding(top = 14.dp)) }
        }
    }
}

/** The picked or shared file's bytes, type and name. */
private fun readFile(context: Context, uri: Uri): Triple<ByteArray, String, String> {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val type = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Couldn't open that file")
    if (bytes.size > 40 * 1024 * 1024) throw IllegalStateException("That file is too big (40 MB at most)")
    return Triple(bytes, type, name)
}
