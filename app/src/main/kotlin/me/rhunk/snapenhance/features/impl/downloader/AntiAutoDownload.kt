package me.rhunk.snapenhance.features.impl.downloader

import me.rhunk.snapenhance.bridge.common.impl.FileAccessRequest
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class AntiAutoDownload : Feature("AntiAutoDownload", loadParams = FeatureLoadParams.ACTIVITY_CREATE_SYNC) {
    private val excludedUsers = mutableListOf<String>()

    override fun onActivityCreate() {
        readExclusionFile()
    }

    private fun readExclusionFile() {
        val userIds = mutableListOf<String>()
        val exclusionFileData: ByteArray = context.bridgeClient.createAndReadFile(FileAccessRequest.FileType.ANTI_AUTO_DOWNLOAD, ByteArray(0))
        with(BufferedReader(InputStreamReader(ByteArrayInputStream(exclusionFileData), StandardCharsets.UTF_8))) {
            var line = ""
            while (readLine()?.also { line = it } != null) userIds.add(line)
            close()
        }
        excludedUsers.clear()
        excludedUsers.addAll(userIds)
    }

    private fun writeExclusionFile() {
        val sb = StringBuilder()
        excludedUsers.forEach {
            sb.append(it).append("\n")
        }
        context.bridgeClient.writeFile(
            FileAccessRequest.FileType.ANTI_AUTO_DOWNLOAD,
            sb.toString().toByteArray(Charsets.UTF_8)
        )
    }

    fun setUserIgnored(userId: String, state: Boolean) {
        userId.hashCode().toLong().toString(16).let {
            if (state) {
                excludedUsers.add(it)
            } else {
                excludedUsers.remove(it)
            }
        }
        writeExclusionFile()
    }

    fun isUserIgnored(userId: String): Boolean {
        return excludedUsers.contains(userId.hashCode().toLong().toString(16))
    }
}