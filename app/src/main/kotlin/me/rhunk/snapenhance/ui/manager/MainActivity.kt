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
import me.rhunk.snapenhance.ui.AppMaterialTheme

class MainActivity : ComponentActivity() {
    private lateinit var sections: Map<EnumSection, Section>
    private lateinit var navController: NavHostController
    private lateinit var managerContext: RemoteSideContext

    override fun onPostResume() {
        super.onPostResume()
        sections.values.forEach { it.onResumed() }
    }

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

        val startDestination = intent.getStringExtra("route")?.let { EnumSection.fromRoute(it) } ?: EnumSection.HOME
        managerContext = SharedContextHolder.remote(this).apply {
            activity = this@MainActivity
            checkForRequirements()
        }

        sections = EnumSection.values().toList().associateWith {
            it.section.constructors.first().call()
        }.onEach { (section, instance) ->
            with(instance) {
                enumSection = section
                context = managerContext
                init()
            }
        }

        setContent {
            navController = rememberNavController()
            val navigation = remember { Navigation(managerContext, sections, navController) }
            AppMaterialTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { navigation.TopBar() },
                    bottomBar = { navigation.NavBar() },
                    floatingActionButton = { navigation.Fab() }
                ) { innerPadding ->
                    navigation.NavigationHost(
                        innerPadding = innerPadding,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
