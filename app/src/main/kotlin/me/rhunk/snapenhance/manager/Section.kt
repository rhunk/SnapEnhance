package me.rhunk.snapenhance.manager

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Stars
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import me.rhunk.snapenhance.manager.data.ManagerContext
import me.rhunk.snapenhance.manager.sections.HomeSection
import me.rhunk.snapenhance.manager.sections.NotImplemented
import me.rhunk.snapenhance.manager.sections.features.FeaturesSection
import kotlin.reflect.KClass

enum class EnumSection(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val section: KClass<out Section> = NotImplemented::class
) {
    DOWNLOADS(
        route = "downloads",
        title = "Downloads",
        icon = Icons.Filled.Download
    ),
    FEATURES(
        route = "features",
        title = "Features",
        icon = Icons.Filled.Stars,
        section = FeaturesSection::class
    ),
    HOME(
        route = "home",
        title = "Home",
        icon = Icons.Filled.Home,
        section = HomeSection::class
    ),
    FRIENDS(
        route = "friends",
        title = "Friends",
        icon = Icons.Filled.Group
    ),
    DEBUG(
        route = "debug",
        title = "Debug",
        icon = Icons.Filled.BugReport
    );

    companion object {
        fun fromRoute(route: String): EnumSection {
            return values().first { it.route == route }
        }
    }
}



open class Section {
    lateinit var enumSection: EnumSection
    lateinit var manager: ManagerContext
    lateinit var navController: NavController

    @Composable
    open fun Content() { NotImplemented() }

    open fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.composable(enumSection.route) {
            Content()
        }
    }
}