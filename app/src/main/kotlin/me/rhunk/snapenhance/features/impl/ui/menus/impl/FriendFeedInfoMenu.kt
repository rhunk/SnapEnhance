package me.rhunk.snapenhance.features.impl.ui.menus.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.database.objects.ConversationMessage
import me.rhunk.snapenhance.database.objects.FriendInfo
import me.rhunk.snapenhance.database.objects.UserConversationLink
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.AntiAutoDownload
import me.rhunk.snapenhance.features.impl.spy.StealthMode
import me.rhunk.snapenhance.features.impl.ui.menus.AbstractMenu
import me.rhunk.snapenhance.features.impl.ui.menus.ViewAppearanceHelper.applyTheme
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class FriendFeedInfoMenu : AbstractMenu() {
    private fun getImageDrawable(url: String): Drawable {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connect()
        val input = connection.inputStream
        return BitmapDrawable(Resources.getSystem(), BitmapFactory.decodeStream(input))
    }

    private fun formatDate(timestamp: Long): String? {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
    }

    fun showProfileInfo(profile: FriendInfo) {
        var icon: Drawable? = null
        try {
            if (profile.bitmojiSelfieId != null && profile.bitmojiAvatarId != null) {
                icon = getImageDrawable(
                    "https://sdk.bitmoji.com/render/panel/" + profile.bitmojiSelfieId
                        .toString() + "-" + profile.bitmojiAvatarId
                        .toString() + "-v1.webp?transparent=1&scale=0"
                )
            }
        } catch (e: Throwable) {
            Logger.xposedLog(e)
        }
        val finalIcon = icon
        context.runOnUiThread {
            val addedTimestamp: Long = profile.addedTimestamp.coerceAtLeast(profile.reverseAddedTimestamp)
            val builder = AlertDialog.Builder(context.mainActivity)
            builder.setIcon(finalIcon)
            builder.setTitle(profile.displayName)

            val birthday = Calendar.getInstance()
            birthday[Calendar.MONTH] = (profile.birthday shr 32).toInt() - 1
            val message: String = """
                ${context.translation.get("profile_info.username")}: ${profile.username}
                ${context.translation.get("profile_info.display_name")}: ${profile.displayName}
                ${context.translation.get("profile_info.added_date")}: ${formatDate(addedTimestamp)}
                ${birthday.getDisplayName(
                    Calendar.MONTH,
                    Calendar.LONG,
                    context.translation.locale
                )?.let {
                    context.translation.get("profile_info.birthday")
                        .replace("{month}", it)
                        .replace("{day}", profile.birthday.toInt().toString())
                }
            }
            """.trimIndent()
            builder.setMessage(message)
            builder.setPositiveButton(
                "OK"
            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            builder.show()
        }
    }

    fun showPreview(userId: String?, conversationId: String, androidCtx: Context?) {
        //query message
        val messages: List<ConversationMessage>? = context.database.getMessagesFromConversationId(
            conversationId,
            context.config.int(ConfigProperty.MESSAGE_PREVIEW_LENGTH)
        )?.reversed()

        if (messages == null || messages.isEmpty()) {
            Toast.makeText(androidCtx, "No messages found", Toast.LENGTH_SHORT).show()
            return
        }
        val participants: Map<String, FriendInfo> = context.database.getConversationParticipants(conversationId)!!
            .map { context.database.getFriendInfo(it)!! }
            .associateBy { it.userId!! }
        
        val messageBuilder = StringBuilder()

        messages.forEach{ message: ConversationMessage ->
            val sender: FriendInfo? = participants[message.sender_id]

            var messageString: String = message.getMessageAsString() ?: ContentType.fromId(message.content_type).name

            if (message.content_type == ContentType.SNAP.id) {
                val readTimeStamp: Long = message.read_timestamp
                messageString = "\uD83D\uDFE5" //red square
                if (readTimeStamp > 0) {
                    messageString += " \uD83D\uDC40 " //eyes
                    messageString += DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT
                    ).format(Date(readTimeStamp))
                }
            }

            var displayUsername = sender?.displayName ?: sender?.usernameForSorting?: context.translation.get("conversation_preview.unknown_user")

            if (displayUsername.length > 12) {
                displayUsername = displayUsername.substring(0, 13) + "... "
            }

            messageBuilder.append(displayUsername).append(": ").append(messageString).append("\n")
        }

        val targetPerson: FriendInfo? =
            if (userId == null) null else participants[userId]

        targetPerson?.let {
            val timeSecondDiff = ((it.streakExpirationTimestamp - System.currentTimeMillis()) / 1000 / 60).toInt()
            messageBuilder.append("\n\n")
                .append("\uD83D\uDD25 ") //fire emoji
                .append(context.translation.get("conversation_preview.streak_expiration").format(
                    timeSecondDiff / 60 / 24,
                    timeSecondDiff / 60 % 24,
                    timeSecondDiff % 60
                ))
        }

        //alert dialog
        val builder = AlertDialog.Builder(context.mainActivity)
        builder.setTitle(context.translation.get("conversation_preview.title"))
        builder.setMessage(messageBuilder.toString())
        builder.setPositiveButton(
            "OK"
        ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        targetPerson?.let {
            builder.setNegativeButton(context.translation.get("modal_option.profile_info")) {_, _ ->
                context.executeAsync {
                    showProfileInfo(it)
                }
            }
        }
        builder.show()
    }

    @SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode", "DefaultLocale")
    fun inject(viewModel: View, viewConsumer: ((View) -> Unit)) {
        val messaging = context.feature(Messaging::class)
        var focusedConversationTargetUser: String? = null
        val conversationId: String
        if (messaging.lastFetchConversationUserUUID != null) {
            focusedConversationTargetUser = messaging.lastFetchConversationUserUUID.toString()
            val conversation: UserConversationLink = context.database.getDMConversationIdFromUserId(focusedConversationTargetUser) ?: return
            conversationId = conversation.client_conversation_id!!.trim().lowercase()
        } else {
            conversationId = messaging.lastFetchConversationUUID.toString()
        }

        //preview button
        val previewButton = Button(viewModel.context)
        previewButton.text = context.translation.get("friend_menu_option.preview")
        applyTheme(viewModel, previewButton)
        val finalFocusedConversationTargetUser = focusedConversationTargetUser
        previewButton.setOnClickListener {
            showPreview(
                finalFocusedConversationTargetUser,
                conversationId,
                previewButton.context
            )
        }

        //stealth switch
        val stealthSwitch = Switch(viewModel.context)
        stealthSwitch.text = context.translation.get("friend_menu_option.stealth_mode")
        stealthSwitch.isChecked = context.feature(StealthMode::class).isStealth(conversationId)
        applyTheme(viewModel, stealthSwitch)
        stealthSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            context.feature(StealthMode::class).setStealth(
                conversationId,
                isChecked
            )
        }

        if (context.config.bool(ConfigProperty.ANTI_DOWNLOAD_BUTTON)) {
            val userId = context.database.getFriendFeedInfoByConversationId(conversationId)?.friendUserId ?: return

            val antiAutoDownload = Switch(viewModel.context)
            antiAutoDownload.text = context.translation.get("friend_menu_option.anti_auto_download")
            antiAutoDownload.isChecked = context.feature(AntiAutoDownload::class).isUserIgnored(userId)
            applyTheme(viewModel, antiAutoDownload)
            antiAutoDownload.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                context.feature(AntiAutoDownload::class).setUserIgnored(
                    userId,
                    isChecked
                )
            }
            viewConsumer(antiAutoDownload)
        }
        viewConsumer(stealthSwitch)
        viewConsumer(previewButton)
    }
}