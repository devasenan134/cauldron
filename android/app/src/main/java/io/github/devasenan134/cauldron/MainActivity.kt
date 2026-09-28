package io.github.devasenan134.cauldron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.devasenan134.cauldron.data.Session
import io.github.devasenan134.cauldron.ui.CauldronTheme
import io.github.devasenan134.cauldron.ui.C
import io.github.devasenan134.cauldron.ui.BottomTabBar
import io.github.devasenan134.cauldron.ui.FridgeScreen
import io.github.devasenan134.cauldron.ui.GroceryScreen
import io.github.devasenan134.cauldron.ui.HomeScreen
import io.github.devasenan134.cauldron.ui.LocalBottomSpace
import io.github.devasenan134.cauldron.ui.LocalOpenSettings
import io.github.devasenan134.cauldron.ui.PlannerScreen
import io.github.devasenan134.cauldron.ui.RecipeEditorScreen
import io.github.devasenan134.cauldron.ui.RecipeScreen
import io.github.devasenan134.cauldron.ui.RecipesScreen
import io.github.devasenan134.cauldron.ui.SettingsScreen
import io.github.devasenan134.cauldron.ui.SignInScreen
import io.github.devasenan134.cauldron.ui.TabItem
import io.github.devasenan134.cauldron.ui.app

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CauldronApp
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

private object Routes {
    const val HOME = "home"
    const val RECIPES = "recipes"
    const val PLAN = "plan"
    const val FRIDGE = "fridge"
    const val GROCERY = "grocery"
    const val SETTINGS = "settings"
    const val NEW_RECIPE = "recipe/new"
    fun recipe(id: Int) = "recipe/$id"
    fun editRecipe(id: Int) = "recipe/$id/edit"

    /** Pages that slide in over the tabs (and slide away on back). */
    fun isDetail(route: String?) = route != null && (route.startsWith("recipe/") || route == SETTINGS)
}

private const val SLIDE_MS = 320

private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val app = app()
    val toBuy = app.grocery.items.collectAsState().value.count { !it.checked }
    val tabs = listOf(
        TabItem(Routes.HOME, "Home", Icons.Outlined.Explore),
        TabItem(Routes.RECIPES, "Recipes", Icons.AutoMirrored.Outlined.MenuBook),
        TabItem(Routes.PLAN, "Plan", Icons.Outlined.CalendarMonth),
        TabItem(Routes.FRIDGE, "Fridge", Icons.Outlined.Kitchen),
        TabItem(Routes.GROCERY, "Grocery", Icons.Outlined.ShoppingBasket, badge = toBuy),
    )
    val showBar = route in tabs.map { it.route }
    val openRecipe: (Int) -> Unit = { nav.navigate(Routes.recipe(it)) }

    CompositionLocalProvider(
        LocalOpenSettings provides { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
        LocalBottomSpace provides if (showBar) 72.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Detail pages slide in from the right while the page below shifts a little; back (and the
            // predictive back gesture, which plays this as you swipe) does the reverse. Tabs crossfade.
            NavHost(
                nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (Routes.isDetail(targetState.destination.route)) slideInHorizontally(tween(SLIDE_MS)) { it }
                    else fadeIn(tween(200))
                },
                exitTransition = {
                    if (Routes.isDetail(targetState.destination.route)) slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeOut(tween(SLIDE_MS), 0.6f)
                    else fadeOut(tween(150))
                },
                popEnterTransition = {
                    if (Routes.isDetail(initialState.destination.route)) slideInHorizontally(tween(SLIDE_MS)) { -it / 4 } + fadeIn(tween(SLIDE_MS), 0.6f)
                    else fadeIn(tween(200))
                },
                popExitTransition = {
                    if (Routes.isDetail(initialState.destination.route)) slideOutHorizontally(tween(SLIDE_MS)) { it }
                    else fadeOut(tween(150))
                },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(openRecipe = openRecipe, openTab = { nav.tab(it) })
                }
                composable(Routes.RECIPES) { RecipesScreen(openRecipe = openRecipe, newRecipe = { nav.navigate(Routes.NEW_RECIPE) }) }
                composable(Routes.NEW_RECIPE) {
                    RecipeEditorScreen(null, back = { nav.popBackStack() },
                        saved = { id -> nav.navigate(Routes.recipe(id)) { popUpTo(Routes.NEW_RECIPE) { inclusive = true } } }, deleted = {})
                }
                composable("recipe/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    val id = it.arguments!!.getInt("id")
                    RecipeScreen(id = id, back = { nav.popBackStack() }, openPlan = { nav.tab(Routes.PLAN) }, edit = { nav.navigate(Routes.editRecipe(id)) })
                }
                composable("recipe/{id}/edit", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                    RecipeEditorScreen(it.arguments!!.getInt("id"), back = { nav.popBackStack() }, saved = { nav.popBackStack() },
                        deleted = { nav.popBackStack(Routes.RECIPES, inclusive = false).let { ok -> if (!ok) nav.tab(Routes.RECIPES) } })
                }
                composable(Routes.PLAN) { PlannerScreen(openRecipe = openRecipe, openGrocery = { nav.tab(Routes.GROCERY) }) }
                composable(Routes.FRIDGE) { FridgeScreen(openRecipe = openRecipe) }
                composable(Routes.GROCERY) { GroceryScreen(openPlan = { nav.tab(Routes.PLAN) }) }
                composable(Routes.SETTINGS) { SettingsScreen(back = { nav.popBackStack() }) }
            }
            // Content scrolls under the transparent status bar; keep the clock and icons readable.
            // (A recipe's page has its photo up there and its own bar that turns solid.)
            val photoPage = route == "recipe/{id}"
            if (!photoPage) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(C.bg.copy(alpha = 0.94f)))
            AnimatedVisibility(
                showBar, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            ) {
                BottomTabBar(tabs, route, onSelect = { nav.tab(it.route) })
            }
        }
    }
}
