package io.github.devasenan134.cauldron

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import io.github.devasenan134.cauldron.data.Session
import io.github.devasenan134.cauldron.ui.BottomTabBar
import io.github.devasenan134.cauldron.ui.C
import io.github.devasenan134.cauldron.ui.CauldronTheme
import io.github.devasenan134.cauldron.ui.FolderScreen
import io.github.devasenan134.cauldron.ui.FridgeScreen
import io.github.devasenan134.cauldron.ui.GroceryScreen
import io.github.devasenan134.cauldron.ui.HomeScreen
import io.github.devasenan134.cauldron.ui.ImportSheet
import io.github.devasenan134.cauldron.ui.LocalBottomSpace
import io.github.devasenan134.cauldron.ui.LocalOpenSettings
import io.github.devasenan134.cauldron.ui.LocalRefresh
import io.github.devasenan134.cauldron.ui.PlannerScreen
import io.github.devasenan134.cauldron.ui.ProfileScreen
import io.github.devasenan134.cauldron.ui.RecipeEditorScreen
import io.github.devasenan134.cauldron.ui.RecipeScreen
import io.github.devasenan134.cauldron.ui.RecipesScreen
import io.github.devasenan134.cauldron.ui.SettingsScreen
import io.github.devasenan134.cauldron.ui.SignInScreen
import io.github.devasenan134.cauldron.ui.TabItem
import io.github.devasenan134.cauldron.ui.app
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedLink(intent)?.let { (application as CauldronApp).sharedLink.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CauldronApp
        sharedLink(intent)?.let { app.sharedLink.value = it }
        setContent {
            val state by app.session.state.collectAsState()
            val theme = (state as? Session.State.SignedIn)?.me?.theme ?: "system"
            CauldronTheme(theme) {
                // Dark status-bar icons on the light theme, light ones on the dark theme.
                val light = !C.dark
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                Box(Modifier.fillMaxSize().background(C.bg)) {
                    when (state) {
                        Session.State.Loading -> {}
                        Session.State.SignedOut -> SignInScreen()
                        is Session.State.SignedIn -> AppRoot()
                    }
                }
            }
        }
    }
}

/**
 * The app's pages, iOS/Cook Well style.
 *
 * The five tabs stay alive once visited (hidden ones aren't drawn). Pages you open (a recipe, the
 * grocery list, settings, a folder, the editor) slide in on top of the page you came from, which also
 * stays alive underneath. So going back never has to build a page: the back swipe just slides the
 * top page away with your finger, and it looks the same whether you swipe slowly or flick. (Rebuilding
 * the page underneath at the start of a quick swipe is what made the old back animation stutter.)
 */
private sealed interface Page {
    data class Recipe(val id: Int) : Page
    data class Edit(val id: Int?) : Page // null = a new recipe
    data class Folder(val id: Int) : Page
    data object Grocery : Page
    data object Settings : Page

    fun save(): String = when (this) {
        is Recipe -> "recipe:$id"; is Edit -> "edit:${id ?: ""}"; is Folder -> "folder:$id"; Grocery -> "grocery"; Settings -> "settings"
    }

    companion object {
        fun load(s: String): Page? = s.split(":").let { p ->
            when (p[0]) {
                "recipe" -> Recipe(p[1].toInt()); "edit" -> Edit(p[1].toIntOrNull()); "folder" -> Folder(p[1].toInt())
                "grocery" -> Grocery; "settings" -> Settings; else -> null
            }
        }
    }
}

/** An open page and how far it has slid away (0 = fully shown, 1 = off to the right). */
private class Layer(val page: Page, val key: Long, shown: Boolean) {
    val offset = Animatable(if (shown) 0f else 1f)
}

/** The link in a "Share → Cauldron" from YouTube or Instagram. */
private fun sharedLink(intent: Intent?): String? =
    intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        ?.let { Regex("https?://\\S+").find(it)?.value }

private object Tabs {
    const val HOME = "home"; const val RECIPES = "recipes"; const val PLAN = "plan"; const val FRIDGE = "fridge"; const val PROFILE = "profile"
}

private val SLIDE = tween<Float>(320, easing = FastOutSlowInEasing)

