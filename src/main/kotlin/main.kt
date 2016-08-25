import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord

// inline the main loop because we can
inline fun <T> loop(
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
    val (env, errors) = Env.parse(System.getenv())
    if (errors != null || env == null) {
        for ((name, message) in errors!!)
            println("$name: $message")

        System.exit(1)
        return
    }
    println(env)

    val decryptinator = when (env.type) {
        DecryptionType.ADX -> TODO()
        DecryptionType.BLOWFISH -> Blowfish(env.decryptionKey)
        DecryptionType.OPENX -> TODO()
    }
    val repacker = Packer({ bytes, len -> decryptinator.decrypt(bytes, len) })

    // TODO move kafka init to helper function
    val deserializer = "org.apache.kafka.common.serialization.ByteArrayDeserializer"
    val consumerConfig = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBrokers,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.GROUP_ID_CONFIG to "decrypt-${env.type}-${env.inTopicValues?.joinToString()}",
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to deserializer,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to deserializer // pretty sure we're not using keys at all
    )

    val serializer = "org.apache.kafka.common.serialization.ByteArraySerializer"
    val producerConfig = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBrokers,
            ProducerConfig.ACKS_CONFIG to "1", // we could switch to "all" for paranoia
            ProducerConfig.LINGER_MS_CONFIG to "1", // take a slight latency hit to batch up outgoing messages
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer // pretty sure we're not using keys at all
    )


    val kc = KafkaConsumer<ByteArray, ByteArray>(consumerConfig)
    kc.subscribe(env.incomingTopics.toMutableList())

    val kp = KafkaProducer<ByteArray, ByteArray>(producerConfig)
    try {
        fun poison(record: ConsumerRecord<ByteArray, ByteArray>, exception: Exception) {
            // FIXME send to poison topic
            System.err.println(record)
            System.err.println(exception)
        }

        loop(env.outgoingTopic, kc, kp, ::poison) { msg -> repacker.repack(msg) }
    } finally {
        kc.close()
        kp.close()
    }
}