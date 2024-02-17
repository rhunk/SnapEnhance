package me.rhunk.snapenhance.core.action.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ProgressBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.data.FriendLinkType
import me.rhunk.snapenhance.common.database.impl.FriendInfo
import me.rhunk.snapenhance.common.ui.createComposeAlertDialog
import me.rhunk.snapenhance.common.util.ktx.copyToClipboard
import me.rhunk.snapenhance.common.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.core.action.AbstractAction
import me.rhunk.snapenhance.core.features.impl.experiments.AddFriendSourceSpoof
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.util.EvictingMap
import me.rhunk.snapenhance.mapper.impl.FriendRelationshipChangerMapper
import java.net.URL
import java.text.DateFormat
import java.util.Date

class BulkMessagingAction : AbstractAction() {
    enum class SortBy {
        NONE,
        USERNAME,
        ADDED_TIMESTAMP,
        SNAP_SCORE,
        STREAK_LENGTH,
    }

    enum class Filter {
        ALL,
        MY_FRIENDS,
        BLOCKED,
        REMOVED_ME,
        DELETED,
        SUGGESTED,
        BUSINESS_ACCOUNTS,
    }

    private val translation by lazy { context.translation.getCategory("bulk_messaging_action") }

    private fun removeAction(ctx: Context, ids: List<String>, action: (String) -> Unit = {}): Job {
        var index = 0
        val dialog = ViewAppearanceHelper.newAlertDialogBuilder(ctx)
            .setTitle("...")
            .setView(ProgressBar(ctx))
            .setCancelable(false)
            .show()

        return context.coroutineScope.launch {
            ids.forEach { id ->
                runCatching {
                    action(id)
                }.onFailure {
                    context.log.error("Failed to process $it", it)
                    context.shortToast("Failed to process $id")
                }
                index++
                withContext(Dispatchers.Main) {
                    dialog.setTitle(
                        translation.format("progress_status", "index" to index.toString(), "total" to ids.size.toString())
                    )
                }
                delay(500)
            }
            withContext(Dispatchers.Main) {
                dialog.dismiss()
            }
        }
    }

