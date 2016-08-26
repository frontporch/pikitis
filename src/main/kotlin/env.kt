import java.io.File
import java.util.*

enum class DecryptionType {
    ADX,
    OPENX,
    RUBICON,
}

data class Env(
        val type: DecryptionType,
        val kafkaBrokers: String,
        // incoming topic -> outoing topic
        val topics: Map<String, String>,
        // poison topic
        val poison: String,
        val decryptionKey: String,
        val integrityKey: String?) {

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

            fun DecryptionType?.integrity() = this == DecryptionType.OPENX || this == DecryptionType.ADX

            // validation?
            val poison = get("TOPIC_POISON")
            val brokers = get("KAFKA_BROKERS")
            val decryptionKey = get("DECRYPTION_KEY", then = ::readAllText)
            val integrityKey = get("INTEGRITY_KEY", then = ::readAllText, required = type.integrity())

            if (errors.any())
                return EnvResult(null, errors)

            return EnvResult(Env(
                    type!!,
                    brokers!!,
                    topicMap!!,
                    poison!!,
                    decryptionKey!!,
                    integrityKey))
        }
    }
}

data class EnvError(val name: String, val message: String)

data class EnvResult(val env: Env?, val errors: Collection<EnvError>? = null)
