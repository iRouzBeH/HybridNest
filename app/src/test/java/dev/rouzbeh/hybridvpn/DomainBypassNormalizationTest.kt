package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
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
    fun `shortened google redirect with url parameter extracts target domain`() {
        val googleUrl = "https://www.google.com/url?sa=t&rct=j&url=https://shad.ir/&ved=2ahUKEw"
        val normalized = SettingsRepository.normalizeDomainEntry(googleUrl)
        assertEquals("shad.ir", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(googleUrl)
        assertEquals(listOf("shad.ir"), parsed)

        // Also verify longer variant with source=web
        val longerGoogleUrl = "https://www.google.com/url?sa=t&source=web&rct=j&url=https://shad.ir/&ved=2ahUKEwi"
        assertEquals("shad.ir", SettingsRepository.normalizeDomainEntry(longerGoogleUrl))
        assertEquals(listOf("shad.ir"), SettingsRepository.parseAndNormalizeDomainEntries(longerGoogleUrl))
    }

    @Test
    fun `google redirect with url-encoded q parameter, www, path, and port extracts clean domain`() {
        val googleUrlQ = "https://www.google.com/url?q=https%3A%2F%2Fwww.example.ir%2Flogin"
        val normalizedQ = SettingsRepository.normalizeDomainEntry(googleUrlQ)
        assertEquals("example.ir", normalizedQ)

        val parsedQ = SettingsRepository.parseAndNormalizeDomainEntries(googleUrlQ)
        assertEquals(listOf("example.ir"), parsedQ)

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
    fun `standard url with www, uppercase, port, path, query, and fragment`() {
        val uppercaseUrl = "https://WWW.Example.COM:8443/path/to?x=1#sec"
        val normalized = SettingsRepository.normalizeDomainEntry(uppercaseUrl)
        assertEquals("example.com", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(uppercaseUrl)
        assertEquals(listOf("example.com"), parsed)

        val complexUrl = "HTTPS://user:pass@WWW.Example.IR:8080/login?next=/home#top."
        assertEquals("example.ir", SettingsRepository.normalizeDomainEntry(complexUrl))
        assertEquals(listOf("example.ir"), SettingsRepository.parseAndNormalizeDomainEntries(complexUrl))
    }

    @Test
    fun `credentials with trailing dot`() {
        val credentialsUrl = "https://user:pass@sub.bank.ir.:443/pay"
        val normalized = SettingsRepository.normalizeDomainEntry(credentialsUrl)
        assertEquals("sub.bank.ir", normalized)

        val parsed = SettingsRepository.parseAndNormalizeDomainEntries(credentialsUrl)
        assertEquals(listOf("sub.bank.ir"), parsed)
    }

    @Test
    fun `idn domain conversion to punycode`() {
        // German umlaut IDN domain
        val umlautDomain = "bücher.example"
        assertEquals("xn--bcher-kva.example", SettingsRepository.normalizeDomainEntry(umlautDomain))
        assertEquals(listOf("xn--bcher-kva.example"), SettingsRepository.parseAndNormalizeDomainEntries(umlautDomain))

        // Persian IDN domain label
        val persianDomain = "مثال.ir"
        assertEquals("xn--mgbh0fb.ir", SettingsRepository.normalizeDomainEntry(persianDomain))
        assertEquals(listOf("xn--mgbh0fb.ir"), SettingsRepository.parseAndNormalizeDomainEntries(persianDomain))

        // Full Persian IDN domain and TLD
        val idnUrl = "https://شاپرک.ایران/test"
        val normalized = SettingsRepository.normalizeDomainEntry(idnUrl)
        assertEquals("xn--mgbuh11anh.xn--mgba3a4f16a", normalized)
        assertEquals(listOf("xn--mgbuh11anh.xn--mgba3a4f16a"), SettingsRepository.parseAndNormalizeDomainEntries(idnUrl))

        // Single label TLD with leading dot in IDN
        val idnTld = ".ایران"
        val normalizedTld = SettingsRepository.normalizeDomainEntry(idnTld)
        assertEquals(".xn--mgba3a4f16a", normalizedTld)
    }

    @Test
    fun `multi-entry input with commas, spaces, newlines, and duplicates`() {
        val rawInput = ".ir, https://www.shaparak.ir/pay\nSHAPARAK.IR  .IR"
        val result = SettingsRepository.parseAndNormalizeDomainEntries(rawInput)
        assertEquals(listOf(".ir", "shaparak.ir"), result)

        val mixedInput = " .ir , https://www.shad.ir/app \n shaparak.ir; www.shad.ir, invalid_domain, 192.168.1.1, https:// "
        val mixedResult = SettingsRepository.parseAndNormalizeDomainEntries(mixedInput)
        assertEquals(listOf(".ir", "shad.ir", "shaparak.ir"), mixedResult)

        // Comma-separated without spaces
        val commaNoSpaces = "shaparak.ir,example.com,https://shad.ir"
        assertEquals(listOf("shaparak.ir", "example.com", "shad.ir"), SettingsRepository.parseAndNormalizeDomainEntries(commaNoSpaces))

        val urlFollowedByDomain = "https://example.com,shaparak.ir"
        assertEquals(listOf("example.com", "shaparak.ir"), SettingsRepository.parseAndNormalizeDomainEntries(urlFollowedByDomain))
    }

    @Test
    fun `invalid inputs rejected cleanly`() {
        val invalidEntries = listOf(
            "",
            "   ",
            ".",
            "..",
            "http://",
            "not a valid domain!!!",
            "https://www.google.com/url?sa=t",
            "192.168.1.1",
            "http://[2001:db8::1]:8080"
        )
        for (invalid in invalidEntries) {
            assertEquals("", SettingsRepository.normalizeDomainEntry(invalid))
            assertTrue(
                "Expected empty list for '$invalid'",
                SettingsRepository.parseAndNormalizeDomainEntries(invalid).isEmpty()
            )
        }
    }

    @Test
    fun `two-label www domain is preserved and not mangled into dot-ir or invalid`() {
        // www.ir is a 2-label domain where "www" is the second-level domain name
        assertEquals("www.ir", SettingsRepository.normalizeDomainEntry("www.ir"))
        assertEquals("www.ir", SettingsRepository.normalizeDomainEntry("WWW.IR"))
        assertEquals("www.ir", SettingsRepository.normalizeDomainEntry("https://www.ir/test"))
        assertEquals("www.com", SettingsRepository.normalizeDomainEntry("www.com"))

        // While a 3-label domain strips www.
        assertEquals("shad.ir", SettingsRepository.normalizeDomainEntry("www.shad.ir"))
    }

    @Test
    fun `url-encoded inner target extraction`() {
        val encodedInner = "https%3A%2F%2Fwww.shad.ir%2Fpath"
        assertEquals("shad.ir", SettingsRepository.normalizeDomainEntry(encodedInner))
        assertEquals(listOf("shad.ir"), SettingsRepository.parseAndNormalizeDomainEntries(encodedInner))
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
        val entries = listOf(".ir", "shaparak.ir", "xn--mgbuh11anh.xn--mgba3a4f16a", "geosite:ir", "www.ir")
        val xrayRules = entries.mapNotNull { XrayConfigGenerator.toXrayDomainRule(it) }

        assertEquals("domain:ir", xrayRules[0])
        assertEquals("domain:shaparak.ir", xrayRules[1])
        assertEquals("domain:xn--mgbuh11anh.xn--mgba3a4f16a", xrayRules[2])
        assertEquals("geosite:ir", xrayRules[3])
        // XrayConfigGenerator strips www. when converting to domain routing rule, matching root TLD
        assertEquals("domain:ir", xrayRules[4])
    }
}
