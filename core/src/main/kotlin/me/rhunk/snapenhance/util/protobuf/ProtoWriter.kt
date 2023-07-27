package me.rhunk.snapenhance.util.protobuf

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

    fun writeBuffer(id: Int, value: ByteArray) {
        writeVarInt(id shl 3 or 2)
        writeVarInt(value.size)
        stream.write(value)
    }

    fun writeConstant(id: Int, value: Int) {
        writeVarInt(id shl 3)
        writeVarInt(value)
    }

    fun writeConstant(id: Int, value: Long) {
        writeVarInt(id shl 3)
        writeVarLong(value)
    }

    fun writeString(id: Int, value: String) = writeBuffer(id, value.toByteArray())

    fun write(id: Int, writer: ProtoWriter.() -> Unit) {
        val writerStream = ProtoWriter()
        writer(writerStream)
        writeBuffer(id, writerStream.stream.toByteArray())
    }

    fun write(vararg ids: Int, writer: ProtoWriter.() -> Unit) {
        val writerStream = ProtoWriter()
        writer(writerStream)
        var stream = writerStream.stream.toByteArray()
        ids.reversed().forEach { id ->
            with(ProtoWriter()) {
                writeBuffer(id, stream)
                stream = this.stream.toByteArray()
            }
        }
        stream.let(this.stream::write)
    }

    fun toByteArray(): ByteArray {
        return stream.toByteArray()
    }
}