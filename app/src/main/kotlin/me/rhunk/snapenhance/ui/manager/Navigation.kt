package me.rhunk.snapenhance.ui.manager

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
        NavHost(
            navHostController,
            startDestination = startDestination.route,
            Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
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
            val backButtonAnimation by animateFloatAsState(if (currentSection.canGoBack()) 1f else 0f,
                label = "backButtonAnimation"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = backButtonAnimation }
                    .width(lerp(0.dp, 48.dp, backButtonAnimation))
                    .height(48.dp)
            ) {
                IconButton(
                    onClick = { navHostController.popBackStack() }
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
            }
        }, actions = {
            currentSection.TopBarActions(this)
        })
    }

    @Composable
    fun Fab() {
        val navBackStackEntry by navHostController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination ?: return
        val currentSection = getCurrentSection(currentDestination)

        currentSection.FloatingActionButton()
    }

    @Composable
    fun NavBar() {
        NavigationBar {
            val navBackStackEntry by navHostController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            sections.keys.forEach { section ->
                fun selected() = currentDestination?.hierarchy?.any { it.route == section.route } == true

                NavigationBarItem(
                    alwaysShowLabel = false,
                    modifier = Modifier
                        .fillMaxHeight(),
                    icon = {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null
                        )
                    },

                    label = {
                        Text(
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
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
