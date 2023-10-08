package me.rhunk.snapenhance.mapper

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.reflect.KClass

class Mapper(
    private vararg val mappers: KClass<out AbstractClassMapper> = arrayOf()
) {
    private val classes = mutableListOf<ClassDef>()
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

    fun start(): JsonObject {
        val mappers = mappers.map { it.java.constructors.first().newInstance() as AbstractClassMapper }
        val context = MapperContext(classes.associateBy { it.type })

        runBlocking {
            withContext(Dispatchers.IO) {
                mappers.forEach { mapper ->
                    launch {
                        mapper.run(context)
                    }
                }
            }
        }

        return context.exportToJson()
    }
}