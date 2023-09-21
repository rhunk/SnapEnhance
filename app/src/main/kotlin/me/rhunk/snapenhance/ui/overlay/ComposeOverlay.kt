package me.rhunk.snapenhance.ui.overlay

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Recomposer
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
fun overlayComposeView(service: Context) = ComposeView(service).apply {
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