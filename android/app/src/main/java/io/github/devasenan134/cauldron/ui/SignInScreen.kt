package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.R
import io.github.devasenan134.cauldron.data.ApiException
import io.github.devasenan134.cauldron.data.googleIdToken
import kotlinx.coroutines.launch

@Composable
fun SignInScreen() {
    val app = app()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(28.dp)) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(C.surface), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(104.dp))
            }
            Text("Cook once,\neat all week.", color = C.ink, style = MaterialTheme.typography.displayMedium, lineHeight = 52.sp, modifier = Modifier.padding(top = 28.dp))
            Text("Recipes, meal plans, batch cooking and groceries: Cauldron.", color = C.muted, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val idToken = googleIdToken(context)
                            if (idToken != null) {
                                app.session.signedIn(app.api.signIn(idToken))
                                app.scope.launch { app.updates.checkNowAndThen() }
                            }
                        } catch (e: ApiException) {
                            error = if (e.code == 403 && "guest list" in (e.message ?: ""))
                                "This Google account isn't on Cauldron's guest list. Ask the owner to add it."
                            else e.friendly()
                        } catch (e: Exception) {
                            error = e.friendly()
                        } finally {
                            busy = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = C.ink, contentColor = C.bg),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = C.bg, strokeWidth = 2.dp)
                else Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            error?.let { Text(it, color = C.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
