package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.core.messaging.SocialScope
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.BitmojiImage
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset
import me.rhunk.snapenhance.util.snap.BitmojiSelfie

class SocialSection : Section() {
    private lateinit var friendList: List<MessagingFriendInfo>
    private lateinit var groupList: List<MessagingGroupInfo>

    companion object {
        const val MAIN_ROUTE = "social_route"
        const val FRIEND_INFO_ROUTE = "friend_info/{id}"
        const val GROUP_INFO_ROUTE = "group_info/{id}"
    }

    private var currentScopeContent: ScopeContent? = null

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
        navGraphBuilder.navigation(route = enumSection.route, startDestination = MAIN_ROUTE) {
            composable(MAIN_ROUTE) {
                Content()
            }

            SocialScope.values().forEach { scope ->
                composable(scope.tabRoute) {
                    val id = it.arguments?.getString("id") ?: return@composable
                    remember {
                        ScopeContent(context, this@SocialSection, navController, scope, id).also { tab ->
                            currentScopeContent = tab
                        }
                    }.Content()
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
                        onConfirm = { scopeContent.deleteScope(coroutineScope); deleteConfirmDialog = false }
                    )
                }
            }
        }

        if (navController.currentBackStackEntry?.destination?.route != MAIN_ROUTE) {
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
                    //TODO: i18n
                    Text(text = "No ${scope.key.lowercase()}s found")
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
                    when (scope) {
                        SocialScope.GROUP -> {
                            val group = groupList[index]
                            Column {
                                Text(text = group.name, maxLines = 1)
                                Text(text = "participantsCount: ${group.participantsCount}", maxLines = 1)
                            }
                        }
                        SocialScope.FRIEND -> {
                            val friend = friendList[index]
                            val streaks = remember { context.modDatabase.getFriendStreaks(friend.userId) }

                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BitmojiImage(
                                    context = context,
                                    url = BitmojiSelfie.getBitmojiSelfie(friend.selfieId, friend.bitmojiId, BitmojiSelfie.BitmojiSelfieType.THREE_D)
                                )
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Text(text = friend.displayName ?: friend.mutableUsername, maxLines = 1, fontWeight = FontWeight.Bold)
                                    Text(text = friend.mutableUsername, maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.Light)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (streaks != null && streaks.notify) {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(id = R.drawable.streak_icon),
                                            contentDescription = null,
                                            modifier = Modifier.height(40.dp),
                                            tint = if (streaks.isAboutToExpire())
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                        )
                                        Text(text = "${streaks.hoursLeft()}h", maxLines = 1, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
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
                                    pagerState.animateScrollToPage( index )
                                }
                            },
                            text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                HorizontalPager(modifier = Modifier.padding(paddingValues), state = pagerState) { page ->
                    when (page) {
                        0 -> ScopeList(SocialScope.FRIEND)
                        1 -> ScopeList(SocialScope.GROUP)
                    }
                }
            }
        }
    }
}