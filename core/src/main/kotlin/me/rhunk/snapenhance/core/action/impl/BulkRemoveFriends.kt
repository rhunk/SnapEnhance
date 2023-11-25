package me.rhunk.snapenhance.core.action.impl

import android.widget.ProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.common.data.FriendLinkType
import me.rhunk.snapenhance.core.action.AbstractAction
import me.rhunk.snapenhance.core.features.impl.experiments.AddFriendSourceSpoof
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper

class BulkRemoveFriends : AbstractAction() {
    private val translation by lazy { context.translation.getCategory("bulk_remove_friends") }

    private fun removeFriends(friendIds: List<String>) {
        var index = 0
        val dialog = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            .setTitle("...")
            .setView(ProgressBar(context.mainActivity))
            .setCancelable(false)
            .show()

        context.coroutineScope.launch {
            friendIds.forEach {
                removeFriend(it)
                index++
                withContext(Dispatchers.Main) {
                    dialog.setTitle(
                        translation.format("progress_status", "index" to index.toString(), "total" to friendIds.size.toString())
                    )
                }
                delay(500)
            }
            withContext(Dispatchers.Main) {
                dialog.dismiss()
            }
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
                .setTitle(translation["selection_dialog_title"])
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
                .setPositiveButton(translation["selection_dialog_remove_button"]) { _, _ ->
                    confirmationDialog {
                        removeFriends(selectedFriends)
                    }
                }
                .setNegativeButton(context.translation["button.cancel"]) { _, _ -> }
                .show()
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