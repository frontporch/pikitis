import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.WakeupException
import java.util.concurrent.atomic.AtomicBoolean

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
    val (env, errors) = Env.parse(System.getenv())
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