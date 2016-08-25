import org.msgpack.core.MessagePack
import org.msgpack.core.buffer.ArrayBufferInput
import org.msgpack.core.buffer.ArrayBufferOutput

// FIXME rename to Repacker (same for file, tests, etc)
/**
 * NOT THREAD SAFE
 */
class Repacker(val transform: (buffer: ByteArray, len: Int) -> String) {
    private var buffer = ByteArray(256)
    private val unpacker = MessagePack.newDefaultUnpacker(buffer)
    private val packer = MessagePack.newDefaultBufferPacker()
    private val output = ArrayBufferOutput() // TODO a subclass is probably needed to get more array reuse

    /**
     * Creates new msgpack array from `packed`
     *
     * Value to transform must be at index 1 (second item)
     * Transformed value will be returned at index 2 (third item)
     * `packed` must have nil at index 2
     *
     * The returned array will have the same number of items as `packed`
     * items at index 0, 1, 3+ will be reproduced exactly as is
     * index 2 will be the only difference between the two arrays
     *
     * @param packed an encoded msgpack array
     * @return an encoded msgpack array
     */
    fun repack(packed: ByteArray): ByteArray {
        // reset everything
        val unpacker = unpacker
        val packer = packer
        unpacker.reset(ArrayBufferInput(packed))
        packer.reset(output)


        // array length
        packer.packArrayHeader(unpacker.unpackArrayHeader())

        // index 0 MUST int (version)
        packer.packInt(unpacker.unpackInt())

        // index 1 MUST byte array
        val len = unpacker.unpackBinaryHeader()
        val buffer = getBuffer(len)
        unpacker.readPayload(buffer, 0, len)
        packer.packBinaryHeader(len)
        packer.writePayload(buffer, 0, len)

        // index 2 transform result
        val result = transform(buffer, len)
        packer.packString(result)
        unpacker.unpackNil()

        // (cheat and) copy the rest verbatim
        val remaining = packed.size - unpacker.totalReadBytes
        packer.writePayload(packed, unpacker.totalReadBytes.toInt(), remaining.toInt())

        val bytes = packer.toByteArray()
        output.clear() // see `output` sad panda on property declaration
        return bytes
    }

    private fun getBuffer(len: Int): ByteArray {
        var buffer = buffer
        if (buffer.size >= len)
            return buffer

        // use next power of 2
        buffer = ByteArray(Integer.highestOneBit(len) * 2)
        this.buffer = buffer
        return buffer
    }
}