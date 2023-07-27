package me.rhunk.snapenhance.ui.menu.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.FriendActionButton
import me.rhunk.snapenhance.database.objects.ConversationMessage
import me.rhunk.snapenhance.database.objects.FriendInfo
import me.rhunk.snapenhance.database.objects.UserConversationLink
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.features.impl.downloader.AntiAutoDownload
import me.rhunk.snapenhance.features.impl.spying.StealthMode
import me.rhunk.snapenhance.features.impl.tweaks.AntiAutoSave
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.ui.menu.AbstractMenu
import me.rhunk.snapenhance.util.snap.BitmojiSelfie
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    private fun showProfileInfo(profile: FriendInfo) {
        var icon: Drawable? = null
        try {
            if (profile.bitmojiSelfieId != null && profile.bitmojiAvatarId != null) {
                icon = getImageDrawable(
                    BitmojiSelfie.getBitmojiSelfie(
                        profile.bitmojiSelfieId.toString(),
                        profile.bitmojiAvatarId.toString(),
                        BitmojiSelfie.BitmojiSelfieType.THREE_D
                    )
                )
            }
        } catch (e: Throwable) {
            Logger.xposedLog(e)
        }
        val finalIcon = icon
        context.runOnUiThread {
            val addedTimestamp: Long = profile.addedTimestamp.coerceAtLeast(profile.reverseAddedTimestamp)
            val builder = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
            builder.setIcon(finalIcon)
            builder.setTitle(profile.displayName ?: profile.username)

            val birthday = Calendar.getInstance()
            birthday[Calendar.MONTH] = (profile.birthday shr 32).toInt() - 1
            val message: String = """
                ${context.translation["profile_info.username"]}: ${profile.username}
                ${context.translation["profile_info.display_name"]}: ${profile.displayName}
                ${context.translation["profile_info.added_date"]}: ${formatDate(addedTimestamp)}
                ${birthday.getDisplayName(
                    Calendar.MONTH,
                    Calendar.LONG,
                    context.translation.locale
                )?.let {
                    context.translation.format("profile_info.birthday",
                        "month" to it,
                        "day" to profile.birthday.toInt().toString())
                }}
            """.trimIndent()
            builder.setMessage(message)
            builder.setPositiveButton(
                "OK"
            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            builder.show()
        }
    }

    private fun showPreview(userId: String?, conversationId: String, androidCtx: Context?) {
        //query message
        val messages: List<ConversationMessage>? = context.database.getMessagesFromConversationId(
            conversationId,
            context.config.int(ConfigProperty.MESSAGE_PREVIEW_LENGTH)
        )?.reversed()

        if (messages.isNullOrEmpty()) {
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

            var displayUsername = sender?.displayName ?: sender?.usernameForSorting?: context.translation["conversation_preview.unknown_user"]

            if (displayUsername.length > 12) {
                displayUsername = displayUsername.substring(0, 13) + "... "
            }

            messageBuilder.append(displayUsername).append(": ").append(messageString).append("\n")
        }

        val targetPerson: FriendInfo? =
            if (userId == null) null else participants[userId]

        targetPerson?.streakExpirationTimestamp?.takeIf { it > 0 }?.let {
            val timeSecondDiff = ((it - System.currentTimeMillis()) / 1000 / 60).toInt()
            messageBuilder.append("\n")
                .append("\uD83D\uDD25 ") //fire emoji
                .append(
                    context.translation.format("conversation_preview.streak_expiration",
                    "day" to (timeSecondDiff / 60 / 24).toString(),
                    "hour" to (timeSecondDiff / 60 % 24).toString(),
                    "minute" to (timeSecondDiff % 60).toString()
                ))
        }

        messages.lastOrNull()?.let {
            messageBuilder
                .append("\n\n")
                .append(context.translation.format("conversation_preview.total_messages", "count" to it.server_message_id.toString()))
                .append("\n")
        }


        //alert dialog
        val builder = ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity)
        builder.setTitle(context.translation["conversation_preview.title"])
        builder.setMessage(messageBuilder.toString())
        builder.setPositiveButton(
            "OK"
        ) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        targetPerson?.let {
            builder.setNegativeButton(context.translation["modal_option.profile_info"]) { _, _ ->
                context.executeAsync {
                    showProfileInfo(it)
                }
            }
        }
        builder.show()
    }

    private fun createEmojiDrawable(text: String, width: Int, height: Int, textSize: Float, disabled: Boolean = false): Drawable {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.textSize = textSize
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, width / 2f, height.toFloat() - paint.descent(), paint)
        if (disabled) {
            paint.color = Color.RED
            paint.strokeWidth = 5f
            canvas.drawLine(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun getCurrentConversationInfo(): Pair<String, String?> {
        val messaging = context.feature(Messaging::class)
        val focusedConversationTargetUser: String? = messaging.lastFetchConversationUserUUID?.toString()

        //mapped conversation fetch (may not work with legacy sc versions)
        messaging.lastFetchGroupConversationUUID?.let {
            context.database.getFriendFeedInfoByConversationId(it.toString())?.let { friendFeedInfo ->
                val participantSize = friendFeedInfo.participantsSize
                return it.toString() to if (participantSize == 1) focusedConversationTargetUser else null
            }
            throw IllegalStateException("No conversation found")
        }

        //old conversation fetch
        val conversationId = if (messaging.lastFetchConversationUUID == null && focusedConversationTargetUser != null) {
            val conversation: UserConversationLink = context.database.getDMConversationIdFromUserId(focusedConversationTargetUser) ?: throw IllegalStateException("No conversation found")
            conversation.client_conversation_id!!.trim().lowercase()
        } else {
            messaging.lastFetchConversationUUID.toString()
        }

        return conversationId to focusedConversationTargetUser
    }

    private fun createToggleFeature(viewConsumer: ((View) -> Unit), text: String, isChecked: () -> Boolean, toggle: (Boolean) -> Unit) {
        val switch = Switch(context.androidContext)
        switch.text = context.translation[text]
        switch.isChecked = isChecked()
        ViewAppearanceHelper.applyTheme(switch)
        switch.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            toggle(checked)
        }
        viewConsumer(switch)
    }

    @SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode", "DefaultLocale", "InflateParams",
        "DiscouragedApi", "ClickableViewAccessibility"
    )
    fun inject(viewModel: View, viewConsumer: ((View) -> Unit)) {
        val modContext = context

        val friendFeedMenuOptions = context.config.options(ConfigProperty.FRIEND_FEED_MENU_BUTTONS)
        if (friendFeedMenuOptions.none { it.value }) return

        val (conversationId, targetUser) = getCurrentConversationInfo()

        if (!context.config.bool(ConfigProperty.ENABLE_FRIEND_FEED_MENU_BAR)) {
            //preview button
            val previewButton = Button(viewModel.context).apply {
                text = modContext.translation["friend_menu_option.preview"]
                ViewAppearanceHelper.applyTheme(this, viewModel.width)
                setOnClickListener {
                    showPreview(
                        targetUser,
                        conversationId,
                        context
                    )
                }
            }

            //stealth switch
            val stealthSwitch = Switch(viewModel.context).apply {
                text = modContext.translation["friend_menu_option.stealth_mode"]
                isChecked = modContext.feature(StealthMode::class).isStealth(conversationId)
                ViewAppearanceHelper.applyTheme(this)
                setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                    modContext.feature(StealthMode::class).setStealth(
                        conversationId,
                        isChecked
                    )
                }
            }

            if (friendFeedMenuOptions["anti_auto_save"] == true) {
                createToggleFeature(viewConsumer,
                    "friend_menu_option.anti_auto_save",
                    { context.feature(AntiAutoSave::class).isConversationIgnored(conversationId) },
                    { context.feature(AntiAutoSave::class).setConversationIgnored(conversationId, it) }
                )
            }

            run {
                val userId = context.database.getFriendFeedInfoByConversationId(conversationId)?.friendUserId ?: return@run
                if (friendFeedMenuOptions["auto_download_blacklist"] == true) {
                    createToggleFeature(viewConsumer,
                        "friend_menu_option.auto_download_blacklist",
                        { context.feature(AntiAutoDownload::class).isUserIgnored(userId) },
                        { context.feature(AntiAutoDownload::class).setUserIgnored(userId, it) }
                    )
                }
            }

            if (friendFeedMenuOptions["stealth_mode"] == true) {
                viewConsumer(stealthSwitch)
            }
            if (friendFeedMenuOptions["conversation_info"] == true) {
                viewConsumer(previewButton)
            }
            return
        }

        val menuButtonBar = LinearLayout(viewModel.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun createActionButton(icon: String, isDisabled: Boolean? = null, onClick: (Boolean) -> Unit) {
            //FIXME: hardcoded values
            menuButtonBar.addView(LinearLayout(viewModel.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                isClickable = false

                var isLineThrough = isDisabled ?: false
                FriendActionButton.new(viewModel.context).apply {
                    fun setLineThrough(value: Boolean) {
                        setIconDrawable(createEmojiDrawable(icon, 60, 60, 50f, if (isDisabled == null) false else value))
                    }
                    setLineThrough(isLineThrough)
                    (instanceNonNull() as View).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 40, 0, 40)
                        }
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                isLineThrough = !isLineThrough
                                onClick(isLineThrough)
                                setLineThrough(isLineThrough)
                            }
                            false
                        }
                    }

                }.also { addView(it.instanceNonNull() as View) }

            })
        }

        if (friendFeedMenuOptions["auto_download_blacklist"] == true) {
            run {
                val userId =
                    context.database.getFriendFeedInfoByConversationId(conversationId)?.friendUserId
                        ?: return@run
                createActionButton(
                    "\u2B07\uFE0F",
                    isDisabled = !context.feature(AntiAutoDownload::class).isUserIgnored(userId)
                ) {
                    context.feature(AntiAutoDownload::class).setUserIgnored(userId, !it)
                }
            }
        }

        if (friendFeedMenuOptions["anti_auto_save"] == true) {
            //diskette
            createActionButton("\uD83D\uDCAC",
                isDisabled = !context.feature(AntiAutoSave::class)
                    .isConversationIgnored(conversationId)
            ) {
                context.feature(AntiAutoSave::class).setConversationIgnored(conversationId, !it)
            }
        }


        if (friendFeedMenuOptions["stealth_mode"] == true) {
            //eyes
            createActionButton(
                "\uD83D\uDC7B",
                isDisabled = !context.feature(StealthMode::class).isStealth(conversationId)
            ) { isChecked ->
                context.feature(StealthMode::class).setStealth(
                    conversationId,
                    !isChecked
                )
            }
        }

        if (friendFeedMenuOptions["conversation_info"] == true) {
            //user
            createActionButton("\uD83D\uDC64") {
                showPreview(
                    targetUser,
                    conversationId,
                    viewModel.context
                )
            }
        }

        viewConsumer(menuButtonBar)
    }
}