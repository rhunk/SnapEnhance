package me.rhunk.snapenhance.core.features.impl.experiments

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.TextView
import me.rhunk.snapenhance.common.data.ContentType
import me.rhunk.snapenhance.common.data.MessageState
import me.rhunk.snapenhance.common.data.MessagingRuleType
import me.rhunk.snapenhance.common.data.RuleState
import me.rhunk.snapenhance.common.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.common.util.protobuf.ProtoReader
import me.rhunk.snapenhance.common.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.core.event.events.impl.AddViewEvent
import me.rhunk.snapenhance.core.event.events.impl.BindViewEvent
import me.rhunk.snapenhance.core.event.events.impl.BuildMessageEvent
import me.rhunk.snapenhance.core.event.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.features.FeatureLoadParams
import me.rhunk.snapenhance.core.features.MessagingRuleFeature
import me.rhunk.snapenhance.core.features.impl.messaging.Messaging
import me.rhunk.snapenhance.core.ui.ViewAppearanceHelper
import me.rhunk.snapenhance.core.ui.addForegroundDrawable
import me.rhunk.snapenhance.core.ui.removeForegroundDrawable
import me.rhunk.snapenhance.core.util.EvictingMap
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.core.wrapper.impl.MessageContent
import me.rhunk.snapenhance.core.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.nativelib.NativeLib
import java.security.MessageDigest
import kotlin.random.Random

