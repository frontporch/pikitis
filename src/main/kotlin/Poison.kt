import com.jcabi.manifests.Manifests
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.msgpack.core.MessageBufferPacker
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.core.buffer.ArrayBufferOutput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Instant

private fun MessageBufferPacker.packBinary(bytes: ByteArray): MessagePacker {
    this.packBinaryHeader(bytes.size)
    return this.addPayload(bytes)
}

data class Poison(
        val version: Int = 1,

        // original message
        val topic: String,
        val partition: Int,
        val offset: Long,
        val message: ByteArray,

        // meta
        val hostname: String = Poison.hostname,
        val gitOrigin: String = Poison.origin,
        val gitVersion: String = Poison.version,
        val errorUnixMilliseconds: Long = Instant.now().toEpochMilli(),
        val errorMessage: String,
        val errorStack: String) {

    constructor(record: ConsumerRecord<ByteArray, ByteArray>, error: Exception): this(
            topic = record.topic(),
            partition = record.partition(),
            offset = record.offset(),
            message = record.value(),

            errorMessage = error.message ?: error.javaClass.name,
            errorStack = error.stackTrace.joinToString(separator = "\n")
    )

    companion object {
        private val version: String
        private val origin: String
        private val hostname: String


        private val packer = MessagePack.newDefaultBufferPacker()
        private val output = getArrayBufferOutput()
        private val logger: Logger = LoggerFactory.getLogger(Poison::class.java)

        init {
            version = getManifestValue("git-version")
            origin =  getManifestValue("git-origin")
            hostname = InetAddress.getLocalHost().hostName!!
        }

        fun toByteArray(record: ConsumerRecord<ByteArray, ByteArray>, error: Exception): ByteArray {
            val packer = packer
            packer.reset(output)

            // hopefully creating this instance gets optimized out
            val poison = Poison(record, error)

            with (packer) {
                packArrayHeader(11)

                // really, really use the order
                packInt(poison.component1())
                packString(poison.component2())
                packInt(poison.component3())
                packLong(poison.component4())
                packBinary(poison.component5())
                packString(poison.component6())
                packString(poison.component7())
                packString(poison.component8())
                packLong(poison.component9())
                packString(poison.component10())
                packString(poison.component11())
            }

            return packer.toByteArray()
        }

        // there is probably a much better way to do this -- but this works once assembled
        private fun getManifestValue(name: String): String {
            return try {
                Manifests.read(name)!!
            } catch (e: Exception) {
                logger.error("Failed to read manifest value: {}", name)
                "<unknown>"
            }
        }
    }
}