package me.rhunk.snapenhance.ui.manager

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import me.rhunk.snapenhance.RemoteSideContext


class Navigation(
    private val context: RemoteSideContext,
    private val sections: Map<EnumSection, Section>,
    private val navHostController: NavHostController
){
    @Composable
    fun NavigationHost(
        startDestination: EnumSection,
        innerPadding: PaddingValues
    ) {
        NavHost(navHostController, startDestination = startDestination.route, Modifier.padding(innerPadding)) {
            sections.forEach { (_, instance) ->
                instance.navController = navHostController
                instance.build(this)
            }
        }
    }

    private fun getCurrentSection(navDestination: NavDestination) = sections.firstNotNullOf { (section, instance) ->
        if (navDestination.hierarchy.any { it.route == section.route }) {
            instance
        } else {
            null
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopBar() {
        val navBackStackEntry by navHostController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination ?: return
        val currentSection = getCurrentSection(currentDestination)

        TopAppBar(title = {
            Text(text = currentSection.sectionTopBarName())
        }, navigationIcon =  {
            if (currentSection.canGoBack()) {
                IconButton(onClick = { navHostController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
            }
        }, actions = {
            currentSection.TopBarActions(this)
        })
    }

    @Composable
    fun NavBar() {
        NavigationBar {
            val navBackStackEntry by navHostController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            sections.keys.forEach { section ->
                fun selected() = currentDestination?.hierarchy?.any { it.route == section.route } == true

                NavigationBarItem(
                    modifier = Modifier
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
                        Text(
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentWidth(unbounded = true).offset(y = labelOffset),
                            text = if (selected()) context.translation["manager.routes.${section.route}"] else "",
                        )
                    },
                    selected = selected(),
                    onClick = {
                        navHostController.navigate(section.route) {
                            popUpTo(navHostController.graph.findStartDestination().id) {
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