class EndToEndEncryption : MessagingRuleFeature(
    "EndToEndEncryption",
    MessagingRuleType.E2E_ENCRYPTION,
    loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC or FeatureLoadParams.INIT_SYNC or FeatureLoadParams.INIT_ASYNC
) {
    private val isEnabled get() = context.config.experimental.e2eEncryption.globalState == true
    private val e2eeInterface by lazy { context.bridgeClient.getE2eeInterface() }

    companion object {
        const val REQUEST_PK_MESSAGE_ID = 1
        const val RESPONSE_SK_MESSAGE_ID = 2
        const val ENCRYPTED_MESSAGE_ID = 3
    }

    private val decryptedMessageCache = EvictingMap<Long, Pair<ContentType, ByteArray>>(100)

    private val pkRequests = mutableMapOf<Long, ByteArray>()
    private val secretResponses = mutableMapOf<Long, ByteArray>()
    private val encryptedMessages = mutableListOf<Long>()

    private fun getE2EParticipants(conversationId: String): List<String> {
        return context.database.getConversationParticipants(conversationId)?.filter { friendId ->
            friendId != context.database.myUserId && e2eeInterface.friendKeyExists(friendId)
        } ?: emptyList()
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
            setState(conversationId, true)
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
            setState(conversationId, true)
            context.longToast("Done! You can now exchange encrypted messages with this friend.")
        }
    }

    private fun openManagementPopup() {
        val conversationId = context.feature(Messaging::class).openedConversationUUID?.toString() ?: return
        val friendId = context.database.getDMOtherParticipant(conversationId)

        if (friendId == null) {
            context.shortToast("This menu is only available in direct messages.")
            return
        }

        val actions = listOf(
            "Initiate a new shared secret",
            "Show shared key fingerprint"
        )

        ViewAppearanceHelper.newAlertDialogBuilder(context.mainActivity!!).apply {
            setTitle("End-to-end encryption")
            setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        warnKeyOverwrite(friendId) {
                            askForKeys(conversationId)
                        }
                    }
                    1 -> {
                        val fingerprint = e2eeInterface.getSecretFingerprint(friendId)
                        ViewAppearanceHelper.newAlertDialogBuilder(context).apply {
                            setTitle("End-to-end encryption")
                            setMessage("Your fingerprint is:\n\n$fingerprint\n\nMake sure to check if it matches your friend's fingerprint!")
                            setPositiveButton("OK") { _, _ -> }
                        }.show()
                    }
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

        val encryptedMessageIndicator by context.config.experimental.e2eEncryption.encryptedMessageIndicator

        // hook view binder to add special buttons
        val receivePublicKeyTag = Random.nextLong().toString(16)
        val receiveSecretTag = Random.nextLong().toString(16)

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
                    viewGroup.removeForegroundDrawable("encryptedMessage")

                    if (encryptedMessages.contains(messageId.toLong())) {
                        viewGroup.addForegroundDrawable("encryptedMessage", ShapeDrawable(object: Shape() {
                            override fun draw(canvas: Canvas, paint: Paint) {
                                paint.textSize = 20f
                                canvas.drawText("\uD83D\uDD12", 0f, canvas.height / 2f, paint)
                            }
                        }))
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

    private fun fixContentType(contentType: ContentType?, message: ProtoReader)
        = ContentType.fromMessageContainer(message) ?: contentType

    private fun hashParticipantId(participantId: String, salt: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").apply {
            update(participantId.toByteArray())
            update(salt)
        }.digest()
    }

    fun tryDecryptMessage(senderId: String, clientMessageId: Long, conversationId: String, contentType: ContentType, messageBuffer: ByteArray): Pair<ContentType, ByteArray> {
        if (contentType != ContentType.STATUS && decryptedMessageCache.containsKey(clientMessageId)) {
            return decryptedMessageCache[clientMessageId]!!
        }

        val reader = ProtoReader(messageBuffer)
        var outputBuffer = messageBuffer
        var outputContentType = fixContentType(contentType, reader) ?: contentType
        val conversationParticipants by lazy {
            getE2EParticipants(conversationId)
        }

        fun setDecryptedMessage(buffer: ByteArray) {
            outputBuffer = buffer
            outputContentType = fixContentType(outputContentType, ProtoReader(buffer)) ?: outputContentType
            decryptedMessageCache[clientMessageId] = outputContentType to buffer
            encryptedMessages.add(clientMessageId)
        }

        fun replaceMessageText(text: String) {
            outputBuffer = ProtoWriter().apply {
                from(2) {
                    addString(1, text)
                }
            }.toByteArray()
        }

        // decrypt messages
        reader.followPath(2, 1) {
            val messageTypeId = getVarInt(1)?.toInt() ?: return@followPath
            val isMe = context.database.myUserId == senderId

            if (messageTypeId == ENCRYPTED_MESSAGE_ID) {
                runCatching {
                    eachBuffer(2) {
                        if (encryptedMessages.contains(clientMessageId)) return@eachBuffer

                        val participantIdHash = getByteArray(1) ?: return@eachBuffer
                        val iv = getByteArray(2) ?: return@eachBuffer
                        val ciphertext = getByteArray(3) ?: return@eachBuffer

                        if (isMe) {
                            if (conversationParticipants.isEmpty()) return@eachBuffer
                            val participantId = conversationParticipants.firstOrNull { participantIdHash.contentEquals(hashParticipantId(it, iv)) } ?: return@eachBuffer
                            setDecryptedMessage(e2eeInterface.decryptMessage(participantId, ciphertext, iv) ?: run {
                                context.log.warn("Failed to decrypt message for participant $participantId")
                                return@eachBuffer
                            })
                            return@eachBuffer
                        }

                        if (!participantIdHash.contentEquals(hashParticipantId(context.database.myUserId, iv))) return@eachBuffer

                        setDecryptedMessage(e2eeInterface.decryptMessage(senderId, ciphertext, iv)?: run {
                            context.log.warn("Failed to decrypt message")
                            return@eachBuffer
                        })
                    }
                }.onFailure {
                    context.log.error("Failed to decrypt message id: $clientMessageId", it)
                    outputContentType = ContentType.CHAT
                    outputBuffer = ProtoWriter().apply {
                        from(2) {
                            addString(1, "Failed to decrypt message, id=$clientMessageId. Check logcat for more details.")
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
                    pkRequests[clientMessageId] = payload
                    replaceMessageText("You just received a public key request. Click below to accept it.")
                }
                RESPONSE_SK_MESSAGE_ID -> {
                    secretResponses[clientMessageId] = payload
                    replaceMessageText("Your friend just accepted your public key. Click below to accept the secret.")
                }
            }
        }

        return outputContentType to outputBuffer
    }

    private fun messageHook(conversationId: String, messageId: Long, senderId: String, messageContent: MessageContent) {
        val (contentType, buffer) = tryDecryptMessage(senderId, messageId, conversationId, messageContent.contentType ?: ContentType.CHAT, messageContent.content!!)
        messageContent.contentType = contentType
        messageContent.content = buffer
    }

    override fun asyncInit() {
        if (!isEnabled) return
        val forceMessageEncryption by context.config.experimental.e2eEncryption.forceMessageEncryption

        // trick to disable fidelius encryption
        context.event.subscribe(SendMessageWithContentEvent::class) { event ->
            val messageContent = event.messageContent
            val destinations = event.destinations

            val e2eeConversations = destinations.conversations!!.filter { getState(it.toString()) && getE2EParticipants(it.toString()).isNotEmpty() }

            if (e2eeConversations.isEmpty()) return@subscribe

            if (e2eeConversations.size != destinations.conversations!!.size || destinations.stories?.isNotEmpty() == true) {
                if (!forceMessageEncryption) return@subscribe
                context.longToast("You can't send encrypted content to both encrypted and unencrypted conversations!")
                event.canceled = true
                return@subscribe
            }

            if (!NativeLib.initialized) {
                context.longToast("Failed to send! Please enable Native Hooks in the settings.")
                event.canceled = true
                return@subscribe
            }

            event.addInvokeLater {
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
            var hasStory = false

            val conversationIds = mutableListOf<SnapUUID>()
            protoReader.eachBuffer(3) {
                if (contains(2)) {
                    hasStory = true
                    return@eachBuffer
                }
                conversationIds.add(SnapUUID.fromBytes(getByteArray(1, 1, 1) ?: return@eachBuffer))
            }

            if (hasStory) {
                context.log.debug("Skipping encryption for story message")
                return@subscribe
            }

            if (conversationIds.any { !getState(it.toString()) || getE2EParticipants(it.toString()).isEmpty() }) {
                context.log.debug("Skipping encryption for conversation ids: ${conversationIds.joinToString(", ")}")
                return@subscribe
            }

            val participantsIds = conversationIds.map { getE2EParticipants(it.toString()) }.flatten().distinct()

            if (participantsIds.isEmpty()) {
                context.shortToast("You don't have any friends in this conversation to encrypt messages with!")
                return@subscribe
            }
            val messageReader = protoReader.followPath(4) ?: return@subscribe

            if (messageReader.getVarInt(4, 2, 1, 1) != null) {
                return@subscribe
            }

            event.buffer = ProtoEditor(event.buffer).apply {
                val contentType = fixContentType(
                    ContentType.fromId(messageReader.getVarInt(2)?.toInt() ?: -1),
                    messageReader.followPath(4) ?: return@apply
                ) ?: return@apply
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

        context.event.subscribe(BuildMessageEvent::class, priority = 0) { event ->
            val message = event.message
            if (message.messageState != MessageState.COMMITTED) return@subscribe
            val conversationId = message.messageDescriptor!!.conversationId.toString()
            messageHook(
                conversationId = conversationId,
                messageId = message.messageDescriptor!!.messageId!!,
                senderId = message.senderId.toString(),
                messageContent = message.messageContent!!
            )

            message.messageContent!!.instanceNonNull()
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