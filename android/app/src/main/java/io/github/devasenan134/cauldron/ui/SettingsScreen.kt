package io.github.devasenan134.cauldron.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.devasenan134.cauldron.BuildConfig
import io.github.devasenan134.cauldron.data.AppRelease
import io.github.devasenan134.cauldron.data.Session
import io.github.devasenan134.cauldron.data.clearGoogleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun SettingsScreen(back: () -> Unit, openLibraries: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val me = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me

    val goal = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me?.kcalGoal ?: 2200
    var editingGoal by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    // Download my data: Android asks where to save the file, then the export is written there.
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) app.scope.launch {
            exportMessage = "Downloading…"
            exportMessage = try {
                val data = app.api.exportData()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } ?: error("couldn't write the file")
                }
                "Saved. It has all your recipes, plans, lists and foods."
            } catch (e: Exception) { e.friendly() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp)) {
            SectionLabel("Account")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(52.dp)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(me?.name?.ifBlank { null } ?: "Signed in", fontWeight = FontWeight.SemiBold)
                        Text(me?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (me?.isOwner == true) Text("Owner", style = MaterialTheme.typography.bodySmall, color = C.goText)
                    }
                }
                OutlinedButton(
                    onClick = {
                        app.scope.launch {
                            runCatching { app.api.signOut() }
                            clearGoogleState(context)
                            app.signOutLocally()
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = C.ink, modifier = Modifier.size(18.dp))
                    Text("  Sign out", color = C.ink)
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { saveExport.launch("cauldron-${LocalDate.now()}.json") }) { Text("Download my data", color = C.ink) }
                    if (me?.isOwner != true) TextButton(onClick = { deleting = true }) { Text("Delete my account", color = C.danger) }
                }
                Text(exportMessage ?: "A JSON file with all your recipes, plans, lists and foods.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }

            SectionLabel("Appearance")
            Card {
                Text("Theme", fontWeight = FontWeight.SemiBold)
                Text("Saved to your account, so the website follows too", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val theme = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me?.theme ?: "system"
                Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(C.surfaceAlt).padding(4.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        val selected = theme == value
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (selected) C.surface else Color.Transparent)
                                .pressable({ app.scope.launch { app.setTheme(value) } }, 0.95f).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) C.ink else C.muted) }
                    }
                }
            }

            SectionLabel("Goals")
            Card {
                Row(Modifier.fillMaxWidth().pressable({ editingGoal = true }, 0.98f), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily calorie goal", fontWeight = FontWeight.SemiBold)
                        Text("Shown on the Home screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%,d kcal".format(goal), style = MaterialTheme.typography.titleMedium, color = C.goText)
                }
            }

            SectionLabel("App updates")
            UpdatesCard()

            if (me?.isOwner == true) {
                SectionLabel("Recipe libraries")
                Card {
                    Row(Modifier.fillMaxWidth().pressable(openLibraries, 0.98f), verticalAlignment = Alignment.CenterVertically) {
                        Text("📚", fontSize = 28.sp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Manage the recipe libraries", fontWeight = FontWeight.SemiBold)
                            Text("The recipes everyone gets, and the ones for your guests: edit, hide or add your own",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", fontSize = 22.sp, color = C.muted)
                    }
                }
            }

            SectionLabel("Feedback")
            Card { FeedbackForm(owner = me?.isOwner == true) }
            if (me?.isOwner == true) {
                SectionLabel("Everyone's feedback")
                Card { FeedbackInbox() }
            }

            SectionLabel("About")
            Card {
                Text("Cauldron ${app.updates.currentVersion}", fontWeight = FontWeight.SemiBold)
                Text("Recipes, meal plans, batch cooking and groceries.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("© 2026 Devasenan Murugan. Open source under the Apache License 2.0, and free to host yourself.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                val context = LocalContext.current
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openUrl(context, COFFEE_URL) }, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo)) {
                        Text("☕ Buy me a coffee", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { openUrl(context, SOURCE_URL) }) { Text("Source code", fontWeight = FontWeight.Bold, color = C.ink) }
                }
                LegalLinks(Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (editingGoal) GoalDialog(goal, onDismiss = { editingGoal = false }) { kcal -> editingGoal = false; app.scope.launch { runCatching { app.setKcalGoal(kcal) } } }
    if (deleting) DeleteAccountDialog(onDismiss = { deleting = false })
}

/** Deleting your account: you type DELETE to confirm, then you're signed out. */
@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text("This deletes your recipes and their photos, your plans, meal log, grocery lists, foods and folders, in the app and on the website. It can't be undone. Download your data first if you want a copy.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Type DELETE to confirm", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, enabled = !busy, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                error?.let { Text(it, color = C.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            val ready = text.trim() == "DELETE" && !busy
            TextButton(enabled = ready, onClick = {
                busy = true; error = null
                app.scope.launch {
                    try {
                        app.api.deleteAccount()
                        clearGoogleState(context)
                        app.signOutLocally()
                    } catch (e: Exception) {
                        error = e.friendly(); busy = false
                    }
                }
            }) { Text(if (busy) "Deleting…" else "Delete everything", color = if (ready) C.danger else C.faint, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** "Privacy policy · Terms of service", opened in a Custom Tab (the website's pages). */
@Composable
fun LegalLinks(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val site = BuildConfig.API_URL.trimEnd('/')
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { openUrl(context, "$site/privacy") }) { Text("Privacy policy", color = C.muted, style = MaterialTheme.typography.bodySmall) }
        Text("·", color = C.muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { openUrl(context, "$site/terms") }) { Text("Terms of service", color = C.muted, style = MaterialTheme.typography.bodySmall) }
    }
}

private const val COFFEE_URL = "https://buymeacoffee.com/devaa"
private const val SOURCE_URL = "https://github.com/devasenan134/cauldron"

/** What the updates card is doing. */
private sealed interface UpdateStep {
    data object Idle : UpdateStep
    data object Checking : UpdateStep
    data object UpToDate : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    data class Ready(val apk: File) : UpdateStep
    data class Failed(val message: String) : UpdateStep
}

@Composable
private fun UpdatesCard() {
    val app = app()
    val updates = app.updates
    val scope = rememberCoroutineScope()
    val available by updates.available.collectAsState()
    var step by remember { mutableStateOf<UpdateStep>(UpdateStep.Idle) }
    // Android's "install unknown apps" switch is flipped in system settings; re-read it on return.
    var canInstall by remember { mutableStateOf(updates.canInstall()) }
    LifecycleResumeEffect(Unit) { canInstall = updates.canInstall(); onPauseOrDispose { } }

    fun check() = scope.launch {
        step = UpdateStep.Checking
        step = try { if (updates.check() == null) UpdateStep.UpToDate else UpdateStep.Idle } catch (e: Exception) { UpdateStep.Failed(e.friendly()) }
    }
    fun download(release: AppRelease) = scope.launch {
        step = UpdateStep.Downloading(0f)
        step = try {
            UpdateStep.Ready(updates.download(release) { step = UpdateStep.Downloading(it) })
        } catch (e: Exception) { UpdateStep.Failed(e.friendly()) }
    }

    var history by remember { mutableStateOf<List<AppRelease>>(emptyList()) }
    LaunchedEffect(Unit) { history = runCatching { app.api.appReleases() }.getOrDefault(emptyList()) }
    val current = history.firstOrNull { it.version == updates.currentVersion }

    Card {
        Text("Installed: version ${updates.currentVersion}", style = MaterialTheme.typography.bodyMedium)
        current?.notes?.takeIf { it.isNotBlank() }?.let {
            Text("What's new in ${current.version}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            PatchNotes(it, Modifier.padding(top = 4.dp))
        }
        val release = available
        if (release == null) {
            when (val s = step) {
                UpdateStep.Checking -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = C.goText, strokeWidth = 2.dp)
                    Text("  Checking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                UpdateStep.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = C.goText, modifier = Modifier.size(18.dp))
                    Text("  You have the latest version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UpdateStep.Failed -> Text(s.message, color = C.danger, modifier = Modifier.padding(top = 12.dp))
                else -> {}
            }
            if (step != UpdateStep.Checking) OutlinedButton(onClick = { check() }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Check for updates", color = C.ink)
            }
            return@Card
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Default.SystemUpdate, null, tint = C.goText)
            Text("  Version ${release.version} is available", fontWeight = FontWeight.SemiBold, color = C.goText)
        }
        if (release.notes.isNotBlank()) PatchNotes(release.notes, Modifier.padding(top = 8.dp))
        if (release.size > 0) Text("%.1f MB".format(release.size / 1_048_576.0), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        when (val s = step) {
            is UpdateStep.Downloading -> Column(Modifier.padding(top = 12.dp)) {
                LinearProgressIndicator(progress = { s.progress }, color = C.goText, trackColor = C.goSoft, modifier = Modifier.fillMaxWidth())
                Text("Downloading… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            is UpdateStep.Ready -> Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!canInstall) {
                    Text("Android needs your permission for Cauldron to install updates. Turn on “Allow from this source”, then come back.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { updates.openInstallPermission() }) { Text("Open settings", color = C.ink) }
                }
                Button(onClick = { updates.install(s.apk) }, enabled = canInstall, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo)) { Text("Install ${release.version}", fontWeight = FontWeight.Bold) }
            }
            is UpdateStep.Failed -> {
                Text(s.message, color = C.danger, modifier = Modifier.padding(top = 12.dp))
                Button(onClick = { download(release) }, colors = ButtonDefaults.buttonColors(containerColor = C.ink), modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
            }
            else -> Button(onClick = { download(release) }, colors = ButtonDefaults.buttonColors(containerColor = C.go, contentColor = C.onGo), modifier = Modifier.padding(top = 12.dp)) {
                Text("Download and install")
            }
        }
    }
}

/** Release notes as bullets; long ones fold to a few lines with "See more". */
@Composable
fun PatchNotes(notes: String, modifier: Modifier = Modifier, foldAt: Int = 4) {
    val lines = notes.lines().filter { it.isNotBlank() }.map { it.replaceFirst(Regex("^\\s*[-*] "), "•  ") }
    var open by remember(notes) { mutableStateOf(false) }
    val long = lines.size > foldAt || notes.length > 280
    Column(modifier.animateContentSize()) {
        Text(
            lines.joinToString("\n"), style = MaterialTheme.typography.bodyMedium, color = C.ink, lineHeight = 21.sp,
            maxLines = if (open || !long) Int.MAX_VALUE else foldAt, overflow = TextOverflow.Ellipsis,
        )
        if (long) Text(if (open) "See less" else "See more", color = C.goText, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).pressable({ open = !open }, 0.95f).padding(vertical = 2.dp))
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = C.surface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, C.line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = content)
    }
}
