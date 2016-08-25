/**
 * Both decryptor and decrypter looked wrong... I give you the Decryptinator!
 */
interface Decryptinator {
    /**
     * we are only interested in turning cipher text into strings
     */
    fun decrypt(bytes: ByteArray, len: Int): String
}