package me.rhunk.snapenhance.ui.manager.sections.features

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.core.config.ConfigContainer
import me.rhunk.snapenhance.core.config.DataProcessors
import me.rhunk.snapenhance.core.config.PropertyKey
import me.rhunk.snapenhance.core.config.PropertyPair
import me.rhunk.snapenhance.core.config.PropertyValue
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.ChooseFolderHelper

class FeaturesSection : Section() {
    private val dialogs by lazy { Dialogs(context.translation) }

    companion object {
        const val MAIN_ROUTE = "feature_root"
        const val FEATURE_CONTAINER_ROUTE = "feature_container/{name}"
        const val SEARCH_FEATURE_ROUTE = "search_feature/{keyword}"
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

    private val allProperties by lazy {
        val properties = mutableMapOf<PropertyKey<*>, PropertyValue<*>>()
        allContainers.values.forEach {
            val container = it.value.get() as ConfigContainer
            container.properties.forEach { property ->
                properties[property.key] = property.value
            }
        }
        properties
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

            composable(FEATURE_CONTAINER_ROUTE) { backStackEntry ->
                backStackEntry.arguments?.getString("name")?.let { containerName ->
                    allContainers[containerName]?.let {
                        Container(it.value.get() as ConfigContainer)
                    }
                }
            }

            composable(SEARCH_FEATURE_ROUTE) { backStackEntry ->
                backStackEntry.arguments?.getString("keyword")?.let { keyword ->
                    val properties = allProperties.filter {
                        it.key.name.contains(keyword, ignoreCase = true) ||
                                context.translation["${it.key.propertyTranslationPath()}.name"].contains(keyword, ignoreCase = true) ||
                                context.translation["${it.key.propertyTranslationPath()}.description"].contains(keyword, ignoreCase = true)
                    }.map { PropertyPair(it.key, it.value) }

                    PropertiesView(properties)
                }
            }
        }
    }

    @Composable
    private fun PropertyAction(property: PropertyPair<*>, registerClickCallback: RegisterClickCallback) {
        var showDialog by remember { mutableStateOf(false) }
        var dialogComposable by remember { mutableStateOf<@Composable () -> Unit>({}) }

        fun registerDialogOnClickCallback() = registerClickCallback { showDialog = true }

        if (showDialog) {
            Dialog(
                onDismissRequest = { showDialog = false }
            ) {
                dialogComposable()
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
                var state by remember { mutableStateOf(propertyValue.get() as Boolean) }
                Switch(
                    checked = state,
                    onCheckedChange = registerClickCallback {
                        state = state.not()
                        propertyValue.setAny(state)
                    }
                )
            }

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                registerDialogOnClickCallback()

                dialogComposable = {
                    dialogs.UniqueSelectionDialog(property)
                }

                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp),
                    text = (propertyValue.getNullable() as? String)?.let{
                        if (property.key.params.shouldTranslate) {
                            context.translation["features.options.${property.name}.$it"]
                        } else it
                    } ?: context.translation["manager.features.disabled"],
                )
            }

            DataProcessors.Type.STRING_MULTIPLE_SELECTION, DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                dialogComposable = {
                    when (dataType) {
                        DataProcessors.Type.STRING_MULTIPLE_SELECTION -> {
                            dialogs.MultipleSelectionDialog(property)
                        }
                        DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                            dialogs.KeyboardInputDialog(property) { showDialog = false }
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
                    navController.navigate(FEATURE_CONTAINER_ROUTE.replace("{name}", property.name))
                }

                if (container.globalState == null) return

                var state by remember { mutableStateOf(container.globalState!!) }

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
                    checked = state,
                    onCheckedChange = {
                        state = state.not()
                        container.globalState = state
                    }
                )
            }
        }

    }

    @Composable
    private fun PropertyCard(property: PropertyPair<*>) {
        var clickCallback by remember { mutableStateOf<ClickCallback?>(null) }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        clickCallback?.invoke(true)
                    }
                    .padding(all = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                property.key.params.icon?.let { iconName ->
                    //TODO: find a better way to load icons
                    val icon: ImageVector? = remember(iconName) {
                        runCatching {
                            val cl = Class.forName("androidx.compose.material.icons.filled.${iconName}Kt")
                            val method = cl.declaredMethods.first()
                            method.invoke(null, Icons.Filled) as ImageVector
                        }.getOrNull()
                    }
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(start = 10.dp)
                        )
                    }
                }

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
                    property.key.params.notices.also {
                        if (it.isNotEmpty()) Spacer(modifier = Modifier.height(5.dp))
                    }.forEach {
                        Text(
                            text = context.translation["features.notices.${it.key}"],
                            color = Color.Yellow,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(all = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PropertyAction(property, registerClickCallback = { callback ->
                        clickCallback = callback
                        callback
                    })
                }
            }
        }
    }

    @Composable
    private fun FeatureSearchBar(rowScope: RowScope, focusRequester: FocusRequester) {
        var searchValue by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        var currentSearchJob by remember { mutableStateOf<Job?>(null) }

        rowScope.apply {
            TextField(
                value = searchValue,
                onValueChange = { keyword ->
                    searchValue = keyword
                    if (keyword.isEmpty()) {
                        navController.navigate(MAIN_ROUTE)
                        return@TextField
                    }
                    currentSearchJob?.cancel()
                    scope.launch {
                        delay(300)
                        navController.navigate(SEARCH_FEATURE_ROUTE.replace("{keyword}", keyword), NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(MAIN_ROUTE, false)
                            .build()
                        )
                    }.also { currentSearchJob = it }
                },

                keyboardActions = KeyboardActions(onDone = {
                    focusRequester.freeFocus()
                }),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .weight(1f, fill = true)
                    .padding(end = 10.dp)
                    .height(70.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    @Composable
    override fun TopBarActions(rowScope: RowScope) {
        var showSearchBar by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }

        if (showSearchBar) {
            FeatureSearchBar(rowScope, focusRequester)
            LaunchedEffect(true) {
                focusRequester.requestFocus()
            }
        }

        IconButton(onClick = {
            showSearchBar = showSearchBar.not()
            if (!showSearchBar && navController.currentBackStackEntry?.destination?.route == SEARCH_FEATURE_ROUTE) {
                navController.navigate(MAIN_ROUTE)
            }
        }) {
            Icon(
                imageVector = if (showSearchBar) Icons.Filled.Close
                    else Icons.Filled.Search,
                contentDescription = null
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PropertiesView(
        properties: List<PropertyPair<*>>
    ) {
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


    @Composable
    private fun Container(
        configContainer: ConfigContainer
    ) {
        val properties = remember {
            configContainer.properties.map { PropertyPair(it.key, it.value) }
        }

        PropertiesView(properties)
    }
}