package me.rhunk.snapenhance.manager.ui

import android.os.Bundle
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.reflect.KClass


class Navigation(
    val navHostController: NavHostController,
    private val tabs: List<Tab>,
    private val defaultTab: KClass<out Tab>
) {

    @Composable
    fun TopBar() {

    }

    @Composable
    fun FloatingActionButtons() {

    }

    fun navigateTo(tab: KClass<out Tab>) {
        navHostController.navigate(tabs.first { it::class == tab }.route)
    }

    fun navigateTo(tab: KClass<out Tab>, noHistory: Boolean) {
        navHostController.navigate(tabs.first { it::class == tab }.route) {
            restoreState = false
            launchSingleTop = true
            popUpTo(navHostController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    fun navigateTo(tab: KClass<out Tab>, args: Bundle) {
        navHostController.currentBackStackEntry?.savedStateHandle?.apply {
            set("args", args)
        }
        navigateTo(tab)
    }

    @Composable
    fun NavigationHost(
        innerPadding: PaddingValues
    ) {
        NavHost(
            navHostController,
            startDestination = tabs.first { it::class == defaultTab }.route,
            Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            tabs.forEach { tab ->
                tab.build(this)
            }
        }
    }


    @Composable
    fun BottomBar() {
        NavigationBar {
            val navBackStackEntry by navHostController.currentBackStackEntryAsState()

            remember { tabs.filter { it.isPrimary } }.forEach { tab ->
                val tabSubRoutes = remember { tab.nestedTabs.map { it.route } }
                NavigationBarItem(
                    selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == tab.route || tabSubRoutes.contains(it.route) } == true,
                    alwaysShowLabel = false,
                    modifier = Modifier.fillMaxHeight(),
                    icon = {
                        Icon(imageVector = tab.icon!!, contentDescription = null)
                    },
                    label = {
                        Text(
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            fontSize = 12.sp,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                            text = tab.route
                        )
                    },
                    onClick = {
                        navHostController.navigate(tab.route) {
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