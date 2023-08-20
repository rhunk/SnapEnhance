package me.rhunk.snapenhance.ui.manager.sections.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.core.messaging.SocialScope

class ScopeContent(
    private val context: RemoteSideContext,
    private val section: SocialSection,
    private val navController: NavController,
    private val scope: SocialScope,
    private val id: String
) {

    @Composable
    private fun DeleteScopeEntityButton() {
        val coroutineScope = rememberCoroutineScope()
        OutlinedButton(onClick = {
            when (scope) {
                SocialScope.FRIEND -> context.modDatabase.deleteFriend(id)
                SocialScope.GROUP -> context.modDatabase.deleteGroup(id)
            }
            context.modDatabase.executeAsync {
                coroutineScope.launch {
                    section.onResumed()
                    navController.popBackStack()
                }
            }
        }) {
            Text(text = "Delete ${scope.key}")
        }
    }

    @Composable
    fun Content() {
        Column {
            when (scope) {
                SocialScope.FRIEND -> Friend()
                SocialScope.GROUP -> Group()
            }

            Spacer(modifier = Modifier.height(16.dp))

            val scopeRules = context.modDatabase.getRulesFromId(scope, id)

            Text(text = "Rules", maxLines = 1)
            Spacer(modifier = Modifier.height(16.dp))

            //manager anti features etc
            MessagingRuleType.values().forEach { feature ->
                var featureEnabled by remember {
                    mutableStateOf(scopeRules.any { it.subject == feature.key })
                }
                val featureEnabledText = if (featureEnabled) "Enabled" else "Disabled"
                Row {
                    Text(text = "${feature.key}: $featureEnabledText", maxLines = 1)
                    Switch(checked = featureEnabled, onCheckedChange = {
                        context.modDatabase.toggleRuleFor(scope, id, feature.key, it)
                        featureEnabled = it
                    })
                }
            }
        }
    }

    @Composable
    private fun Friend() {
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
            DeleteScopeEntityButton()
        }

    }

    @Composable
    private fun Group() {
        //fetch the group from the database
        val group = remember { context.modDatabase.getGroupInfo(id) } ?: run {
            Text(text = "Group not found")
            return
        }

        Column {
            Text(text = group.name, maxLines = 1)
            Text(text = "participantsCount: ${group.participantsCount}", maxLines = 1)
            Spacer(modifier = Modifier.height(16.dp))
            DeleteScopeEntityButton()
        }
    }
}