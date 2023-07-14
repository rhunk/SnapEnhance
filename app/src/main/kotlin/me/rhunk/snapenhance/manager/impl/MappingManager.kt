package me.rhunk.snapenhance.manager.impl

import android.app.AlertDialog
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.Constants
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.ModContext
import me.rhunk.snapenhance.R
import me.rhunk.snapenhance.bridge.common.impl.file.BridgeFileType
import me.rhunk.snapenhance.manager.Manager
import me.rhunk.snapenhance.mapping.Mapper
import me.rhunk.snapenhance.mapping.impl.BCryptClassMapper
import me.rhunk.snapenhance.mapping.impl.CallbackMapper
import me.rhunk.snapenhance.mapping.impl.DefaultMediaItemMapper
import me.rhunk.snapenhance.mapping.impl.EnumMapper
import me.rhunk.snapenhance.mapping.impl.OperaPageViewControllerMapper
import me.rhunk.snapenhance.mapping.impl.PlatformAnalyticsCreatorMapper
import me.rhunk.snapenhance.mapping.impl.PlusSubscriptionMapper
import me.rhunk.snapenhance.mapping.impl.ScCameraSettingsMapper
import me.rhunk.snapenhance.mapping.impl.StoryBoostStateMapper
import me.rhunk.snapenhance.util.getObjectField
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
class MappingManager(private val context: ModContext) : Manager {
    private val mappers = mutableListOf<Mapper>().apply {
        add(CallbackMapper())
        add(EnumMapper())
        add(OperaPageViewControllerMapper())
        add(PlusSubscriptionMapper())
        add(DefaultMediaItemMapper())
        add(BCryptClassMapper())
        add(PlatformAnalyticsCreatorMapper())
        add(ScCameraSettingsMapper())
        add(StoryBoostStateMapper())
    }

    private val mappings = ConcurrentHashMap<String, Any>()
    val areMappingsLoaded: Boolean
        get() = mappings.isNotEmpty()
    private var snapBuildNumber = 0

    @Suppress("deprecation")
    override fun init() {
        val currentBuildNumber = context.androidContext.packageManager.getPackageInfo(
            Constants.SNAPCHAT_PACKAGE_NAME,
            0
        ).longVersionCode.toInt()
        snapBuildNumber = currentBuildNumber

        if (context.bridgeClient.isFileExists(BridgeFileType.MAPPINGS)) {
            runCatching {
                loadCached()
            }.onFailure {
                context.crash("Failed to load cached mappings ${it.message}", it)
            }

            if (snapBuildNumber != currentBuildNumber) {
                context.bridgeClient.deleteFile(BridgeFileType.MAPPINGS)
                context.softRestartApp()
            }
            return
        }
        context.runOnUiThread {
            val statusDialogBuilder = AlertDialog.Builder(context.mainActivity, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage("Generating mappings, please wait...")
                .setCancelable(false)
                .setView(android.widget.ProgressBar(context.mainActivity).apply {
                    setPadding(0, 20, 0, 20)
                })

            val loadingDialog = statusDialogBuilder.show()

            context.executeAsync {
                runCatching {
                    refresh()
                }.onSuccess {
                    context.shortToast("Generated mappings for build $snapBuildNumber")
                    context.softRestartApp()
                }.onFailure {
                    Logger.error("Failed to generate mappings", it)
                    context.runOnUiThread {
                        loadingDialog.dismiss()
                        statusDialogBuilder.setView(null)
                        statusDialogBuilder.setMessage("Failed to generate mappings: $it")
                        statusDialogBuilder.setNegativeButton("Close") { _, _ ->
                            context.mainActivity!!.finish()
                        }
                        statusDialogBuilder.show()
                    }
                }
             }
        }
    }

    private fun loadCached() {
        if (!context.bridgeClient.isFileExists(BridgeFileType.MAPPINGS)) {
            Logger.xposedLog("Mappings file does not exist")
            return
        }
        val mappingsObject = JsonParser.parseString(
            String(
                context.bridgeClient.readFile(BridgeFileType.MAPPINGS),
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
                    Logger.xposedLog("Failed to execute mapper ${mapper.javaClass.simpleName}", it)
                }
            }.also { jobs.add(it) }
        }
        jobs.joinAll()
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun refresh() {
        val classes: MutableList<Class<*>> = ArrayList()

        val classLoader = context.androidContext.classLoader
        val dexPathList = classLoader.getObjectField("pathList")!!
        val dexElements = dexPathList.getObjectField("dexElements") as Array<Any>

        dexElements.forEach { dexElement: Any ->
            (dexElement.getObjectField("dexFile") as dalvik.system.DexFile?)?.apply {
                entries().toList().forEach fileList@{ className ->
                    //ignore classes without a dot in them
                    if (className.contains(".") && !className.startsWith("com.snap")) return@fileList
                    runCatching {
                        classLoader.loadClass(className)?.let {
                            //force load fields to avoid ClassNotFoundExceptions when executing mappers
                            it.declaredFields
                            classes.add(it)
                        }
                    }.onFailure {
                        Logger.debug("Failed to load class $className")
                    }
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
            BridgeFileType.MAPPINGS,
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