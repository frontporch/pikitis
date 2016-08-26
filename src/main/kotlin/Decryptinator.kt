const val MICROS_PER_CURRENCY_UNIT = 1000000.0
/**
 * Both the names decryptor and decrypter looked wrong... I give you the Decryptinator!
 */
interface Decryptinator {
    /**
     * we are only interested in turning cipher text into strings
     */
    fun decrypt(bytes: ByteArray, len: Int): String

    fun microsToCurrency(micros: Long): String {
        // this could be faster
        // effectively this is regular integer to string (base 10)
        // just with a fixed decimal point 7 positions from the end of the string
        val currency = micros.toDouble() / MICROS_PER_CURRENCY_UNIT
        return currency.toString()
    }
}