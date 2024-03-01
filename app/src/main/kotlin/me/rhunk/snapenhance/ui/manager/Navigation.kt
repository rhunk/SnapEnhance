package me.rhunk.snapenhance.ui.manager

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import me.rhunk.snapenhance.RemoteSideContext

@OptIn(ExperimentalMaterial3Api::class)
class Navigation(
    private val context: RemoteSideContext,
    private val navController: NavHostController,
    val routes: Routes = Routes(context).also {
        it.navController = navController
    }
){
    @Composable
    fun TopBar() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()

        val canGoBack = remember (navBackStackEntry) { routes.getCurrentRoute(navBackStackEntry)?.let {
            !it.routeInfo.primary || it.routeInfo.childIds.contains(routes.currentDestination)
        } == true }

        TopAppBar(title = {
            routes.getCurrentRoute(navBackStackEntry)?.title?.invoke() ?: Text(text = routes.getCurrentRoute(navBackStackEntry)?.routeInfo?.translatedKey ?: "Unknown Page")
        }, navigationIcon =  {
            val backButtonAnimation by animateFloatAsState(if (canGoBack) 1f else 0f,
                label = "backButtonAnimation"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = backButtonAnimation }
                    .width(lerp(0.dp, 48.dp, backButtonAnimation))
                    .height(48.dp)
            ) {
                IconButton(
                    onClick = {
                        if (canGoBack) {
                            navController.popBackStack()
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        }, actions = {
            routes.getCurrentRoute(navBackStackEntry)?.topBarActions?.invoke(this)
        })
    }

    @Composable
    fun BottomBar() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val primaryRoutes = remember { routes.getRoutes().filter { it.routeInfo.primary } }

        NavigationBar {
            val currentRoute = routes.getCurrentRoute(navBackStackEntry)
            primaryRoutes.forEach { route ->
                NavigationBarItem(
                    alwaysShowLabel = false,
                    icon = {
                        Icon(imageVector = route.routeInfo.icon, contentDescription = null)
                    },
                    label = {
                        Text(
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                            text = if (currentRoute == route) context.translation["manager.routes.${route.routeInfo.key.substringBefore("/")}"] else "",
                        )
                    },
                    selected = currentRoute == route,
                    onClick = {
                        route.navigateReset()
                    }
                )
            }
        }
    }

    @Composable
    fun FloatingActionButton() {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        routes.getCurrentRoute(navBackStackEntry)?.floatingActionButton?.invoke()
    }

    @Composable
    fun Content(paddingValues: PaddingValues, startDestination: String) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            Modifier.padding(paddingValues),
            enterTransition = { fadeIn(tween(100)) },
            exitTransition = { fadeOut(tween(100)) }
        ) {
            routes.getRoutes().filter { it.parentRoute == null }.forEach { route ->
                val children = routes.getRoutes().filter { it.parentRoute == route }
                if (children.isEmpty()) {
                    composable(route.routeInfo.id) {
                        route.content.invoke(it)
                    }
                    route.customComposables.invoke(this)
                } else {
                    navigation("main_" + route.routeInfo.id, route.routeInfo.id) {
                        composable("main_" + route.routeInfo.id) {
                            route.content.invoke(it)
                        }
                        children.forEach { child ->
                            composable(child.routeInfo.id) {
                                child.content.invoke(it)
                            }
                        }
                        route.customComposables.invoke(this)
                    }
                }
            }
        }
    }
}
