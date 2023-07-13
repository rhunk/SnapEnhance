package me.rhunk.snapmapper

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.BufferedInputStream
import java.util.zip.ZipFile
import kotlin.reflect.KClass

class Mapper(
    private vararg val mappers: KClass<out AbstractClassMapper> = arrayOf()
) {
    private val classes = mutableListOf<DexBackedClassDef>()
    fun loadApk(path: String) {
        ZipFile(path).apply {
            entries().toList().filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }.forEach { dexEntry ->
                getInputStream(dexEntry).use {
                    runCatching {
                        classes.addAll(
                            DexBackedDexFile.fromInputStream(Opcodes.getDefault(), BufferedInputStream(it)).classes
                        )
                    }.onFailure {
                        throw Throwable("Failed to load dex file: ${dexEntry.name}", it)
                    }
                }
            }
        }
    }

    fun start(): JsonObject {
        val mappers = mappers.map { it.java.constructors.first().newInstance() as AbstractClassMapper }
        val context = MapperContext(classes.associateBy { it.type })

        runBlocking {
            withContext(Dispatchers.IO) {
                val finishedJobs = mutableListOf<Class<*>>()
                val dependentsMappers = mappers.filter { it.dependsOn.isNotEmpty() }

                fun onJobFinished(mapper: AbstractClassMapper) {
                    finishedJobs.add(mapper.javaClass)

                    dependentsMappers.filter { it ->
                        !finishedJobs.contains(it.javaClass) &&
                                it.dependsOn.all {
                                    finishedJobs.contains(it.java)
                                }
                    }.forEach {
                        launch {
                            it.run(context)
                            onJobFinished(it)
                        }
                    }
                }

                mappers.forEach { mapper ->
                    if (mapper.dependsOn.isNotEmpty()) return@forEach
                    launch {
                        mapper.run(context)
                        onJobFinished(mapper)
                    }
                }
            }
        }

        return context.exportToJson()
    }
}