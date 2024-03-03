package me.rhunk.snapenhance.core.ui

import android.app.Activity
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rhunk.snapenhance.common.ui.AppMaterialTheme
import me.rhunk.snapenhance.common.ui.createComposeView
import me.rhunk.snapenhance.core.util.ktx.isDarkTheme
import kotlin.math.roundToInt

class InAppOverlay {
    inner class Toast(
        val composable: @Composable Toast.() -> Unit,
        val durationMs: Int
    ) {
        var shown by mutableStateOf(false)
        var visible by mutableStateOf(false)
    }

    private val toasts = mutableStateListOf<Toast>()

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun OverlayContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            toasts.forEach { toast ->
                val animation by animateFloatAsState(
                    targetValue = if (toast.visible) 1f else 0f,
                    animationSpec = if (toast.visible) tween(durationMillis = 150) else tween(durationMillis = 300),
                    label = "toast"
                )

                LaunchedEffect(toast) {
                    toast.visible = true
                    delay(toast.durationMs.toLong())
                    toast.visible = false
                    delay(1000)
                    toast.shown = true
                    synchronized(toasts) {
                        if (toasts.isNotEmpty() && toasts.all { it.shown }) toasts.clear()
                    }
                }

                val deviceWidth = LocalContext.current.resources.displayMetrics.widthPixels
                val draggableState = remember {
                    AnchoredDraggableState(
                        initialValue = 0,
                        positionalThreshold = { distance: Float -> distance * 0.5f },
                        velocityThreshold = { deviceWidth / 2f },
                        animationSpec = tween(),
                        confirmValueChange = {
                            toast.visible = false
                            true
                        }
                    ).apply {
                        updateAnchors(
                            DraggableAnchors {
                                -1 at -deviceWidth.toFloat()
                                0 at 0f
                                1 at deviceWidth.toFloat()
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .anchoredDraggable(draggableState, Orientation.Horizontal)
                        .offset { IntOffset(draggableState.offset.roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = animation
                            translationY = -100.dp.toPx() * (1 - animation)
                        }
                ) {
                    if (animation > 0.01f) {
                        toast.composable(toast)
                    }
                }
            }
        }
    }

    fun onActivityCreate(activity: Activity) {
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        root.post {
            root.addView(createComposeView(activity) {
                AppMaterialTheme(isDarkTheme = remember { activity.isDarkTheme() }) {
                    OverlayContent()
                }
            }.apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })
        }
    }

    @Composable
    private fun DurationProgress(
        duration: Int,
        modifier: Modifier = Modifier
    ) {
        val progress = remember { Animatable(1f) }

        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
        }

        LinearProgressIndicator(
            progress = { progress.value },
            modifier = modifier
        )
    }

    fun showStatusToast(
        icon: ImageVector,
        text: String,
        durationMs: Int = 2000,
        showDuration: Boolean = true,
    ) {
        showToast(
            icon = { Icon(icon, contentDescription = "icon", modifier = Modifier.size(32.dp)) },
            text = {
                Text(text, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            durationMs = durationMs,
            showDuration = showDuration
        )
    }

    fun showToast(
        icon: @Composable () -> Unit = {
            Icon(Icons.Outlined.Warning, contentDescription = "icon", modifier = Modifier.size(32.dp))
        },
        text: @Composable () -> Unit = {},
        durationMs: Int = 3000,
        showDuration: Boolean = true,
    ) {
        toasts.add(Toast(
            composable = {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        icon()
                        text()
                    }
                    if (showDuration) {
                        DurationProgress(duration = durationMs, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            durationMs = durationMs
        ))
    }
}