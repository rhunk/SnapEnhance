package me.rhunk.snapenhance.ui.manager.sections.social

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rhunk.snapenhance.RemoteSideContext
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.core.messaging.SocialScope
import me.rhunk.snapenhance.ui.util.BitmojiImage
import me.rhunk.snapenhance.core.util.snap.BitmojiSelfie
import me.rhunk.snapenhance.ui.util.AlertDialogs
import me.rhunk.snapenhance.ui.util.Dialog
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ScopeContent(
    private val context: RemoteSideContext,
    private val section: SocialSection,
    private val navController: NavController,
    val scope: SocialScope,
    private val id: String
) {
    private val dialogs by lazy { AlertDialogs(context.translation) }
    private val translation by lazy { context.translation.getCategory("manager.sections.social") }

    fun deleteScope(coroutineScope: CoroutineScope) {
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
    }

    @Composable
    fun Content() {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            when (scope) {
                SocialScope.FRIEND -> Friend()
                SocialScope.GROUP -> Group()
            }

            Spacer(modifier = Modifier.height(16.dp))

            val rules = context.modDatabase.getRules(id)

            SectionTitle(translation["rules_title"])

            ContentCard {
                //manager anti features etc
                MessagingRuleType.values().forEach { ruleType ->
                    var ruleEnabled by remember {
                        mutableStateOf(rules.any { it.key == ruleType.key })
                    }

                    val ruleState = context.config.root.rules.getRuleState(ruleType)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(all = 4.dp)
                    ) {
                        Text(
                            text = if (ruleType.listMode && ruleState != null) {
                                context.translation["rules.properties.${ruleType.key}.options.${ruleState.key}"]
                            } else context.translation["rules.properties.${ruleType.key}.name"],
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = ruleEnabled,
                            enabled = if (ruleType.listMode) ruleState != null else true,
                            onCheckedChange = {
                                context.modDatabase.setRule(id, ruleType.key, it)
                                ruleEnabled = it
                            })
                    }
                }
            }
        }
    }

    @Composable
    private fun ContentCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        Card(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .then(modifier)
            ) {
                content()
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            maxLines = 1,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .offset(x = 20.dp)
                .padding(bottom = 10.dp)
        )
    }

    //need to display all units?
    private fun computeStreakETA(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val stringBuilder = StringBuilder()
        val diff = timestamp - now
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        if (days > 0) {
            stringBuilder.append("$days days ")
            return stringBuilder.toString()
        }
        if (hours > 0) {
            stringBuilder.append("$hours hours ")
            return stringBuilder.toString()
        }
        if (minutes > 0) {
            stringBuilder.append("$minutes minutes ")
            return stringBuilder.toString()
        }
        if (seconds > 0) {
            stringBuilder.append("$seconds seconds ")
            return stringBuilder.toString()
        }
        return "Expired"
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Composable
    private fun Friend() {
        //fetch the friend from the database
        val friend = remember { context.modDatabase.getFriendInfo(id) } ?: run {
            Text(text = translation["not_found"])
            return
        }

        val streaks = remember {
            context.modDatabase.getFriendStreaks(id)
        }

        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bitmojiUrl = BitmojiSelfie.getBitmojiSelfie(
                friend.selfieId, friend.bitmojiId, BitmojiSelfie.BitmojiSelfieType.THREE_D
            )
            BitmojiImage(context = context, url = bitmojiUrl, size = 100)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = friend.displayName ?: friend.mutableUsername,
                maxLines = 1,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = friend.mutableUsername,
                maxLines = 1,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Column {
            //streaks
            streaks?.let {
                var shouldNotify by remember { mutableStateOf(it.notify) }
                SectionTitle(translation["streaks_title"])
                ContentCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = translation.format(
                                    "streaks_length_text", "length" to streaks.length.toString()
                                ), maxLines = 1
                            )
                            Text(
                                text = translation.format(
                                    "streaks_expiration_text",
                                    "eta" to computeStreakETA(streaks.expirationTimestamp)
                                ), maxLines = 1
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translation["reminder_button"],
                                maxLines = 1,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Switch(checked = shouldNotify, onCheckedChange = {
                                context.modDatabase.setFriendStreaksNotify(id, it)
                                shouldNotify = it
                            })
                        }
                    }
                }
            }
            // e2ee section

            SectionTitle(translation["e2ee_title"])
            var hasSecretKey by remember { mutableStateOf(context.e2eeImplementation.friendKeyExists(friend.userId))}
            var importDialog by remember { mutableStateOf(false) }

            if (importDialog) {
                Dialog(
                    onDismissRequest = { importDialog = false }
                ) {
                    dialogs.RawInputDialog(onDismiss = { importDialog = false  }, onConfirm = { newKey ->
                        importDialog = false
                        runCatching {
                            val key = Base64.decode(newKey)
                            if (key.size != 32) {
                                context.longToast("Invalid key size (must be 32 bytes)")
                                return@runCatching
                            }

                            context.e2eeImplementation.storeSharedSecretKey(friend.userId, key)
                            context.longToast("Successfully imported key")
                            hasSecretKey = true
                        }.onFailure {
                            context.longToast("Failed to import key: ${it.message}")
                            context.log.error("Failed to import key", it)
                        }
                    })
                }
            }

            ContentCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasSecretKey) {
                        OutlinedButton(onClick = {
                            val secretKey = Base64.encode(context.e2eeImplementation.getSharedSecretKey(friend.userId) ?: return@OutlinedButton)
                            //TODO: fingerprint auth
                            context.activity!!.startActivity(Intent.createChooser(Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, secretKey)
                                type = "text/plain"
                            }, "").apply {
                                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(
                                    Intent().apply {
                                        putExtra(Intent.EXTRA_TEXT, secretKey)
                                        putExtra(Intent.EXTRA_SUBJECT, secretKey)
                                    })
                                )
                            })
                        }) {
                            Text(
                                text = "Export Base64",
                                maxLines = 1
                            )
                        }
                    }

                    OutlinedButton(onClick = { importDialog = true }) {
                        Text(
                            text = "Import Base64",
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Group() {
        //fetch the group from the database
        val group = remember { context.modDatabase.getGroupInfo(id) } ?: run {
            Text(text = translation["not_found"])
            return
        }


        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = group.name, maxLines = 1, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = translation.format(
                    "participants_text", "count" to group.participantsCount.toString()
                ), maxLines = 1, fontSize = 12.sp, fontWeight = FontWeight.Light
            )
        }
    }
}