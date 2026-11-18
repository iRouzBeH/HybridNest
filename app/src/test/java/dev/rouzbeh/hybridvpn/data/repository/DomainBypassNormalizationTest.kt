package dev.rouzbeh.hybridvpn.data.repository

import dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBypassNormalizationTest {

    @Test
    fun `simple domain inputs preserve dot-ir and shaparak-ir`() {
        assertEquals(".ir", SettingsRepository.normalizeDomainEntry(".ir"))
        assertEquals(".ir", SettingsRepository.normalizeDomainEntry("  .IR  "))
        assertEquals("shaparak.ir", SettingsRepository.normalizeDomainEntry("shaparak.ir"))
        assertEquals("shaparak.ir", SettingsRepository.normalizeDomainEntry("SHAPARAK.IR"))
        // Multi-label with accidental leading dot should drop the leading dot
        assertEquals("shaparak.ir", SettingsRepository.normalizeDomainEntry(".shaparak.ir"))

        assertEquals(listOf(".ir"), SettingsRepository.parseAndNormalizeDomainEntries(".ir"))
        assertEquals(listOf("shaparak.ir"), SettingsRepository.parseAndNormalizeDomainEntries("shaparak.ir"))
    }

    @Test
    fun `google redirect url with url parameter extracts target domain`() {
        val googleUrl = "https://www.google.com/url?sa=t&source=web&rct=j&url=https://shad.ir/&ved=2ahUKEwi"
        val normalized = SettingsRepository.normalizeDomainEntry(googleUrl)
        assertEquals("shad.ir", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(googleUrl)
        assertEquals(listOf("shad.ir"), parsed)
    }

    @Test
    fun `google redirect url with url-encoded q parameter, www, path, and port extracts clean domain`() {
        val encodedGoogleUrl = "https://www.google.com/url?q=https%3A%2F%2Fwww.shaparak.ir%3A8443%2Fpath%2Fto%2Fpage%3Ffoo%3Dbar%23baz&sa=U"
        val normalized = SettingsRepository.normalizeDomainEntry(encodedGoogleUrl)
        assertEquals("shaparak.ir", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(encodedGoogleUrl)
        assertEquals(listOf("shaparak.ir"), parsed)
    }

    @Test
    fun `google redirect url without url or q parameter is rejected`() {
        val invalidGoogleUrl = "https://www.google.com/url?sa=t"
        assertEquals("", SettingsRepository.normalizeDomainEntry(invalidGoogleUrl))
        assertTrue(SettingsRepository.parseAndNormalizeDomainEntries(invalidGoogleUrl).isEmpty())

        val emptyQGoogleUrl = "https://www.google.com/url?q="
        assertEquals("", SettingsRepository.normalizeDomainEntry(emptyQGoogleUrl))
        assertTrue(SettingsRepository.parseAndNormalizeDomainEntries(emptyQGoogleUrl).isEmpty())
    }

    @Test
    fun `standard url with credentials, www, port, path, query, fragment, and trailing dot`() {
        val complexUrl = "HTTPS://user:pass@WWW.Example.IR:8080/login?next=/home#top."
        val normalized = SettingsRepository.normalizeDomainEntry(complexUrl)
        assertEquals("example.ir", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(complexUrl)
        assertEquals(listOf("example.ir"), parsed)
    }

    @Test
    fun `idn domain conversion to punycode`() {
        val idnUrl = "https://شاپرک.ایران/test"
        val normalized = SettingsRepository.normalizeDomainEntry(idnUrl)
        assertEquals("xn--mgbuh11anh.xn--mgba3a4f16a", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(idnUrl)
        assertEquals(listOf("xn--mgbuh11anh.xn--mgba3a4f16a"), parsed)

        // Single label TLD with leading dot in IDN
        val idnTld = ".ایران"
        val normalizedTld = SettingsRepository.normalizeDomainEntry(idnTld)
        assertEquals(".xn--mgba3a4f16a", normalizedTld)
    }

    @Test
    fun `multi-entry input separated by commas, spaces, semicolons, and newlines with duplicates and invalid tokens`() {
        val rawInput = " .ir , https://www.shad.ir/app \n shaparak.ir; www.shad.ir, invalid_domain, 192.168.1.1, https:// "
        val result = SettingsRepository.parseAndNormalizeDomainEntries(rawInput)
        assertEquals(listOf(".ir", "shad.ir", "shaparak.ir"), result)
    }

    @Test
    fun `preservation of xray prefix rules`() {
        assertEquals("geosite:ir", SettingsRepository.normalizeDomainEntry("geosite:ir"))
        assertEquals("geosite:ir", SettingsRepository.normalizeDomainEntry("  GEOSITE:ir  "))
        assertEquals("domain:ir", SettingsRepository.normalizeDomainEntry("domain:ir"))
        assertEquals("full:google.com", SettingsRepository.normalizeDomainEntry("full:google.com"))
        assertEquals("regexp:.*\\.ir$", SettingsRepository.normalizeDomainEntry("regexp:.*\\.ir$"))

        // Blank value after prefix should be rejected
        assertEquals("", SettingsRepository.normalizeDomainEntry("geosite:"))
        assertEquals("", SettingsRepository.normalizeDomainEntry("geosite:   "))
    }

    @Test
    fun `compatibility with XrayConfigGenerator toXrayDomainRule`() {
        val entries = listOf(".ir", "shaparak.ir", "xn--mgbuh11anh.xn--mgba3a4f16a", "geosite:ir")
        val xrayRules = entries.mapNotNull { XrayConfigGenerator.toXrayDomainRule(it) }

        assertEquals("domain:ir", xrayRules[0])
        assertEquals("domain:shaparak.ir", xrayRules[1])
        assertEquals("domain:xn--mgbuh11anh.xn--mgba3a4f16a", xrayRules[2])
        assertEquals("geosite:ir", xrayRules[3])
    }
}
