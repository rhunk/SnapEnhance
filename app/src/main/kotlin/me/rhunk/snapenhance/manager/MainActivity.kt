package me.rhunk.snapenhance.manager

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.rememberNavController
import me.rhunk.snapenhance.manager.data.ManagerContext

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startDestination = intent.getStringExtra("route")?.let { EnumSection.fromRoute(it) } ?: EnumSection.HOME
        val managerContext = ManagerContext(this)

        setContent {
            val navController = rememberNavController()
            val navigation = Navigation(managerContext)
            AppMaterialTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { navigation.NavBar(navController = navController) }
                ) { innerPadding ->
                    navigation.NavigationHost(navController = navController, innerPadding = innerPadding, startDestination = startDestination)
                }
            }
        }
    }
}
