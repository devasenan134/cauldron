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
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (back != null) 8.dp else 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = if (back != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
        if (back == null) Avatar()
    }
}

@Composable
fun Avatar(size: Dp = 40.dp) {
    val app = app()
    val me = (app.session.state.collectAsState().value as? Session.State.SignedIn)?.me
    val update by app.updates.available.collectAsState()
    val open = LocalOpenSettings.current
    val initial = (me?.name?.ifBlank { null } ?: me?.email ?: "?").first().uppercaseChar().toString()
    BadgedBox(badge = { if (update != null) Badge(containerColor = EmberBright) }, modifier = Modifier.padding(start = 4.dp)) {
        Box(
            Modifier.size(size).clip(CircleShape).background(Ink).pressable(open),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, color = Cream, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

data class TabItem(val route: String, val label: String, val icon: ImageVector, val badge: Int = 0)

/** The floating pill at the bottom: the selected tab grows to show its name. */
@Composable
fun FloatingTabBar(tabs: List<TabItem>, selected: String?, onSelect: (TabItem) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = Ink, shape = RoundedCornerShape(50),
            modifier = Modifier.shadow(16.dp, RoundedCornerShape(50), ambientColor = Ink, spotColor = Ink),
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEach { tab -> TabPill(tab, tab.route == selected) { onSelect(tab) } }
            }
        }
    }
}

@Composable
private fun TabPill(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) EmberBright else Color.Transparent, label = "tab")
    val fg by animateColorAsState(if (selected) Color.White else Cream.copy(alpha = 0.7f), label = "tabText")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).pressable(onClick, 0.9f).height(48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedBox(badge = {
            if (tab.badge > 0 && !selected) Badge(containerColor = EmberBright) { Text("${tab.badge}", fontSize = 10.sp) }
        }) { Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(22.dp)) }
        AnimatedVisibility(
            selected,
            enter = fadeIn() + expandHorizontally(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(tab.label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        }
    }
}
