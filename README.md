# pikitis

kafka transform decrypt

## See also

<https://github.com/openx/SSRTBPriceCrypter>


## Environment Variables

This console app uses environment variables for all configuration, most are required.

| Environment Variable | Required | Description |
| :---                 | :---     | :---        |
| `DECRYPTION_TYPE`    | yes      | `openx` or `adx` or `rubicon` |
| `TOPIC_IN`           | yes      | One or more topics space separated |
| `TOPIC_OUT`          | yes      | One or more topics space separated. Must be exactly one or one for each `TOPIC_IN`|
| `TOPIC_POISON`       | yes      | Topic for poison messages. Possible reasons: failed to deserialize, failed to decrypt, integrity check failed, etc. |
| `KAFKA_BROKERS`      | yes      | Kafka `bootstrap.servers` configuration |
| `DECRYPTION_KEY`     | yes      | Path to file containing decryption key from exchange |
| `INTEGRITY_KEY`      | if `openx` or `adx` | Path to file containing integrity key from exchange |
| `JAVA_OPTS`          | no       | See [Logging](#Logging) |

## Logging

Use environmental variable `JAVA_OPTS="-Dlogback.configurationFile=/path/to/logback.xml"`.
Configure according to  <http://logback.qos.ch/manual/configuration.html>

See [default logback.xml](src/main/resources/logback.xml)


## License

Apache 2.0
