package me.rhunk.snapenhance.features.impl.spy

import me.rhunk.snapenhance.bridge.common.impl.FileAccessRequest
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


class StealthMode : Feature("StealthMode", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val stealthConversations = mutableListOf<String>()

    override fun onActivityCreate() {
        readStealthFile()
    }

    private fun writeStealthFile() {
        val sb = StringBuilder()
        for (stealthConversation in stealthConversations) {
            sb.append(stealthConversation).append("\n")
        }
        context.bridgeClient.writeFile(
            FileAccessRequest.FileType.STEALTH,
            sb.toString().toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun readStealthFile() {
        val conversations = mutableListOf<String>()
        val stealthFileData: ByteArray = context.bridgeClient.createAndReadFile(FileAccessRequest.FileType.STEALTH, ByteArray(0))
        //read conversations
        with(BufferedReader(InputStreamReader(
                ByteArrayInputStream(stealthFileData),
                StandardCharsets.UTF_8
        ))) {
            var line: String = ""
            while (readLine()?.also { line = it } != null) {
                conversations.add(line)
            }
            close()
        }
        stealthConversations.clear()
        stealthConversations.addAll(conversations)
    }

    fun setStealth(conversationId: String, stealth: Boolean) {
        conversationId.hashCode().toLong().toString(16).let {
            if (stealth) {
                stealthConversations.add(it)
            } else {
                stealthConversations.remove(it)
            }
        }
        writeStealthFile()
    }

    fun isStealth(conversationId: String): Boolean {
        return stealthConversations.contains(conversationId.hashCode().toLong().toString(16))
    }
}
