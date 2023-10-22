package me.rhunk.snapenhance.manager.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.manager.BuildConfig
import me.rhunk.snapenhance.manager.lspatch.LSPatch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val coroutineScope = rememberCoroutineScope()
            MaterialTheme(
                colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemInDarkTheme()) dynamicDarkColorScheme(LocalContext.current)
                    else dynamicLightColorScheme(LocalContext.current)
                } else MaterialTheme.colorScheme
            ) {
                val context = LocalContext.current
                val logs = remember { mutableStateListOf<String>() }
                fun printLog(data: Any) {
                    when (data) {
                        is Throwable -> {
                            logs += data.message.toString()
                            logs += StringWriter().apply {
                                data.printStackTrace(PrintWriter(this))
                            }.toString()
                        }
                        else -> logs += data.toString()
                    }
                }

                val scrollState = rememberLazyListState(0)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(text = "SE Manager")

                    Button(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                val lspatch = LSPatch(
                                    context,
                                    mapOf(
                                        BuildConfig.APPLICATION_ID to File(context.packageManager.getPackageInfo(
                                            BuildConfig.APPLICATION_ID, 0).applicationInfo.sourceDir)
                                    )
                                ) { printLog(it) }
                                lspatch.patch(
                                    File(context.packageManager.getPackageInfo("com.snapchat.android", 0).applicationInfo.sourceDir),
                                    File(context.filesDir, "patched.apk")
                                )
                            }.onFailure { printLog(it) }
                        }
                    }) {
                        Text(text = "Test patch apk")
                    }

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxWidth().padding(5.dp).height(500.dp).border(1.dp, color = Color.Black),
                        content = {
                            items(logs) {
                                Text(text = it, modifier = Modifier.padding(2.dp))
                            }
                        }
                    )

                    LaunchedEffect(logs.size) {
                        scrollState.scrollToItem((logs.size - 1).coerceAtLeast(0))
                    }
                }
            }
        }
    }
}