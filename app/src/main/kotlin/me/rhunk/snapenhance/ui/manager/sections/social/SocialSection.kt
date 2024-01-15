package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.BitmojiImage
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset

class SocialSection : Section() {
    private lateinit var friendList: List<MessagingFriendInfo>
    private lateinit var groupList: List<MessagingGroupInfo>

    companion object {
        const val MAIN_ROUTE = "social_route"
        const val MESSAGING_PREVIEW_ROUTE = "messaging_preview/?id={id}&scope={scope}"
        const val LOGGED_STORIES_ROUTE = "logged_stories/?userId={userId}"
    }

    private var currentScopeContent: ScopeContent? = null
    private var currentMessagingPreview by mutableStateOf(null as MessagingPreview?)

    private val addFriendDialog by lazy {
        AddFriendDialog(context, this)
    }

    //FIXME: don't reload the entire list when a friend is added/deleted
    override fun onResumed() {
        friendList = context.modDatabase.getFriends(descOrder = true)
        groupList = context.modDatabase.getGroups()
    }

    override fun canGoBack() = currentRoute != MAIN_ROUTE

    override fun build(navGraphBuilder: NavGraphBuilder) {
        navGraphBuilder.navigation(route = enumSection.route, startDestination = MAIN_ROUTE) {
            composable(MAIN_ROUTE) {
                Content()
            }

            SocialScope.entries.forEach { scope ->
                composable(scope.tabRoute) {
                    val id = it.arguments?.getString("id") ?: return@composable
                    remember {
                        ScopeContent(
                            context,
                            this@SocialSection,
                            navController,
                            scope,
                            id
                        ).also { tab ->
                            currentScopeContent = tab
                        }
                    }.Content()
                }
            }

            composable(LOGGED_STORIES_ROUTE) {
                val userId = it.arguments?.getString("userId") ?: return@composable
                LoggedStories(context, userId)
            }

            composable(MESSAGING_PREVIEW_ROUTE) { navBackStackEntry ->
                val id = navBackStackEntry.arguments?.getString("id") ?: return@composable
                val scope = navBackStackEntry.arguments?.getString("scope") ?: return@composable
                val messagePreview = remember {
                    MessagingPreview(context, SocialScope.getByName(scope), id)
                }
                LaunchedEffect(key1 = id) {
                    currentMessagingPreview = messagePreview
                }
                messagePreview.Content()
                DisposableEffect(Unit) {
                    onDispose {
                        currentMessagingPreview = null
                    }
                }
            }
        }
    }

    @Composable
    override fun TopBarActions(rowScope: RowScope) {
        var deleteConfirmDialog by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        if (deleteConfirmDialog) {
            currentScopeContent?.let { scopeContent ->
                Dialog(onDismissRequest = { deleteConfirmDialog = false }) {
                    remember { AlertDialogs(context.translation) }.ConfirmDialog(
                        title = "Are you sure you want to delete this ${scopeContent.scope.key.lowercase()}?",
                        onDismiss = { deleteConfirmDialog = false },
                        onConfirm = {
                            scopeContent.deleteScope(coroutineScope); deleteConfirmDialog = false
                        }
                    )
                }
            }
        }

        if (currentRoute == MESSAGING_PREVIEW_ROUTE) {
            currentMessagingPreview?.TopBarAction()
        }

        if (currentRoute == SocialScope.FRIEND.tabRoute || currentRoute == SocialScope.GROUP.tabRoute) {
            IconButton(
                onClick = { deleteConfirmDialog = true },
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null
                )
            }
        }
    }


    @Composable
    private fun ScopeList(scope: SocialScope) {
        val remainingHours = remember { context.config.root.streaksReminder.remainingHours.get() }

        LazyColumn(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            //check if scope list is empty
            val listSize = when (scope) {
                SocialScope.GROUP -> groupList.size
                SocialScope.FRIEND -> friendList.size
            }

            if (listSize == 0) {
                item {
                    Text(
                        text = "(empty)", modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp), textAlign = TextAlign.Center
                    )
                }
            }

            items(listSize) { index ->
                val id = when (scope) {
                    SocialScope.GROUP -> groupList[index].conversationId
                    SocialScope.FRIEND -> friendList[index].userId
                }

                Card(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth()
                        .height(80.dp)
                        .clickable {
                            navController.navigate(
                                scope.tabRoute.replace("{id}", id)
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (scope) {
                            SocialScope.GROUP -> {
                                val group = groupList[index]
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = group.name,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            SocialScope.FRIEND -> {
                                val friend = friendList[index]
                                var streaks by remember { mutableStateOf(friend.streaks) }

                                LaunchedEffect(friend.userId) {
                                    withContext(Dispatchers.IO) {
                                        streaks = context.modDatabase.getFriendStreaks(friend.userId)
                                    }
                                }

                                BitmojiImage(
                                    context = context,
                                    url = BitmojiSelfie.getBitmojiSelfie(
                                        friend.selfieId,
                                        friend.bitmojiId,
                                        BitmojiSelfie.BitmojiSelfieType.THREE_D
                                    )
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = friend.displayName ?: friend.mutableUsername,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = friend.mutableUsername,
                                        maxLines = 1,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    streaks?.takeIf { it.notify }?.let { streaks ->
                                        Icon(
                                            imageVector = ImageVector.vectorResource(id = R.drawable.streak_icon),
                                            contentDescription = null,
                                            modifier = Modifier.height(40.dp),
                                            tint = if (streaks.isAboutToExpire(remainingHours))
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = context.translation.format(
                                                "manager.sections.social.streaks_expiration_short",
                                                "hours" to (((streaks.expirationTimestamp - System.currentTimeMillis()) / 3600000).toInt().takeIf { it > 0 } ?: 0)
                                                    .toString()
                                            ),
                                            maxLines = 1,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        FilledIconButton(onClick = {
                            navController.navigate(
                                MESSAGING_PREVIEW_ROUTE.replace("{id}", id).replace("{scope}", scope.key)
                            )
                        }) {
                            Icon(imageVector = Icons.Filled.RemoveRedEye, contentDescription = null)
                        }
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val titles = listOf("Friends", "Groups")
        val coroutineScope = rememberCoroutineScope()
        val pagerState = rememberPagerState { titles.size }
        var showAddFriendDialog by remember { mutableStateOf(false) }

        if (showAddFriendDialog) {
            addFriendDialog.Content {
                showAddFriendDialog = false
            }
        }

        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        showAddFriendDialog = true
                    },
                    modifier = Modifier.padding(10.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(selectedTabIndex = pagerState.currentPage, indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.pagerTabIndicatorOffset(
                            pagerState = pagerState,
                            tabPositions = tabPositions
                        )
                    )
                }) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                HorizontalPager(
                    modifier = Modifier.padding(paddingValues),
                    state = pagerState
                ) { page ->
                    when (page) {
                        0 -> ScopeList(SocialScope.FRIEND)
                        1 -> ScopeList(SocialScope.GROUP)
                    }
                }
            }
        }
    }
}