package me.rhunk.snapenhance.ui.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.SharedContextHolder
import me.rhunk.snapenhance.common.ui.AppMaterialTheme

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private lateinit var managerContext: RemoteSideContext

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized.not()) return
        intent.getStringExtra("route")?.let { route ->
            navController.popBackStack()
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id){
                    inclusive = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        managerContext = SharedContextHolder.remote(this).apply {
            activity = this@MainActivity
            checkForRequirements()
        }

        val routes = Routes(managerContext)
        routes.getRoutes().forEach { it.init() }

        setContent {
            navController = rememberNavController()
            val navigation = remember {
                Navigation(managerContext, navController, routes.also {
                    it.navController = navController
                })
            }
            val startDestination = remember { intent.getStringExtra("route") ?: routes.home.routeInfo.id }

            AppMaterialTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { navigation.TopBar() },
                    bottomBar = { navigation.BottomBar() },
                    floatingActionButton = { navigation.FloatingActionButton() }
                ) { innerPadding -> navigation.Content(innerPadding, startDestination) }
            }
        }
    }
}
