package me.rhunk.snapenhance.core.action.impl

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.rhunk.snapenhance.common.action.EnumAction
import me.rhunk.snapenhance.common.data.FriendLinkType
import me.rhunk.snapenhance.common.ui.createComposeAlertDialog
import me.rhunk.snapenhance.core.action.AbstractAction
import me.rhunk.snapenhance.core.event.events.impl.ActivityResultEvent
import me.rhunk.snapenhance.core.features.impl.experiments.AddFriendSourceSpoof
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.wrapper.impl.Snapchatter
import me.rhunk.snapenhance.mapper.impl.FriendRelationshipChangerMapper
import kotlin.random.Random

class ManageFriendList : AbstractAction() {
    private var pendingPickerAction: Pair<Int, (data: Uri) -> Unit>? = null

    private val uuidRegex by lazy {
        Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }

    private fun addFriend(userId: String) {
        val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance
        context.mappings.useMapper(FriendRelationshipChangerMapper::class) {
            val addFriend = friendshipRelationshipChangerKtx.get()?.methods?.firstOrNull { it.name == addFriendMethod.get() }
                ?: return@useMapper

            addFriend.invoke(
                null,
                friendRelationshipChangerInstance,
                userId,
                addFriend.parameterTypes[2].enumConstants.first { it.toString() == "ADDED_BY_USERNAME" },
                addFriend.parameterTypes[3].enumConstants.first { it.toString() == "SEARCH" },
                addFriend.parameterTypes[4].enumConstants.first { it.toString() == "SEARCH" },
                0
            )
        }
    }

    override fun onActivityCreate() {
        context.runOnUiThread {
            context.actionManager.execute(EnumAction.MANAGE_FRIEND_LIST)
        }

        context.event.subscribe(ActivityResultEvent::class) { event ->
            if (event.requestCode == pendingPickerAction?.first) {
                val pendingAction = pendingPickerAction ?: return@subscribe
                this.pendingPickerAction = null
                event.canceled = true
                pendingAction.second(event.intent.data!!)
            }
        }
    }

    private fun exportFriends(
        userIds: List<String>
    ) {
        pendingPickerAction = Random.nextInt(0, 65535) to { data ->
            context.androidContext.contentResolver.openOutputStream(data).use { output ->
                output?.bufferedWriter()?.use { writer ->
                    userIds.forEach {
                        writer.write(it)
                        writer.newLine()
                    }
                }
                context.longToast("Exported ${userIds.size} friends!")
            }
        }
        context.mainActivity?.startActivityForResult(
            Intent.createChooser(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "my_friends.txt")
                },
                "Select a location to save the file"
            ),
            pendingPickerAction!!.first
        )
    }

    private val userIdToSnapchatter = mutableMapOf<String, Snapchatter>()

    @Composable
    private fun ManagerDialog() {
        val pendingFriendRequests = remember { mutableStateMapOf<String, Job>() }
        var fetchedFriends by remember { mutableStateOf<List<String>?>(null) } // list of uuids
        val coroutineScope = rememberCoroutineScope()

        if (fetchedFriends == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Manage Friend List", fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Export friends allows you to save a list of your friends' IDs in a text file. Importing from a file will display the friends in a list where you can add them.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = {
                            exportFriends(context.database.getAllFriends().filter { it.friendLinkType == FriendLinkType.MUTUAL.value && it.addedTimestamp > 0L }.mapNotNull { it.userId })
                        }) {
                            Text("Export friends")
                        }
                        Button(onClick = {
                            pendingPickerAction = Random.nextInt(0, 65535) to { data ->
                                runCatching {
                                    fetchedFriends = null
                                    context.androidContext.contentResolver.openInputStream(data).use { input ->
                                        fetchedFriends = input?.bufferedReader()?.readLines()?.filter {
                                            it.matches(uuidRegex)
                                        }?.map { it.trim() }?.toMutableList() ?: mutableListOf()
                                    }
                                }.onFailure {
                                    context.log.error("Failed to import friends", it)
                                    context.longToast("Failed to import friends: ${it.message}")
                                }
                            }
                            // launch file picker
                            context.mainActivity?.startActivityForResult(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" },
                                    "Select a file"
                                ),
                                pendingPickerAction!!.first
                            )
                        }) {
                            Text("Import from file")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        modifier = Modifier.padding(8.dp),
                        onClick = {
                            fetchedFriends = null
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(8.dp)
                ) {
                    item {
                        if (fetchedFriends?.isEmpty() == true) {
                            Text("No friends found", modifier = Modifier.padding(8.dp))
                        }
                    }
                    items(fetchedFriends ?: emptyList()) { userId ->
                        fun fetchLocalLinkType(): FriendLinkType? {
                            return context.database.getFriendInfo(userId)?.friendLinkType?.let { FriendLinkType.fromValue(it) }
                        }

                        var friendSnapchatter by remember(userId) { mutableStateOf<Snapchatter?>(null) }
                        var failedToFetch by remember(userId) { mutableStateOf(false) }
                        var friendLinkType by remember(userId) { mutableStateOf(fetchLocalLinkType()) }

                        LaunchedEffect(userId) {
                            launch(Dispatchers.IO) {
                                friendSnapchatter = userIdToSnapchatter.getOrPut(userId) {
                                    context.feature(Messaging::class).fetchSnapchatterInfos(listOf(userId)).firstOrNull() ?: run {
                                        failedToFetch = true
                                        return@launch
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                friendSnapchatter?.let { snapchatter ->
                                    Text(snapchatter.displayName?.let { "$it (${snapchatter.username}) " } ?: snapchatter.username ?: "Unknown")
                                }
                                Text(userId, fontSize = 12.sp, fontWeight = FontWeight.Light)
                            }

                            if (friendSnapchatter != null && friendLinkType != FriendLinkType.FOLLOWING) {
                                Button(
                                    enabled = friendLinkType != FriendLinkType.MUTUAL,
                                    onClick = {
                                        val prevLinkType = fetchLocalLinkType()
                                        if (prevLinkType == FriendLinkType.MUTUAL || pendingFriendRequests[userId]?.isActive == true) return@Button
                                        addFriend(userId)
                                        pendingFriendRequests[userId] = coroutineScope.launch {
                                            withTimeout(10000) {
                                                while (fetchLocalLinkType()?.value == prevLinkType?.value) {
                                                    delay(500)
                                                }
                                            }
                                        }.apply {
                                            invokeOnCompletion {
                                                pendingFriendRequests.remove(userId)
                                                friendLinkType = fetchLocalLinkType()
                                            }
                                        }
                                    }
                                ) {
                                    if (friendLinkType == FriendLinkType.MUTUAL) {
                                        Text("Added")
                                    } else if (pendingFriendRequests[userId]?.isActive == true) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 1.dp)
                                    } else {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            createComposeAlertDialog(context.mainActivity!!) {
                ManagerDialog()
            }.apply {
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }
}