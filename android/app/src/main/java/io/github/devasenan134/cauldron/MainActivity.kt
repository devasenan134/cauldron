package io.github.devasenan134.cauldron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.devasenan134.cauldron.data.Session
import io.github.devasenan134.cauldron.ui.CauldronTheme
import io.github.devasenan134.cauldron.ui.Cream
import io.github.devasenan134.cauldron.ui.EmberSoft
import io.github.devasenan134.cauldron.ui.Ember
import io.github.devasenan134.cauldron.ui.FridgeScreen
import io.github.devasenan134.cauldron.ui.GroceryScreen
import io.github.devasenan134.cauldron.ui.PlannerScreen
import io.github.devasenan134.cauldron.ui.RecipeScreen
import io.github.devasenan134.cauldron.ui.RecipesScreen
import io.github.devasenan134.cauldron.ui.SignInScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CauldronApp
        setContent {
            CauldronTheme {
                val state by app.session.state.collectAsState()
                when (state) {
                    Session.State.Loading -> Box(Modifier.fillMaxSize())
                    Session.State.SignedOut -> SignInScreen()
                    is Session.State.SignedIn -> AppRoot()
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Recipes("recipes", "Recipes", Icons.AutoMirrored.Filled.MenuBook),
    Plan("plan", "Plan", Icons.Default.CalendarMonth),
    Fridge("fridge", "Fridge", Icons.Default.Kitchen),
    Grocery("grocery", "Grocery", Icons.Default.ShoppingCart),
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val app = io.github.devasenan134.cauldron.ui.app()
    val toBuy = app.grocery.items.collectAsState().value.count { !it.checked }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Cream) {
                Tab.entries.forEach { tab ->
                    val selected = route == tab.route || (tab == Tab.Recipes && route?.startsWith("recipe/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (tab == Tab.Grocery && toBuy > 0) {
                                BadgedBox(badge = { Badge(containerColor = Ember) { Text("$toBuy") } }) { Icon(tab.icon, null) }
                            } else {
                                Icon(tab.icon, null)
                            }
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = EmberSoft, selectedIconColor = Ember, selectedTextColor = Ember),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Recipes.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Tab.Recipes.route) { RecipesScreen(openRecipe = { nav.navigate("recipe/$it") }) }
            composable("recipe/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                RecipeScreen(id = it.arguments!!.getInt("id"), back = { nav.popBackStack() }, openPlan = { nav.navigate(Tab.Plan.route) })
            }
            composable(Tab.Plan.route) {
                PlannerScreen(openRecipe = { nav.navigate("recipe/$it") }, openGrocery = { nav.navigate(Tab.Grocery.route) })
            }
            composable(Tab.Fridge.route) { FridgeScreen(openRecipe = { nav.navigate("recipe/$it") }) }
            composable(Tab.Grocery.route) { GroceryScreen(openPlan = { nav.navigate(Tab.Plan.route) }) }
        }
    }
}
