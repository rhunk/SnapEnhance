package me.rhunk.snapenhance.ui.manager

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.ui.manager.sections.NotImplemented
import me.rhunk.snapenhance.ui.manager.sections.TasksSection
import me.rhunk.snapenhance.ui.manager.sections.features.FeaturesSection
import me.rhunk.snapenhance.ui.manager.sections.home.HomeSection
import me.rhunk.snapenhance.ui.manager.sections.scripting.ScriptsSection
import me.rhunk.snapenhance.ui.manager.sections.social.SocialSection
import kotlin.reflect.KClass

enum class EnumSection(
    val route: String,
    val icon: ImageVector,
    val section: KClass<out Section> = NotImplemented::class
) {
    TASKS(
        route = "tasks",
        icon = Icons.Filled.TaskAlt,
        section = TasksSection::class
    ),
    FEATURES(
        route = "features",
        icon = Icons.Filled.Stars,
        section = FeaturesSection::class
    ),
    HOME(
        route = "home",
        icon = Icons.Filled.Home,
        section = HomeSection::class
    ),
    SOCIAL(
        route = "social",
        icon = Icons.Filled.Group,
        section = SocialSection::class
    ),
    SCRIPTS(
        route = "scripts",
        icon = Icons.Filled.DataObject,
        section = ScriptsSection::class
    );

    companion object {
        fun fromRoute(route: String): EnumSection {
            return entries.first { it.route == route }
        }
    }
}



open class Section {
    lateinit var enumSection: EnumSection
    lateinit var context: RemoteSideContext
    lateinit var navController: NavController

    val currentRoute get() = navController.currentBackStackEntry?.destination?.route

    open fun init() {}
    open fun onResumed() {}

    open fun sectionTopBarName(): String = context.translation["manager.routes.${enumSection.route}"]
    open fun canGoBack(): Boolean = false

    @Composable
    open fun Content() { NotImplemented() }

    @Composable
    open fun TopBarActions(rowScope: RowScope) {}

    @Composable
    open fun FloatingActionButton() {}

    open fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.composable(enumSection.route) {
            Content()
        }
    }
}