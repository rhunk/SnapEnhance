package me.rhunk.snapenhance.manager.patch.util

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.dexlib2.writer.pool.StringPool
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream


private fun obfuscateStrings(dexFile: DexFile, dexStrings: Map<String, String?>): DexPool {
    val dexPool = object: DexPool(dexFile.opcodes) {
        override fun getSectionProvider(): SectionProvider {
            val dexPool = this
            return object: DexPoolSectionProvider() {
                override fun getStringSection() = object: StringPool(dexPool) {
                    private val cacheMap = mutableMapOf<String, String>()

                    override fun intern(string: CharSequence) {
                        dexStrings[string.toString()]?.let {
                            cacheMap[string.toString()] = it
                            println("mapping $string to $it")
                            super.intern(it)
                            return
                        }
                        super.intern(string)
                    }

                    override fun getItemIndex(key: CharSequence): Int {
                        return cacheMap[key.toString()]?.let {
                            internedItems[it]
                        } ?: super.getItemIndex(key)
                    }

                    override fun getItemIndex(key: StringReference): Int {
                        return cacheMap[key.toString()]?.let {
                            internedItems[it]
                        } ?: super.getItemIndex(key)
                    }
                }
            }
        }
    }
    dexFile.classes.forEach { dexBackedClassDef ->
        dexPool.internClass(dexBackedClassDef)
    }
    return dexPool
}

fun InputStream.obfuscateDexFile(cacheFolder: File, dexStrings: Map<String, String?>)
    = this.obfuscateDexFile(cacheFolder, { true }, dexStrings)!!

fun InputStream.obfuscateDexFile(cacheFolder: File, filter: (DexFile) -> Boolean, dexStrings: Map<String, String?>): File? {
    val dexFile = DexBackedDexFile.fromInputStream(Opcodes.forApi(29), BufferedInputStream(this))
    if (!filter(dexFile)) return null
    val outputFile = File.createTempFile("dexobf", ".dex", cacheFolder)
    obfuscateStrings(dexFile, dexStrings).writeTo(FileDataStore(outputFile))
    return outputFile
}
