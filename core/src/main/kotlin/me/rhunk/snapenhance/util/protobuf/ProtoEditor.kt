package me.rhunk.snapenhance.util.protobuf

class ProtoEditor(
    private var buffer: ByteArray
) {
    fun edit(vararg path: Int, callback: ProtoWriter.() -> Unit) {
        val writer = ProtoWriter()
        callback(writer)
        buffer = writeAtPath(path, 0, ProtoReader(buffer), writer.toByteArray())
    }

    private fun writeAtPath(path: IntArray, currentIndex: Int, rootReader: ProtoReader, bufferToWrite: ByteArray): ByteArray {
        if (currentIndex == path.size) {
            return bufferToWrite
        }
        val id = path[currentIndex]
        val output = ProtoWriter()
        val wires = mutableListOf<Pair<Int, ByteArray>>()

        rootReader.list { tag, value ->
            if (tag == id) {
                val childReader = rootReader.readPath(id)
                if (childReader == null) {
                    wires.add(Pair(tag, value))
                    return@list
                }
                wires.add(Pair(tag, writeAtPath(path, currentIndex + 1, childReader, bufferToWrite)))
                return@list
            }
            wires.add(Pair(tag, value))
        }

        wires.forEach { (tag, value) ->
            output.writeBuffer(tag, value)
        }

        return output.toByteArray()
    }

    fun toByteArray() = buffer
}