import com.google.doubleclick.crypto.DoubleClickCrypto
import kotlin.test.assertEquals
import org.junit.Test
import java.util.*
import javax.crypto.spec.SecretKeySpec

class AdxTests {
    val encryptKey = "3q2+796tvu/erb7v3q2+796tvu/erb7v3q2+796tvu8="
    val integrityKey = "vu/erb7v3q2+796tvu/erb7v3q2+796tvu/erb7v3q0="

    @Test
    fun decryptinator() {
        val b64 = Base64.getDecoder()
        val keys = DoubleClickCrypto.Keys(
                SecretKeySpec(b64.decode(encryptKey), "HmacSHA1"),
                SecretKeySpec(b64.decode(integrityKey), "HmacSHA1"))

        val googleCrypto = DoubleClickCrypto.Price(keys)
        val acmeCrypto = Adx(encryptKey, integrityKey)

        val rand = Random(42)
        val values = mutableListOf<Long>(0, 1, 2, 3)
        values.addAll((0..1000).map { Math.abs(rand.nextLong()) })

        for (micros in values) {
            val expected = (micros / MICROS_PER_CURRENCY_UNIT).toString()
            val encoded = googleCrypto.encodePriceMicros(micros, null).toByteArray()
            val actual = acmeCrypto.decrypt(encoded, encoded.size)
            assertEquals(expected, actual)

            // with extra bytes
            assertEquals(expected, acmeCrypto.decrypt(encoded.copyOf(encoded.size + 1), encoded.size))
            assertEquals(expected, acmeCrypto.decrypt(encoded.copyOf(encoded.size + 2), encoded.size))
            assertEquals(expected, acmeCrypto.decrypt(encoded.copyOf(encoded.size + 3), encoded.size))
        }
    }
}
