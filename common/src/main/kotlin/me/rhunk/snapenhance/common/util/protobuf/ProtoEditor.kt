package me.rhunk.snapenhance.common.util.protobuf


typealias WireCallback = EditorContext.() -> Unit

class EditorContext(
    private val wires: MutableMap<Int, MutableList<Wire>>
) {
    fun clear() {
        wires.clear()
    }
    fun addWire(wire: Wire) {
        wires.getOrPut(wire.id) { mutableListOf() }.add(wire)
    }
    fun addVarInt(id: Int, value: Int) = addVarInt(id, value.toLong())
    fun addVarInt(id: Int, value: Long) = addWire(Wire(id, WireType.VARINT, value))
    fun addBuffer(id: Int, value: ByteArray) = addWire(Wire(id, WireType.CHUNK, value))
    fun add(id: Int, content: ProtoWriter.() -> Unit) = addBuffer(id, ProtoWriter().apply(content).toByteArray())
    fun addString(id: Int, value: String) = addBuffer(id, value.toByteArray())
    fun addFixed64(id: Int, value: Long) = addWire(Wire(id, WireType.FIXED64, value))
    fun addFixed32(id: Int, value: Int) = addWire(Wire(id, WireType.FIXED32, value))

    fun firstOrNull(id: Int) = wires[id]?.firstOrNull()
    fun getOrNull(id: Int) = wires[id]
    fun get(id: Int) = wires[id]!!

    fun remove(id: Int) = wires.remove(id)
    fun remove(id: Int, index: Int) = wires[id]?.removeAt(index)

    fun edit(id: Int, callback: EditorContext.() -> Unit) {
        val wire = wires[id]?.firstOrNull() ?: return
        val editor = ProtoEditor(wire.value as ByteArray)
        editor.edit {
            callback()
        }
        remove(id)
        addBuffer(id, editor.toByteArray())
    }

    fun editEach(id: Int, callback: EditorContext.() -> Unit) {
        val wires = wires[id] ?: return
        val newWires = mutableListOf<Wire>()
        wires.toList().forEachIndexed { _, wire ->
            val editor = ProtoEditor(wire.value as ByteArray)
            editor.edit {
                callback()
            }
            newWires.add(Wire(wire.id, WireType.CHUNK, editor.toByteArray()))
        }
        wires.clear()
        wires.addAll(newWires)
    }
}

class ProtoEditor(
    private var buffer: ByteArray
) {
    fun edit(vararg path: Int, callback: WireCallback) {
        buffer = writeAtPath(path, 0, ProtoReader(buffer), callback)
    }

    private fun writeAtPath(path: IntArray, currentIndex: Int, rootReader: ProtoReader, wireToWriteCallback: WireCallback): ByteArray {
        val id = path.getOrNull(currentIndex)
        val output = ProtoWriter()
        val wires = sortedMapOf<Int, MutableList<Wire>>()

        rootReader.forEach { wireId, value ->
            wires.putIfAbsent(wireId, mutableListOf())
            if (id != null && wireId == id) {
                val childReader = rootReader.followPath(id)
                if (childReader == null) {
                    wires.getOrPut(wireId) { mutableListOf() }.add(value)
                    return@forEach
                }
                wires[wireId]!!.add(Wire(wireId, WireType.CHUNK, writeAtPath(path, currentIndex + 1, childReader, wireToWriteCallback)))
                return@forEach
            }
            wires[wireId]!!.add(value)
        }

        if (currentIndex == path.size) {
            wireToWriteCallback(EditorContext(wires))
        }

        wires.values.flatten().forEach(output::addWire)

        return output.toByteArray()
    }

    fun toByteArray() = buffer
}