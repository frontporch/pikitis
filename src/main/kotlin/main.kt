import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.WakeupException
import org.msgpack.core.MessagePack
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

enum class DecryptionType {
    ADX,
    BLOWFISH,
    OPENX,
}

data class Env(
        val type: DecryptionType,
        val kafkaBrokers: String,
        val incomingTopics: Collection<String>,
        val outgoingTopic: String,
        val decryptionKey: String,
        val verificationKey: String?,
        val inTopicValues: Collection<String>?)

data class EnvError(val name: String, val message: String)

data class EnvResult(val env: Env?, val errors: Collection<EnvError>? = null)

fun parseEnv(variables: Map<String, String>): EnvResult {
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
            "blowfish" -> DecryptionType.BLOWFISH
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


fun <T> loop(
        topic: String,
        consumer: Consumer<T, T>,
        producer: Producer<T, T>,
        poison: (ConsumerRecord<T, T>, Exception) -> Unit,
        transform: (T) -> T) {

    while (true) {
        val records = consumer.poll(1000)
        for (record in records) {
            val before = record.value()
            val after = try {
                transform(before)
            } catch (e: Exception) {
                poison(record, e)
                continue
            }

            producer.send(ProducerRecord(topic, after))
        }
        consumer.commitSync()
    }
}

fun main(args: Array<String>) {
    val (env, errors) = parseEnv(System.getenv())
    if (errors != null || env == null) {
        for ((name, message) in errors!!)
            println("$name: $message")

        System.exit(1)
        return
    }
    println(env)

    val deserializer = "org.apache.kafka.common.serialization.StringDeserializer"
    val consumerConfig = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBrokers,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.GROUP_ID_CONFIG to "decrypt-${env.type}-${env.inTopicValues?.joinToString()}",
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to deserializer,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to deserializer // pretty sure we're not using keys at all
    )

    val serializer = "org.apache.kafka.common.serialization.StringSerializer"
    val producerConfig = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBrokers,
            ProducerConfig.ACKS_CONFIG to "1", // we could switch to "all" for paranoia
            ProducerConfig.LINGER_MS_CONFIG to "1", // take a slight latency hit to batch up outgoing messages
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer // pretty sure we're not using keys at all
    )



    // TODO switch to bytes
    val kc = KafkaConsumer<String, String>(consumerConfig)
    kc.subscribe(env.incomingTopics.toMutableList())
    val closed = AtomicBoolean(false)
    Runtime.getRuntime().addShutdownHook(Thread {
        println("this is the shutdown stuff")
        closed.set(true)
        kc.wakeup()
    })

    val kp = KafkaProducer<String, String>(producerConfig)
    try {
        fun poison(record: ConsumerRecord<String, String>, exception: Exception) {
            // FIXME send to poison topic
            System.err.println(record)
            System.err.println(exception)
        }

        loop(env.outgoingTopic, kc, kp, ::poison) {
            // TODO
            // - deserialize
            // - decrypt
            it
        }
    } catch (e: WakeupException) {
        if (!closed.get()) throw e
    } finally {
        kc.close()
        kp.close()
        println("see ya")
    }
}