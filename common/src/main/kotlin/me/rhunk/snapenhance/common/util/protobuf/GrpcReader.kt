package me.rhunk.snapenhance.common.util.protobuf

class GrpcReader(
    private val buffer: ByteArray
) {
    private val _messages = mutableListOf<ProtoReader>()
    private val _headers = mutableMapOf<String, String>()

    val headers get() = _headers.toMap()
    val messages get() = _messages.toList()

    fun read(reader: ProtoReader.() -> Unit) {
        messages.forEach { message ->
            message.reader()
        }
    }

    private var position: Int = 0

    init {
        read()
    }

    private fun readByte() = buffer[position++].toInt()

    private fun readUInt32() = (readByte() and 0xFF) shl 24 or
            ((readByte() and 0xFF) shl 16) or
            ((readByte() and 0xFF) shl 8) or
            (readByte() and 0xFF)

    private fun read() {
        while (position < buffer.size) {
            when (val type = readByte() and 0xFF) {
                0 -> {
                    val length = readUInt32()
                    val value = buffer.copyOfRange(position, position + length)
                    position += length
                    _messages.add(ProtoReader(value))
                }
                128 -> {
                    val length = readUInt32()
                    val rawHeaders = String(buffer.copyOfRange(position, position + length), Charsets.UTF_8)
                    position += length
                    rawHeaders.trim().split("\n").forEach { header ->
                        val (key, value) = header.split(":")
                        _headers[key] = value
                    }
                }
                else -> throw Exception("Unknown type $type")
            }
        }
    }
}