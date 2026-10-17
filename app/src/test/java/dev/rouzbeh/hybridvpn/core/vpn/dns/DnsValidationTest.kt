package dev.rouzbeh.hybridvpn.core.vpn.dns

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DnsValidation] literal-IP checks. Pure JVM.
 */
class DnsValidationTest {

    @Test
    fun `valid ipv4 addresses pass`() {
        assertTrue(DnsValidation.isValidIpv4("8.8.8.8"))
        assertTrue(DnsValidation.isValidIpv4("1.1.1.1"))
        assertTrue(DnsValidation.isValidIpv4("9.9.9.9"))
        assertTrue(DnsValidation.isValidIpv4("208.67.222.222"))
        assertTrue(DnsValidation.isValidIpv4("94.140.14.14"))
        assertTrue(DnsValidation.isValidIpv4("0.0.0.0"))
        assertTrue(DnsValidation.isValidIpv4("255.255.255.255"))
        // Surrounding whitespace is tolerated.
        assertTrue(DnsValidation.isValidIpv4("  8.8.8.8  "))
    }

    @Test
    fun `invalid ipv4 rejected`() {
        assertFalse(DnsValidation.isValidIpv4("8.8.8"))            // too few octets
        assertFalse(DnsValidation.isValidIpv4("8.8.8.8.8"))        // too many
        assertFalse(DnsValidation.isValidIpv4("256.1.1.1"))        // octet > 255
        assertFalse(DnsValidation.isValidIpv4("1.2.3.256"))
        assertFalse(DnsValidation.isValidIpv4("a.b.c.d"))          // non-numeric
        assertFalse(DnsValidation.isValidIpv4("8.8.8.8a"))
        assertFalse(DnsValidation.isValidIpv4(""))
        assertFalse(DnsValidation.isValidIpv4("google.com"))
    }

    @Test
    fun `valid ipv6 addresses pass`() {
        assertTrue(DnsValidation.isValidIpv6("fd00:646e:7373::53"))
        assertTrue(DnsValidation.isValidIpv6("::1"))
        assertTrue(DnsValidation.isValidIpv6("2606:4700:4700::1111"))
    }

    @Test
    fun `ipv4 is not accepted as ipv6`() {
        assertFalse(DnsValidation.isValidIpv6("8.8.8.8"))
    }

    @Test
    fun `isValidIp accepts both families`() {
        assertTrue(DnsValidation.isValidIp("8.8.8.8"))
        assertTrue(DnsValidation.isValidIp("2606:4700:4700::1111"))
        assertFalse(DnsValidation.isValidIp("not-an-ip"))
        assertFalse(DnsValidation.isValidIp(""))
    }
}
