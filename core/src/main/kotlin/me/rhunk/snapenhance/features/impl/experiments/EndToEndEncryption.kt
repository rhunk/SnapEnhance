package me.rhunk.snapenhance.features.impl.experiments

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.event.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.core.messaging.RuleState
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.MessageContent
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.MessagingRuleFeature
import me.rhunk.snapenhance.features.impl.Messaging
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor
import me.rhunk.snapenhance.ui.ViewAppearanceHelper
import java.security.MessageDigest
import kotlin.random.Random

class EndToEndEncryption : MessagingRuleFeature(
    "EndToEndEncryption",
    MessagingRuleType.E2E_ENCRYPTION,
    loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC or FeatureLoadParams.INIT_SYNC or FeatureLoadParams.INIT_ASYNC
) {
    private val isEnabled get() = context.config.experimental.useE2EEncryption.get()
    private val e2eeInterface by lazy { context.bridgeClient.getE2eeInterface() }

    companion object {
        const val REQUEST_PK_MESSAGE_ID = 1
        const val RESPONSE_SK_MESSAGE_ID = 2
        const val ENCRYPTED_MESSAGE_ID = 3
    }

    private val pkRequests = mutableMapOf<Long, ByteArray>()
    private val secretResponses = mutableMapOf<Long, ByteArray>()
    private val encryptedMessages = mutableListOf<Long>()

    private fun getE2EParticipants(conversationId: String): List<String> {
        return context.database.getConversationParticipants(conversationId)?.filter { friendId -> e2eeInterface.friendKeyExists(friendId) } ?: emptyList()
    }

    private fun askForKeys(conversationId: String) {
        val friendId = context.database.getDMOtherParticipant(conversationId) ?: run {
            context.longToast("Can't find friendId for conversationId $conversationId")
            return
        }

        val publicKey = e2eeInterface.createKeyExchange(friendId) ?: run {
            context.longToast("Can't create key exchange for friendId $friendId")
            return
        }

        context.log.verbose("created publicKey: ${publicKey.contentToString()}")

        sendCustomMessage(conversationId, REQUEST_PK_MESSAGE_ID) {
            addBuffer(2, publicKey)
        }
    }

    private fun sendCustomMessage(conversationId: String, messageId: Int, message: ProtoWriter.() -> Unit) {
        context.messageSender.sendCustomChatMessage(
            listOf(SnapUUID.fromString(conversationId)),
            ContentType.CHAT,
            message = {
                from(2) {
                    from(1) {
                        addVarInt(1, messageId)
                        addBuffer(2, ProtoWriter().apply(message).toByteArray())
                    }
                }
            }
        )
    }

    private fun warnKeyOverwrite(friendId: String, block: () -> Unit) {
        if (!e2eeInterface.friendKeyExists(friendId)) {
            block()
            return
        }

        context.mainActivity?.runOnUiThread {
            val mainActivity = context.mainActivity ?: return@runOnUiThread
            ViewAppearanceHelper.newAlertDialogBuilder(mainActivity).apply {
                setTitle("End-to-end encryption")
                setMessage("WARNING: This will overwrite your existing key. You will loose access to all encrypted messages from this friend. Are you sure you want to continue?")
                setPositiveButton("Yes") { _, _ ->
                    ViewAppearanceHelper.newAlertDialogBuilder(mainActivity).apply {
                        setTitle("End-to-end encryption")
                        setMessage("Are you REALLY sure you want to continue? This is your last chance to back out.")
                        setNeutralButton("Yes") { _, _ -> block() }
                        setPositiveButton("No") { _, _ -> }
                    }.show()
                }
                setNegativeButton("No") { _, _ -> }
            }.show()
        }
    }

    private fun handlePublicKeyRequest(conversationId: String, publicKey: ByteArray) {
        val friendId = context.database.getDMOtherParticipant(conversationId) ?: run {
            context.longToast("Can't find friendId for conversationId $conversationId")
            return
        }
        warnKeyOverwrite(friendId) {
            val encapsulatedSecret = e2eeInterface.acceptPairingRequest(friendId, publicKey)
            if (encapsulatedSecret == null) {
                context.longToast("Failed to accept public key")
                return@warnKeyOverwrite
            }
            context.longToast("Public key successfully accepted")

            sendCustomMessage(conversationId, RESPONSE_SK_MESSAGE_ID) {
                addBuffer(2, encapsulatedSecret)
            }
        }
    }

    private fun handleSecretResponse(conversationId: String, secret: ByteArray) {
        val friendId = context.database.getDMOtherParticipant(conversationId) ?: run {
            context.longToast("Can't find friendId for conversationId $conversationId")
            return
        }
        warnKeyOverwrite(friendId) {
            context.log.verbose("handleSecretResponse, secret = $secret")
            val result = e2eeInterface.acceptPairingResponse(friendId, secret)
            if (!result) {
                context.longToast("Failed to accept secret")
                return@warnKeyOverwrite
            }
            context.longToast("Done! You can now exchange encrypted messages with this friend.")
        }
    }

    private fun openManagementPopup() {
        val conversationId = context.feature(Messaging::class).openedConversationUUID?.toString() ?: return

        if (context.database.getDMOtherParticipant(conversationId) == null) {
            context.shortToast("This menu is only available in direct messages.")
            return
        }

        val actions = listOf(
            "Initiate a new shared secret"
        )

        ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!).apply {
            setTitle("End-to-end encryption")
            setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> askForKeys(conversationId)
                }
            }
            setPositiveButton("OK") { _, _ -> }
        }.show()
    }

    @SuppressLint("SetTextI18n", "DiscouragedApi")
    override fun onActivityCreate() {
        if (!isEnabled) return
        // add button to input bar
        context.event.subscribe(AddViewEvent::class) { param ->
            if (param.view.toString().contains("default_input_bar")) {
                (param.view as ViewGroup).addView(TextView(param.view.context).apply {
                    layoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
                    setOnClickListener { openManagementPopup() }
                    setPadding(20, 20, 20, 20)
                    textSize = 23f
                    text = "\uD83D\uDD12"
                })
            }
        }

        val encryptedMessageIndicator by context.config.experimental.encryptedMessageIndicator
        val chatMessageContentContainerId = context.resources.getIdentifier("chat_message_content_container", "id", context.androidContext.packageName)

        // hook view binder to add special buttons
        val receivePublicKeyTag = Random.nextLong().toString(16)
        val receiveSecretTag = Random.nextLong().toString(16)
        val encryptedMessageTag = Random.nextLong().toString(16)

        context.event.subscribe(BindViewEvent::class) { event ->
            event.chatMessage { conversationId, messageId ->
                val viewGroup = event.view as ViewGroup

                viewGroup.findViewWithTag<View>(receiveSecretTag)?.also {
                    viewGroup.removeView(it)
                }

                viewGroup.findViewWithTag<View>(receivePublicKeyTag)?.also {
                    viewGroup.removeView(it)
                }

                if (encryptedMessageIndicator) {
                    viewGroup.findViewWithTag<ViewGroup>(encryptedMessageTag)?.also {
                        val chatMessageContentContainer = viewGroup.findViewById<View>(chatMessageContentContainerId) as? LinearLayout ?: return@chatMessage
                        it.removeView(chatMessageContentContainer)
                        viewGroup.removeView(it)
                        viewGroup.addView(chatMessageContentContainer, 0)
                    }

                    if (encryptedMessages.contains(messageId.toLong())) {
                        val chatMessageContentContainer = viewGroup.findViewById<View>(chatMessageContentContainerId) as? LinearLayout ?: return@chatMessage
                        viewGroup.removeView(chatMessageContentContainer)

                        viewGroup.addView(RelativeLayout(viewGroup.context).apply {
                            tag = encryptedMessageTag
                            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                            addView(chatMessageContentContainer)
                            addView(TextView(viewGroup.context).apply {
                                text = "\uD83D\uDD12"
                                textAlignment = View.TEXT_ALIGNMENT_TEXT_END
                                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                                setPadding(20, 0, 20, 0)
                            })
                        }, 0)
                    }
                }

                secretResponses[messageId.toLong()]?.also { secret ->
                    viewGroup.addView(Button(context.mainActivity!!).apply {
                        text = "Accept secret"
                        tag = receiveSecretTag
                        setOnClickListener {
                            handleSecretResponse(conversationId, secret)
                        }
                    })
                }

                pkRequests[messageId.toLong()]?.also { publicKey ->
                    viewGroup.addView(Button(context.mainActivity!!).apply {
                        text = "Receive public key"
                        tag = receivePublicKeyTag
                        setOnClickListener {
                            handlePublicKeyRequest(conversationId, publicKey)
                        }
                    })
                }
            }
        }
    }

    private fun fixContentType(contentType: ContentType, message: ProtoReader): ContentType {
        return when {
            contentType == ContentType.EXTERNAL_MEDIA && message.containsPath(11) -> {
                ContentType.SNAP
            }
            contentType == ContentType.SHARE && message.containsPath(2) -> {
                ContentType.CHAT
            }
            else -> contentType
        }
    }

    private fun hashParticipantId(participantId: String, salt: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").apply {
            update(participantId.toByteArray())
            update(salt)
        }.digest()
    }

    private fun messageHook(conversationId: String, messageId: Long, senderId: String, messageContent: MessageContent) {
        val reader = ProtoReader(messageContent.content)

        fun replaceMessageText(text: String) {
            messageContent.content = ProtoWriter().apply {
                from(2) {
                    addString(1, text)
                }
            }.toByteArray()
        }

        // decrypt messages
        reader.followPath(2, 1) {
            val messageTypeId = getVarInt(1)?.toInt() ?: return@followPath
            val isMe = context.database.myUserId == senderId
            val conversationParticipants by lazy {
                getE2EParticipants(conversationId)
            }

            if (messageTypeId == ENCRYPTED_MESSAGE_ID) {
                runCatching {
                    replaceMessageText("Cannot find a key to decrypt this message.")
                    eachBuffer(2) {
                        val participantIdHash = getByteArray(1) ?: return@eachBuffer
                        val iv = getByteArray(2) ?: return@eachBuffer
                        val ciphertext = getByteArray(3) ?: return@eachBuffer

                        if (isMe) {
                            if (conversationParticipants.isEmpty()) return@eachBuffer
                            val participantId = conversationParticipants.firstOrNull { participantIdHash.contentEquals(hashParticipantId(it, iv)) } ?: return@eachBuffer
                            messageContent.content = e2eeInterface.decryptMessage(participantId, ciphertext, iv)
                            encryptedMessages.add(messageId)
                            return@eachBuffer
                        }

                        if (!participantIdHash.contentEquals(hashParticipantId(context.database.myUserId, iv))) return@eachBuffer

                        messageContent.content = e2eeInterface.decryptMessage(senderId, ciphertext, iv)
                        encryptedMessages.add(messageId)
                    }
                }.onFailure {
                    context.log.error("Failed to decrypt message id: $messageId", it)
                    messageContent.contentType = ContentType.CHAT
                    messageContent.content = ProtoWriter().apply {
                        from(2) {
                            addString(1, "Failed to decrypt message, id=$messageId. Check logcat for more details.")
                        }
                    }.toByteArray()
                }

                return@followPath
            }

            val payload = getByteArray(2, 2) ?: return@followPath

            if (senderId == context.database.myUserId) {
                when (messageTypeId) {
                    REQUEST_PK_MESSAGE_ID -> {
                        replaceMessageText("[Key exchange request]")
                    }
                    RESPONSE_SK_MESSAGE_ID -> {
                        replaceMessageText("[Key exchange response]")
                    }
                }
                return@followPath
            }

            when (messageTypeId) {
                REQUEST_PK_MESSAGE_ID -> {
                    pkRequests[messageId] = payload
                    replaceMessageText("You just received a public key request. Click below to accept it.")
                }
                RESPONSE_SK_MESSAGE_ID -> {
                    secretResponses[messageId] = payload
                    replaceMessageText("Your friend just accepted your public key. Click below to accept the secret.")
                }
            }
        }
    }

    override fun asyncInit() {
        if (!isEnabled) return
        // trick to disable fidelius encryption
        context.event.subscribe(SendMessageWithContentEvent::class) { param ->
            val messageContent = param.messageContent
            val destinations = param.destinations
            if (destinations.conversations.none { getState(it.toString()) }) return@subscribe

            param.addInvokeLater {
                if (messageContent.contentType == ContentType.SNAP) {
                    messageContent.contentType = ContentType.EXTERNAL_MEDIA
                }

                if (messageContent.contentType == ContentType.CHAT) {
                    messageContent.contentType = ContentType.SHARE
                }
            }
        }

        context.event.subscribe(UnaryCallEvent::class) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
            val protoReader = ProtoReader(event.buffer)

            val conversationIds = mutableListOf<SnapUUID>()
            protoReader.eachBuffer(3) {
                conversationIds.add(SnapUUID.fromBytes(getByteArray(1, 1, 1) ?: return@eachBuffer))
            }

            if (conversationIds.any { !getState(it.toString()) }) {
                context.log.debug("Skipping encryption for conversation ids: ${conversationIds.joinToString(", ")}")
                return@subscribe
            }

            val participantsIds = conversationIds.map { getE2EParticipants(it.toString()) }.flatten().distinct()

            if (participantsIds.isEmpty()) {
                context.longToast("You don't have any friends in this conversation to encrypt messages with!")
                return@subscribe
            }
            val messageReader = protoReader.followPath(4) ?: return@subscribe

            if (messageReader.getVarInt(4, 2, 1, 1) != null) {
                return@subscribe
            }

            event.buffer = ProtoEditor(event.buffer).apply {
                val contentType = fixContentType(ContentType.fromId(messageReader.getVarInt(2)?.toInt() ?: -1), messageReader.followPath(4) ?: return@apply)
                val messageContent = messageReader.getByteArray(4) ?: return@apply

                runCatching {
                    edit(4) {
                        //set message content type
                        remove(2)
                        addVarInt(2, contentType.id)

                        //set encrypted content
                        remove(4)
                        add(4) {
                            from(2) {
                                from(1) {
                                    addVarInt(1, ENCRYPTED_MESSAGE_ID)
                                    participantsIds.forEach { participantId ->
                                        val encryptedMessage = e2eeInterface.encryptMessage(participantId,
                                            messageContent
                                        ) ?: run {
                                            context.log.error("Failed to encrypt message for $participantId")
                                            return@forEach
                                        }
                                        context.log.debug("encrypted message size = ${encryptedMessage.ciphertext.size} for $participantId")
                                        from(2) {
                                            // participantId is hashed with iv to prevent leaking it when sending to multiple conversations
                                            addBuffer(1, hashParticipantId(participantId, encryptedMessage.iv))
                                            addBuffer(2, encryptedMessage.iv)
                                            addBuffer(3, encryptedMessage.ciphertext)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.onFailure {
                    event.canceled = true
                    context.log.error("Failed to encrypt message", it)
                    context.longToast("Failed to encrypt message! Check logcat for more details.")
                }
            }.toByteArray()
        }
    }

    override fun init() {
        if (!isEnabled) return

        context.classCache.message.hookConstructor(HookStage.AFTER) { param ->
            val message = Message(param.thisObject())
            val conversationId = message.messageDescriptor.conversationId.toString()
            messageHook(
                conversationId = conversationId,
                messageId = message.messageDescriptor.messageId,
                senderId = message.senderId.toString(),
                messageContent = message.messageContent
            )

            message.messageContent.contentType?.also {
                message.messageContent.contentType = fixContentType(it, ProtoReader(message.messageContent.content))
            }

            message.messageContent.instanceNonNull()
                .getObjectField("mQuotedMessage")
                ?.getObjectField("mContent")
                ?.also { quotedMessage ->
                messageHook(
                    conversationId = conversationId,
                    messageId = quotedMessage.getObjectField("mMessageId")?.toString()?.toLong() ?: return@also,
                    senderId = SnapUUID(quotedMessage.getObjectField("mSenderId")).toString(),
                    messageContent = MessageContent(quotedMessage)
                )
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}