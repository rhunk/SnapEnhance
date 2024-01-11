package me.rhunk.snapenhance.mapper.tests

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import me.rhunk.snapenhance.mapper.ClassMapper
import me.rhunk.snapenhance.mapper.impl.*
import org.junit.jupiter.api.Test
import java.io.File


class TestMappings {
    @Test
    fun testMappings() {
        val classMapper = ClassMapper()

        val gson = GsonBuilder().setPrettyPrinting().create()
        val apkFile = File(System.getenv("SNAPCHAT_APK")!!)
        classMapper.loadApk(apkFile.absolutePath)
        runBlocking {
            val result = classMapper.run()
            println("Mappings: ${gson.toJson(result)}")
        }
    }
}
