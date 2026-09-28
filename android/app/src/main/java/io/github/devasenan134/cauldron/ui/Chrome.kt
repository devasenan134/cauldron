package io.github.devasenan134.cauldron.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.cauldron.data.Session

/** Opens the Settings screen; provided by the navigation host. */
val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** Room to leave at the bottom of scrolling content, so the floating tab bar doesn't cover it. */
val LocalBottomSpace = compositionLocalOf { 0.dp }

/** Content padding for a screen's scrolling list: sides, and room for the tab bar below. */
@Composable
fun screenPadding(horizontal: Dp = 20.dp, top: Dp = 0.dp) =
    PaddingValues(start = horizontal, end = horizontal, top = top, bottom = LocalBottomSpace.current + 16.dp)

/** A tab's big title, with its actions and the avatar (which opens Settings). */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) {
            FloatingCircle(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.ink) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = if (back != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun Avatar(size: Dp = 44.dp) {
    val app = app()
    val me = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me
    val update by app.updates.available.collectAsState()
    val open = LocalOpenSettings.current
    val initial = (me?.name?.ifBlank { null } ?: me?.email ?: "?").first().uppercaseChar().toString()
    BadgedBox(badge = { if (update != null) Badge(containerColor = C.go) }, modifier = Modifier.padding(start = 4.dp)) {
        FloatingCircle(open, size) {
            Text(initial, color = C.ink, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        }
    }
}

/** A round button that floats over the page on a soft shadow (back, search, settings). */
@Composable
fun FloatingCircle(onClick: () -> Unit, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).shadow(if (C.dark) 0.dp else 10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(CircleShape).background(C.surface).pressable(onClick, 0.9f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

data class TabItem(val route: String, val label: String, val icon: ImageVector, val badge: Int = 0, val dot: Boolean = false)

/** The bottom bar: flat, a hairline on top, outline icons with their names. */
@Composable
fun BottomTabBar(tabs: List<TabItem>, selected: String?, onSelect: (TabItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(C.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab -> TabButton(tab, tab.route == selected, Modifier.weight(1f)) { onSelect(tab) } }
        }
    }
}

@Composable
private fun TabButton(tab: TabItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg by animateColorAsState(if (selected) C.ink else C.faint, label = "tab")
    Column(modifier.fillMaxHeight().pressable(onClick, 0.9f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BadgedBox(badge = {
            if (tab.badge > 0) Badge(containerColor = C.ink, contentColor = C.bg) { Text("${tab.badge}", fontSize = 10.sp) }
            else if (tab.dot) Badge(containerColor = C.go)
        }) { Icon(tab.icon, null, tint = fg, modifier = Modifier.size(24.dp)) }
        Text(tab.label, color = fg, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
    }
}
