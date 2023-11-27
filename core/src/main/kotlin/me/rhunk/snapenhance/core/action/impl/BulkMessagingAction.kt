package me.rhunk.snapenhance.core.action.impl

import android.widget.ProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.data.FriendLinkType
import me.rhunk.snapenhance.core.action.AbstractAction
import me.rhunk.snapenhance.core.features.impl.experiments.AddFriendSourceSpoof
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.messaging.EnumBulkAction
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper

class BulkMessagingAction : AbstractAction() {
    private val translation by lazy { context.translation.getCategory("bulk_messaging_action") }

    private fun removeAction(ids: List<String>, action: (String) -> Unit = {}) {
        var index = 0
        val dialog = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle("...")
            .setView(ProgressBar(context.mainActivity))
            .setCancelable(false)
            .show()

        context.coroutineScope.launch {
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

    private suspend fun askActionType() = suspendCancellableCoroutine { cont ->
        context.runOnUiThread {
            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(translation["choose_action_title"])
                .setItems(EnumBulkAction.entries.map { translation["actions.${it.key}"] }.toTypedArray()) { _, which ->
                    cont.resumeWith(Result.success(EnumBulkAction.entries[which]))
                }
                .setOnCancelListener {
                    cont.resumeWith(Result.success(null))
                }
                .show()
        }
    }

    private fun confirmationDialog(onConfirm: () -> Unit) {
        ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle(translation["confirmation_dialog.title"])
            .setMessage(translation["confirmation_dialog.message"])
            .setPositiveButton(context.translation["button.positive"]) { _, _ ->
                onConfirm()
            }
            .setNegativeButton(context.translation["button.negative"]) { _, _ -> }
            .show()
    }

    override fun run() {
        val userIdBlacklist = arrayOf(
            context.database.myUserId,
            "b42f1f70-5a8b-4c53-8c25-34e7ec9e6781", // myai
            "84ee8839-3911-492d-8b94-72dd80f3713a", // teamsnapchat
        )

        context.coroutineScope.launch(Dispatchers.Main) {
            val bulkAction = askActionType() ?: return@launch

            val friends = context.database.getAllFriends().filter {
                it.userId !in userIdBlacklist &&
                it.addedTimestamp != -1L &&
                it.friendLinkType == FriendLinkType.MUTUAL.value ||
                it.friendLinkType == FriendLinkType.OUTGOING.value
            }.sortedByDescending {
                it.friendLinkType == FriendLinkType.OUTGOING.value
            }

            val selectedFriends = mutableListOf<String>()

            ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
                .setTitle(translation["actions.${bulkAction.key}"])
                .setMultiChoiceItems(friends.map { friend ->
                    (friend.displayName?.let {
                        "$it (${friend.mutableUsername})"
                    } ?: friend.mutableUsername) +
                    ": ${context.translation["friendship_link_type.${FriendLinkType.fromValue(friend.friendLinkType).shortName}"]}"
                }.toTypedArray(), null) { _, which, isChecked ->
                    if (isChecked) {
                        selectedFriends.add(friends[which].userId!!)
                    } else {
                        selectedFriends.remove(friends[which].userId)
                    }
                }
                .setPositiveButton(translation["selection_dialog_continue_button"]) { _, _ ->
                    confirmationDialog {
                        when (bulkAction) {
                            EnumBulkAction.REMOVE_FRIENDS -> {
                                removeAction(selectedFriends) {
                                    removeFriend(it)
                                }
                            }
                            EnumBulkAction.CLEAR_CONVERSATIONS -> clearConversations(selectedFriends)
                        }
                    }
                }
                .setNegativeButton(context.translation["button.cancel"]) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun clearConversations(friendIds: List<String>) {
        val messaging = context.feature(Messaging::class)

        messaging.conversationManager?.apply {
            getOneOnOneConversationIds(friendIds, onError = { error ->
                context.shortToast("Failed to fetch conversations: $error")
            }, onSuccess = { conversations ->
                context.runOnUiThread {
                    removeAction(conversations.map { it.second }.distinct()) {
                        messaging.clearConversationFromFeed(it, onError = { error ->
                            context.shortToast("Failed to clear conversation: $error")
                        })
                    }
                }
            })
        }
    }

    private fun removeFriend(userId: String) {
        val friendRelationshipChangerMapping = context.mappings.getMappedMap("FriendRelationshipChanger")
        val friendRelationshipChangerInstance = context.feature(AddFriendSourceSpoof::class).friendRelationshipChangerInstance!!

        val removeFriendMethod = friendRelationshipChangerInstance::class.java.methods.first {
            it.name == friendRelationshipChangerMapping["removeFriendMethod"].toString()
        }

        val completable = removeFriendMethod.invoke(friendRelationshipChangerInstance,
            userId, // userId
            removeFriendMethod.parameterTypes[1].enumConstants.first { it.toString() == "DELETED_BY_MY_FRIENDS" }, // source
            null, // unknown
            null, // unknown
            null // InteractionPlacementInfo
        )!!
        completable::class.java.methods.first {
            it.name == "subscribe" && it.parameterTypes.isEmpty()
        }.invoke(completable)
    }
}