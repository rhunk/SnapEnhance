package me.rhunk.snapenhance.manager

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import me.rhunk.snapenhance.manager.data.ManagerContext

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val managerContext = ManagerContext(this)

        setContent {
            App(managerContext)
        }
    }
}

@Composable
fun App(
    context: ManagerContext
) {
    val navController = rememberNavController()
    val navigation = Navigation(context)
    AppMaterialTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { navigation.NavBar(navController = navController) }
        ) { innerPadding ->
            navigation.NavigationHost(navController = navController, innerPadding = innerPadding)
        }
    }
}