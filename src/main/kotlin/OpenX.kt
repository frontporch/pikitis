import org.openx.market.ssrtb.crypter.SsRtbCrypter

class OpenX(encryptKey: String, integrityKey: String) : Decryptinator {
    private val crypter = SsRtbCrypter()
    private val encryptKey = crypter.hexStrToKey(encryptKey)
    private val integrityKey = crypter.hexStrToKey(integrityKey)

    override fun decrypt(bytes: ByteArray, len: Int): String {
        // slow but probably correct version
        // could be faster:
        // - decrypt the bytes directly
        // - base64 decode websafe directly from byte array
        // - reuse HMAC_SHA1 instance
        // - turn byte array of 'long micros' directly to currency string
        val micros = crypter.decodeDecrypt(bytes.toString(Charsets.US_ASCII), encryptKey, integrityKey)
        val currency = micros.toDouble() / 1000000.0
        return currency.toString()
    }
}