package me.rhunk.snapenhance.common.util.protobuf

import java.util.UUID

data class Wire(val id: Int, val type: WireType, val value: Any) {
    fun toReader() = ProtoReader(value as ByteArray)
}

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
            try {
                val tag = readVarInt().toInt()
                val id = tag ushr 3
                val type = WireType.fromValue(tag and 0x7) ?: break
                val value = when (type) {
                    WireType.VARINT -> readVarInt()
                    WireType.FIXED64 -> {
                        val bytes = ByteArray(8)
                        for (i in 0..7) {
                            bytes[i] = readByte()
                        }
                        bytes
                    }
                    WireType.CHUNK -> {
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
                if (wire.type == WireType.CHUNK) {
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

    private fun prettyPrint(tabSize: Int): String {
        val tabLine = "    ".repeat(tabSize)
        val stringBuilder = StringBuilder()
        values.forEach { (id, wires) ->
            wires.forEach { wire ->
                stringBuilder.append(tabLine)
                stringBuilder.append("$id <${wire.type.name.lowercase()}> = ")
                when (wire.type) {
                    WireType.VARINT -> stringBuilder.append("${wire.value}\n")
                    WireType.FIXED64, WireType.FIXED32 -> {
                        //print as double, int, floating point
                        val doubleValue = run {
                            val bytes = wire.value as ByteArray
                            var value = 0L
                            for (i in bytes.indices) {
                                value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
                            }
                            value
                        }.let {
                            if (wire.type == WireType.FIXED32) {
                                it.toInt()
                            } else {
                                it
                            }
                        }

                        stringBuilder.append("$doubleValue/${doubleValue.toDouble().toBits().toString(16)}\n")
                    }
                    WireType.CHUNK -> {
                        fun printArray() {
                            stringBuilder.append("\n")
                            stringBuilder.append("$tabLine    ")
                            stringBuilder.append((wire.value as ByteArray).joinToString(" ") { byte -> "%02x".format(byte) })
                            stringBuilder.append("\n")
                        }
                        runCatching {
                            val array = (wire.value as ByteArray)
                            if (array.isEmpty()) {
                                stringBuilder.append("empty\n")
                                return@runCatching
                            }
                            //auto detect ascii strings
                            if (array.all { it in (0x20..0x7E) || it == 0x0A.toByte() || it == 0x0D.toByte() }) {
                                stringBuilder.append("string: ${array.toString(Charsets.UTF_8)}\n")
                                return@runCatching
                            }

                            // auto detect uuids
                            if (array.size == 16) {
                                val longs = LongArray(2)
                                for (i in 0 .. 7) {
                                    longs[0] = longs[0] or ((array[i].toLong() and 0xFF) shl ((7 - i) * 8))
                                }
                                for (i in 8 .. 15) {
                                    longs[1] = longs[1] or ((array[i].toLong() and 0xFF) shl ((15 - i) * 8))
                                }
                                stringBuilder.append("uuid: ${UUID(longs[0], longs[1])}\n")
                                return@runCatching
                            }

                            ProtoReader(array).prettyPrint(tabSize + 1).takeIf { it.isNotEmpty() }?.let {
                                stringBuilder.append("message:\n")
                                stringBuilder.append(it)
                            } ?: printArray()
                        }.onFailure {
                            printArray()
                        }
                    }
                    else -> stringBuilder.append("unknown\n")
                }
            }
        }

        return stringBuilder.toString()
    }

    override fun toString() = prettyPrint(0)
}