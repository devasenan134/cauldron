package io.github.devasenan134.cauldron.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.devasenan134.cauldron.BuildConfig

/**
 * "Sign in with Google" through Android's Credential Manager. Returns a Google ID token for the
 * server's OAuth client (the same kind the website gets), or null if the person backed out.
 *
 * Google only answers apps whose package name and signing-key SHA-1 are registered as an
 * "Android" OAuth client in the same Google Cloud project.
 */
suspend fun googleIdToken(activityContext: Context): String? {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            error("unexpected credential type ${credential.type}")
        }
    } catch (e: GetCredentialCancellationException) {
        // Google reports "this app isn't registered" (package name + signing-key SHA-1 not in an
        // Android OAuth client) as a cancellation too, right after the account is picked.
        Log.w("GoogleAuth", "sign-in cancelled", e)
        if (e.message?.contains("cancelled by the user", ignoreCase = true) == true) null
        else throw IllegalStateException("Google didn't finish signing in (${e.message}). The app may not be registered with Google yet.")
    } catch (e: NoCredentialException) {
        Log.w("GoogleAuth", "no credential", e)
        throw IllegalStateException("Google offered no sign-in (${e.message}). Check that a Google account is on this phone and that the app is registered with Google.")
    } catch (e: GetCredentialException) {
        Log.w("GoogleAuth", "sign-in failed", e)
        throw IllegalStateException("Google sign-in failed: ${e.type}: ${e.message}")
    }
}

suspend fun clearGoogleState(context: Context) {
    runCatching { CredentialManager.create(context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
}
