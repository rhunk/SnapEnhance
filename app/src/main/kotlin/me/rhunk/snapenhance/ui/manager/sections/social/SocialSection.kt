package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset

class SocialSection : Section() {
    private lateinit var friendList: List<MessagingFriendInfo>
    private lateinit var groupList: List<MessagingGroupInfo>

    companion object {
        const val MAIN_ROUTE = "social_route"
        const val FRIEND_INFO_ROUTE = "friend_info/{id}"
        const val GROUP_INFO_ROUTE = "group_info/{id}"
    }

    private var currentScopeTab: ScopeTab? = null

    private val addFriendDialog by lazy {
        AddFriendDialog(context, this)
    }

    //FIXME: don't reload the entire list when a friend is added/deleted
    override fun onResumed() {
        friendList = context.modDatabase.getFriends(descOrder = true)
        groupList = context.modDatabase.getGroups()
    }

    override fun canGoBack() = navController.currentBackStackEntry?.destination?.route != MAIN_ROUTE

    override fun build(navGraphBuilder: NavGraphBuilder) {
        fun switchTab(id: String) = ScopeTab(context, this, navController, id).also { tab ->
            currentScopeTab = tab
        }

        navGraphBuilder.navigation(route = enumSection.route, startDestination = MAIN_ROUTE) {
            composable(MAIN_ROUTE) {
                Content()
            }

            composable(FRIEND_INFO_ROUTE) {
                val id = it.arguments?.getString("id") ?: return@composable
                remember { switchTab(id) }.Friend()
            }

            composable(GROUP_INFO_ROUTE) {
                val id = it.arguments?.getString("id") ?: return@composable
                remember { switchTab(id) }.Group()
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
                                    pagerState.animateScrollToPage( index )
                                }
                            },
                            text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }


                HorizontalPager(modifier = Modifier.padding(paddingValues), state = pagerState) { page ->
                    when (page) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxWidth()
                            ) {
                                if (friendList.isEmpty()) {
                                    item {
                                        Text(text = "No friends found")
                                    }
                                }
                                items(friendList.size) { index ->
                                    val friend = friendList[index]
                                    Card(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clickable {
                                                navController.navigate(
                                                    FRIEND_INFO_ROUTE.replace(
                                                        "{id}",
                                                        friend.userId
                                                    )
                                                )
                                            },
                                    ) {
                                        Text(text = friend.displayName ?: friend.mutableUsername)
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxSize()
                                    .scrollable(rememberScrollState(), Orientation.Vertical)
                            ) {
                                groupList.forEach {
                                    Card(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .fillMaxWidth()
                                            .height(100.dp),
                                    ) {
                                        Text(text = it.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}