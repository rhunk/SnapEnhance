package me.rhunk.snapenhance.manager

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import me.rhunk.snapenhance.manager.data.ManagerContext


class Navigation(
    private val context: ManagerContext
) {
    @Composable
    fun NavigationHost(
        navController: NavHostController,
        innerPadding: PaddingValues
    ) {
        val sections = remember { EnumSection.values().toList().map {
            it to it.section.constructors.first().call()
        }.onEach { (_, instance) ->
            instance.manager = context
            instance.navController = navController
        } }
        val homeSection = EnumSection.HOME

        NavHost(navController, startDestination = homeSection.route, Modifier.padding(innerPadding)) {
            sections.forEach { (section, instance) ->
                composable(section.route) {
                    instance.Content()
                }
            }
        }
    }

    @Composable
    fun NavBar(
        navController: NavController
    ) {
        NavigationBar {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            EnumSection.values().toList().forEach { section ->
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
}
