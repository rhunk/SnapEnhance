package me.rhunk.snapenhance.mapper

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rhunk.snapenhance.mapper.impl.*
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class ClassMapper(
    private vararg val mappers: AbstractClassMapper = DEFAULT_MAPPERS,
) {
    private val classes = mutableListOf<ClassDef>()

    companion object {
        val DEFAULT_MAPPERS get() = arrayOf(
            BCryptClassMapper(),
            CallbackMapper(),
            DefaultMediaItemMapper(),
            MediaQualityLevelProviderMapper(),
            OperaPageViewControllerMapper(),
            PlusSubscriptionMapper(),
            StoryBoostStateMapper(),
            FriendsFeedEventDispatcherMapper(),
            CompositeConfigurationProviderMapper(),
            ScoreUpdateMapper(),
            FriendRelationshipChangerMapper(),
            ViewBinderMapper(),
            FriendingDataSourcesMapper(),
            OperaViewerParamsMapper(),
        )
    }


    fun loadApk(path: String) {
        val apkFile = ZipFile(path)
        val apkEntries = apkFile.entries().toList()

        fun readClass(stream: InputStream) = runCatching {
            classes.addAll(
                DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(stream)).classes
            )
        }.onFailure {
            throw Throwable("Failed to load dex file", it)
        }

        fun filterDexClasses(name: String) = name.startsWith("classes") && name.endsWith(".dex")

        apkEntries.firstOrNull { it.name.endsWith("lspatch/origin.apk") }?.let { origin ->
            val originApk = ZipInputStream(apkFile.getInputStream(origin))
            var nextEntry = originApk.nextEntry
            while (nextEntry != null) {
                if (filterDexClasses(nextEntry.name)) {
                    readClass(originApk)
                }
                originApk.closeEntry()
                nextEntry = originApk.nextEntry
            }
            return
        }

        apkEntries.toList().filter { filterDexClasses(it.name) }.forEach {
            readClass(apkFile.getInputStream(it))
        }
    }

    suspend fun run(): JsonObject {
        val context = MapperContext(classes.associateBy { it.type })

        withContext(Dispatchers.IO) {
            mappers.forEach { mapper ->
                launch {
                    mapper.run(context)
                }
            }
        }

        val outputJson = JsonObject()
        mappers.forEach { mapper ->
            outputJson.add(mapper.mapperName, JsonObject().apply {
                mapper.writeFromJson(this)
            })
        }
        return outputJson
    }
}
