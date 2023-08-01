package me.rhunk.snapenhance.manager.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.rhunk.snapenhance.manager.AppMaterialTheme
import me.rhunk.snapenhance.manager.setup.screens.SetupScreen
import me.rhunk.snapenhance.manager.setup.screens.impl.FfmpegScreen
import me.rhunk.snapenhance.manager.setup.screens.impl.LanguageScreen
import me.rhunk.snapenhance.manager.setup.screens.impl.MappingsScreen
import me.rhunk.snapenhance.manager.setup.screens.impl.SaveFolderScreen
import me.rhunk.snapenhance.manager.setup.screens.impl.WelcomeScreen


class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requirements = intent.getBundleExtra("requirements")?.let {
            Requirements.fromBundle(it)
        } ?: Requirements(firstRun = true)

        val requiredScreens = mutableListOf<SetupScreen>()

        with(requiredScreens) {
            with(requirements) {
                if (firstRun || language) add(LanguageScreen().apply { route = "language" })
                if (firstRun) add(WelcomeScreen().apply { route = "welcome" })
                if (firstRun || saveFolder) add(SaveFolderScreen().apply { route = "saveFolder" })
                if (firstRun || mappings) add(MappingsScreen().apply { route = "mappings" })
                if (firstRun || ffmpeg) add(FfmpegScreen().apply { route = "ffmpeg" })
            }
        }

        if (requiredScreens.isEmpty()) {
            finish()
            return
        }

        setContent {
            val navController = rememberNavController()
            val canGoNext = remember { mutableStateOf(false) }

            fun nextScreen() {
                if (!canGoNext.value) return
                canGoNext.value = false
                if (requiredScreens.size > 1) {
                    requiredScreens.removeFirst()
                    navController.navigate(requiredScreens.first().route)
                } else {
                    finish()
                }
            }

            AppMaterialTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val alpha: Float by animateFloatAsState(if (canGoNext.value) 1f else 0f,
                                label = "NextButton"
                            )

                            FilledIconButton(
                                onClick = { nextScreen() },
                                modifier = Modifier.padding(50.dp)
                                    .width(60.dp)
                                    .height(60.dp)
                                    .alpha(alpha)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForwardIos,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = requiredScreens.first().route
                        ) {
                            requiredScreens.forEach { screen ->
                                screen.allowNext = { canGoNext.value = it }
                                composable(screen.route) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        screen.Content()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}