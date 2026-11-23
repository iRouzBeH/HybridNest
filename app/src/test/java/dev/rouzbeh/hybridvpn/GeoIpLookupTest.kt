package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.feature.dashboard.GeoIpLookup
import dev.rouzbeh.hybridvpn.feature.dashboard.GeoIpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeoIpLookupTest {

    @Test
    fun `public IP endpoints priority order prioritizes Tier-1 consensus endpoints`() {
        val endpoints = GeoIpLookup.PUBLIC_IP_ENDPOINTS
        assertEquals(6, endpoints.size)
        assertEquals("https://ipinfo.io/json", endpoints[0])
        assertEquals("https://ipwho.is/", endpoints[1])
        assertEquals("https://free.freeipapi.com/api/json", endpoints[2])
        assertEquals("https://api-ipv4.ip.sb/geoip", endpoints[3])
        assertEquals("https://ifconfig.co/json", endpoints[4])
        assertEquals("http://ip-api.com/json", endpoints[5])

        val primary = GeoIpLookup.PRIMARY_PUBLIC_IP_ENDPOINTS
        assertEquals(2, primary.size)
        assertEquals("https://ipinfo.io/json", primary[0])
        assertEquals("https://ipwho.is/", primary[1])
    }

    @Test
    fun `host lookup URLs prioritizes ipinfo and ipwho over fallback providers`() {
        val urls = GeoIpLookup.hostLookupUrls("2.31.203.3")
        assertEquals(5, urls.size)
        assertEquals("https://ipinfo.io/2.31.203.3/json", urls[0])
        assertEquals("https://ipwho.is/2.31.203.3", urls[1])
        assertEquals("https://free.freeipapi.com/api/json/2.31.203.3", urls[2])
        assertEquals("https://api.ip.sb/geoip/2.31.203.3", urls[3])
        assertEquals("http://ip-api.com/json/2.31.203.3", urls[4])

        val primary = GeoIpLookup.primaryHostLookupUrls("2.31.203.3")
        assertEquals(2, primary.size)
        assertEquals("https://ipinfo.io/2.31.203.3/json", primary[0])
        assertEquals("https://ipwho.is/2.31.203.3", primary[1])
    }

    @Test
    fun `parse ipinfo-io success response resolves Amsterdam Benthost server to NL and Netherlands`() {
        val response = """
            {
                "ip": "178.253.233.142",
                "city": "Amsterdam",
                "region": "North Holland",
                "country": "NL",
                "loc": "52.3740,4.8897",
                "org": "AS200000 Benthost LLC",
                "postal": "1012",
                "timezone": "Europe/Amsterdam"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipinfo.io/json", response)
        assertNotNull(result)
        assertEquals("178.253.233.142", result!!.ip)
        assertEquals("nl", result.countryCode)
        assertEquals("Netherlands", result.countryName)
        assertEquals("Netherlands", result.location)
        assertEquals("nl", result.flagCode)
    }

    @Test
    fun `parse ipinfo-io success response resolves Frankfurt Hetzner server to DE and Germany`() {
        val response = """
            {
                "ip": "2.31.203.3",
                "city": "Frankfurt am Main",
                "region": "Hesse",
                "country": "DE",
                "loc": "50.1109,8.6821",
                "org": "AS24940 Hetzner Online GmbH",
                "postal": "60311",
                "timezone": "Europe/Berlin"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipinfo.io/2.31.203.3/json", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `rejection of ipinfo-io bogon response`() {
        val response = """
            {
                "ip": "127.0.0.1",
                "bogon": true
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipinfo.io/json", response)
        assertNull(result)
    }

    @Test
    fun `selectConsensusResult returns unanimous agreement when ipinfo and ipwho match`() {
        val ipinfo = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val ipwho = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val result = GeoIpLookup.selectConsensusResult(listOf(ipinfo, ipwho))
        assertNotNull(result)
        assertEquals("178.253.233.142", result!!.ip)
        assertEquals("nl", result.countryCode)
        assertEquals("Netherlands", result.countryName)
    }

    @Test
    fun `selectConsensusResult returns single survivor when one provider times out`() {
        val ipinfo = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val result = GeoIpLookup.selectConsensusResult(listOf(ipinfo))
        assertNotNull(result)
        assertEquals("178.253.233.142", result!!.ip)
        assertEquals("nl", result.countryCode)
    }

    @Test
    fun `selectConsensusResult picks majority when ipinfo and freeipapi agree against ipsb misclassification`() {
        val ipinfo = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val ipsb = GeoIpResult("178.253.233.142", "rs", "Serbia")
        val freeipapi = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val result = GeoIpLookup.selectConsensusResult(listOf(ipinfo, ipsb, freeipapi))
        assertNotNull(result)
        assertEquals("nl", result!!.countryCode)
        assertEquals("Netherlands", result.countryName)
    }

    @Test
    fun `selectConsensusResult breaks two-way tie in favor of highest-priority candidate`() {
        val ipinfo = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val ipsb = GeoIpResult("178.253.233.142", "rs", "Serbia")
        val result = GeoIpLookup.selectConsensusResult(listOf(ipinfo, ipsb))
        assertNotNull(result)
        assertEquals("nl", result!!.countryCode)
        assertEquals("Netherlands", result.countryName)
    }

    @Test
    fun `selectConsensusResult prefers IPv4 over IPv6 when both are present`() {
        val ipv6 = GeoIpResult("2a01:4f8:c012:a1b::1", "de", "Germany")
        val ipv4 = GeoIpResult("178.253.233.142", "nl", "Netherlands")
        val result = GeoIpLookup.selectConsensusResult(listOf(ipv6, ipv4))
        assertNotNull(result)
        assertEquals("178.253.233.142", result!!.ip)
        assertEquals("nl", result.countryCode)
    }

    @Test
    fun `parse ipwho-is success response correctly resolves Hetzner German server`() {
        val response = """
            {
                "ip": "2.31.203.3",
                "success": true,
                "type": "IPv4",
                "continent": "Europe",
                "continent_code": "EU",
                "country": "Germany",
                "country_code": "DE",
                "region": "Hesse",
                "region_code": "HE",
                "city": "Frankfurt am Main"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipwho.is/2.31.203.3", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
        assertEquals("Germany", result.location)
        assertEquals("de", result.flagCode)
    }

    @Test
    fun `parse ip-sb success response correctly resolves Hetzner German server`() {
        val response = """
            {
                "organization": "Hetzner Online GmbH",
                "longitude": 8.6821,
                "city": "Frankfurt am Main",
                "timezone": "Europe/Berlin",
                "isp": "Hetzner Online GmbH",
                "offset": 3600,
                "region": "Hesse",
                "asn": 24940,
                "asn_organization": "Hetzner Online GmbH",
                "country": "Germany",
                "ip": "2.31.203.3",
                "latitude": 50.1109,
                "continent_code": "EU",
                "country_code": "DE",
                "region_code": "HE"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://api.ip.sb/geoip/2.31.203.3", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `parse ifconfig-co success response`() {
        val response = """
            {
                "ip": "2.31.203.3",
                "ip_decimal": 35638019,
                "country": "Germany",
                "country_iso": "DE",
                "country_eu": true,
                "region_name": "Hesse",
                "region_code": "HE",
                "city": "Frankfurt am Main"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ifconfig.co/json", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `parse free freeipapi-com success response`() {
        val response = """
            {
                "ipVersion": 4,
                "ipAddress": "2.31.203.3",
                "latitude": 50.1109,
                "longitude": 8.6821,
                "countryName": "Germany",
                "countryCode": "DE",
                "cityName": "Frankfurt am Main",
                "regionName": "Hesse"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://free.freeipapi.com/api/json", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `parse ip-api-com success response`() {
        val response = """
            {
                "status": "success",
                "country": "Germany",
                "countryCode": "DE",
                "region": "HE",
                "city": "Frankfurt am Main",
                "query": "2.31.203.3"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("http://ip-api.com/json/2.31.203.3", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `rejection of ipwho-is rate limit error payload`() {
        val errorPayload = """
            {
                "success": false,
                "message": "rate limit"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipwho.is/", errorPayload)
        assertNull(result)
    }

    @Test
    fun `rejection of ip-api-com error payload`() {
        val errorPayload = """
            {
                "status": "fail",
                "message": "reserved range",
                "query": "127.0.0.1"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("http://ip-api.com/json/127.0.0.1", errorPayload)
        assertNull(result)
    }

    @Test
    fun `rejection of malformed json, empty string, and html error page`() {
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", ""))
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", "   "))
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", "<html>502 Bad Gateway</html>"))
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", "{ invalid json "))
    }

    @Test
    fun `rejection of missing, blank, or 0_0_0_0 IP address`() {
        val blankIp = """{"ip":"","country":"Germany","country_code":"DE"}"""
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", blankIp))

        val zeroIp = """{"ip":"0.0.0.0","country":"Germany","country_code":"DE"}"""
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", zeroIp))

        val invalidIp = """{"ip":"invalid_ip","country":"Germany","country_code":"DE"}"""
        assertNull(GeoIpLookup.parseResponse("https://ipwho.is/", invalidIp))
    }

    @Test
    fun `cleanCountryName strips leading The from country names`() {
        val response = """
            {
                "ip": "1.2.3.4",
                "country": "The Netherlands",
                "country_code": "NL"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipwho.is/1.2.3.4", response)
        assertNotNull(result)
        assertEquals("Netherlands", result!!.countryName)
        assertEquals("nl", result.countryCode)
    }

    @Test
    fun `un or xx raw country code falls back to countryName locale resolution`() {
        val response = """
            {
                "ip": "2.31.203.3",
                "country": "Germany",
                "country_code": "un"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipwho.is/2.31.203.3", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `completely unknown country preserves IP with un and Unknown fallback`() {
        val response = """
            {
                "ip": "2.31.203.3",
                "country": "UnknownCountryNameNonexistent",
                "country_code": "xx"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipwho.is/2.31.203.3", response)
        assertNotNull(result)
        assertEquals("2.31.203.3", result!!.ip)
        assertEquals("un", result.countryCode)
        assertEquals("UnknownCountryNameNonexistent", result.countryName)
    }

    @Test
    fun `valid IPv6 address is parsed and accepted`() {
        val response = """
            {
                "ip": "2a01:4f8:c012:a1b::1",
                "country": "Germany",
                "country_code": "DE"
            }
        """.trimIndent()
        val result = GeoIpLookup.parseResponse("https://ipwho.is/", response)
        assertNotNull(result)
        assertEquals("2a01:4f8:c012:a1b::1", result!!.ip)
        assertEquals("de", result.countryCode)
        assertEquals("Germany", result.countryName)
    }

    @Test
    fun `unrecognized endpoint url returns null`() {
        val response = """{"ip":"2.31.203.3","country":"Germany","country_code":"DE"}"""
        assertNull(GeoIpLookup.parseResponse("https://unknown-service.com/json", response))
    }
}
