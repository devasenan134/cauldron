package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(120.dp))
        Text("Cauldron", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Recipes, meal plans and groceries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(
            enabled = !busy,
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        val idToken = googleIdToken(context)
                        if (idToken != null) app.session.signedIn(app.api.signIn(idToken))
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
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Cream, strokeWidth = 2.dp)
            else Text("Sign in with Google")
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = Danger, textAlign = TextAlign.Center)
        }
    }
}
