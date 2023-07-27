package android.util

object Log {
    @JvmStatic
    fun d(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    @JvmStatic
    fun i(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }

    @JvmStatic
    fun v(tag: String, msg: String): Int {
        println("[$tag] $msg")
        return 0
    }
}