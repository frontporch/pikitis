import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

fun hexToByte(lhs: Byte, rhs: Byte): Byte {
    var l = lhs.toInt() and 0b1001111
    var r = rhs.toInt() and 0b1001111

    // would love a branchless way to do this
    if (l > 10)
        l -= 55
    if (r > 10)
        r -= 55

    return ((l.shl(4) + r) and 255).toByte()
}

class Rubicon (key: String, bufferSize: Int = 256) : Decryptinator {
    private val cipher = Cipher.getInstance("Blowfish/ECB/NoPadding")
    private val buffer = ByteArray(bufferSize)

    init {
        val keySpec = SecretKeySpec(key.toByteArray(), "Blowfish")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
    }

    override fun decrypt(bytes: ByteArray, len: Int): String {
        val out = buffer

        /* Strip off the 0x if you see it (classic prefix for hex encoding) */
        var ix = 0
        if (len > 2 && bytes[0] == '0'.toByte() && bytes[1] == 'x'.toByte())
            ix = 2

        var dst = 0
        /* Assume it's a sequence of hexadecimal numbers [0-F] and read as a string */
        /* Convert to an array of uint8_t types (every two chars = 1 byte) */
        while (ix < len) {
            out[dst] = hexToByte(bytes[ix], bytes[ix + 1])

            dst += 1
            ix += 2
        }

        /* Decrypt to array of bytes with your key */
        /* Interpret as ascii string and parse to a double */
        val written = cipher.doFinal(out, 0, dst, out, dst)

        // we need the string
        return String(out, dst, written, Charsets.US_ASCII)
    }
}