import kotlin.test.assertEquals
import org.junit.Test
import org.openx.market.ssrtb.crypter.SsRtbCrypter
import javax.crypto.spec.SecretKeySpec

class OpenxTests {

    val encryptionKeyBytes = byteArrayOf(
            0xb0.toByte(), 0x8c.toByte(), 0x70.toByte(), 0xcf.toByte(), 0xbc.toByte(), 0xb0.toByte(), 0xeb.toByte(), 0x6c.toByte(),
            0xab.toByte(), 0x7e.toByte(), 0x82.toByte(), 0xc6.toByte(), 0xb7.toByte(), 0x5d.toByte(), 0xa5.toByte(), 0x20.toByte(),
            0x72.toByte(), 0xae.toByte(), 0x62.toByte(), 0xb2.toByte(), 0xbf.toByte(), 0x4b.toByte(), 0x99.toByte(), 0x0b.toByte(),
            0xb8.toByte(), 0x0a.toByte(), 0x48.toByte(), 0xd8.toByte(), 0x14.toByte(), 0x1e.toByte(), 0xec.toByte(), 0x07.toByte())

    val integrityKeyBytes = byteArrayOf(
            0xbf.toByte(), 0x77.toByte(), 0xec.toByte(), 0x55.toByte(), 0xc3.toByte(), 0x01.toByte(), 0x30.toByte(), 0xc1.toByte(),
            0xd8.toByte(), 0xcd.toByte(), 0x18.toByte(), 0x62.toByte(), 0xed.toByte(), 0x2a.toByte(), 0x4c.toByte(), 0xd2.toByte(),
            0xc7.toByte(), 0x6a.toByte(), 0xc3.toByte(), 0x3b.toByte(), 0xc0.toByte(), 0xc4.toByte(), 0xce.toByte(), 0x8a.toByte(),
            0x3d.toByte(), 0x3b.toByte(), 0xbd.toByte(), 0x3a.toByte(), 0xd5.toByte(), 0x68.toByte(), 0x77.toByte(), 0x92.toByte())

    val encryptKey = SecretKeySpec(encryptionKeyBytes, "HmacSHA1");
    val integrityKey = SecretKeySpec(integrityKeyBytes, "HmacSHA1");

    @Test fun encryptsDirectly() {
        val crypter = SsRtbCrypter()

        val value: Long = 709959680

        val ciphered = crypter.encryptEncode(value, encryptKey, integrityKey)
        val unciphered = crypter.decodeDecrypt(ciphered, encryptKey, integrityKey)

        assertEquals(value, unciphered)
    }

    @Test
    fun decryptsDirectly() {
        assertEquals(709959680, SsRtbCrypter().decodeDecrypt("SjpvRwAB4kB7jEpgW5IA8p73ew9ic6VZpFsPnA==", encryptKey, integrityKey))
    }
}