@Composable
private fun AppRoot() {
    val app = app()
    val scope = rememberCoroutineScope()
    val update by app.updates.available.collectAsState()
    val tabs = listOf(
        TabItem(Tabs.HOME, "Home", Icons.Outlined.Explore),
        TabItem(Tabs.RECIPES, "Recipes", Icons.AutoMirrored.Outlined.MenuBook),
        TabItem(Tabs.PLAN, "Plan", Icons.Outlined.CalendarMonth),
        TabItem(Tabs.FRIDGE, "Fridge", Icons.Outlined.Kitchen),
        TabItem(Tabs.PROFILE, "Profile", Icons.Outlined.AccountCircle, dot = update != null),
    )

    var tab by rememberSaveable { mutableStateOf(Tabs.HOME) }
    var visited by rememberSaveable { mutableStateOf(listOf(Tabs.HOME)) }
    var saved by rememberSaveable { mutableStateOf(listOf<String>()) } // the open pages, to survive a restart
    val layers = remember { mutableStateListOf<Layer>().apply { addAll(saved.mapNotNull(Page::load).mapIndexed { i, p -> Layer(p, i.toLong(), shown = true) }) } }
    var nextKey by remember { mutableLongStateOf(layers.size.toLong()) }
    fun remember() { saved = layers.map { it.page.save() } }
    val holder = rememberSaveableStateHolder()
    var refresh by remember { mutableIntStateOf(0) }

    fun open(page: Page) {
        val layer = Layer(page, nextKey++, shown = false)
        layers += layer
        remember()
        scope.launch { layer.offset.animateTo(0f, SLIDE) }
    }
    fun close(layer: Layer? = layers.lastOrNull()) {
        layer ?: return
        scope.launch {
            layer.offset.animateTo(1f, SLIDE)
            layers.remove(layer)
            holder.removeState(layer.key)
            remember()
            refresh++
        }
    }
    fun closeAll() { layers.forEach { holder.removeState(it.key) }; layers.clear(); remember() }
    fun goTab(t: String) {
        closeAll()
        tab = t
        refresh++
        if (t !in visited) visited = visited + t
    }
    val openRecipe: (Int) -> Unit = { open(Page.Recipe(it)) }
    var importing by remember { mutableStateOf(false) }
    val shared by app.sharedLink.collectAsState()

    // Back from a tab other than Home: it shrinks away over Home (like Android's own back), then Home.
    val tabBack = remember { Animatable(0f) }
    PredictiveBackHandler(enabled = layers.isEmpty() && tab != Tabs.HOME) { progress ->
        if (Tabs.HOME !in visited) visited = visited + Tabs.HOME
        try {
            progress.collect { tabBack.snapTo(it.progress) }
            tabBack.animateTo(1f, tween(150))
            tab = Tabs.HOME
            tabBack.snapTo(0f)
        } catch (e: CancellationException) {
            tabBack.animateTo(0f, tween(200))
            throw e
        }
    }
    // Back from an open page: it follows your finger off to the right; let go early and it springs back.
    PredictiveBackHandler(enabled = layers.isNotEmpty()) { progress ->
        val top = layers.last()
        try {
            progress.collect { top.offset.snapTo(it.progress) }
            top.offset.animateTo(1f, tween((320 * (1 - top.offset.value)).toInt().coerceAtLeast(120), easing = FastOutSlowInEasing))
            layers.remove(top)
            holder.removeState(top.key)
            remember()
            refresh++
        } catch (e: CancellationException) {
            top.offset.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            throw e
        }
    }

    val topOffset = layers.lastOrNull()?.offset?.value ?: 1f // 1 = no page over the tabs
    CompositionLocalProvider(
        LocalOpenSettings provides { open(Page.Settings) },
        LocalBottomSpace provides 72.dp,
        LocalRefresh provides refresh,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(C.bg)) {
            val width = constraints.maxWidth.toFloat()

            // The tabs. Hidden ones aren't drawn; Home shows under a tab being swiped back.
            val tabsVisible = layers.size < 2 || layers[layers.size - 2].offset.value > 0f
            Box(Modifier.fillMaxSize().graphicsLayer {
                // Drift left a little while a page covers them (a touch of depth, like iOS).
                translationX = if (layers.isEmpty()) 0f else -(1f - topOffset) * width * 0.2f
                alpha = if (tabsVisible) 1f else 0f
            }) {
                visited.forEach { t ->
                    val current = t == tab
                    val underneath = t == Tabs.HOME && tab != Tabs.HOME && tabBack.value > 0f
                    val dim = if (underneath) 0.35f * (1f - tabBack.value) else 0f
                    Box(
                        Modifier.fillMaxSize().zIndex(if (current) 1f else 0f).graphicsLayer {
                            alpha = if (current || underneath) 1f else 0f
                            if (current && tabBack.value > 0f) {
                                val p = tabBack.value
                                // Stays solid (never see-through): it shrinks to a card and slides a little.
                                scaleX = 1f - 0.12f * p; scaleY = scaleX
                                translationX = p * size.width * 0.08f
                                shape = RoundedCornerShape(28.dp); clip = true
                                shadowElevation = 32f
                            }
                        }.background(C.bg),
                    ) {
                        if (dim > 0f) Box(Modifier.fillMaxSize().zIndex(3f).background(Color.Black.copy(alpha = dim)))
                        holder.SaveableStateProvider("tab:$t") {
                            when (t) {
                                Tabs.HOME -> HomeScreen(openRecipe = openRecipe, openTab = { goTab(it) }, openGrocery = { open(Page.Grocery) })
                                Tabs.RECIPES -> RecipesScreen(openRecipe = openRecipe, newRecipe = { open(Page.Edit(null)) }, importRecipe = { importing = true })
                                Tabs.PLAN -> PlannerScreen(openRecipe = openRecipe, openGrocery = { open(Page.Grocery) })
                                Tabs.FRIDGE -> FridgeScreen(openRecipe = openRecipe)
                                Tabs.PROFILE -> ProfileScreen(openRecipe = openRecipe, openFolder = { open(Page.Folder(it)) })
                            }
                        }
                    }
                }
                // Keep the clock readable over scrolling content.
                Box(Modifier.align(Alignment.TopCenter).zIndex(2f).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(C.bg.copy(alpha = 0.94f)))
                BottomTabBar(tabs, tab, onSelect = { goTab(it.route) }, modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f))
            }

            // Open pages, each on top of the last.
            layers.forEachIndexed { i, layer ->
                key(layer.key) {
                    val isTop = i == layers.lastIndex
                    val above = layers.getOrNull(i + 1)
                    val drawn = isTop || i == layers.size - 2 // only the top two are ever visible
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            translationX = layer.offset.value * width - (above?.let { (1f - it.offset.value) * width * 0.2f } ?: 0f)
                            alpha = if (drawn) 1f else 0f
                            shadowElevation = if (isTop && layer.offset.value > 0f) 24f else 0f
                        }.background(C.bg),
                    ) {
                        holder.SaveableStateProvider(layer.key) {
                            CompositionLocalProvider(LocalBottomSpace provides 0.dp) {
                                PageContent(layer.page, back = { close(layer) }, open = ::open, openRecipe = openRecipe,
                                    replace = { page ->
                                        // e.g. a new recipe was saved: show it instead of the editor
                                        val idx = layers.indexOf(layer)
                                        if (idx >= 0) { layers[idx] = Layer(page, nextKey++, shown = true); holder.removeState(layer.key); remember() }
                                    },
                                    toTab = { goTab(it) })
                            }
                        }
                    }
                }
            }
            // The status-bar strip for open pages (a recipe's photo page draws its own).
            val top = layers.lastOrNull()
            if (top != null && top.page !is Page.Recipe) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().graphicsLayer { translationX = top.offset.value * width }
                    .windowInsetsTopHeight(WindowInsets.statusBars).background(C.bg.copy(alpha = 0.94f)))
            }
        }
    }
    if (importing || shared != null) ImportSheet(
        initial = shared,
        onDismiss = { importing = false; app.sharedLink.value = null },
        openRecipe = { id -> goTab(Tabs.RECIPES); open(Page.Recipe(id)) },
    )
}

@Composable
private fun PageContent(page: Page, back: () -> Unit, open: (Page) -> Unit, openRecipe: (Int) -> Unit, replace: (Page) -> Unit, toTab: (String) -> Unit) {
    when (page) {
        is Page.Recipe -> RecipeScreen(id = page.id, back = back, openPlan = { toTab(Tabs.PLAN) },
            edit = { open(Page.Edit(page.id)) }, openRecipe = openRecipe, editNew = { open(Page.Edit(it)) })
        is Page.Edit -> RecipeEditorScreen(page.id, back = back,
            saved = { id -> if (page.id == null) replace(Page.Recipe(id)) else back() },
            deleted = { toTab(Tabs.RECIPES) })
        is Page.Folder -> FolderScreen(page.id, back = back, openRecipe = openRecipe)
        Page.Grocery -> GroceryScreen(openPlan = { toTab(Tabs.PLAN) }, back = back)
        Page.Settings -> SettingsScreen(back = back)
    }
}
