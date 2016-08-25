import kotlin.test.assertEquals
import org.junit.Test

class RubiconTests {
    private val key = "DEADBEEFDEADBEEF"
    @Test fun hex() {
        // hard coded sanity
        assertEquals(254.toByte(), hexToByte('f'.toByte(), 'e'.toByte()))
        assertEquals(254.toByte(), hexToByte('F'.toByte(), 'E'.toByte()))
        assertEquals(239.toByte(), hexToByte('e'.toByte(), 'f'.toByte()))
        assertEquals(239.toByte(), hexToByte('E'.toByte(), 'F'.toByte()))

        for (i in 0..255) {
            var s = Integer.toHexString(i)
            if (s.length < 2)
                s = '0' + s
            s = s.toLowerCase()

            var result = hexToByte(s[0].toByte(), s[1].toByte())
            assertEquals(result, i.toByte())

            s = s.toUpperCase()
            result = hexToByte(s[0].toByte(), s[1].toByte())
            assertEquals(result, i.toByte())
        }
    }

    @Test fun decrypt() {
        val r = Rubicon(key)
        val expected = "0.238298"
        val inputs = listOf(
                "9CE9A1B1AB9902E2",
                "0x9CE9A1B1AB9902E2",
                "0x9ce9a1b1ab9902e2")

        for (input in inputs) {
            val bytes = encode(input)
            val actual = r.decrypt(bytes, bytes.size)
            assertEquals(expected, actual)
        }

        val examples = mapOf(
            "98FB4D5583FC8482" to "0.180000",
            "3B4517D334A6C03B" to "0.118473",
            "26B2401D9967E4AC" to "0.197758")

        for ((input, expected) in examples) {
            val bytes = encode(input)
            val actual = r.decrypt(bytes, bytes.size)
            assertEquals(expected, actual)
        }
    }

    private fun encode(s: String): ByteArray {
        var buffer = Charsets.US_ASCII.encode(s)
        var bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }
}