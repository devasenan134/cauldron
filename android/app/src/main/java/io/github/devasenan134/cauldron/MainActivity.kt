package io.github.devasenan134.cauldron

import android.os.Bundle
import io.github.devasenan134.cauldron.ui.FolderScreen
import io.github.devasenan134.cauldron.ui.ProfileScreen
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContentScope
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
    const val PROFILE = "profile"
    const val GROCERY = "grocery"
    const val SETTINGS = "settings"
    const val NEW_RECIPE = "recipe/new"
    fun recipe(id: Int) = "recipe/$id"
    fun editRecipe(id: Int) = "recipe/$id/edit"
    fun folder(id: Int) = "folder/$id"
    val TABS = setOf(HOME, RECIPES, PLAN, FRIDGE, PROFILE)
}

private const val SLIDE_MS = 320

private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * How pages move. Every page is drawn on a solid background, so two pages never show through each
 * other mid-animation (the back swipe used to smear Plan over Home).
 *
 * - Opening a page (recipe, grocery, settings…): it slides in from the right over the page below,
 *   which drifts left a little. Back reverses it; the predictive back gesture scrubs this with your
 *   finger, so the page follows the swipe and the one below is already there.
 * - Switching tabs: a quick crossfade. Back from a tab to Home: the tab shrinks and fades away
 *   (the system's own back look) over Home, which stays put.
 */
private fun isPage(route: String?) = route != null && route !in Routes.TABS

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val app = app()
    val update by app.updates.available.collectAsState()
    val tabs = listOf(
        TabItem(Routes.HOME, "Home", Icons.Outlined.Explore),
        TabItem(Routes.RECIPES, "Recipes", Icons.AutoMirrored.Outlined.MenuBook),
        TabItem(Routes.PLAN, "Plan", Icons.Outlined.CalendarMonth),
        TabItem(Routes.FRIDGE, "Fridge", Icons.Outlined.Kitchen),
        TabItem(Routes.PROFILE, "Profile", Icons.Outlined.AccountCircle, dot = update != null),
    )
    val showBar = route in Routes.TABS
    val openRecipe: (Int) -> Unit = { nav.navigate(Routes.recipe(it)) }
    val openGrocery: () -> Unit = { nav.navigate(Routes.GROCERY) { launchSingleTop = true } }

    CompositionLocalProvider(
        LocalOpenSettings provides { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
        LocalBottomSpace provides if (showBar) 72.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            NavHost(
                nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (isPage(targetState.destination.route)) slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it }
                    else fadeIn(tween(180))
                },
                exitTransition = {
                    if (isPage(targetState.destination.route)) slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -it / 5 }
                    else fadeOut(tween(120))
                },
                popEnterTransition = {
                    if (isPage(initialState.destination.route)) slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -it / 5 }
                    else EnterTransition.None
                },
                popExitTransition = {
                    if (isPage(initialState.destination.route)) slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it }
                    else scaleOut(tween(SLIDE_MS), targetScale = 0.9f) + fadeOut(tween(SLIDE_MS))
                },
            ) {
                page(Routes.HOME) { HomeScreen(openRecipe = openRecipe, openTab = { nav.tab(it) }, openGrocery = openGrocery) }
                page(Routes.RECIPES) { RecipesScreen(openRecipe = openRecipe, newRecipe = { nav.navigate(Routes.NEW_RECIPE) }) }
                page(Routes.NEW_RECIPE) {
                    RecipeEditorScreen(null, back = { nav.popBackStack() },
                        saved = { id -> nav.navigate(Routes.recipe(id)) { popUpTo(Routes.NEW_RECIPE) { inclusive = true } } }, deleted = {})
                }
                page("recipe/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                    val id = it.arguments!!.getInt("id")
                    RecipeScreen(id = id, back = { nav.popBackStack() }, openPlan = { nav.tab(Routes.PLAN) },
                        edit = { nav.navigate(Routes.editRecipe(id)) }, openRecipe = openRecipe,
                        editNew = { newId -> nav.navigate(Routes.editRecipe(newId)) })
                }
                page("recipe/{id}/edit", listOf(navArgument("id") { type = NavType.IntType })) {
                    RecipeEditorScreen(it.arguments!!.getInt("id"), back = { nav.popBackStack() }, saved = { nav.popBackStack() },
                        deleted = { nav.popBackStack(Routes.RECIPES, inclusive = false).let { ok -> if (!ok) nav.tab(Routes.RECIPES) } })
                }
                page(Routes.PLAN) { PlannerScreen(openRecipe = openRecipe, openGrocery = openGrocery) }
                page(Routes.FRIDGE) { FridgeScreen(openRecipe = openRecipe) }
                page(Routes.PROFILE) { ProfileScreen(openRecipe = openRecipe, openFolder = { nav.navigate(Routes.folder(it)) }) }
                page("folder/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                    FolderScreen(it.arguments!!.getInt("id"), back = { nav.popBackStack() }, openRecipe = openRecipe)
                }
                page(Routes.GROCERY) { GroceryScreen(openPlan = { nav.tab(Routes.PLAN) }, back = { nav.popBackStack() }) }
                page(Routes.SETTINGS) { SettingsScreen(back = { nav.popBackStack() }) }
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

/** A destination drawn on the page colour, so pages never show through each other while animating. */
private fun NavGraphBuilder.page(
    route: String, arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    Box(Modifier.fillMaxSize().background(C.bg)) { content(entry) }
}
