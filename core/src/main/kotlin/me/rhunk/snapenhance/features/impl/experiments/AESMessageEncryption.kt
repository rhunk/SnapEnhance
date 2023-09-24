package me.rhunk.snapenhance.features.impl.experiments

import me.rhunk.snapenhance.core.event.events.impl.SendMessageWithContentEvent
import me.rhunk.snapenhance.core.event.events.impl.UnaryCallEvent
import me.rhunk.snapenhance.core.messaging.MessagingRuleType
import me.rhunk.snapenhance.core.messaging.RuleState
import me.rhunk.snapenhance.core.util.protobuf.ProtoEditor
import me.rhunk.snapenhance.core.util.protobuf.ProtoReader
import me.rhunk.snapenhance.core.util.protobuf.ProtoWriter
import me.rhunk.snapenhance.data.ContentType
import me.rhunk.snapenhance.data.wrapper.impl.Message
import me.rhunk.snapenhance.data.wrapper.impl.SnapUUID
import me.rhunk.snapenhance.features.FeatureLoadParams
import me.rhunk.snapenhance.features.MessagingRuleFeature
import me.rhunk.snapenhance.hook.HookStage
import me.rhunk.snapenhance.hook.hookConstructor
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/*
    To prevent snapchat from using fidelius, snaps are spoofed to external media and chats into status before it's sent to the native.
    When the CreateContentMessage request is sent to the server, the content is encrypted
 */

//TODO: RSA encryption
class AESMessageEncryption : MessagingRuleFeature(
    "AESMessageEncryption",
    MessagingRuleType.AES_ENCRYPTION,
    loadParams = FeatureLoadParams.INIT_ASYNC or FeatureLoadParams.INIT_SYNC
) {
    private val key = intArrayOf(
        0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f,
        0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f,
        0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f,
        0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f, 0x6f
    ).map { it.toByte() }.toByteArray()

    private val isEnabled get() = context.config.experimental.useMessageAESEncryption.get()

    private fun useCipher(input: ByteArray, iv: ByteArray, decrypt: Boolean = false): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(if (decrypt) Cipher.DECRYPT_MODE else Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(input)
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

    override fun asyncInit() {
        // trick to disable fidelius encryption
        context.event.subscribe(SendMessageWithContentEvent::class, { isEnabled }) { param ->
            val messageContent = param.messageContent
            val destinations = param.destinations
            if (destinations.conversations.size != 1 || destinations.stories.isNotEmpty()) return@subscribe

            if (!getState(destinations.conversations.first().toString())) return@subscribe

            if (messageContent.contentType == ContentType.SNAP) {
                messageContent.contentType = ContentType.EXTERNAL_MEDIA
            }

            if (messageContent.contentType == ContentType.CHAT) {
                messageContent.contentType = ContentType.SHARE
            }
        }
    }

    override fun init() {
        context.event.subscribe(UnaryCallEvent::class, { isEnabled }) { event ->
            if (event.uri != "/messagingcoreservice.MessagingCoreService/CreateContentMessage") return@subscribe
            val protoReader = ProtoReader(event.buffer)

            val conversationIds = mutableListOf<SnapUUID>()
            protoReader.eachBuffer(3) {
                conversationIds.add(SnapUUID.fromBytes(getByteArray(1, 1, 1) ?: return@eachBuffer))
            }

            if (conversationIds.size != 1) return@subscribe

            if (!getState(conversationIds.first().toString())) return@subscribe

            val generatedIv = ByteArray(16).also { Random.nextBytes(it) }

            event.buffer = ProtoEditor(event.buffer).apply {
                protoReader.followPath(4) {
                    val contentType = fixContentType(ContentType.fromId(getVarInt(2)?.toInt() ?: -1), followPath(4) ?: return@followPath)

                    runCatching {
                        val encryptedMessage = useCipher(getByteArray(4) ?: return@followPath, generatedIv, false)
                        edit(4) {
                            //set message content type
                            remove(2)
                            addVarInt(2, contentType.id)

                            //set encrypted content
                            remove(4)
                            add(4) {
                                from(2) {
                                    from(1) {
                                        addBuffer(1, encryptedMessage)
                                        addBuffer(2, generatedIv)
                                    }
                                    addVarInt(2, 1)
                                }
                            }
                        }
                    }.onFailure {
                        event.canceled = true
                        context.log.error("Failed to encrypt message", it)
                        context.longToast("Failed to encrypt message! Check logcat for more details.")
                    }
                }
            }.toByteArray()
        }

        context.classCache.message.hookConstructor(HookStage.AFTER, { isEnabled }) { param ->
            val message = Message(param.thisObject())
            val reader = ProtoReader(message.messageContent.content)

            // fix content type
            message.messageContent.contentType?.also {
                message.messageContent.contentType = fixContentType(it, reader)
            }

            reader.followPath(2) {
                if (getVarInt(2) != 1L) return@followPath

                runCatching {
                    followPath(1) path@{
                        val encryptedMessage = getByteArray(1) ?: return@path
                        val iv = getByteArray(2) ?: return@path

                        val decryptedMessage = useCipher(encryptedMessage, iv, decrypt = true)
                        message.messageContent.content = decryptedMessage
                    }
                }.onFailure {
                    context.log.error("Failed to decrypt message id: ${message.messageDescriptor.messageId}", it)
                    message.messageContent.contentType = ContentType.CHAT
                    message.messageContent.content = ProtoWriter().apply {
                        from(2) {
                            addString(1, "Failed to decrypt message, id=${message.messageDescriptor.messageId}. Check logcat for more details.")
                        }
                    }.toByteArray()
                }
            }
        }
    }

    override fun getRuleState() = RuleState.WHITELIST
}