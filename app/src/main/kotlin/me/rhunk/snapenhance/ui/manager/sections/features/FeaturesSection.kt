package me.rhunk.snapenhance.ui.manager.sections.features

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.common.config.*
import me.rhunk.snapenhance.ui.manager.MainActivity
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.*

@OptIn(ExperimentalMaterial3Api::class)
class FeaturesSection : Section() {
    private val alertDialogs by lazy { AlertDialogs(context.translation) }

    companion object {
        const val MAIN_ROUTE = "feature_root"
        const val FEATURE_CONTAINER_ROUTE = "feature_container/{name}"
        const val SEARCH_FEATURE_ROUTE = "search_feature/{keyword}"
    }


    private var activityLauncherHelper: ActivityLauncherHelper? = null
    private val featuresRouteName by lazy { context.translation["manager.routes.features"] }

    private lateinit var rememberScaffoldState: BottomSheetScaffoldState

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

    private fun navigateToMainRoot() {
        navController.navigate(MAIN_ROUTE, NavOptions.Builder()
            .setPopUpTo(navController.graph.findStartDestination().id, false)
            .setLaunchSingleTop(true)
            .build()
        )
    }

    override fun canGoBack() = sectionTopBarName() != featuresRouteName

    override fun sectionTopBarName(): String {
        navController.currentBackStackEntry?.arguments?.getString("name")?.let { routeName ->
            val currentContainerPair = allContainers[routeName]
            return context.translation["${currentContainerPair?.key?.propertyTranslationPath()}.name"]
        }
        return featuresRouteName
    }

    override fun init() {
        activityLauncherHelper = ActivityLauncherHelper(context.activity!!)
    }

