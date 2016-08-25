import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Blowfish (key: String, bufferSize: Int = 256) : Decryptinator {
    private val cipher = Cipher.getInstance("Blowfish")
    private val buffer = ByteArray(bufferSize)

    init {
        val keySpec = SecretKeySpec(key.toByteArray(), "Blowfish")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
    }

    override fun decrypt(bytes: ByteArray, len: Int): String {
        val out = buffer
        val written = cipher.doFinal(bytes, 0, len, out, out.size)


        // FIXME the result is sitting in out[0..written]
        return written.toString()
    }
}