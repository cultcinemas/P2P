package com.example.btchat.crypto

import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    const val AES_TRANSFORM = "AES/CBC/PKCS5Padding"

    fun generateRSAKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        return gen.genKeyPair()
    }

    fun generateAESKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return kg.generateKey()
    }

    fun encryptRSA(publicKey: PublicKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        c.init(Cipher.ENCRYPT_MODE, publicKey)
        return c.doFinal(data)
    }

    fun decryptRSA(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        c.init(Cipher.DECRYPT_MODE, privateKey)
        return c.doFinal(data)
    }

    fun encryptAES(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        val iv = SecureRandom().generateSeed(16)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val enc = cipher.doFinal(plain)
        return iv + enc // prepend IV
    }

    fun decryptAES(key: SecretKey, input: ByteArray): ByteArray {
        val iv = input.copyOfRange(0, 16)
        val enc = input.copyOfRange(16, input.size)
        val cipher = Cipher.getInstance(AES_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(enc)
    }

    fun keyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")
}
