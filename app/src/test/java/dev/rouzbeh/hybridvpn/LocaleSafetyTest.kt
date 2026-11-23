package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator
import dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import dev.rouzbeh.hybridvpn.service.LogStore
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocaleSafetyTest {

    private val testLocales = listOf(
        Locale("fa", "IR"),
        Locale("tr", "TR")
    )

    private fun runUnderLocales(block: (Locale) -> Unit) {
        val previous = Locale.getDefault()
        try {
            for (locale in testLocales) {
                Locale.setDefault(locale)
                block(locale)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `traffic and speed formatting produces strictly ASCII digits and period separator`() {
        runUnderLocales { locale ->
            val formatBytesMethod = VpnStateManager::class.java.getDeclaredMethod(
                "formatBytesStr",
                java.lang.Long.TYPE
            ).apply { isAccessible = true }

            val testSizes = listOf(0L, 512L, 1024L, 55_600_000L, 1_500_000_000L, 2_400_000_000_000L)
            for (size in testSizes) {
                val formatted = formatBytesMethod.invoke(VpnStateManager, size) as String
                assertTrue(
                    "Formatted size '$formatted' under locale $locale must match ASCII number format",
                    formatted.matches(Regex("^[0-9]+(\\.[0-9]+)? (B|KB|MB|GB|TB)$"))
                )
                // Ensure no Persian/Arabic-Indic digits or localized decimal separators
                for (ch in formatted) {
                    assertFalse(
                        "Formatted size '$formatted' contains non-ASCII digit or separator '$ch' under $locale",
                        ch in '\u06F0'..'\u06F9' || ch in '\u0660'..'\u0669' || ch == '٫' || ch == '/'
                    )
                }
            }

            // Verify LogStore timestamp formatting under RTL/Turkish locales
            LogStore.clear("WireGuard")
            LogStore.append("WireGuard", "LocaleSafety probe message")
            val lastLog = LogStore.wireguardLogs.value.lastOrNull()
            assertNotNull("Log entry should not be null", lastLog)
            val timeHeader = lastLog!!.substringAfter('[').substringBefore(']')
            assertTrue(
                "Time header '$timeHeader' under locale $locale must match HH:mm:ss in ASCII digits",
                timeHeader.matches(Regex("^[0-9]{2}:[0-9]{2}:[0-9]{2}$"))
            )
        }
    }

    @Test
    fun `WireGuardConfig parsing and serialization succeed with uppercase INI headers under tr-TR and fa-IR`() {
        runUnderLocales { locale ->
            val samplePrivKey = "YKh/8+7bVzM+p0yT7v6E2Z1r0x5y8z9w1a2b3c4d5e8="
            val samplePubKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzHP0JuVo5VCa2wWg4="
            val samplePsk = "dGVzdHByZXNoYXJlZGtleXRlc3RwcmVzaGFyZWRrZXk="

            val uppercaseIni = """
                [INTERFACE]
                PRIVATEKEY = $samplePrivKey
                ADDRESS = 10.0.0.2/32, fd00::2/128
                DNS = 1.1.1.1, 8.8.8.8
                MTU = 1420
                LISTENPORT = 51820

                [PEER]
                PUBLICKEY = $samplePubKey
                PRESHAREDKEY = $samplePsk
                ENDPOINT = 198.51.100.1:51820
                PERSISTENTKEEPALIVE = 25
                ALLOWEDIPS = 0.0.0.0/0, ::/0
            """.trimIndent()

            val parsed = WireGuardConfig.parseConf(uppercaseIni, defaultRemark = "Frankfurt WG")

            assertEquals("Private key must be parsed correctly under $locale", samplePrivKey, parsed.secretKey)
            assertEquals("Public key must be parsed correctly under $locale", samplePubKey, parsed.publicKey)
            assertEquals("Preshared key must be parsed correctly under $locale", samplePsk, parsed.preSharedKey)
            assertEquals("Endpoint host must be parsed correctly under $locale", "198.51.100.1", parsed.endpointHost)
            assertEquals("Endpoint port must be parsed correctly under $locale", 51820, parsed.endpointPort)
            assertEquals("Addresses must be parsed correctly under $locale", listOf("10.0.0.2/32", "fd00::2/128"), parsed.localAddresses)
            assertEquals("MTU must be parsed correctly under $locale", 1420, parsed.mtu)
            assertEquals("Keepalive must be parsed correctly under $locale", 25, parsed.keepAlive)
            assertEquals("Listen port must be parsed correctly under $locale", 51820, parsed.listenPort)
            assertEquals("Allowed IPs must be parsed correctly under $locale", listOf("0.0.0.0/0", "::/0"), parsed.allowedIps)

            // Test round-trip URI serialization
            val uri = parsed.toUri()
            assertTrue("Serialized URI must start with wireguard:// under $locale", uri.startsWith("wireguard://"))
            val reparsed = WireGuardConfig.parseUri(uri)
            assertEquals(parsed.secretKey, reparsed.secretKey)
            assertEquals(parsed.publicKey, reparsed.publicKey)
            assertEquals(parsed.endpointHost, reparsed.endpointHost)
            assertEquals(parsed.endpointPort, reparsed.endpointPort)
            assertEquals(parsed.allowedIps, reparsed.allowedIps)

            // Test wireguard:// URI with uppercase query parameters under tr-TR and fa-IR
            val uppercaseUri = "wireguard://${java.net.URLEncoder.encode(samplePrivKey, "UTF-8")}@198.51.100.1:51820" +
                "?PUBLICKEY=${java.net.URLEncoder.encode(samplePubKey, "UTF-8")}" +
                "&ADDRESS=10.0.0.5%2F32" +
                "&ALLOWEDIPS=0.0.0.0%2F0" +
                "#UPPERCASE_TEST"

            val uriParsed = WireGuardConfig.parseUri(uppercaseUri)
            assertEquals(samplePrivKey, uriParsed.secretKey)
            assertEquals(samplePubKey, uriParsed.publicKey)
            assertEquals("198.51.100.1", uriParsed.endpointHost)
            assertEquals(listOf("10.0.0.5/32"), uriParsed.localAddresses)
        }
    }

    @Test
    fun `SettingsRepository parseAndNormalizeDomainEntries handles uppercase I cleanly under tr-TR and fa-IR`() {
        runUnderLocales { locale ->
            val inputsWithUppercaseI = listOf(
                "HTTPS://WWW.IRAN.IR/PATH" to listOf("iran.ir"),
                "HTTP://SHAPARAK.IR:443/TEST" to listOf("shaparak.ir"),
                "IRAN.IR, INTERNET.IR" to listOf("iran.ir", "internet.ir"),
                "DOMAIN:IRAN.IR" to listOf("domain:iran.ir"),
                "REGEXP:.*\\.IRAN\\.IR$" to listOf("regexp:.*\\.iran\\.ir$"),
                ".IR" to listOf(".ir"),
                "HTTPS://WWW.GOOGLE.COM/url?q=HTTPS%3A%2F%2FWWW.IRAN.IR%2FTEST" to listOf("iran.ir")
            )

            for ((rawInput, expected) in inputsWithUppercaseI) {
                val normalized = SettingsRepository.parseAndNormalizeDomainEntries(rawInput)
                assertEquals(
                    "Normalization of '$rawInput' under locale $locale must match expected",
                    expected,
                    normalized
                )
                for (entry in normalized) {
                    assertFalse(
                        "Normalized entry '$entry' under $locale must not contain Turkish dotless 'ı' or dotted 'İ'",
                        entry.contains('\u0131') || entry.contains('\u0130')
                    )
                }
            }
        }
    }

    @Test
    fun `XrayConfigGenerator generates valid JSON config for VLESS and WireGuard under tr-TR and fa-IR`() {
        runUnderLocales { locale ->
            // 1. VLESS profile
            val vlessProfile = VpnProfile(
                name = "VLESS Reality Test",
                protocol = "v2ray",
                serverAddress = "vless://00000000-0000-4000-8000-000000000000@example.com:443?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com&type=tcp#Test"
            )
            val vlessConfigStr = XrayConfigGenerator.generateConfig(vlessProfile)
            assertNotNull("VLESS config string should not be null", vlessConfigStr)
            val vlessJson = JSONObject(vlessConfigStr)
            val outbounds = vlessJson.getJSONArray("outbounds")
            assertTrue("Outbounds array must not be empty", outbounds.length() > 0)
            val proxyOutbound = outbounds.getJSONObject(0)
            assertEquals("Protocol must be vless under $locale", "vless", proxyOutbound.getString("protocol"))
            val streamSettings = proxyOutbound.getJSONObject("streamSettings")
            assertEquals("Security must be reality under $locale", "reality", streamSettings.getString("security"))
            assertEquals("Network must be tcp under $locale", "tcp", streamSettings.getString("network"))

            // 2. WireGuard profile
            val wgUri = "wireguard://YKh%2F8%2B7bVzM%2Bp0yT7v6E2Z1r0x5y8z9w1a2b3c4d5e8%3D@198.51.100.1:51820" +
                "?address=10.0.0.2%2F32" +
                "&publickey=bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzHP0JuVo5VCa2wWg4%3D" +
                "&allowedips=0.0.0.0%2F0" +
                "#WireGuardXray"
            val wgProfile = VpnProfile(
                name = "WireGuard Test",
                protocol = "wireguard",
                serverAddress = wgUri
            )
            val wgConfigStr = XrayConfigGenerator.generateConfig(wgProfile)
            assertNotNull("WireGuard config string should not be null", wgConfigStr)
            val wgJson = JSONObject(wgConfigStr)
            val wgOutbounds = wgJson.getJSONArray("outbounds")
            val wgProxy = wgOutbounds.getJSONObject(0)
            assertEquals("Protocol must be wireguard under $locale", "wireguard", wgProxy.getString("protocol"))
            val wgSettings = wgProxy.getJSONObject("settings")
            val wgAddressArray = wgSettings.getJSONArray("address")
            assertEquals("10.0.0.2/32", wgAddressArray.getString(0))
            val peer = wgSettings.getJSONArray("peers").getJSONObject(0)
            assertEquals("198.51.100.1:51820", peer.getString("endpoint"))
            assertEquals("bmXOC+F1FxEMF9dyiK2H5/1SUtzHP0JuVo5VCa2wWg4=", peer.getString("publicKey"))

            // 3. Xray domain rule conversion under Turkish/Persian locales
            val rule = XrayConfigGenerator.toXrayDomainRule("HTTPS://WWW.IRAN.IR/PAGE")
            assertEquals("Xray domain rule under $locale must normalize to domain:iran.ir", "domain:iran.ir", rule)
            assertFalse(
                "Domain rule '$rule' under $locale must not contain dotless 'ı'",
                rule?.contains('\u0131') == true
            )
        }
    }
}