    private fun activityLauncher(block: ActivityLauncherHelper.() -> Unit) {
        activityLauncherHelper?.let(block) ?: run {
            //open manager if activity launcher is null
            val intent = Intent(context.androidContext, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("route", enumSection.route)
            context.androidContext.startActivity(intent)
        }
    }

    override fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.navigation(route = enumSection.route, startDestination = MAIN_ROUTE) {
            composable(MAIN_ROUTE) {
                Container(context.config.root)
            }

            composable(FEATURE_CONTAINER_ROUTE, enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(100))
            }, exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            }) { backStackEntry ->
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
                                context.translation[it.key.propertyName()].contains(keyword, ignoreCase = true) ||
                                context.translation[it.key.propertyDescription()].contains(keyword, ignoreCase = true)
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
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                ),
                onDismissRequest = { showDialog = false },
            ) {
                dialogComposable()
            }
        }

        val propertyValue = property.value

        if (property.key.params.flags.contains(ConfigFlag.FOLDER)) {
            IconButton(onClick = registerClickCallback {
                activityLauncher {
                    chooseFolder { uri ->
                        propertyValue.setAny(uri)
                    }
                }
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

            DataProcessors.Type.MAP_COORDINATES -> {
                registerDialogOnClickCallback()
                dialogComposable = {
                    alertDialogs.ChooseLocationDialog(property) {
                        showDialog = false
                    }
                }

                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp),
                    text = (propertyValue.get() as Pair<*, *>).let {
                        "${it.first.toString().toFloatOrNull() ?: 0F}, ${it.second.toString().toFloatOrNull() ?: 0F}"
                    }
                )
            }

            DataProcessors.Type.STRING_UNIQUE_SELECTION -> {
                registerDialogOnClickCallback()

                dialogComposable = {
                    alertDialogs.UniqueSelectionDialog(property)
                }

                Text(
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.widthIn(0.dp, 120.dp),
                    text = (propertyValue.getNullable() as? String ?: "null").let {
                        property.key.propertyOption(context.translation, it)
                    }
                )
            }

            DataProcessors.Type.STRING_MULTIPLE_SELECTION, DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                dialogComposable = {
                    when (dataType) {
                        DataProcessors.Type.STRING_MULTIPLE_SELECTION -> {
                            alertDialogs.MultipleSelectionDialog(property)
                        }
                        DataProcessors.Type.STRING, DataProcessors.Type.INTEGER, DataProcessors.Type.FLOAT -> {
                            alertDialogs.KeyboardInputDialog(property) { showDialog = false }
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

                if (!container.hasGlobalState) return

                var state by remember { mutableStateOf(container.globalState ?: false) }

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
        val noticeColorMap = mapOf(
            FeatureNotice.UNSTABLE.key to Color(0xFFFFFB87),
            FeatureNotice.BAN_RISK.key to Color(0xFFFF8585),
            FeatureNotice.INTERNAL_BEHAVIOR.key to Color(0xFFFFFB87),
            FeatureNotice.REQUIRE_NATIVE_HOOKS.key to Color(0xFFFF5722),
        )

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
                        text = context.translation[property.key.propertyName()],
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = context.translation[property.key.propertyDescription()],
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    )
                    property.key.params.notices.also {
                        if (it.isNotEmpty()) Spacer(modifier = Modifier.height(5.dp))
                    }.forEach {
                        Text(
                            text = context.translation["features.notices.${it.key}"],
                            color = noticeColorMap[it.key] ?: Color(0xFFFFFB87),
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
                        navigateToMainRoot()
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
            if (!showSearchBar && currentRoute == SEARCH_FEATURE_ROUTE) {
                navigateToMainRoot()
            }
        }) {
            Icon(
                imageVector = if (showSearchBar) Icons.Filled.Close
                    else Icons.Filled.Search,
                contentDescription = null
            )
        }

        if (showSearchBar) return

        var showExportDropdownMenu by remember { mutableStateOf(false) }
        var showResetConfirmationDialog by remember { mutableStateOf(false) }

        if (showResetConfirmationDialog) {
            Dialog(onDismissRequest = { showResetConfirmationDialog = false }) {
                alertDialogs.ConfirmDialog(
                    title = "Reset config",
                    message = "Are you sure you want to reset the config?",
                    onConfirm = {
                        context.config.reset()
                        context.shortToast("Config successfully reset!")
                    },
                    onDismiss = { showResetConfirmationDialog = false }
                )
            }
        }

        val actions = remember {
            mapOf(
                "Export" to {
                    activityLauncher {
                        saveFile("config.json", "application/json") { uri ->
                            context.androidContext.contentResolver.openOutputStream(Uri.parse(uri))?.use {
                                context.config.writeConfig()
                                context.config.exportToString().byteInputStream().copyTo(it)
                                context.shortToast("Config exported successfully!")
                            }
                        }
                    }
                },
                "Import" to {
                    activityLauncher {
                        openFile("application/json") { uri ->
                            context.androidContext.contentResolver.openInputStream(Uri.parse(uri))?.use {
                                runCatching {
                                    context.config.loadFromString(it.readBytes().toString(Charsets.UTF_8))
                                }.onFailure {
                                    context.longToast("Failed to import config ${it.message}")
                                    return@use
                                }
                                context.shortToast("Config successfully loaded!")
                            }
                        }
                    }
                },
                "Reset" to { showResetConfirmationDialog = true }
            )
        }

        IconButton(onClick = { showExportDropdownMenu = !showExportDropdownMenu}) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null
            )
        }

        if (showExportDropdownMenu) {
            DropdownMenu(expanded = true, onDismissRequest = { showExportDropdownMenu = false }) {
                actions.forEach { (name, action) ->
                    DropdownMenuItem(
                        text = {
                            Text(text = name)
                        },
                        onClick = {
                            action()
                            showExportDropdownMenu = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun PropertiesView(
        properties: List<PropertyPair<*>>
    ) {
        rememberScaffoldState = rememberBottomSheetScaffoldState()
        Scaffold(
            snackbarHost = { SnackbarHost(rememberScaffoldState.snackbarHostState) },
            modifier = Modifier.fillMaxSize(),
            content = { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(innerPadding),
                    //save button space
                    contentPadding = PaddingValues(top = 10.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    items(properties) {
                        PropertyCard(it)
                    }
                }
            }
        )
    }

    @Composable
    override fun FloatingActionButton() {
        val scope = rememberCoroutineScope()
        FloatingActionButton(
            onClick = {
                context.config.writeConfig()
                scope.launch {
                    rememberScaffoldState.snackbarHostState.showSnackbar("Saved")
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