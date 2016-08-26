import org.openx.market.ssrtb.crypter.SsRtbCrypter

class OpenX(encryptKey: String, integrityKey: String) : Decryptinator {
    private val crypto = SsRtbCrypter()
    private val encryptKey = crypto.hexStrToKey(encryptKey)
    private val integrityKey = crypto.hexStrToKey(integrityKey)

    override fun decrypt(bytes: ByteArray, len: Int): String {
        // could be faster:
        // - base64 url decode directly from byte array
        // - reuse arrays in `decrypt`
        // - reuse HMAC_SHA1 instances in `decrypt`
        // - after decryption turn byte array segment directly to long
        //
        // hopefully the crypto overhead dwarfs any of these potential optimizations anyway

        val s = String(bytes, 0, len, Charsets.US_ASCII)
        val micros = crypto.decodeDecrypt(s, encryptKey, integrityKey)
        return microsToCurrency(micros)
    }
}