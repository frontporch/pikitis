import java.io.File

object Encryptinator {
    fun shouldEncryptinate(args: Array<String>) = args.any()

    private class ValidationError(message: String) : Exception(message)

    fun main(args: Array<String>) {
        try {
            run(args)
        } catch (ve: ValidationError) {
            System.err.println(ve.message)
            System.exit(1)
        }
    }

    private fun require(value: Boolean, message: String) {
        if (!value) {
            throw ValidationError(message)
        }
    }

    private fun <T> require(test: () -> T, message: String): T {
        try {
            return test()
        } catch (e: Exception) {
            throw ValidationError(message)
        }
    }

    private fun readAllText(path: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }

        val text = File(path).readText().trim()
        return if (text.isNullOrBlank()) null else text
    }

    private fun run(args: Array<String>) {
       val usage = "Usage: ${DecryptionType.values().joinToString(separator = "|")} currency"
        require(args.size == 2, usage)
        val type = require({ DecryptionType.valueOf(args[0].toUpperCase()) }, usage)
        val currency = require({ args[1].toDouble() }, usage)
        val env = System.getenv()
        val encryptionKey = readAllText(env["DECRYPTION_KEY"]) ?: type.sampleEncryptionKey
        val integrityKey = readAllText(env["INTEGRITY_KEY"]) ?: type.sampleIntegrityKey

        println(type.encrypt(encryptionKey, integrityKey, currency))
    }
}
