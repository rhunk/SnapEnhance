package me.rhunk.snapenhance.common.scripting

import android.content.Context
import me.rhunk.snapenhance.bridge.scripting.IScripting
import me.rhunk.snapenhance.common.logger.AbstractLogger
import me.rhunk.snapenhance.common.scripting.type.ModuleInfo
import org.mozilla.javascript.ScriptableObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream

open class ScriptRuntime(
    val androidContext: Context,
    logger: AbstractLogger,
) {
    val logger = ScriptingLogger(logger)

    lateinit var scripting: IScripting
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
            name = properties["name"]?.also {
                if (!it.matches(Regex("[a-z_]+"))) {
                    throw Exception("Invalid module name : Only lowercase letters and underscores are allowed")
                }
            } ?: throw Exception("Missing module name"),
            version = properties["version"] ?: throw Exception("Missing module version"),
            displayName = properties["displayName"],
            description = properties["description"],
            author = properties["author"],
            minSnapchatVersion = properties["minSnapchatVersion"]?.toLongOrNull(),
            minSEVersion = properties["minSEVersion"]?.toLongOrNull(),
            grantedPermissions = properties["permissions"]?.split(",")?.map { it.trim() } ?: emptyList(),
        )
    }

    fun getModuleInfo(inputStream: InputStream): ModuleInfo {
        return readModuleInfo(inputStream.bufferedReader())
    }

    fun unload(scriptPath: String) {
        val module = modules[scriptPath] ?: return
        logger.info("Unloading module $scriptPath")
        module.unload()
        modules.remove(scriptPath)
    }

    fun load(scriptPath: String, content: String): JSModule? {
        logger.info("Loading module $scriptPath")
        return runCatching {
            JSModule(
                scriptRuntime = this,
                moduleInfo = readModuleInfo(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)).bufferedReader()),
                content = content,
            ).apply {
                load {
                    buildModuleObject(this, this@apply)
                }
                modules[scriptPath] = this
            }
        }.onFailure {
            logger.error("Failed to load module $scriptPath", it)
        }.getOrNull()
    }
}