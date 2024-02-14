package me.rhunk.snapenhance.ui.manager

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.ui.manager.pages.LoggerHistoryRoot
import me.rhunk.snapenhance.ui.manager.pages.TasksRoot
import me.rhunk.snapenhance.ui.manager.pages.features.FeaturesRoot
import me.rhunk.snapenhance.ui.manager.pages.home.HomeLogs
import me.rhunk.snapenhance.ui.manager.pages.home.HomeRoot
import me.rhunk.snapenhance.ui.manager.pages.home.HomeSettings
import me.rhunk.snapenhance.ui.manager.pages.scripting.ScriptingRoot
import me.rhunk.snapenhance.ui.manager.pages.social.LoggedStories
import me.rhunk.snapenhance.ui.manager.pages.social.ManageScope
import me.rhunk.snapenhance.ui.manager.pages.social.MessagingPreview
import me.rhunk.snapenhance.ui.manager.pages.social.SocialRoot


data class RouteInfo(
    val id: String,
    val key: String = id,
    val icon: ImageVector = Icons.Default.Home,
    val primary: Boolean = false,
) {
    var translatedKey: String? = null
    val childIds = mutableListOf<String>()
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Routes(
    private val context: RemoteSideContext,
) {
    lateinit var navController: NavController
    private val routes = mutableListOf<Route>()

    val tasks = route(RouteInfo("tasks", icon = Icons.Default.TaskAlt, primary = true), TasksRoot())

    val features = route(RouteInfo("features", icon = Icons.Default.Stars, primary = true), FeaturesRoot())

    val home = route(RouteInfo("home", icon = Icons.Default.Home, primary = true), HomeRoot())
    val settings = route(RouteInfo("home_settings"), HomeSettings()).parent(home)
    val homeLogs = route(RouteInfo("home_logs"), HomeLogs()).parent(home)
    val loggerHistory = route(RouteInfo("logger_history"), LoggerHistoryRoot()).parent(home)

    val social = route(RouteInfo("social", icon = Icons.Default.Group, primary = true), SocialRoot())
    val manageScope = route(RouteInfo("manage_scope/?scope={scope}&id={id}"), ManageScope()).parent(social)
    val messagingPreview = route(RouteInfo("messaging_preview/?scope={scope}&id={id}"), MessagingPreview()).parent(social)
    val loggedStories = route(RouteInfo("logged_stories/?id={id}"), LoggedStories()).parent(social)

    val scripting = route(RouteInfo("scripts", icon = Icons.Filled.DataObject, primary = true), ScriptingRoot())

    open class Route {
        open val init: () -> Unit = { }
        open val title: @Composable (() -> Unit)? = null
        open val topBarActions: @Composable RowScope.() -> Unit = {}
        open val floatingActionButton: @Composable () -> Unit = {}
        open val content: @Composable (NavBackStackEntry) -> Unit = {}
        open val customComposables: NavGraphBuilder.() -> Unit = {}

        var parentRoute: Route? = null
            private set

        lateinit var context: RemoteSideContext
        lateinit var routeInfo: RouteInfo
        lateinit var routes: Routes

        private fun replaceArguments(id: String, args: Map<String, String>) = args.takeIf { it.isNotEmpty() }?.let {
            args.entries.fold(id) { acc, (key, value) ->
                acc.replace("{$key}", value)
            }
        } ?: id

        fun navigate(args: MutableMap<String, String>.() -> Unit = {}) {
            routes.navController.navigate(replaceArguments(routeInfo.id, HashMap<String, String>().apply { args() }))
        }

        fun navigateReset(args: MutableMap<String, String>.() -> Unit = {}) {
            routes.navController.navigate(replaceArguments(routeInfo.id, HashMap<String, String>().apply { args() })) {
                popUpTo(routes.navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun parent(route: Route): Route {
            assert(route.routeInfo.primary) { "Parent route must be a primary route" }
            parentRoute = route
            return this
        }
    }

    val currentRoute: Route?
        get() = routes.firstOrNull { route ->
            navController.currentBackStackEntry?.destination?.hierarchy?.any { it.route == route.routeInfo.id } ?: false
        }

    val currentDestination: String?
        get() = navController.currentBackStackEntry?.destination?.route

    fun getCurrentRoute(navBackStackEntry: NavBackStackEntry?): Route? {
        if (navBackStackEntry == null) return null

        return navBackStackEntry.destination.hierarchy.firstNotNullOfOrNull { destination ->
            routes.firstOrNull { route ->
                route.routeInfo.id == destination.route || route.routeInfo.childIds.contains(destination.route)
            }
        }
    }

    fun getRoutes(): List<Route> = routes

    private fun route(routeInfo: RouteInfo, route: Route): Route {
        route.apply {
            this.routeInfo = routeInfo
            routes = this@Routes
            context = this@Routes.context
            this.routeInfo.translatedKey = context.translation.getOrNull("manager.routes.${route.routeInfo.key.substringBefore("/")}")
        }
        routes.add(route)
        return route
    }
}