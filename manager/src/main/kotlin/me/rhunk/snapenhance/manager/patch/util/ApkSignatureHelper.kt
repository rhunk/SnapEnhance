package me.rhunk.snapenhance.manager.patch.util;

import com.android.tools.build.apkzlib.sign.SigningExtension
import com.android.tools.build.apkzlib.sign.SigningOptions
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarFile


//https://github.com/LSPosed/LSPatch/blob/master/patch/src/main/java/org/lsposed/patch/util/ApkSignatureHelper.java
object ApkSignatureHelper {
    private val APK_V2_MAGIC = charArrayOf('A', 'P', 'K', ' ', 'S', 'i', 'g', ' ',
        'B', 'l', 'o', 'c', 'k', ' ', '4', '2')

    fun provideSigningExtension(keyStoreInputStream: InputStream): SigningExtension {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(keyStoreInputStream, "123456".toCharArray())
        val key = keyStore.getEntry("key0", KeyStore.PasswordProtection("123456".toCharArray())) as KeyStore.PrivateKeyEntry
        val certificates = key.certificateChain.mapNotNull { it as? X509Certificate }.toTypedArray()

        return SigningExtension(
            SigningOptions.builder().apply {
                setMinSdkVersion(28)
                setV2SigningEnabled(true)
                setCertificates(*certificates)
                setKey(key.privateKey)
            }.build()
        )
    }

    private fun toChars(mSignature: ByteArray): CharArray {
        val N = mSignature.size
        val N2 = N * 2
        val text = CharArray(N2)
        for (j in 0 until N) {
            val v = mSignature[j]
            var d = v.toInt() shr 4 and 0xf
            text[j * 2] = (if (d >= 10) 'a'.code + d - 10 else '0'.code + d).toChar()
            d = v.toInt() and 0xf
            text[j * 2 + 1] = (if (d >= 10) 'a'.code + d - 10 else '0'.code + d).toChar()
        }
        return text
    }

    private fun loadCertificates(
        jarFile: JarFile,
        je: JarEntry?,
        readBuffer: ByteArray
    ): Array<Certificate?>? {
        try {
            val `is` = jarFile.getInputStream(je)
            while (`is`.read(readBuffer, 0, readBuffer.size) != -1) {
            }
            `is`.close()
            return je?.certificates as Array<Certificate?>?
        } catch (e: Exception) {
        }
        return null
    }

    fun getApkSignInfo(apkFilePath: String): String? {
        return try {
            getApkSignV2(apkFilePath)
        } catch (e: Exception) {
            getApkSignV1(apkFilePath)
        }
    }

    fun getApkSignV1(apkFilePath: String?): String? {
        val readBuffer = ByteArray(8192)
        var certs: Array<Certificate?>? = null
        try {
            val jarFile = JarFile(apkFilePath)
            val entries: Enumeration<*> = jarFile.entries()
            while (entries.hasMoreElements()) {
                val je = entries.nextElement() as JarEntry
                if (je.isDirectory) {
                    continue
                }
                if (je.name.startsWith("META-INF/")) {
                    continue
                }
                val localCerts = loadCertificates(jarFile, je, readBuffer)
                if (certs == null) {
                    certs = localCerts
                } else {
                    for (i in certs.indices) {
                        var found = false
                        for (j in localCerts!!.indices) {
                            if (certs[i] != null && certs[i] == localCerts[j]) {
                                found = true
                                break
                            }
                        }
                        if (!found || certs.size != localCerts.size) {
                            jarFile.close()
                            return null
                        }
                    }
                }
            }
            jarFile.close()
            return if (certs != null) String(toChars(certs[0]!!.encoded)) else null
        } catch (ignored: Throwable) {
        }
        return null
    }

    @Throws(IOException::class)
    private fun getApkSignV2(apkFilePath: String): String {
        RandomAccessFile(apkFilePath, "r").use { apk ->
            val buffer = ByteBuffer.allocate(0x10)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            apk.seek(apk.length() - 0x6)
            apk.readFully(buffer.array(), 0x0, 0x6)
            val offset = buffer.getInt()
            if (buffer.getShort().toInt() != 0) {
                throw UnsupportedEncodingException("no zip")
            }
            apk.seek((offset - 0x10).toLong())
            apk.readFully(buffer.array(), 0x0, 0x10)
            if (!buffer.array().contentEquals(APK_V2_MAGIC.map { it.code.toByte() }.toByteArray())) {
                throw UnsupportedEncodingException("no apk v2")
            }

            // Read and compare size fields
            apk.seek((offset - 0x18).toLong())
            apk.readFully(buffer.array(), 0x0, 0x8)
            buffer.rewind()
            var size = buffer.getLong().toInt()
            val block = ByteBuffer.allocate(size + 0x8)
            block.order(ByteOrder.LITTLE_ENDIAN)
            apk.seek((offset - block.capacity()).toLong())
            apk.readFully(block.array(), 0x0, block.capacity())
            if (size.toLong() != block.getLong()) {
                throw UnsupportedEncodingException("no apk v2")
            }
            while (block.remaining() > 24) {
                size = block.getLong().toInt()
                if (block.getInt() == 0x7109871a) {
                    // signer-sequence length, signer length, signed data length
                    block.position(block.position() + 12)
                    size = block.getInt() // digests-sequence length

                    // digests, certificates length
                    block.position(block.position() + size + 0x4)
                    size = block.getInt() // certificate length
                    break
                } else {
                    block.position(block.position() + size - 0x4)
                }
            }
            val certificate = ByteArray(size)
            block[certificate]
            return String(toChars(certificate))
        }
    }
}