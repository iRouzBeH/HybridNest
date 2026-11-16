package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledFlagsTest {

    private fun resolveFlagsDir(): File {
        val candidates = listOf(
            File("src/main/assets/flags"),
            File("app/src/main/assets/flags")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate src/main/assets/flags directory from ${File(".").absolutePath}")
    }

    @Test
    fun `normalizeCode rejects unknown, sentinel, and invalid country codes`() {
        assertNull(BundledFlags.normalizeCode(null))
        assertNull(BundledFlags.normalizeCode(""))
        assertNull(BundledFlags.normalizeCode("   "))
        assertNull(BundledFlags.normalizeCode("un"))
        assertNull(BundledFlags.normalizeCode("UN"))
        assertNull(BundledFlags.normalizeCode("xx"))
        assertNull(BundledFlags.normalizeCode("__loading__"))
        assertNull(BundledFlags.normalizeCode("zz"))
        assertNull(BundledFlags.normalizeCode("usa"))
        assertFalse(BundledFlags.isSupported("un"))
    }

    @Test
    fun `normalizeCode normalizes valid ISO codes and territory aliases`() {
        assertEquals("nl", BundledFlags.normalizeCode("NL"))
        assertEquals("ir", BundledFlags.normalizeCode(" IR "))
        assertEquals("de", BundledFlags.normalizeCode("de"))
        assertEquals("gb", BundledFlags.normalizeCode("uk"))
        assertEquals("fr", BundledFlags.normalizeCode("cp"))
        assertEquals("io", BundledFlags.normalizeCode("dg"))
        assertEquals("es", BundledFlags.normalizeCode("ic"))
        assertEquals("flags/ir.webp", BundledFlags.assetPath("IR"))
        assertTrue(BundledFlags.isSupported("ir"))
    }

    @Test
    fun `all supported codes exist as valid lossless WebP files in assets`() {
        val flagsDir = resolveFlagsDir()
        assertEquals(251, BundledFlags.SUPPORTED_CODES.size)

        val actualWebpFiles = flagsDir.listFiles { f -> f.isFile && f.name.endsWith(".webp") }
            ?.map { it.name.removeSuffix(".webp") }
            ?.toSet() ?: emptySet()
        assertEquals("Asset directory WebP files must match SUPPORTED_CODES exactly", BundledFlags.SUPPORTED_CODES, actualWebpFiles)

        for (code in BundledFlags.SUPPORTED_CODES) {
            val file = File(flagsDir, "$code.webp")
            assertTrue("Missing bundled flag asset for '$code' at ${file.path}", file.isFile)
            val bytes = file.readBytes()
            assertTrue("Flag asset '$code.webp' is unexpectedly small (${bytes.size} bytes)", bytes.size > 50)
            // Verify RIFF....WEBPVP8L lossless WebP header
            val riff = String(bytes, 0, 4, Charsets.US_ASCII)
            val webp = String(bytes, 8, 4, Charsets.US_ASCII)
            val chunk = String(bytes, 12, 4, Charsets.US_ASCII)
            assertEquals("Invalid RIFF header in $code.webp", "RIFF", riff)
            assertEquals("Invalid WEBP header in $code.webp", "WEBP", webp)
            assertEquals("Expected lossless VP8L chunk in $code.webp", "VP8L", chunk)
        }

        val irFile = File(flagsDir, "ir.webp")
        assertNotNull(irFile)
        assertTrue("Custom Iran Lion and Sun flag (ir.webp) should be present at high resolution", irFile.length() > 15_000)
    }
}
