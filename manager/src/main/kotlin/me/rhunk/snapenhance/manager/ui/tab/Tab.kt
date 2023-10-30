package me.rhunk.snapenhance.manager.ui.tab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import me.rhunk.snapenhance.manager.data.SharedConfig
import me.rhunk.snapenhance.manager.ui.Navigation
import kotlin.reflect.KClass

open class Tab(
    val route: String,
    val isPrimary: Boolean = false,
    val icon: ImageVector? = null,
) {
    lateinit var activity: ComponentActivity
    val nestedTabs = mutableListOf<Tab>()

    fun getNestedTab(tab: KClass<out Tab>) = nestedTabs.firstOrNull { it::class == tab }

    fun registerNestedTab(tab: KClass<out Tab>) = nestedTabs.add((tab.java.constructors.first().newInstance() as Tab).also {
        it.init(activity)
    })

    lateinit var navigation: Navigation
    lateinit var sharedConfig: SharedConfig

    fun getArguments() = navigation.navHostController.currentBackStackEntry?.savedStateHandle?.get<Bundle>("args")

    open fun init(activity: ComponentActivity) {
        this.activity = activity
    }

    open fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.composable(route) {
            Content()
        }
    }

    @Composable
    open fun TopBar() {}

    @Composable
    open fun FloatingActionButtons() {}

    @Composable
    open fun Content() {}

    fun toast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}