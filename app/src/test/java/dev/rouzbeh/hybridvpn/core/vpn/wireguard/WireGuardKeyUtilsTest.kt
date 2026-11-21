package dev.rouzbeh.hybridvpn.core.vpn.wireguard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardKeyUtilsTest {

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string length must be even, was $length" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // =========================================================================
    // 1. RFC 7748 Section 6.1 — Diffie-Hellman Curve25519 Test Vectors
    // =========================================================================

    @Test
    fun `RFC 7748 Section 6_1 Alice and Bob key derivation and shared secret match official vectors`() {
        val alicePrivateHex = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        val aliceExpectedPubHex = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"

        val bobPrivateHex = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        val bobExpectedPubHex = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"

        val expectedSharedSecretHex = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"

        val aBytes = alicePrivateHex.hexToByteArray()
        val bBytes = bobPrivateHex.hexToByteArray()

        // 1. ScalarMultBase against base point U=9
        val alicePubBytes = WireGuardKeyUtils.scalarMultBase(aBytes)
        assertEquals(aliceExpectedPubHex, alicePubBytes.toHex())

        val bobPubBytes = WireGuardKeyUtils.scalarMultBase(bBytes)
        assertEquals(bobExpectedPubHex, bobPubBytes.toHex())

        // 2. derivePublicKey with hex input strings producing valid Base64
        val alicePubB64 = WireGuardKeyUtils.derivePublicKey(alicePrivateHex)
        assertNotNull(alicePubB64)
        assertEquals(44, alicePubB64!!.length)
        assertArrayEquals(alicePubBytes, WireGuardConfig.Base64Util.decode(alicePubB64))

        val bobPubB64 = WireGuardKeyUtils.derivePublicKey(bobPrivateHex)
        assertNotNull(bobPubB64)
        assertEquals(44, bobPubB64!!.length)
        assertArrayEquals(bobPubBytes, WireGuardConfig.Base64Util.decode(bobPubB64))

        // 3. Diffie-Hellman shared secret: X25519(a, bobPub) == X25519(b, alicePub) == K
        val sharedSecretFromAlice = WireGuardKeyUtils.scalarMult(aBytes, bobPubBytes)
        val sharedSecretFromBob = WireGuardKeyUtils.scalarMult(bBytes, alicePubBytes)

        assertEquals(expectedSharedSecretHex, sharedSecretFromAlice.toHex())
        assertEquals(expectedSharedSecretHex, sharedSecretFromBob.toHex())
        assertArrayEquals(sharedSecretFromAlice, sharedSecretFromBob)
    }

    // =========================================================================
    // 2. RFC 7748 Section 5.2 — X25519 Direct Test Vectors
    // =========================================================================

    @Test
    fun `RFC 7748 Section 5_2 direct test vector 1 matches expected output`() {
        val scalarHex = "a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"
        val uHex = "e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"
        val expectedOutputHex = "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"

        val computed = WireGuardKeyUtils.scalarMult(scalarHex.hexToByteArray(), uHex.hexToByteArray())
        assertEquals(expectedOutputHex, computed.toHex())
    }

    @Test
    fun `RFC 7748 Section 5_2 direct test vector 2 matches expected output`() {
        val scalarHex = "4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d"
        val uHex = "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493"
        val expectedOutputHex = "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957"

        val computed = WireGuardKeyUtils.scalarMult(scalarHex.hexToByteArray(), uHex.hexToByteArray())
        assertEquals(expectedOutputHex, computed.toHex())
    }

    // =========================================================================
    // 3. RFC 7748 Section 5.2 — X25519 Iterative Test Vector (1 and 1,000)
    // =========================================================================

    @Test
    fun `RFC 7748 Section 5_2 iterative test vector matches after 1 and 1000 iterations`() {
        // Initial k and u are both the 32-byte representation of 9 (little-endian)
        var k = ByteArray(32).apply { this[0] = 9 }
        var u = ByteArray(32).apply { this[0] = 9 }

        // After 1 iteration:
        val next1 = WireGuardKeyUtils.scalarMult(k, u)
        u = k
        k = next1
        val expected1Hex = "422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079"
        assertEquals(expected1Hex, k.toHex())

        // Continue from iteration 2 up to 1,000 iterations:
        for (i in 2..1000) {
            val next = WireGuardKeyUtils.scalarMult(k, u)
            u = k
            k = next
        }
        val expected1000Hex = "684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51"
        assertEquals(expected1000Hex, k.toHex())
    }

    // =========================================================================
    // 4. Clamping, Validation & Keygen Properties
    // =========================================================================

    @Test
    fun `clampPrivateKey enforces bitmask rules idempotently without mutating original array`() {
        val raw = ByteArray(32) { 0xFF.toByte() }
        val originalCopy = raw.copyOf()

        val clamped = WireGuardKeyUtils.clampPrivateKey(raw)

        // Did not mutate input
        assertArrayEquals(originalCopy, raw)

        // Lowest 3 bits of byte 0 are cleared
        assertEquals(0, clamped[0].toInt() and 7)
        // Highest bit of byte 31 is cleared
        assertEquals(0, clamped[31].toInt() and 128)
        // Second highest bit of byte 31 is set
        assertEquals(64, clamped[31].toInt() and 64)

        // Idempotency: clamping twice yields identical result
        val clampedAgain = WireGuardKeyUtils.clampPrivateKey(clamped)
        assertArrayEquals(clamped, clampedAgain)
    }

    @Test
    fun `scalarMult masks MSB of final byte of u-coordinate per RFC 7748`() {
        val scalar = ByteArray(32).apply { this[0] = 9 }
        val uNormal = ByteArray(32).apply { this[0] = 9 }
        val uWithMsbSet = uNormal.copyOf().apply {
            this[31] = (this[31].toInt() or 0x80).toByte()
        }

        val out1 = WireGuardKeyUtils.scalarMult(scalar, uNormal)
        val out2 = WireGuardKeyUtils.scalarMult(scalar, uWithMsbSet)

        assertArrayEquals(out1, out2)
    }

    @Test
    fun `derivePublicKey validation handles valid and invalid inputs correctly`() {
        // Blank or wrong length
        assertNull(WireGuardKeyUtils.derivePublicKey(""))
        assertNull(WireGuardKeyUtils.derivePublicKey("   "))
        assertNull(WireGuardKeyUtils.derivePublicKey("invalid-base64!"))
        assertNull(WireGuardKeyUtils.derivePublicKey("YWJj")) // "abc" -> 3 bytes, not 32

        // Accepts 44-char Base64
        val sampleBase64 = "yMwfq4FOewHuMInFetjq6KcO/Ril2qF2USB1upSBlHk="
        val pubFromB64 = WireGuardKeyUtils.derivePublicKey(sampleBase64)
        assertNotNull(pubFromB64)
        assertEquals(44, pubFromB64!!.length)

        // Accepts 64-char Hex
        val sampleHex = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        val pubFromHex = WireGuardKeyUtils.derivePublicKey(sampleHex)
        assertNotNull(pubFromHex)
        assertEquals(44, pubFromHex!!.length)
    }

    @Test
    fun `generateKeyPair produces distinct valid pairs where derived public key matches`() {
        val (priv1, pub1) = WireGuardKeyUtils.generateKeyPair()
        val (priv2, pub2) = WireGuardKeyUtils.generateKeyPair()

        assertEquals(44, priv1.length)
        assertEquals(44, pub1.length)
        assertEquals(44, priv2.length)
        assertEquals(44, pub2.length)

        assertFalse("Generated keys should be distinct", priv1 == priv2)
        assertFalse("Generated keys should be distinct", pub1 == pub2)

        assertEquals(pub1, WireGuardKeyUtils.derivePublicKey(priv1))
        assertEquals(pub2, WireGuardKeyUtils.derivePublicKey(priv2))
    }
}
