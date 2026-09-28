package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.devasenan134.cauldron.data.AppRelease
import io.github.devasenan134.cauldron.data.Session
import io.github.devasenan134.cauldron.data.clearGoogleState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(back: () -> Unit) {
    val app = app()
    val context = LocalContext.current
    val me = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me

    val goal = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me?.kcalGoal ?: 2200
    var editingGoal by remember { mutableStateOf(false) }
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
                        if (me?.isOwner == true) Text("Owner", style = MaterialTheme.typography.bodySmall, color = Ember)
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
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Ink, modifier = Modifier.size(18.dp))
                    Text("  Sign out", color = Ink)
                }
            }

            SectionLabel("Goals")
            Card {
                Row(Modifier.fillMaxWidth().pressable({ editingGoal = true }, 0.98f), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily calorie goal", fontWeight = FontWeight.SemiBold)
                        Text("Shown on the Home screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%,d kcal".format(goal), style = MaterialTheme.typography.titleMedium, color = Ember)
                }
            }

            SectionLabel("App updates")
            UpdatesCard()

            SectionLabel("About")
            Card {
                Text("Cauldron ${app.updates.currentVersion}", fontWeight = FontWeight.SemiBold)
                Text("Recipes, meal plans, batch cooking and groceries.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("© 2026 Devasenan Murugan. All rights reserved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (editingGoal) GoalDialog(goal, onDismiss = { editingGoal = false }) { kcal -> editingGoal = false; app.scope.launch { runCatching { app.setKcalGoal(kcal) } } }
}

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

    Card {
        Text("Installed: version ${updates.currentVersion}", style = MaterialTheme.typography.bodyMedium)
        val release = available
        if (release == null) {
            when (val s = step) {
                UpdateStep.Checking -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Ember, strokeWidth = 2.dp)
                    Text("  Checking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                UpdateStep.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                    Text("  You have the latest version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is UpdateStep.Failed -> Text(s.message, color = Danger, modifier = Modifier.padding(top = 12.dp))
                else -> {}
            }
            if (step != UpdateStep.Checking) OutlinedButton(onClick = { check() }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Check for updates", color = Ink)
            }
            return@Card
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Default.SystemUpdate, null, tint = Ember)
            Text("  Version ${release.version} is available", fontWeight = FontWeight.SemiBold, color = Ember)
        }
        if (release.notes.isNotBlank()) Text(release.notes.lines().joinToString("\n") { it.replaceFirst(Regex("^\\s*[-*] "), "• ") }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        if (release.size > 0) Text("%.1f MB".format(release.size / 1_048_576.0), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        when (val s = step) {
            is UpdateStep.Downloading -> Column(Modifier.padding(top = 12.dp)) {
                LinearProgressIndicator(progress = { s.progress }, color = Ember, trackColor = EmberSoft, modifier = Modifier.fillMaxWidth())
                Text("Downloading… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            is UpdateStep.Ready -> Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!canInstall) {
                    Text("Android needs your permission for Cauldron to install updates. Turn on “Allow from this source”, then come back.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { updates.openInstallPermission() }) { Text("Open settings", color = Ink) }
                }
                Button(onClick = { updates.install(s.apk) }, enabled = canInstall, colors = ButtonDefaults.buttonColors(containerColor = EmberBright)) { Text("Install ${release.version}", fontWeight = FontWeight.Bold) }
            }
            is UpdateStep.Failed -> {
                Text(s.message, color = Danger, modifier = Modifier.padding(top = 12.dp))
                Button(onClick = { download(release) }, colors = ButtonDefaults.buttonColors(containerColor = Ink), modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
            }
            else -> Button(onClick = { download(release) }, colors = ButtonDefaults.buttonColors(containerColor = EmberBright), modifier = Modifier.padding(top = 12.dp)) {
                Text("Download and install")
            }
        }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = content)
    }
}
