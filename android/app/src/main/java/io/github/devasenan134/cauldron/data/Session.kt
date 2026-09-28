package io.github.devasenan134.cauldron.data

import android.content.Context
import io.github.devasenan134.cauldron.BuildConfig
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString

private val Context.sessionStore by preferencesDataStore("session")
private val TOKEN = stringPreferencesKey("token")
private val ME = stringPreferencesKey("me")

/**
 * Who is signed in. The token lives in the app's private storage (DataStore); the server can
 * revoke it, and a 401 from any call clears it.
 */
class Session(private val context: Context) {
    sealed interface State {
        data object Loading : State
        data object SignedOut : State
        data class SignedIn(val me: Me) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    @Volatile var token: String? = null
        private set

    suspend fun load() {
        val prefs = context.sessionStore.data.first()
        token = prefs[TOKEN]
        var me = prefs[ME]?.let { runCatching { Api.json.decodeFromString<Me>(it) }.getOrNull() }
        if (token == null && BuildConfig.DEV_TOKEN.isNotEmpty()) {
            token = BuildConfig.DEV_TOKEN
            me = Me(email = "(test build)")
        }
        _state.value = if (token != null && me != null) State.SignedIn(me) else State.SignedOut
    }

    suspend fun signedIn(me: Me) {
        val t = requireNotNull(me.token) { "server sent no token" }
        token = t
        val saved = me.copy(token = null)
        context.sessionStore.edit { it[TOKEN] = t; it[ME] = Api.json.encodeToString(saved) }
        _state.value = State.SignedIn(saved)
    }

    suspend fun updateMe(me: Me) {
        context.sessionStore.edit { it[ME] = Api.json.encodeToString(me.copy(token = null)) }
        if (_state.value is State.SignedIn) _state.value = State.SignedIn(me.copy(token = null))
    }

    suspend fun signedOut() {
        token = null
        context.sessionStore.edit { it.clear() }
        _state.value = State.SignedOut
    }
}
