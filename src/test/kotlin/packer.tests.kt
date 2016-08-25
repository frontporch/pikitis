import kotlin.test.assertEquals
import org.junit.Test
import org.msgpack.core.MessagePack
import org.msgpack.value.Value
import org.msgpack.value.ValueFactory
import org.msgpack.value.ValueType

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

private fun bytesToHex(buffer: ByteArray) = bytesToHex(buffer, buffer.count())
private fun bytesToHex(buffer: ByteArray, len: Int): String {
    val result = StringBuffer(len * 2)

    var ix = 0
    while (ix < len) {
        val octet = buffer[ix].toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
        ix++
    }

    return result.toString()
}

private fun Value.toAny(): Any? = when (this.valueType) {
    ValueType.NIL -> null
    ValueType.BOOLEAN -> this.asBooleanValue().boolean
    ValueType.INTEGER -> this.asIntegerValue().toLong()
    ValueType.FLOAT -> this.asFloatValue().toFloat()
    ValueType.STRING -> this.asStringValue().toString()
    ValueType.BINARY -> this.asBinaryValue().asByteArray()
    ValueType.ARRAY -> this.asArrayValue().map { it.toAny() }
    ValueType.MAP -> TODO()
    ValueType.EXTENSION -> TODO()
}

private class MessageBuilder {
    private val values = mutableListOf<Value>()

    fun integer(value: Int) {
        values.add(ValueFactory.newInteger(value))
    }

    fun nil() {
        values.add(ValueFactory.newNil())
    }

    fun binary(value: ByteArray) {
        values.add(ValueFactory.newBinary(value))
    }

    fun string(value: String) {
        values.add(ValueFactory.newString(value))
    }

    fun float(value: Double) {
        values.add(ValueFactory.newFloat(value))
    }

    fun toByteArray(): ByteArray {
        val packer = MessagePack.newDefaultBufferPacker()

        packer.packArrayHeader(values.count())
        for (v in values)
            packer.packValue(v)

        return packer.toByteArray()
    }

    fun values() = values.map { it.toAny() }
}

private fun build(init: MessageBuilder.() -> Unit): MessageBuilder {
    var b = MessageBuilder()
    b.init()
    return b
}

private fun values(buffer: ByteArray): MutableList<Any?> {
    val unpacker = MessagePack.newDefaultUnpacker(buffer)
    val items = mutableListOf<Any?>()
    val len = unpacker.unpackArrayHeader()
    while (unpacker.hasNext())
        items.add(unpacker.unpackValue().toAny())

    assertEquals(items.count(), len)
    return items
}


class PackerTest {
    @Test fun shouldWorkMultipleTimes() {
        val packer = Packer(::bytesToHex)
        val msg = build {
            integer(1)
            binary(byteArrayOf(1, 2, 3))
            nil()
            string("hello")
            string("world")
        }

        for (i in 1..10)
            test(packer, msg)
    }

    @Test fun shouldHandleAssortedTypesAndLengths() {
        val packer = Packer(::bytesToHex)
        test(packer, build {
            integer(1)
            binary(byteArrayOf(42))
            nil()
        })

        test(packer, build {
            integer(2)
            binary(byteArrayOf())
            nil()
            nil()
        })

        test(packer, build {
            integer(2)
            binary(byteArrayOf(0, 0))
            nil()
            float(Math.PI)
        })
    }

    private fun test(packer: Packer, msg: MessageBuilder) {
        val expected = msg.values().toMutableList()
        expected[2] = bytesToHex(expected[1] as ByteArray)
        val beforeBytes = msg.toByteArray()
        val afterBytes = packer.repack(beforeBytes)
        val actual = values(afterBytes)

        hexify(expected, actual)
        assertEquals(expected, actual)
    }

    // turn the ByteArray to hex before comparison :(
    private fun hexify(vararg items: MutableList<Any?>) {
        for (list in items)
            list[1] = bytesToHex(list[1] as ByteArray)
    }

}