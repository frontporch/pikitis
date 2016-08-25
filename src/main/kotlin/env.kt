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
        val incomingTopics: Collection<String>,
        val outgoingTopic: String,
        val decryptionKey: String,
        val verificationKey: String?,
        val inTopicValues: Collection<String>?) {

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
                when (it.toLowerCase()) {
                    "adx" -> DecryptionType.ADX
                    "blowfish" -> DecryptionType.RUBICON
                    "openx" -> DecryptionType.OPENX
                    else -> throw Exception("Expected one of: ${DecryptionType.values().joinToString()}")
                }
            }

            var inTopicValues: Collection<String>? = null;
            val topicsIn = get("TOPIC_IN") { topic ->
                val values = variables["TOPIC_IN_VALUES"]
                if (values.isNullOrBlank()) {
                    listOf(topic)
                }
                else {
                    if (!topic.contains('$'))
                        throw Exception("TOPIC_IN_VALUES was provided, expected '$topic' to contain '$'")

                    inTopicValues = values!!.split(",").map { it.trim() }
                    inTopicValues!!.map { topic.replace("$", it) }
                }
            }

            // validation?
            val brokers = get("KAFKA_BROKERS")
            val topicOut = get("TOPIC_OUT")
            val decryptionKey = get("DECRYPTION_KEY", then = ::readAllText)
            val verificationKey = get("VERIFICATION_KEY", required = false, then = ::readAllText)

            if (errors.any())
                return EnvResult(null, errors)

            return EnvResult(Env(
                    type!!,
                    brokers!!,
                    topicsIn!!,
                    topicOut!!,
                    decryptionKey!!,
                    verificationKey,
                    inTopicValues))
        }
    }
}

data class EnvError(val name: String, val message: String)

data class EnvResult(val env: Env?, val errors: Collection<EnvError>? = null)
