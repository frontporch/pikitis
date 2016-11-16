import com.google.doubleclick.crypto.DoubleClickCrypto
import org.apache.commons.codec.binary.Hex
import org.openx.market.ssrtb.crypter.SsRtbCrypter
import java.io.File
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

enum class DecryptionType {
    ADX {
        override val sampleEncryptionKey = "3q2+796tvu/erb7v3q2+796tvu/erb7v3q2+796tvu8="
        override val sampleIntegrityKey = "vu/erb7v3q2+796tvu/erb7v3q2+796tvu/erb7v3q0="
        override fun encrypt(encryptionKey: String, integrityKey: String?, currency: Double): String {

            val b64 = Base64.getDecoder()
            val keys = DoubleClickCrypto.Keys(
                    SecretKeySpec(b64.decode(encryptionKey), "HmacSHA1"),
                    SecretKeySpec(b64.decode(integrityKey!!), "HmacSHA1"))


            val googleCrypto = DoubleClickCrypto.Price(keys)
            return googleCrypto.encodePriceValue(currency, null)
        }
    },
    OPENX {
        override val sampleEncryptionKey = "sIxwz7yw62yrfoLGt12lIHKuYrK/S5kLuApI2BQe7Ac="
        override val sampleIntegrityKey = "v3fsVcMBMMHYzRhi7SpM0sdqwzvAxM6KPTu9OtVod5I="
        override fun encrypt(encryptionKey: String, integrityKey: String?, currency: Double): String {
            val micros = (currency * MICROS_PER_CURRENCY_UNIT).toLong()
            return SsRtbCrypter().encryptEncode(micros, encryptionKey, integrityKey!!)
        }

    },
    RUBICON {
        override val sampleEncryptionKey = "DEADBEEFDEADBEEF"
        override val sampleIntegrityKey = null
        override fun encrypt(encryptionKey: String, integrityKey: String?, currency: Double): String {
            val cipher = Cipher.getInstance("Blowfish/ECB/NoPadding")
            val keySpec = SecretKeySpec(encryptionKey.toByteArray(), "Blowfish")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)

            // TODO don't break for exponential notation
            var currencyString = currency.toString()
            while (currencyString.length % 8 != 0)
                currencyString += '0'

            val bytes = cipher.doFinal(currencyString.toByteArray())
            return Hex.encodeHexString(bytes).toUpperCase()
        }
    };

    abstract fun encrypt(encryptionKey: String, integrityKey: String?, currency: Double): String;
    abstract val sampleEncryptionKey: String
    abstract val sampleIntegrityKey: String?

}

data class Env(
        val type: DecryptionType,
        val kafkaBrokers: String,
        // incoming topic -> outoing topic
        val topics: Map<String, String>,
        // poison topic
        val poison: String,
        val decryptionKey: String,
        val integrityKey: String?,
        val httpPort: Int?) {

    companion object {
        fun parse(variables: Map<String, String>): EnvResult {
            val errors = ArrayList<EnvError>()

            fun <T> get(name: String, required: Boolean = true, then: (String) -> T): T? {
                val value = variables[name]
                if (value == null) {
                    if (required)
                        errors.add(EnvError(name, "missing required environment variable"))
                    return null
                }

                try {
                    return then(value)
                }
                catch (e: Exception) {
                    errors.add(EnvError(name, e.message ?: e.toString()))
                    return null
                }
            }

            fun get(name: String) = get(name) { it }

            fun readAllText(path: String) = File(path).readText().trim()


            val type = get("DECRYPTION_TYPE") {
                try {
                    DecryptionType.valueOf(it.toUpperCase())
                } catch (e: IllegalArgumentException) {
                    throw Exception("Expected one of: ${DecryptionType.values().joinToString()}")
                }
            }

            // require "one to one" or "all to one"; anything else is ambiguous
            val whitespace = Regex("\\s+")
            val inTopics = get("TOPIC_IN") { it.trim().split(whitespace) }
            val outTopics = get("TOPIC_OUT") {
                val topics = it.trim().split(whitespace)
                if (topics.size != 1 && topics.size != inTopics?.size)
                    throw Exception("Expected exactly one TOPIC_OUT or one for each TOPIC_IN")

                topics
            }
            val topicMap = if (inTopics != null && outTopics != null) {
                val many = outTopics.size > 1
                inTopics.withIndex().associate { Pair(it.value, outTopics[if (many) it.index else 0]) }
            } else {
                null
            }

            // validation?
            val poison = get("TOPIC_POISON")
            val brokers = get("KAFKA_BROKERS")
            val decryptionKey = get("DECRYPTION_KEY", then = ::readAllText)
            val integrityKey = get("INTEGRITY_KEY", then = ::readAllText, required = type?.sampleIntegrityKey != null)
            val httpPort = get(name = "HTTP_PORT", required = false, then = String::toInt)

            if (errors.any())
                return EnvResult(null, errors)

            return EnvResult(Env(
                    type!!,
                    brokers!!,
                    topicMap!!,
                    poison!!,
                    decryptionKey!!,
                    integrityKey,
                    httpPort))
        }
    }
}

data class EnvError(val name: String, val message: String)

data class EnvResult(val env: Env?, val errors: Collection<EnvError>? = null)
