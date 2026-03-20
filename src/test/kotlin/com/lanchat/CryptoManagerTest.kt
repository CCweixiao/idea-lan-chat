package com.lanchat

import com.lanchat.util.CryptoManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CryptoManagerTest {

    @BeforeEach
    fun setup() {
        CryptoManager.initialize()
        CryptoManager.isEnabled = true
    }

    @Test
    fun `encrypt and decrypt should return original text`() {
        val original = "你好，这是一条测试消息！Hello World 123"
        val encrypted = CryptoManager.encrypt(original)
        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypted text should differ from original`() {
        val original = "Hello World"
        val encrypted = CryptoManager.encrypt(original)
        assertNotEquals(original, encrypted)
        assertTrue(encrypted.length > original.length)
    }

    @Test
    fun `same plaintext produces different ciphertext each time`() {
        val original = "Same message"
        val enc1 = CryptoManager.encrypt(original)
        val enc2 = CryptoManager.encrypt(original)
        assertNotEquals(enc1, enc2, "Random IV should produce different ciphertext")
        assertEquals(original, CryptoManager.decrypt(enc1))
        assertEquals(original, CryptoManager.decrypt(enc2))
    }

    @Test
    fun `different keys cannot decrypt each other`() {
        CryptoManager.initialize("key-A")
        val encrypted = CryptoManager.encrypt("secret data")

        CryptoManager.initialize("key-B")
        assertThrows(Exception::class.java) {
            CryptoManager.decrypt(encrypted)
        }
    }

    @Test
    fun `empty string encrypt and decrypt`() {
        val encrypted = CryptoManager.encrypt("")
        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun `long message encrypt and decrypt`() {
        val original = "A".repeat(10000)
        val encrypted = CryptoManager.encrypt(original)
        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `json message encrypt and decrypt`() {
        val json = """{"type":"TEXT","senderId":"abc-123","content":"你好世界","timestamp":1234567890}"""
        val encrypted = CryptoManager.encrypt(json)
        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals(json, decrypted)
    }

    @Test
    fun `custom passphrase works`() {
        CryptoManager.initialize("my-custom-secret-key")
        val original = "encrypted with custom key"
        val encrypted = CryptoManager.encrypt(original)
        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `tampered ciphertext should fail decryption`() {
        val encrypted = CryptoManager.encrypt("original")
        val tampered = encrypted.dropLast(2) + "XX"
        assertThrows(Exception::class.java) {
            CryptoManager.decrypt(tampered)
        }
    }
}
