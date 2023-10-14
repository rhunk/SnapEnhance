package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.common.ReceiversConfig
import me.rhunk.snapenhance.common.data.MessagingFriendInfo
import me.rhunk.snapenhance.common.data.MessagingGroupInfo
import me.rhunk.snapenhance.common.data.SocialScope
import me.rhunk.snapenhance.common.util.snap.SnapWidgetBroadcastReceiverHelper

class AddFriendDialog(
    private val context: RemoteSideContext,
    private val section: SocialSection,
) {

    private val translation by lazy { context.translation.getCategory("manager.dialogs.add_friend")}

    @Composable
    private fun ListCardEntry(name: String, getCurrentState: () -> Boolean, onState: (Boolean) -> Unit = {}) {
        var currentState by remember { mutableStateOf(getCurrentState()) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    currentState = !currentState
                    onState(currentState)
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 15.sp,
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned {
                        currentState = getCurrentState()
                    }
            )

            Checkbox(
                checked = currentState,
                onCheckedChange = {
                    currentState = it
                    onState(currentState)
                }
            )
        }
    }

    @Composable
    private fun DialogHeader(searchKeyword: MutableState<String>) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Text(
                text = translation["title"],
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(alignment = Alignment.CenterHorizontally)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchKeyword.value,
                onValueChange = { searchKeyword.value = it },
                label = {
                    Text(text = translation["search_hint"])
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            )
        }
    }


    @Composable
    fun Content(dismiss: () -> Unit = { }) {
        var cachedFriends by remember { mutableStateOf(null as List<MessagingFriendInfo>?) }
        var cachedGroups by remember { mutableStateOf(null as List<MessagingGroupInfo>?) }

        val coroutineScope = rememberCoroutineScope()

        var timeoutJob: Job? = null
        var hasFetchError by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            context.modDatabase.receiveMessagingDataCallback = { friends, groups ->
                cachedFriends = friends
                cachedGroups = groups
                timeoutJob?.cancel()
                hasFetchError = false
            }
            SnapWidgetBroadcastReceiverHelper.create(ReceiversConfig.BRIDGE_SYNC_ACTION) {}.also {
                runCatching {
                    context.androidContext.sendBroadcast(it)
                }.onFailure {
                    context.log.error("Failed to send broadcast", it)
                    hasFetchError = true
                }
            }
            timeoutJob = coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    delay(10000)
                    hasFetchError = true
                }
            }
        }

        Dialog(
            onDismissRequest = {
                timeoutJob?.cancel()
                dismiss()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                colors = CardDefaults.elevatedCardColors(),
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .padding(all = 20.dp)
            ) {
                if (cachedGroups == null || cachedFriends == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (hasFetchError) {
                            Text(
                                text = translation["fetch_error"],
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp, top = 10.dp)
                            )
                            return@Card
                        }
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding()
                                .size(30.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    return@Card
                }

                val searchKeyword = remember { mutableStateOf("") }

                val filteredGroups = cachedGroups!!.takeIf { searchKeyword.value.isNotBlank() }?.filter {
                    it.name.contains(searchKeyword.value, ignoreCase = true)
                } ?: cachedGroups!!

                val filteredFriends = cachedFriends!!.takeIf { searchKeyword.value.isNotBlank() }?.filter {
                    it.mutableUsername.contains(searchKeyword.value, ignoreCase = true) ||
                    it.displayName?.contains(searchKeyword.value, ignoreCase = true) == true
                } ?: cachedFriends!!

                DialogHeader(searchKeyword)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    item {
                        if (filteredGroups.isEmpty()) return@item
                        Text(text = translation["category_groups"],
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp, top = 10.dp)
                        )
                    }

                    items(filteredGroups.size) {
                        val group = filteredGroups[it]
                        ListCardEntry(
                            name = group.name,
                            getCurrentState = { context.modDatabase.getGroupInfo(group.conversationId) != null }
                        ) { state ->
                            if (state) {
                                context.bridgeService?.triggerScopeSync(SocialScope.GROUP, group.conversationId)
                            } else {
                                context.modDatabase.deleteGroup(group.conversationId)
                            }
                            context.modDatabase.executeAsync {
                                section.onResumed()
                            }
                        }
                    }

                    item {
                        if (filteredFriends.isEmpty()) return@item
                        Text(text = translation["category_friends"],
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 10.dp, top = 10.dp)
                        )
                    }

                    items(filteredFriends.size) {
                        val friend = filteredFriends[it]

                        ListCardEntry(
                            name = friend.displayName?.takeIf { name -> name.isNotBlank() } ?: friend.mutableUsername,
                            getCurrentState = { context.modDatabase.getFriendInfo(friend.userId) != null }
                        ) { state ->
                            if (state) {
                                context.bridgeService?.triggerScopeSync(SocialScope.FRIEND, friend.userId)
                            } else {
                                context.modDatabase.deleteFriend(friend.userId)
                            }
                            context.modDatabase.executeAsync {
                                section.onResumed()
                            }
                        }
                    }
                }
            }
        }
    }
}