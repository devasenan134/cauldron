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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A211A), Ink)))) {
        // A warm glow, like embers under the pot.
        Box(Modifier.align(Alignment.Center).size(420.dp).clip(CircleShape).background(Brush.radialGradient(listOf(EmberBright.copy(alpha = 0.35f), Color.Transparent))))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(140.dp).clip(RoundedCornerShape(40.dp)).background(Cream), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(150.dp))
            }
            Text("Cauldron", color = Cream, style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 24.dp))
            Text("Cook once, eat all week.", color = Cream.copy(alpha = 0.7f), fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
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
                colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp)
                else Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            error?.let { Text(it, color = Color(0xFFFCA5A5), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
