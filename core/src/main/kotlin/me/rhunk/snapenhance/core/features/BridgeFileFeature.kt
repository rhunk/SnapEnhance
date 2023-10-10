package me.rhunk.snapenhance.core.features

import me.rhunk.snapenhance.common.bridge.types.BridgeFileType
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

abstract class BridgeFileFeature(name: String, private val bridgeFileType: BridgeFileType, loadParams: Int) : Feature(name, loadParams) {
    private val fileLines = mutableListOf<String>()

    protected fun readFile() {
        val temporaryLines = mutableListOf<String>()
        val fileData: ByteArray = context.bridgeClient.createAndReadFile(bridgeFileType, ByteArray(0))
        with(BufferedReader(InputStreamReader(ByteArrayInputStream(fileData), StandardCharsets.UTF_8))) {
            var line = ""
            while (readLine()?.also { line = it } != null) temporaryLines.add(line)
            close()
        }
        fileLines.clear()
        fileLines.addAll(temporaryLines)
    }

    private fun updateFile() {
        val sb = StringBuilder()
        fileLines.forEach {
            sb.append(it).append("\n")
        }
        context.bridgeClient.writeFile(bridgeFileType, sb.toString().toByteArray(Charsets.UTF_8))
    }

    protected fun exists(line: String) = fileLines.contains(line)

    protected fun toggle(line: String) {
        if (exists(line)) fileLines.remove(line) else fileLines.add(line)
        updateFile()
    }

    protected fun setState(line: String, state: Boolean) {
        if (state) {
            if (!exists(line)) fileLines.add(line)
        } else {
            if (exists(line)) fileLines.remove(line)
        }
        updateFile()
    }

    protected fun reload() = readFile()

    protected fun put(line: String) {
        fileLines.add(line)
        updateFile()
    }
}