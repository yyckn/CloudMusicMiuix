package com.musichub.miuix.data.api

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object NeteaseCrypto {

    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b72" +
        "5152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbd" +
        "a92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe48" +
        "75d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val PUB_KEY = "010001"
    private const val NONCE = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"

    private fun createSecretKey(size: Int): String {
        val chars = "012345679abcdef".toList()
        return buildString {
            repeat(size) {
                append(chars[Random.nextInt(chars.size)])
            }
        }
    }

    private fun aesEncrypt(text: String, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun rsaEncrypt(text: String): String {
        val reversed = text.reversed()
        val bytes = reversed.toByteArray(Charsets.UTF_8)
        val hexString = bytes.joinToString("") { "%02x".format(it) }

        val n = java.math.BigInteger(MODULUS, 16)
        val e = java.math.BigInteger(PUB_KEY, 16)
        val b = java.math.BigInteger(hexString, 16)
        val encrypted = b.modPow(e, n).toString(16).padStart(256, '0')
        return encrypted
    }

    fun weapi(text: String): Map<String, String> {
        val secKey = createSecretKey(16)

        // First encryption with NONCE
        val firstEncrypt = aesEncrypt(text, NONCE)
        val firstBase64 = Base64.encodeToString(firstEncrypt, Base64.NO_WRAP)

        // Second encryption with secKey
        val secondEncrypt = aesEncrypt(firstBase64, secKey)
        val encText = Base64.encodeToString(secondEncrypt, Base64.NO_WRAP)

        val encSecKey = rsaEncrypt(secKey)

        return mapOf(
            "params" to encText,
            "encSecKey" to encSecKey
        )
    }

    fun eapi(url: String, data: String): Map<String, String> {
        val eapiKey = "e82ckenh8dichen8"
        val message = "nobody${url}use${data}md5forencrypt"
        val digest = md5(message)
        val encryptedData = "${url}-36cd479b6b5-${data}-36cd479b6b5-${digest}"

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(eapiKey.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(encryptedData.toByteArray(Charsets.UTF_8))
        val hexResult = encrypted.joinToString("") { "%02x".format(it) }.uppercase()

        return mapOf("params" to hexResult)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
