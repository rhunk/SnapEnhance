package me.rhunk.snapenhance.common.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// https://github.com/tberghuis/FloatingCountdownTimer/blob/master/app/src/main/java/xyz/tberghuis/floatingtimer/service/overlayViewFactory.kt
fun createComposeView(context: Context, content: @Composable () -> Unit) = ComposeView(context).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    val lifecycleOwner = OverlayLifecycleOwner().apply {
        performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    setViewTreeLifecycleOwner(lifecycleOwner)
    setViewTreeSavedStateRegistryOwner(lifecycleOwner)

    val viewModelStore = ViewModelStore()
    setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore
            get() = viewModelStore
    })

    val backPressedDispatcherOwner = OnBackPressedDispatcher()
    setViewTreeOnBackPressedDispatcherOwner(object: OnBackPressedDispatcherOwner {
        override val lifecycle: Lifecycle
            get() = lifecycleOwner.lifecycle
        override val onBackPressedDispatcher: OnBackPressedDispatcher
            get() = backPressedDispatcherOwner
    })

    val coroutineContext = AndroidUiDispatcher.CurrentThread
    val runRecomposeScope = CoroutineScope(coroutineContext)
    val recomposer = Recomposer(coroutineContext)
    compositionContext = recomposer
    runRecomposeScope.launch {
        recomposer.runRecomposeAndApplyChanges()
    }

    setContent {
        AppMaterialTheme {
            content()
        }
    }
}

fun createComposeAlertDialog(context: Context, builder: AlertDialog.Builder.() -> Unit = {}, content: @Composable (alertDialog: AlertDialog) -> Unit): AlertDialog {
    lateinit var alertDialog: AlertDialog

    return AlertDialog.Builder(context)
        .apply(builder)
        .setView(createComposeView(context) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                content(alertDialog)
            }
        })
        .create().apply {
            alertDialog = this
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
}

private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private var mLifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private var mSavedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        mLifecycleRegistry.handleLifecycleEvent(event)
    }
    fun performRestore(savedState: Bundle?) {
        mSavedStateRegistryController.performRestore(savedState)
    }
    fun performSave(outBundle: Bundle) {
        mSavedStateRegistryController.performSave(outBundle)
    }
}