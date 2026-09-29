package io.github.devasenan134.cauldron

import android.app.Application
import io.github.devasenan134.cauldron.data.Api
import io.github.devasenan134.cauldron.data.GroceryRepo
import io.github.devasenan134.cauldron.data.Store
import io.github.devasenan134.cauldron.data.Session
import io.github.devasenan134.cauldron.data.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CauldronApp : Application() {
    /** Lives as long as the app process; for work that must outlast a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var session: Session
        private set
    lateinit var api: Api
        private set
    lateinit var grocery: GroceryRepo
        private set
    lateinit var updates: Updates
        private set
    lateinit var store: Store
        private set

    /** A link shared to Cauldron from another app (YouTube, Instagram), waiting to be imported. */
    val sharedLink = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        api = Api(BuildConfig.API_URL, token = { session.token }, onSignedOut = { scope.launch { signOutLocally() } })
        grocery = GroceryRepo(api, filesDir)
        updates = Updates(this, api)
        store = Store(api)
        scope.launch {
            session.load()
            // Pick up a changed name or owner status; offline is fine, the saved copy stays.
            if (session.state.value is Session.State.SignedIn) {
                runCatching { session.updateMe(api.me()) }
                runCatching { grocery.sync() } // also sends changes made offline last time
                updates.checkNowAndThen()
            }
        }
    }

    /** The daily calorie goal is saved on the server, so the website shows the same one. */
    suspend fun setKcalGoal(kcal: Int) = session.updateMe(api.setKcalGoal(kcal))

    /** "system", "light" or "dark"; saved on the account so the website follows too. Shown at once. */
    suspend fun setTheme(theme: String) {
        (session.state.value as? Session.State.SignedIn)?.me?.let { session.updateMe(it.copy(theme = theme)) }
        runCatching { session.updateMe(api.setTheme(theme)) }
    }

    /** Grid or list in Profile → Catalog; kept on the server, like the website's. */
    suspend fun setCatalogView(view: String) {
        (session.state.value as? Session.State.SignedIn)?.me?.let { session.updateMe(it.copy(catalogView = view)) }
        runCatching { session.updateMe(api.setCatalogView(view)) }
    }

    suspend fun signOutLocally() {
        grocery.clear()
        store.clear()
        session.signedOut()
    }
}
