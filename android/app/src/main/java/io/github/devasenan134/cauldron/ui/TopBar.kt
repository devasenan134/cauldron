package io.github.devasenan134.cauldron.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(title: String, back: (() -> Unit)? = null, showAccount: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = {
            actions()
            if (showAccount) AccountButton()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
    )
}

/** Opens the Settings screen; provided by the navigation host. */
val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
private fun AccountButton() {
    val update by app().updates.available.collectAsState()
    val open = LocalOpenSettings.current
    IconButton(onClick = open) {
        if (update != null) {
            BadgedBox(badge = { Badge(containerColor = Ember) }) { Icon(Icons.Default.AccountCircle, "Settings (update available)") }
        } else {
            Icon(Icons.Default.AccountCircle, "Settings")
        }
    }
}
