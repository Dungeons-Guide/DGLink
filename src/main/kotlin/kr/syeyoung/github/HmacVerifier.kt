package kr.syeyoung.github

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

val secretKey = System.getenv("WEBHOOK_SECRET")
val spec: SecretKeySpec = SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")

fun verifyHmac(signature: String, body: String): Boolean {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(spec)
    val data = mac.doFinal(body.toByteArray())
    return data.toHex() == signature;
}