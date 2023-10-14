package me.rhunk.snapenhance.common.scripting

import android.content.Context
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import org.mozilla.javascript.ScriptableObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream

open class ScriptRuntime(
    val androidContext: Context,
    val logger: AbstractLogger,
) {
    var buildModuleObject: ScriptableObject.(JSModule) -> Unit = {}
    private val modules = mutableMapOf<String, JSModule>()

    fun eachModule(f: JSModule.() -> Unit) {
        modules.values.forEach { module ->
            runCatching {
                module.f()
            }.onFailure {
                logger.error("Failed to run module function in ${module.moduleInfo.name}", it)
            }
        }
    }

    fun getModuleByName(name: String): JSModule? {
        return modules.values.find { it.moduleInfo.name == name }
    }

    private fun readModuleInfo(reader: BufferedReader): ModuleInfo {
        val header = reader.readLine()
        if (!header.startsWith("// ==SE_module==")) {
            throw Exception("Invalid module header")
        }

        val properties = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine()
            if (line.startsWith("// ==/SE_module==")) {
                break
            }
            val split = line.replaceFirst("//", "").split(":")
            if (split.size != 2) {
                throw Exception("Invalid module property")
            }
            properties[split[0].trim()] = split[1].trim()
        }

        return ModuleInfo(
            name = properties["name"] ?: throw Exception("Missing module name"),
            version = properties["version"] ?: throw Exception("Missing module version"),
            description = properties["description"],
            author = properties["author"],
            minSnapchatVersion = properties["minSnapchatVersion"]?.toLong(),
            minSEVersion = properties["minSEVersion"]?.toLong(),
            grantPermissions = properties["permissions"]?.split(",")?.map { it.trim() },
        )
    }

    fun getModuleInfo(inputStream: InputStream): ModuleInfo {
        return readModuleInfo(inputStream.bufferedReader())
    }

    fun reload(path: String, content: String) {
        unload(path)
        load(path, content)
    }

    private fun unload(path: String) {
        val module = modules[path] ?: return
        module.unload()
        modules.remove(path)
    }

    fun load(path: String, content: String): JSModule? {
        logger.info("Loading module $path")
        return runCatching {
            JSModule(
                scriptRuntime = this,
                moduleInfo = readModuleInfo(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)).bufferedReader()),
                content = content,
            ).apply {
                load {
                    buildModuleObject(this, this@apply)
                }
                modules[path] = this
            }
        }.onFailure {
            logger.error("Failed to load module $path", it)
        }.getOrNull()
    }
}