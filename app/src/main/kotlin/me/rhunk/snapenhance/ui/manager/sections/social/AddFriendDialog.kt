package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.bridge.BridgeClient
import me.rhunk.snapenhance.core.messaging.MessagingFriendInfo
import me.rhunk.snapenhance.core.messaging.MessagingGroupInfo
import me.rhunk.snapenhance.util.snap.SnapWidgetBroadcastReceiverHelper

class AddFriendDialog(
    private val context: RemoteSideContext,
    private val section: SocialSection,
) {


    @Composable
    private fun ListCardEntry(name: String, modifier: Modifier = Modifier) {
        Card(
            modifier = Modifier.padding(5.dp).then(modifier),
        ) {
            Text(text = name, modifier = Modifier.padding(10.dp))
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
            SnapWidgetBroadcastReceiverHelper.create(BridgeClient.BRIDGE_SYNC_ACTION) {}.also {
                runCatching {
                    context.androidContext.sendBroadcast(it)
                }.onFailure {
                    Logger.error("Failed to send broadcast", it)
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

        Dialog(onDismissRequest = {
            timeoutJob?.cancel()
            dismiss()
        }) {
            if (hasFetchError) {
                Text(text = "Failed to load friends and groups. Make sure Snapchat is installed and logged in.")
                return@Dialog
            }
            if (cachedGroups == null || cachedFriends == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding()
                        .size(30.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                return@Dialog
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(text = "Groups", fontSize = 20.sp)
                    Spacer(modifier = Modifier.padding(5.dp))
                }
                items(cachedGroups!!.size) {
                    ListCardEntry(name = cachedGroups!![it].name, modifier = Modifier.clickable {
                        context.bridgeService.triggerGroupSync(cachedGroups!![it].conversationId)
                        context.modDatabase.executeAsync {
                            section.onResumed()
                        }
                    })
                }
                item {
                    Text(text = "Friends", fontSize = 20.sp)
                    Spacer(modifier = Modifier.padding(5.dp))
                }
                items(cachedFriends!!.size) {
                    ListCardEntry(name = cachedFriends!![it].displayName ?: cachedFriends!![it].mutableUsername, modifier = Modifier.clickable {
                        context.bridgeService.triggerFriendSync(cachedFriends!![it].userId)
                        context.modDatabase.executeAsync {
                            section.onResumed()
                        }
                    })
                }
            }
        }
    }
}