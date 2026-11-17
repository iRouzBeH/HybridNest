package dev.rouzbeh.hybridvpn.core.vpn.wireguard

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Pure-Kotlin RFC 7748 Curve25519 (X25519) key generation and public key derivation.
 *
 * Implements the standard constant-structure 255-bit Montgomery ladder over prime
 * P = 2^255 - 19 with base point U = 9. Fully compatible with minSdk 24 without
 * requiring external cryptographic dependencies (e.g. BouncyCastle or Tink).
 */
object WireGuardKeyUtils {

    val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    val A24: BigInteger = BigInteger.valueOf(121665)
    val U: BigInteger = BigInteger.valueOf(9)

    private val secureRandom = SecureRandom()

    /**
     * Clamps a 32-byte private key according to RFC 7748:
     * - Clears 3 lowest bits of byte 0: key[0] &= 248
     * - Clears highest bit of byte 31: key[31] &= 127
     * - Sets second highest bit of byte 31: key[31] |= 64
     */
    fun clampPrivateKey(k: ByteArray): ByteArray {
        require(k.size == 32) { "Private key must be 32 bytes, was ${k.size}" }
        val copy = k.copyOf()
        copy[0] = (copy[0].toInt() and 248).toByte()
        copy[31] = (copy[31].toInt() and 127).toByte()
        copy[31] = (copy[31].toInt() or 64).toByte()
        return copy
    }

    /**
     * Computes RFC 7748 X25519 scalar multiplication on base point U = 9.
     * Takes a 32-byte private key, clamps it, runs the 255-bit Montgomery ladder,
     * and returns the resulting 32-byte little-endian public key.
     */
    fun scalarMultBase(privateKeyBytes: ByteArray): ByteArray {
        require(privateKeyBytes.size == 32) { "Private key bytes must be 32 bytes, was ${privateKeyBytes.size}" }
        val clamped = clampPrivateKey(privateKeyBytes)

        // Decode little-endian into scalar BigInteger k
        val beBytes = ByteArray(32) { i -> clamped[31 - i] }
        val k = BigInteger(1, beBytes)

        val x1 = U
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = U
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap != 0) {
                var tmp = x2; x2 = x3; x3 = tmp
                tmp = z2; z2 = z3; z3 = tmp
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            val daPlusCb = da.add(cb).mod(P)
            x3 = daPlusCb.multiply(daPlusCb).mod(P)

            val daMinusCb = da.subtract(cb).mod(P)
            z3 = x1.multiply(daMinusCb.multiply(daMinusCb)).mod(P)

            x2 = aa.multiply(bb).mod(P)
            val a24TimesE = A24.multiply(e).mod(P)
            z2 = e.multiply(aa.add(a24TimesE)).mod(P)
        }

        if (swap != 0) {
            var tmp = x2; x2 = x3; x3 = tmp
            tmp = z2; z2 = z3; z3 = tmp
        }

        val z2Inv = z2.modPow(P.subtract(BigInteger.valueOf(2)), P)
        val x = x2.multiply(z2Inv).mod(P)

        // Encode 32-byte little-endian
        val result = ByteArray(32)
        var temp = x
        for (i in 0 until 32) {
            result[i] = temp.and(BigInteger.valueOf(0xFF)).toByte()
            temp = temp.shiftRight(8)
        }
        return result
    }

    /**
     * Generates a cryptographically secure 32-byte WireGuard private key,
     * clamps it, and returns it as a 44-character Base64 string.
     */
    fun generatePrivateKey(): String {
        val raw = ByteArray(32)
        secureRandom.nextBytes(raw)
        val clamped = clampPrivateKey(raw)
        return WireGuardConfig.Base64Util.encodeToString(clamped)
    }

    /**
     * Derives a 44-character Base64 public key from a Base64 or 64-char hexadecimal
     * private key string. Returns null if invalid or cannot be decoded into 32 bytes.
     */
    fun derivePublicKey(privateKeyInput: String): String? {
        return try {
            val normalized = WireGuardConfig.normalizeKey(privateKeyInput.trim())
            val bytes = WireGuardConfig.Base64Util.decode(normalized)
            if (bytes.size != 32) return null
            val pubBytes = scalarMultBase(bytes)
            WireGuardConfig.Base64Util.encodeToString(pubBytes)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates a matched private/public keypair as Base64 strings.
     */
    fun generateKeyPair(): Pair<String, String> {
        val priv = generatePrivateKey()
        val pub = derivePublicKey(priv) ?: error("Failed to derive public key from generated private key")
        return Pair(priv, pub)
    }
}
