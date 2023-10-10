package me.rhunk.snapenhance.common.util.protobuf

import java.io.ByteArrayOutputStream

class ProtoWriter {
    private val stream: ByteArrayOutputStream = ByteArrayOutputStream()

    private fun writeVarInt(value: Int) {
        var v = value
        while (v and -0x80 != 0) {
            stream.write(v and 0x7F or 0x80)
            v = v ushr 7
        }
        stream.write(v)
    }

    private fun writeVarLong(value: Long) {
        var v = value
        while (v and -0x80L != 0L) {
            stream.write((v and 0x7FL or 0x80L).toInt())
            v = v ushr 7
        }
        stream.write(v.toInt())
    }

    fun addBuffer(id: Int, value: ByteArray) {
        writeVarInt(id shl 3 or WireType.CHUNK.value)
        writeVarInt(value.size)
        stream.write(value)
    }

    fun addVarInt(id: Int, value: Int) = addVarInt(id, value.toLong())

    fun addVarInt(id: Int, value: Long) {
        writeVarInt(id shl 3)
        writeVarLong(value)
    }

    fun addString(id: Int, value: String) = addBuffer(id, value.toByteArray())

    fun addFixed32(id: Int, value: Int) {
        writeVarInt(id shl 3 or WireType.FIXED32.value)
        val bytes = ByteArray(4)
        for (i in 0..3) {
            bytes[i] = (value shr (i * 8)).toByte()
        }
        stream.write(bytes)
    }

    fun addFixed64(id: Int, value: Long) {
        writeVarInt(id shl 3 or WireType.FIXED64.value)
        val bytes = ByteArray(8)
        for (i in 0..7) {
            bytes[i] = (value shr (i * 8)).toByte()
        }
        stream.write(bytes)
    }

    fun from(id: Int, writer: ProtoWriter.() -> Unit) {
        val writerStream = ProtoWriter()
        writer(writerStream)
        addBuffer(id, writerStream.stream.toByteArray())
    }

    fun from(vararg ids: Int, writer: ProtoWriter.() -> Unit) {
        val writerStream = ProtoWriter()
        writer(writerStream)
        var stream = writerStream.stream.toByteArray()
        ids.reversed().forEach { id ->
            with(ProtoWriter()) {
                addBuffer(id, stream)
                stream = this.stream.toByteArray()
            }
        }
        stream.let(this.stream::write)
    }

    fun addWire(wire: Wire) {
        writeVarInt(wire.id shl 3 or wire.type.value)
        when (wire.type) {
            WireType.VARINT -> writeVarLong(wire.value as Long)
            WireType.FIXED64, WireType.FIXED32 -> {
                when (wire.value) {
                    is Int -> {
                        val bytes = ByteArray(4)
                        for (i in 0..3) {
                            bytes[i] = (wire.value shr (i * 8)).toByte()
                        }
                        stream.write(bytes)
                    }
                    is Long -> {
                        val bytes = ByteArray(8)
                        for (i in 0..7) {
                            bytes[i] = (wire.value shr (i * 8)).toByte()
                        }
                        stream.write(bytes)
                    }
                    is ByteArray -> stream.write(wire.value)
                }
            }
            WireType.CHUNK -> {
                val value = wire.value as ByteArray
                writeVarInt(value.size)
                stream.write(value)
            }
            WireType.START_GROUP -> {
                val value = wire.value as ByteArray
                stream.write(value)
            }
            WireType.END_GROUP -> return
        }
    }

    fun toByteArray(): ByteArray {
        return stream.toByteArray()
    }
}