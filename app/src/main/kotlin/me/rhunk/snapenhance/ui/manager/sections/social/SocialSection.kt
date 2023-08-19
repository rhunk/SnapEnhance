package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.ui.manager.Section
import me.rhunk.snapenhance.ui.util.pagerTabIndicatorOffset

class SocialSection : Section() {
    private lateinit var friendList: List<MessagingFriendInfo>
    private lateinit var groupList: List<MessagingGroupInfo>

    private val addFriendDialog by lazy {
        AddFriendDialog(context, this)
    }

    override fun onResumed() {
        friendList = context.modDatabase.getFriends()
        groupList = context.modDatabase.getGroups()
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (page) {
                            0 -> {
                                Text(text = "Friends")
                                Column {
                                    friendList.forEach {
                                        Text(text = it.displayName ?: it.mutableUsername)
                                    }
                                }
                            }
                            1 -> {
                                Text(text = "Groups")
                                Column {
                                    groupList.forEach {
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