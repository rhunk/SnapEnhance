package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import me.rhunk.snapenhance.RemoteSideContext

class ScopeTab(
    private val context: RemoteSideContext,
    private val section: SocialSection,
    private val navController: NavController,
    private val id: String
) {
    @Composable
    fun Friend() {
        //fetch the friend from the database
        val friend = remember { context.modDatabase.getFriendInfo(id) } ?: run {
            Text(text = "Friend not found")
            return
        }
        Column {
            Text(text = friend.displayName ?: "No display name", maxLines = 1)
            Text(text = "bitmojiId: ${friend.bitmojiId ?: "No bitmojiId"}", maxLines = 1)
            Text(text = "selfieId: ${friend.selfieId ?: "No selfieId"}", maxLines = 1)

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = {
                context.modDatabase.deleteFriend(id)
                section.onResumed()
                navController.popBackStack()
            }) {
                Text(text = "Delete friend")
            }
        }

    }


    @Composable
    fun Group() {
        //fetch the group from the database
        val group = remember { context.modDatabase.getGroupInfo(id) } ?: run {
            Text(text = "Group not found")
            return
        }

        Column {
            Text(text = group.name, maxLines = 1)
            Text(text = "participantsCount: ${group.participantsCount}", maxLines = 1)

            Spacer(modifier = Modifier.height(16.dp))


            OutlinedButton(onClick = {
                context.modDatabase.deleteGroup(id)
                section.onResumed()
                navController.popBackStack()
            }) {
                Text(text = "Delete group")
            }
        }

    }
}