    @Composable
    private fun ConfirmationDialog(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(text = translation["confirmation_dialog.title"]) },
            text = { Text(text = translation["confirmation_dialog.message"]) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = context.translation["button.positive"])
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text(text = context.translation["button.negative"])
                }
            }
        )
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BulkMessagingDialog() {
        var sortBy by remember { mutableStateOf(SortBy.USERNAME) }
        var filter by remember { mutableStateOf(Filter.REMOVED_ME) }
        var sortReverseOrder by remember { mutableStateOf(false) }
        val selectedFriends = remember { mutableStateListOf<String>() }
        val friends = remember { mutableStateListOf<FriendInfo>() }
        val bitmojiCache = remember { EvictingMap<String, Bitmap>(50) }
        val noBitmojiBitmap = remember { BitmapFactory.decodeResource(context.resources, android.R.drawable.ic_menu_report_image).asImageBitmap() }

        suspend fun refreshList() {
            withContext(Dispatchers.Main) {
                selectedFriends.clear()
                friends.clear()
            }
            withContext(Dispatchers.IO) {
                val userIdBlacklist = arrayOf(
                    context.database.myUserId,
                    "b42f1f70-5a8b-4c53-8c25-34e7ec9e6781", // myai
                    "84ee8839-3911-492d-8b94-72dd80f3713a", // teamsnapchat
                )
                val newFriends = context.database.getAllFriends().filter {
                    it.userId !in userIdBlacklist && when (filter) {
                        Filter.ALL -> true
                        Filter.MY_FRIENDS -> it.friendLinkType == FriendLinkType.MUTUAL.value && it.addedTimestamp > 0
                        Filter.BLOCKED -> it.friendLinkType == FriendLinkType.BLOCKED.value
                        Filter.REMOVED_ME -> it.friendLinkType == FriendLinkType.OUTGOING.value && it.addedTimestamp > 0 && it.businessCategory == 0 // ignore followed accounts
                        Filter.SUGGESTED -> it.friendLinkType == FriendLinkType.SUGGESTED.value
                        Filter.DELETED -> it.friendLinkType == FriendLinkType.DELETED.value
                        Filter.BUSINESS_ACCOUNTS -> it.businessCategory > 0
                    }
                }.toMutableList()
                when (sortBy) {
                    SortBy.NONE -> {}
                    SortBy.USERNAME -> newFriends.sortBy { it.mutableUsername }
                    SortBy.ADDED_TIMESTAMP -> newFriends.sortBy { it.addedTimestamp }
                    SortBy.SNAP_SCORE -> newFriends.sortBy { it.snapScore }
                    SortBy.STREAK_LENGTH -> newFriends.sortBy { it.streakLength }
                }
                if (sortReverseOrder) newFriends.reverse()
                withContext(Dispatchers.Main) {
                    friends.addAll(newFriends)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var filterMenuExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = filterMenuExpanded,
                    onExpandedChange = { filterMenuExpanded = it },
                ) {
                    ElevatedCard(
                        modifier = Modifier.menuAnchor()
                    ) {
                        Text(text = filter.name, modifier = Modifier.padding(5.dp))
                    }

                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        Filter.entries.forEach { entry ->
                            DropdownMenuItem(onClick = {
                                filter = entry
                                filterMenuExpanded = false
                            }, text = {
                                Text(text = entry.name, fontWeight = if (entry == filter) FontWeight.Bold else FontWeight.Normal)
                            })
                        }
                    }
                }

                var sortMenuExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = sortMenuExpanded,
                    onExpandedChange = { sortMenuExpanded = it },
                ) {
                    ElevatedCard(
                        modifier = Modifier.menuAnchor()
                    ) {
                        Text(text = "Sort by", modifier = Modifier.padding(5.dp))
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortBy.entries.forEach { entry ->
                            DropdownMenuItem(onClick = {
                                sortBy = entry
                                sortMenuExpanded = false
                            }, text = {
                                Text(text = entry.name, fontWeight = if (entry == sortBy) FontWeight.Bold else FontWeight.Normal)
                            })
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = sortReverseOrder,
                        onCheckedChange = { sortReverseOrder = it },
                    )
                    Text(text = "Reverse order", fontSize = 15.sp, fontWeight = FontWeight.Light)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    if (friends.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Selected " + selectedFriends.size + " friends", fontSize = 12.sp, fontWeight = FontWeight.Light)
                            Checkbox(
                                checked = selectedFriends.size == friends.size,
                                onCheckedChange = { state ->
                                    if (state) {
                                        friends.mapNotNull { it.userId }.forEach { userId ->
                                            if (!selectedFriends.contains(userId)) {
                                                selectedFriends.add(userId)
                                            }
                                        }
                                    } else selectedFriends.clear()
                                }
                            )
                        }
                    } else {
                        Text(text = "No friends found", fontSize = 12.sp, fontWeight = FontWeight.Light, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                items(friends) { friendInfo ->
                    var bitmojiBitmap by remember(friendInfo) { mutableStateOf(bitmojiCache[friendInfo.bitmojiAvatarId]) }

                    fun selectFriend(state: Boolean) {
                        friendInfo.userId?.let {
                            if (state) {
                                selectedFriends.add(it)
                            } else {
                                selectedFriends.remove(it)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectFriend(!selectedFriends.contains(friendInfo.userId))
                            }.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { context.androidContext.copyToClipboard(friendInfo.mutableUsername.toString()) }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LaunchedEffect(friendInfo) {
                            withContext(Dispatchers.IO) {
                                if (bitmojiBitmap != null || friendInfo.bitmojiAvatarId == null || friendInfo.bitmojiSelfieId == null) return@withContext

                                val bitmojiUrl = BitmojiSelfie.getBitmojiSelfie(friendInfo.bitmojiSelfieId, friendInfo.bitmojiAvatarId, BitmojiSelfie.BitmojiSelfieType.NEW_THREE_D) ?: return@withContext
                                runCatching {
                                    URL(bitmojiUrl).openStream().use { input ->
                                        bitmojiCache[friendInfo.bitmojiAvatarId ?: return@withContext] = BitmapFactory.decodeStream(input)
                                    }
                                    bitmojiBitmap = bitmojiCache[friendInfo.bitmojiAvatarId ?: return@withContext]
                                }.onFailure {
                                    context.log.error("Failed to load bitmoji", it)
                                }
                            }
                        }

                        Image(
                            bitmap = remember (bitmojiBitmap) { bitmojiBitmap?.asImageBitmap() ?: noBitmojiBitmap },
                            contentDescription = null,
                            modifier = Modifier.size(35.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ){
                                Text(text = (friendInfo.displayName ?: friendInfo.mutableUsername).toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, overflow = TextOverflow.Ellipsis, maxLines = 1)
                                Text(text = friendInfo.mutableUsername.toString(), fontSize = 10.sp, fontWeight = FontWeight.Light, overflow = TextOverflow.Ellipsis, maxLines = 1)
                            }
                            Text(text = "Relationship: " + remember(friendInfo) {
                                 context.translation["friendship_link_type.${FriendLinkType.fromValue(friendInfo.friendLinkType).shortName}"]
                            }, fontSize = 12.sp, fontWeight = FontWeight.Light)
                            remember(friendInfo) { friendInfo.addedTimestamp.takeIf { it > 0L }?.let {
                                DateFormat.getDateTimeInstance().format(Date(friendInfo.addedTimestamp))
                            } }?.let {
                                Text(text = "Added $it", fontSize = 12.sp, fontWeight = FontWeight.Light)
                            }
                            remember(friendInfo) { friendInfo.snapScore.takeIf { it > 0 } }?.let {
                                Text(text = "Snap Score: $it", fontSize = 12.sp, fontWeight = FontWeight.Light)
                            }
                            remember(friendInfo) { friendInfo.streakLength.takeIf { it > 0 } }?.let {
                                Text(text = "Streaks length: $it", fontSize = 12.sp, fontWeight = FontWeight.Light)
                            }
                        }

                        Checkbox(
                            checked = selectedFriends.contains(friendInfo.userId),
                            onCheckedChange = { selectFriend(it) }
                        )
                    }
                }
            }

            var showConfirmationDialog by remember { mutableStateOf(false) }
            var action by remember { mutableStateOf({}) }

            if (showConfirmationDialog) {
                ConfirmationDialog(
                    onConfirm = {
                        action()
                        action = {}
                        showConfirmationDialog = false
                    },
                    onCancel = {
                        action = {}
                        showConfirmationDialog = false
                    }
                )
            }

            val ctx = LocalContext.current

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    onClick = {
                        showConfirmationDialog = true
                        action = {
                            val messaging = context.feature(Messaging::class)
                            messaging.conversationManager?.apply {
                                getOneOnOneConversationIds(selectedFriends, onError = { error ->
                                    context.shortToast("Failed to fetch conversations: $error")
                                }, onSuccess = { conversations ->
                                    context.runOnUiThread {
                                        removeAction(ctx, conversations.map { it.second }.distinct()) {
                                            messaging.clearConversationFromFeed(it, onError = { error ->
                                                context.shortToast("Failed to clear conversation: $error")
                                            })
                                        }.invokeOnCompletion {
                                            context.coroutineScope.launch { refreshList() }
                                        }
                                    }
                                })
                                selectedFriends.clear()
                            }
                        }
                    },
                    enabled = selectedFriends.isNotEmpty()
                ) {
                    Text(text = "Clear " + selectedFriends.size + " conversations")
                }
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    onClick = {
                        showConfirmationDialog = true
                        action = {
                            removeAction(ctx, selectedFriends.also {
                                selectedFriends.clear()
                            }) { removeFriend(it) }.invokeOnCompletion {
                                context.coroutineScope.launch { refreshList() }
                            }
                        }
                    },
                    enabled = selectedFriends.isNotEmpty()
                ) {
                    Text(text = "Remove " + selectedFriends.size + " friends")
                }
            }
        }

        LaunchedEffect(filter, sortBy, sortReverseOrder) {
            refreshList()
        }
    }

    override fun run() {
        context.coroutineScope.launch(Dispatchers.Main) {
            createComposeAlertDialog(context.mainActivity!!) {
                BulkMessagingDialog()
            }.apply {
                setCanceledOnTouchOutside(false)
                show()
            }
        }
    }

    private fun removeFriend(userId: String) {
        context.mappings.useMapper(FriendRelationshipChangerMapper::class) {
            val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance!!
            val removeMethod = removeFriendClass.getAsClass()?.methods?.first {
                it.name == removeFriendMethod.getAsString()
            } ?: throw Exception("Failed to find removeFriend method")

            val completable = removeMethod.invoke(null,
                friendRelationshipChangerInstance,
                userId, // userId
                removeMethod.parameterTypes[2].enumConstants.first { it.toString() == "DELETED_BY_MY_FRIENDS" }, // source
                null, // InteractionPlacementInfo
                0
            )!!
            completable::class.java.methods.first {
                it.name == "subscribe" && it.parameterTypes.isEmpty()
            }.invoke(completable)
        }
    }
}