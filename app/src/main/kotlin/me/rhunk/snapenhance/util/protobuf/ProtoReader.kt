package me.rhunk.snapenhance.util.protobuf

data class Wire(val type: Int, val value: Any)

class ProtoReader(private val buffer: ByteArray) {
    private var offset: Int = 0
    private val values = mutableMapOf<Int, MutableList<Wire>>()

    init {
        read()
    }

    fun getBuffer() = buffer

    private fun readByte() = buffer[offset++]

    private fun readVarInt(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = readByte()
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b.toInt() and 0x80 == 0) {
                break
            }
            shift += 7
        }
        return result
    }

    private fun read() {
        while (offset < buffer.size) {
            val tag = readVarInt().toInt()
            val id = tag ushr 3
            val type = tag and 0x7
            try {
                val value = when (type) {
                    0 -> readVarInt().toString().toByteArray()
                    2 -> {
                        val length = readVarInt().toInt()
                        val value = buffer.copyOfRange(offset, offset + length)
                        offset += length
                        value
                    }
                    else -> break
                }
                values.getOrPut(id) { mutableListOf() }.add(Wire(type, value))
            } catch (t: Throwable) {
                values.clear()
                break
            }
        }
    }

    fun readPath(vararg ids: Int, reader: (ProtoReader.() -> Unit)? = null): ProtoReader? {
        var thisReader = this
        ids.forEach { id ->
            if (!thisReader.exists(id)) {
                return null
            }
            thisReader = ProtoReader(thisReader.get(id) as ByteArray)
        }
        if (reader != null) {
            thisReader.reader()
        }
        return thisReader
    }

    fun pathExists(vararg ids: Int): Boolean {
        var thisReader = this
        ids.forEach { id ->
            if (!thisReader.exists(id)) {
                return false
            }
            thisReader = ProtoReader(thisReader.get(id) as ByteArray)
        }
        return true
    }

    fun getByteArray(id: Int) = values[id]?.first()?.value as ByteArray?
    fun getByteArray(vararg ids: Int): ByteArray? {
        if (ids.isEmpty() || ids.size < 2) {
            return null
        }
        val lastId = ids.last()
        var value: ByteArray? = null
        readPath(*(ids.copyOfRange(0, ids.size - 1))) {
            value = getByteArray(lastId)
        }
        return value
    }

    fun getString(id: Int) = getByteArray(id)?.toString(Charsets.UTF_8)
    fun getString(vararg ids: Int) = getByteArray(*ids)?.toString(Charsets.UTF_8)

    fun getInt(id: Int) = getString(id)?.toInt()
    fun getInt(vararg ids: Int) = getString(*ids)?.toInt()

    fun getLong(id: Int) = getString(id)?.toLong()
    fun getLong(vararg ids: Int) = getString(*ids)?.toLong()

    fun exists(id: Int) = values.containsKey(id)

    fun get(id: Int) = values[id]!!.first().value

    fun isValid() = values.isNotEmpty()

    fun getCount(id: Int) = values[id]!!.size

    fun each(id: Int, reader: ProtoReader.(index: Int) -> Unit) {
        values[id]!!.forEachIndexed { index, _ ->
            ProtoReader(values[id]!![index].value as ByteArray).reader(index)
        }
    }

    fun eachExists(id: Int, reader: ProtoReader.(index: Int) -> Unit) {
        if (!exists(id)) {
            return
        }
        each(id, reader)
    }
}