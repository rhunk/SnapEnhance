package me.rhunk.snapenhance.util.protobuf

data class Wire(val id: Int, val type: WireType, val value: Any)

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
            val type = WireType.fromValue(tag and 0x7) ?: break
            try {
                val value = when (type) {
                    WireType.VARINT -> readVarInt()
                    WireType.FIXED64 -> {
                        val bytes = ByteArray(8)
                        for (i in 0..7) {
                            bytes[i] = readByte()
                        }
                        bytes
                    }
                    WireType.LENGTH_DELIMITED -> {
                        val length = readVarInt().toInt()
                        val bytes = ByteArray(length)
                        for (i in 0 until length) {
                            bytes[i] = readByte()
                        }
                        bytes
                    }
                    WireType.START_GROUP -> {
                        val bytes = mutableListOf<Byte>()
                        while (true) {
                            val b = readByte()
                            if (b.toInt() == WireType.END_GROUP.value) {
                                break
                            }
                            bytes.add(b)
                        }
                        bytes.toByteArray()
                    }
                    WireType.FIXED32 -> {
                        val bytes = ByteArray(4)
                        for (i in 0..3) {
                            bytes[i] = readByte()
                        }
                        bytes
                    }
                    WireType.END_GROUP -> continue
                }
                values.getOrPut(id) { mutableListOf() }.add(Wire(id, type, value))
            } catch (t: Throwable) {
                values.clear()
                break
            }
        }
    }

    fun followPath(vararg ids: Int, excludeLast: Boolean = false, reader: (ProtoReader.() -> Unit)? = null): ProtoReader? {
        var thisReader = this
        ids.let {
            if (excludeLast) {
                it.sliceArray(0 until it.size - 1)
            } else {
                it
            }
        }.forEach { id ->
            if (!thisReader.contains(id)) {
                return null
            }
            thisReader = ProtoReader(thisReader.getByteArray(id) ?: return null)
        }
        if (reader != null) {
            thisReader.reader()
        }
        return thisReader
    }

    fun containsPath(vararg ids: Int): Boolean {
        var thisReader = this
        ids.forEach { id ->
            if (!thisReader.contains(id)) {
                return false
            }
            thisReader = ProtoReader(thisReader.getByteArray(id) ?: return false)
        }
        return true
    }

    fun forEach(reader: (Int, Wire) -> Unit) {
        values.forEach { (id, wires) ->
            wires.forEach { wire ->
                reader(id, wire)
            }
        }
    }

    fun forEach(vararg id: Int, reader: ProtoReader.() -> Unit) {
        followPath(*id)?.eachBuffer { _, buffer ->
            ProtoReader(buffer).reader()
        }
    }

    fun eachBuffer(vararg ids: Int, reader: ProtoReader.() -> Unit) {
        followPath(*ids, excludeLast = true)?.eachBuffer { id, buffer ->
            if (id == ids.last()) {
                ProtoReader(buffer).reader()
            }
        }
    }

    fun eachBuffer(reader: (Int, ByteArray) -> Unit) {
        values.forEach { (id, wires) ->
            wires.forEach { wire ->
                if (wire.type == WireType.LENGTH_DELIMITED) {
                    reader(id, wire.value as ByteArray)
                }
            }
        }
    }

    fun contains(id: Int) = values.containsKey(id)

    fun getWire(id: Int) = values[id]?.firstOrNull()
    fun getRawValue(id: Int) = getWire(id)?.value
    fun getByteArray(id: Int) = getRawValue(id) as? ByteArray
    fun getByteArray(vararg ids: Int) = followPath(*ids, excludeLast = true)?.getByteArray(ids.last())
    fun getString(id: Int) = getByteArray(id)?.toString(Charsets.UTF_8)
    fun getString(vararg ids: Int) = followPath(*ids, excludeLast = true)?.getString(ids.last())
    fun getVarInt(id: Int) = getRawValue(id) as? Long
    fun getVarInt(vararg ids: Int) = followPath(*ids, excludeLast = true)?.getVarInt(ids.last())
    fun getCount(id: Int) = values[id]?.size ?: 0

    fun getFixed64(id: Int): Long {
        val bytes = getByteArray(id) ?: return 0L
        var value = 0L
        for (i in 0..7) {
            value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
        }
        return value
    }


    fun getFixed32(id: Int): Int {
        val bytes = getByteArray(id) ?: return 0
        var value = 0
        for (i in 0..3) {
            value = value or ((bytes[i].toInt() and 0xFF) shl (i * 8))
        }
        return value
    }
}