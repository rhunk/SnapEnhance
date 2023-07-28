package me.rhunk.snapenhance.manager

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import me.rhunk.snapenhance.manager.sections.NotImplemented


enum class MainSections(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit
) {
    DOWNLOADS(
        route = "downloads",
        title = "Downloads",
        icon = Icons.Filled.Download,
        content = { NotImplemented() }
    ),
    FEATURES(
        route = "features",
        title = "Features",
        icon = Icons.Filled.Stars,
        content = { NotImplemented() }
    ),
    HOME(
        route = "home",
        title = "Home",
        icon = Icons.Filled.Home,
        content = { NotImplemented() }
    ),
    FRIENDS(
        route = "friends",
        title = "Friends",
        icon = Icons.Filled.Group,
        content = { NotImplemented() }
    ),
    DEBUG(
        route = "debug",
        title = "Debug",
        icon = Icons.Filled.BugReport,
        content = { NotImplemented() }
    );
}

@Composable
fun NavBar(
    navController: NavController
) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        MainSections.values().toList().forEach { section ->
            fun selected() = currentDestination?.hierarchy?.any { it.route == section.route } == true

            NavigationBarItem(
                modifier = Modifier
                    .requiredWidth(120.dp)
                    .fillMaxHeight(),
                icon = {
                    val iconOffset by animateDpAsState(
                        if (selected()) 0.dp else 10.dp,
                        label = ""
                    )
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        modifier = Modifier.offset(y = iconOffset)
                    )
                },

                label = {
                    val labelOffset by animateDpAsState(
                        if (selected()) 0.dp else (-5).dp,
                        label = ""
                    )
                    Text(text = if (selected()) section.title else "", modifier = Modifier.offset(y = labelOffset))
                },
                selected = selected(),
                onClick = {
                    navController.navigate(section.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}