package me.rhunk.snapenhance.ui.manager.sections.features

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.DataProcessors
import me.rhunk.snapenhance.core.config.PropertyPair
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.ChooseFolderHelper

class FeaturesSection : Section() {
    private val dialogs by lazy { Dialogs() }

    companion object {
        const val MAIN_ROUTE = "feature_root"
        const val FEATURE_CONTAINER_ROOT = "feature_container/{name}"
    }

    private lateinit var openFolderCallback: (uri: String) -> Unit
    private lateinit var openFolderLauncher: () -> Unit

    private val featuresRouteName by lazy { context.translation["manager.routes.features"] }

    private val allContainers by lazy {
        val containers = mutableMapOf<String, PropertyPair<*>>()
        fun queryContainerRecursive(container: ConfigContainer) {
            container.properties.forEach {
                if (it.key.dataType.type == DataProcessors.Type.CONTAINER) {
                    containers[it.key.name] = PropertyPair(it.key, it.value)
                    queryContainerRecursive(it.value.get() as ConfigContainer)
                }
            }
        }
        queryContainerRecursive(context.config.root)
        containers
    }

    override fun init() {
        openFolderLauncher = ChooseFolderHelper.createChooseFolder(context.activity!! as ComponentActivity) {
            openFolderCallback(it)
        }
    }

    override fun canGoBack() = sectionTopBarName() != featuresRouteName

    override fun sectionTopBarName(): String {
        navController.currentBackStackEntry?.arguments?.getString("name")?.let { routeName ->
            val currentContainerPair = allContainers[routeName]
            val propertyTree = run {
                var key = currentContainerPair?.key
                val tree = mutableListOf<String>()
                while (key != null) {
                    tree.add(key.propertyTranslationPath())
                    key = key.parentKey
                }
                tree
            }

            val translatedKey = propertyTree.reversed().joinToString(" > ") {
                context.translation["$it.name"]
            }

            return "$featuresRouteName > $translatedKey"
        }
        return featuresRouteName
    }

    override fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.navigation(route = enumSection.route, startDestination = MAIN_ROUTE) {
            composable(MAIN_ROUTE) {
                Container(context.config.root)
            }

            composable(FEATURE_CONTAINER_ROOT) { backStackEntry ->
                backStackEntry.arguments?.getString("name")?.let { containerName ->
                    allContainers[containerName]?.let {
                        Container(it.value.get() as ConfigContainer)
                    }
                }
            }
        }
    }

    @Composable
    private fun PropertyAction(property: PropertyPair<*>, registerClickCallback: RegisterClickCallback) {
        val showDialog = remember { mutableStateOf(false) }
        val dialogComposable = remember { mutableStateOf<@Composable () -> Unit>({}) }

        fun registerDialogOnClickCallback() = registerClickCallback {
            showDialog.value = true
        }

        if (showDialog.value) {
            Dialog(
                onDismissRequest = { showDialog.value = false }
            ) {
                dialogComposable.value()
            }
        }

        val propertyValue = property.value

        if (property.key.params.isFolder) {
            IconButton(onClick = registerClickCallback {
                openFolderCallback = { uri ->
                    propertyValue.setAny(uri)
                }
                openFolderLauncher()
            }.let { { it.invoke(true) } }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
            }
            return
        }

        when (val dataType = remember { property.key.dataType.type }) {
            DataProcessors.Type.BOOLEAN -> {
                val state = remember { mutableStateOf(propertyValue.get() as Boolean) }
                Switch(
                    checked = state.value,
                    onCheckedChange = registerClickCallback {
                        state.value = state.value.not()
                        propertyValue.setAny(state.value)
                    }
                )
            }

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                registerDialogOnClickCallback()

                dialogComposable.value = {
                    dialogs.UniqueSelectionDialog(property)
                }

                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp),
                    text = (propertyValue.getNullable() as? String) ?: context.translation["manager.features.disabled"],
                )
            }

            DataProcessors.Type.STRING_MULTIPLE_SELECTION, DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                dialogComposable.value = {
                    when (dataType) {
                        DataProcessors.Type.STRING_MULTIPLE_SELECTION -> {
                            dialogs.MultipleSelectionDialog(property)
                        }
                        DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                            dialogs.KeyboardInputDialog(property) { showDialog.value = false }
                        }
                        else -> {}
                    }
                }

                registerDialogOnClickCallback().let { { it.invoke(true) } }.also {
                    if (dataType == DataProcessors.Type.INTEGER ||
                        dataType == DataProcessors.Type.FLOAT) {
                        FilledIconButton(onClick = it) {
                            Text(
                                text = propertyValue.get().toString(),
                                modifier = Modifier.wrapContentWidth(),
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        }
                    }
                }
            }
            DataProcessors.Type.CONTAINER -> {
                val container = propertyValue.get() as ConfigContainer

                registerClickCallback {
                    navController.navigate(FEATURE_CONTAINER_ROOT.replace("{name}", property.name))
                }

                if (container.globalState == null) return

                val state = remember { mutableStateOf(container.globalState!!) }

                Box(
                    modifier = Modifier
                        .padding(end = 15.dp),
                ) {

                    Box(modifier = Modifier
                        .height(50.dp)
                        .width(1.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(5.dp)
                        ))
                }

                Switch(
                    checked = state.value,
                    onCheckedChange = {
                        state.value = state.value.not()
                        container.globalState = state.value
                    }
                )
            }
        }

    }

    @Composable
    private fun PropertyCard(property: PropertyPair<*>) {
        val clickCallback = remember { mutableStateOf<ClickCallback?>(null) }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        clickCallback.value?.invoke(true)
                    }
                    .padding(all = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = true)
                        .padding(all = 10.dp)
                ) {
                    Text(
                        text = context.translation["${property.key.propertyTranslationPath()}.name"],
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = context.translation["${property.key.propertyTranslationPath()}.description"],
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(all = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PropertyAction(property, registerClickCallback = { callback ->
                        clickCallback.value = callback
                        callback
                    })
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Container(
        configContainer: ConfigContainer
    ) {
        val properties = remember {
            configContainer.properties.map { PropertyPair(it.key, it.value) }
        }

        val scope = rememberCoroutineScope()
        val scaffoldState = rememberBottomSheetScaffoldState()
        Scaffold(
            snackbarHost = { SnackbarHost(scaffoldState.snackbarHostState) },
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        context.config.writeConfig()
                        scope.launch {
                            scaffoldState.snackbarHostState.showSnackbar("Saved")
                        }
                    },
                    modifier = Modifier.padding(10.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null
                    )
                }
            },
            content = { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(innerPadding),
                    //save button space
                    contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    items(properties) {
                        PropertyCard(it)
                    }
                }
            }
        )
    }

}