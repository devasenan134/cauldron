package io.github.devasenan134.cauldron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    fun recipe(id: Int) = "recipe/$id"
}

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
            NavHost(
                nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(160)) },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(openRecipe = openRecipe, openTab = { nav.tab(it) })
                }
                composable(Routes.RECIPES) { RecipesScreen(openRecipe = openRecipe) }
                composable(
                    "recipe/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType }),
                    enterTransition = { slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(220)) },
                    popExitTransition = { slideOutVertically(tween(260)) { it / 6 } + fadeOut(tween(200)) },
                ) {
                    RecipeScreen(id = it.arguments!!.getInt("id"), back = { nav.popBackStack() }, openPlan = { nav.tab(Routes.PLAN) })
                }
                composable(Routes.PLAN) { PlannerScreen(openRecipe = openRecipe, openGrocery = { nav.tab(Routes.GROCERY) }) }
                composable(Routes.FRIDGE) { FridgeScreen(openRecipe = openRecipe) }
                composable(Routes.GROCERY) { GroceryScreen(openPlan = { nav.tab(Routes.PLAN) }) }
                composable(Routes.SETTINGS) { SettingsScreen(back = { nav.popBackStack() }) }
            }
            AnimatedVisibility(
                showBar, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            ) {
                BottomTabBar(tabs, route, onSelect = { nav.tab(it.route) })
            }
        }
    }
}
