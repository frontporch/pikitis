import io.prometheus.client.Counter
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.vertx.MetricsHandler
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future

object Main {
    val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    val workerRecordsReceived = Counter.build()
            .name("worker_records_received_total")
            .help("Total records received")
            .labelNames("job")
            .register()

    val workerRecordErrors = Counter.build()
            .name("worker_records_error_total")
            .help("Total records that could not be parsed")
            .labelNames("job")
            .register()

    // inline the main loop because we can
    inline fun <T> loop(
            jobName: String,
            topics: Map<String, String>,
            consumer: Consumer<T, T>,
            producer: Producer<T, T>,
            poison: (ConsumerRecord<T, T>, Exception) -> Unit,
            transform: (T) -> T) {

        val recordsReceived = workerRecordsReceived.labels(jobName)
        val recordErrors = workerRecordErrors.labels(jobName)

        val futures = arrayListOf<Future<RecordMetadata>>()
        while (true) {
            val records = consumer.poll(1000)
            logger.debug("Got {} records", records.count())
            futures.clear()
            futures.ensureCapacity(records.count())

            for (record in records) {
                recordsReceived.inc()
                val before = record.value()
                val after = try {
                    transform(before)
                } catch (e: Exception) {
                    recordErrors.inc()
                    poison(record, e)
                    continue
                }

                val outTopic = topics[record.topic()]
                val future = producer.send(ProducerRecord(outTopic, after))
                futures.add(future)
            }

            // ensure we've succeeded before committing anything
            for (future in futures)
                future.get()

            // finally
            consumer.commitSync()
        }
    }

    fun main() {
        val (env, errors) = Env.parse(System.getenv())
        if (errors != null || env == null) {
            for ((name, message) in errors!!)
                logger.error("$name: $message")

            System.exit(1)
            return
        }
        logger.info(env.toString())
        val httpServer = if (env.httpPort != null) {
            DefaultExports.initialize()

            val vertx = Vertx.vertx()
            val server = vertx.createHttpServer()
            val router = Router.router(vertx)
            router.route("/metrics").handler(MetricsHandler())
            server.requestHandler({ router.accept(it) }).listen(env.httpPort)
        } else {
            null
        }

        val repacker = Repacker(when (env.type) {
            DecryptionType.ADX -> Adx(env.decryptionKey, env.integrityKey!!)
            DecryptionType.RUBICON -> Rubicon(env.decryptionKey)
            DecryptionType.OPENX -> OpenX(env.decryptionKey, env.integrityKey!!)
        })

        val deserializer = "org.apache.kafka.common.serialization.ByteArrayDeserializer"
        val consumerConfig = mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to env.kafkaBrokers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.GROUP_ID_CONFIG to "decrypt-${env.type}-${env.topics.keys.joinToString()}",
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
        kc.subscribe(env.topics.keys.toMutableList())

        val kp = KafkaProducer<ByteArray, ByteArray>(producerConfig)

        fun poison(record: ConsumerRecord<ByteArray, ByteArray>, exception: Exception) {
            val poisonMessage = Poison.toByteArray(record, exception)
            kp.send(ProducerRecord(env.poison, poisonMessage))
        }

        fun transform(bytes: ByteArray) = repacker.repack(bytes)

        try {
            loop(env.type.name, env.topics, kc, kp, ::poison, ::transform)
        } finally {
            kc.close()
            kp.close()
            httpServer?.close()
        }
    }
}

fun main(args: Array<String>) {
    if (Encryptinator.shouldEncryptinate(args)) {
        Encryptinator.main(args)
    } else {
        Main.main()
    }
}
