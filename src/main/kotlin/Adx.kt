import com.google.doubleclick.crypto.DoubleClickCrypto
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class Adx(encryptKey: String, integrityKey: String) : Decryptinator {
    private val crypto: DoubleClickCrypto.Price

    init {
        // google unit test used base64 url
        // ... but our keys look standard base64 ?!?
        val b64 = Base64.getDecoder()
        val keys = DoubleClickCrypto.Keys(
                SecretKeySpec(b64.decode(encryptKey), "HmacSHA1"),
                SecretKeySpec(b64.decode(integrityKey), "HmacSHA1"))

        crypto = DoubleClickCrypto.Price(keys)
    }

    override fun decrypt(bytes: ByteArray, len: Int): String {
        // could be somewhat faster (also see OpenX decryptinator)
        // - google's code looks reasonably tight
        // - getting from `bytes` here to HMAC instances could be more direct (less intermediate arrays and string)
        //
        // Calling `DoubleClickCrypto.decrypt` directly
        // and then working from returned byte array is the most we'd probably ever do
        // (byte array of Long straight to currency unit string)

        val s = String(bytes, 0, len, Charsets.US_ASCII)
        val micros = crypto.decodePriceMicros(s)
        return microsToCurrency(micros)
    }
}
