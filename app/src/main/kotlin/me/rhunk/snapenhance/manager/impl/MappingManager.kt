package me.rhunk.snapenhance.manager.impl

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dalvik.system.DexFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.bridge.common.impl.FileAccessRequest
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.mapping.Mapper
import me.rhunk.snapenhance.mapping.impl.CallbackMapper
import me.rhunk.snapenhance.mapping.impl.EnumMapper
import me.rhunk.snapenhance.mapping.impl.OperaPageViewControllerMapper
import me.rhunk.snapenhance.mapping.impl.PlusSubscriptionMapper
import me.rhunk.snapenhance.util.getObjectField
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
class MappingManager(private val context: ModContext) : Manager {
    private val mappers = mutableListOf<Mapper>().apply {
        add(CallbackMapper())
        add(EnumMapper())
        add(OperaPageViewControllerMapper())
        add(PlusSubscriptionMapper())
    }

    private val mappings = ConcurrentHashMap<String, Any>()
    private var snapBuildNumber = 0

    override fun init() {
        val currentBuildNumber = context.androidContext.packageManager.getPackageInfo(
            Constants.SNAPCHAT_PACKAGE_NAME,
            0
        ).longVersionCode.toInt()
        snapBuildNumber = currentBuildNumber

        if (context.bridgeClient.isFileExists(FileAccessRequest.FileType.MAPPINGS)) {
            runCatching {
                loadCached()
            }.onFailure {
                if (it is FileNotFoundException) {
                    Logger.xposedLog(it)
                    context.forceCloseApp()
                }
                Logger.error("Failed to load cached mappings", it)
            }

            if (snapBuildNumber != currentBuildNumber) {
                context.bridgeClient.deleteFile(FileAccessRequest.FileType.MAPPINGS)
                context.softRestartApp()
            }
            return
        }
        refresh()
    }

    private fun loadCached() {
        if (!context.bridgeClient.isFileExists(FileAccessRequest.FileType.MAPPINGS)) {
            Logger.xposedLog("Mappings file does not exist")
            return
        }
        val mappingsObject = JsonParser.parseString(
            String(
                context.bridgeClient.readFile(FileAccessRequest.FileType.MAPPINGS),
                StandardCharsets.UTF_8
            )
        ).asJsonObject.also {
            snapBuildNumber = it["snap_build_number"].asInt
        }

        mappingsObject.entrySet().forEach { (key, value): Map.Entry<String, JsonElement> ->
            if (value.isJsonArray) {
                mappings[key] = context.gson.fromJson(value, ArrayList::class.java)
                return@forEach
            }
            if (value.isJsonObject) {
                mappings[key] = context.gson.fromJson(value, ConcurrentHashMap::class.java)
                return@forEach
            }
            mappings[key] = value.asString
        }
    }

    private fun executeMappers(classes: List<Class<*>>) = runBlocking {
        val jobs = mutableListOf<Job>()
        mappers.forEach { mapper ->
            mapper.context = context
            launch {
                runCatching {
                    mapper.useClasses(context.androidContext.classLoader, classes, mappings)
                }.onFailure {
                    Logger.error("Failed to execute mapper ${mapper.javaClass.simpleName}", it)
                }
            }.also { jobs.add(it) }
        }
        jobs.forEach { it.join() }
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun refresh() {
        context.shortToast("Loading mappings (this may take a while)")
        val classes: MutableList<Class<*>> = ArrayList()

        val classLoader = context.androidContext.classLoader
        val dexPathList = classLoader.getObjectField("pathList")
        val dexElements = dexPathList.getObjectField("dexElements") as Array<Any>

        dexElements.forEach { dexElement: Any ->
            val dexFile = dexElement.getObjectField("dexFile") as DexFile
            dexFile.entries().toList().forEach fileList@{ className ->
                //ignore classes without a dot in them
                if (className.contains(".") && !className.startsWith("com.snap")) return@fileList
                runCatching {
                    classLoader.loadClass(className)?.let { classes.add(it) }
                }
            }
        }

        executeMappers(classes)
        write()
    }

    private fun write() {
        val mappingsObject = JsonObject()
        mappingsObject.addProperty("snap_build_number", snapBuildNumber)
        mappings.forEach { (key, value) ->
            if (value is List<*>) {
                mappingsObject.add(key, context.gson.toJsonTree(value))
                return@forEach
            }
            if (value is Map<*, *>) {
                mappingsObject.add(key, context.gson.toJsonTree(value))
                return@forEach
            }
            mappingsObject.addProperty(key, value.toString())
        }

        context.bridgeClient.writeFile(
            FileAccessRequest.FileType.MAPPINGS,
            mappingsObject.toString().toByteArray()
        )
    }

    fun getMappedObject(key: String): Any {
        if (mappings.containsKey(key)) {
            return mappings[key]!!
        }
        throw Exception("No mapping found for $key")
    }

    fun getMappedClass(className: String): Class<*> {
        return context.androidContext.classLoader.loadClass(getMappedObject(className) as String)
    }

    fun getMappedClass(key: String, subKey: String): Class<*> {
        return context.androidContext.classLoader.loadClass(getMappedValue(key, subKey))
    }

    fun getMappedValue(key: String): String {
        return getMappedObject(key) as String
    }

    fun <T : Any> getMappedList(key: String): List<T> {
        return listOf(getMappedObject(key) as List<T>).flatten()
    }

    fun getMappedValue(key: String, subKey: String): String {
        return getMappedMap(key)[subKey] as String
    }

    fun getMappedMap(key: String): Map<String, *> {
        return getMappedObject(key) as Map<String, *>
    }
}