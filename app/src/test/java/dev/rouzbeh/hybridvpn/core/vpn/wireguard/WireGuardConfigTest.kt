package dev.rouzbeh.hybridvpn.core.vpn.wireguard

import dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class WireGuardConfigTest {

    private val samplePrivKey = "YKh/8+7bVzM+p0yT7v6E2Z1r0x5y8z9w1a2b3c4d5e8="
    private val samplePubKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzHP0JuVo5VCa2wWg4="
    private val samplePsk = "dGVzdHByZXNoYXJlZGtleXRlc3RwcmVzaGFyZWRrZXk="

    // ================================================== 1. wireguard:// URI Parsing

    @Test
    fun `parse wireguard URI with standard IPv4 endpoint and URL-encoded keys`() {
        val encPrivKey = URLEncoder.encode(samplePrivKey, "UTF-8")
        val encPubKey = URLEncoder.encode(samplePubKey, "UTF-8")
        val encPsk = URLEncoder.encode(samplePsk, "UTF-8")
        val encRemark = URLEncoder.encode("Frankfurt WireGuard #1", "UTF-8")

        val uri = "wireguard://$encPrivKey@198.51.100.1:51820" +
            "?address=10.0.0.2%2F32" +
            "&publickey=$encPubKey" +
            "&presharedkey=$encPsk" +
            "&mtu=1360" +
            "&keepalive=25" +
            "&reserved=12%2C34%2C56" +
            "&dns=1.1.1.1" +
            "&listenport=51820" +
            "&allowedips=0.0.0.0%2F0%2C%3A%3A%2F0" +
            "#$encRemark"

        val parsed = WireGuardConfig.parseUri(uri)

        assertEquals("Frankfurt WireGuard #1", parsed.remark)
        assertEquals(samplePrivKey, parsed.secretKey)
        assertEquals(samplePubKey, parsed.publicKey)
        assertEquals(samplePsk, parsed.preSharedKey)
        assertEquals("198.51.100.1", parsed.endpointHost)
        assertEquals(51820, parsed.endpointPort)
        assertEquals(listOf("10.0.0.2/32"), parsed.localAddresses)
        assertEquals(1360, parsed.mtu)
        assertEquals(25, parsed.keepAlive)
        assertEquals(listOf(12, 34, 56), parsed.reserved)
        assertEquals("1.1.1.1", parsed.dns)
        assertEquals(51820, parsed.listenPort)
        assertEquals(listOf("0.0.0.0/0", "::/0"), parsed.allowedIps)
    }

    @Test
    fun `parse wireguard URI with bracketed IPv6 endpoint and parameter aliases`() {
        val encPrivKey = URLEncoder.encode(samplePrivKey, "UTF-8")
        val encPubKey = URLEncoder.encode(samplePubKey, "UTF-8")

        val uri = "wireguard://$encPrivKey@[2001:db8::1]:2408" +
            "?ip=10.0.0.2,fd00::2" +
            "&pbk=$encPubKey" +
            "&psk=$samplePsk" +
            "&ka=15" +
            "#IPv6%20Node"

        val parsed = WireGuardConfig.parseUri(uri)

        assertEquals("IPv6 Node", parsed.remark)
        assertEquals(samplePrivKey, parsed.secretKey)
        assertEquals(samplePubKey, parsed.publicKey)
        assertEquals(samplePsk, parsed.preSharedKey)
        assertEquals("2001:db8::1", parsed.endpointHost)
        assertEquals(2408, parsed.endpointPort)
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), parsed.localAddresses)
        assertEquals(1420, parsed.mtu) // default
        assertEquals(15, parsed.keepAlive)
        assertNull(parsed.listenPort)
        assertEquals(listOf("0.0.0.0/0"), parsed.allowedIps)
    }

    @Test
    fun `parse wireguard URI with base64 encoded reserved field`() {
        val encPrivKey = URLEncoder.encode(samplePrivKey, "UTF-8")
        val encPubKey = URLEncoder.encode(samplePubKey, "UTF-8")
        val b64Reserved = "DCI4" // [12, 34, 56]

        val uri = "wireguard://$encPrivKey@example.com:51820" +
            "?address=10.0.0.2%2F32&publickey=$encPubKey&reserved=$b64Reserved"

        val parsed = WireGuardConfig.parseUri(uri)
        assertEquals(listOf(12, 34, 56), parsed.reserved)
    }

    // ================================================== 2. WireGuard .conf Parsing

    @Test
    fun `parse standard WireGuard conf file with interface and peer`() {
        val conf = """
            # Name = Amsterdam Cloud Server
            [Interface]
            PrivateKey = $samplePrivKey
            Address = 10.0.0.2/32, fd7b:6c4d:8e::2/128
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1380
            ListenPort = 51820

            [Peer]
            PublicKey = $samplePubKey
            PresharedKey = $samplePsk
            Endpoint = ams.wireguard.example.com:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 30
            Reserved = 10, 20, 30
        """.trimIndent()

        val parsed = WireGuardConfig.parseConf(conf)

        assertEquals("Amsterdam Cloud Server", parsed.remark)
        assertEquals(samplePrivKey, parsed.secretKey)
        assertEquals(samplePubKey, parsed.publicKey)
        assertEquals(samplePsk, parsed.preSharedKey)
        assertEquals("ams.wireguard.example.com", parsed.endpointHost)
        assertEquals(51820, parsed.endpointPort)
        assertEquals(listOf("10.0.0.2/32", "fd7b:6c4d:8e::2/128"), parsed.localAddresses)
        assertEquals(1380, parsed.mtu)
        assertEquals(30, parsed.keepAlive)
        assertEquals(listOf(10, 20, 30), parsed.reserved)
        assertEquals("1.1.1.1, 8.8.8.8", parsed.dns)
        assertEquals(51820, parsed.listenPort)
        assertEquals(listOf("0.0.0.0/0", "::/0"), parsed.allowedIps)
    }

    @Test
    fun `parse WireGuard conf with bracketed IPv6 endpoint and default remark fallback`() {
        val conf = """
            [Interface]
            PrivateKey = $samplePrivKey
            Address = 172.16.0.2

            [Peer]
            PublicKey = $samplePubKey
            Endpoint = [2606:4700:d0::a29f:c001]:2408
        """.trimIndent()

        val parsed = WireGuardConfig.parse(conf, defaultRemark = "Fallback Remark")

        assertEquals("Fallback Remark", parsed.remark)
        assertEquals("2606:4700:d0::a29f:c001", parsed.endpointHost)
        assertEquals(2408, parsed.endpointPort)
        assertEquals(listOf("172.16.0.2/32"), parsed.localAddresses)
        assertEquals(1420, parsed.mtu)
        assertEquals(0, parsed.keepAlive)
        assertNull(parsed.preSharedKey)
        assertNull(parsed.reserved)
        assertNull(parsed.listenPort)
        assertEquals(listOf("0.0.0.0/0"), parsed.allowedIps)
    }

    // ================================================== 3. Round-Trip Serialization

    @Test
    fun `round-trip serialization from conf to URI and back`() {
        val originalConf = """
            # Name = Roundtrip Test
            [Interface]
            PrivateKey = $samplePrivKey
            Address = 10.14.0.2/32
            DNS = 9.9.9.9
            MTU = 1350
            ListenPort = 49152

            [Peer]
            PublicKey = $samplePubKey
            PresharedKey = $samplePsk
            Endpoint = vpn.secure.net:51820
            AllowedIPs = 10.0.0.0/8, 192.168.0.0/16
            PersistentKeepalive = 21
            Reserved = 1, 2, 3
        """.trimIndent()

        val fromConf = WireGuardConfig.fromConfig(originalConf)
        val canonicalUri = fromConf.toUri()

        assertTrue("URI must start with wireguard://", canonicalUri.startsWith("wireguard://"))
        assertTrue("URI must contain listenport", canonicalUri.contains("listenport=49152"))
        assertTrue("URI must contain allowedips", canonicalUri.contains("allowedips="))

        val fromUri = WireGuardConfig.fromConfig(canonicalUri)

        assertEquals(fromConf.remark, fromUri.remark)
        assertEquals(fromConf.secretKey, fromUri.secretKey)
        assertEquals(fromConf.publicKey, fromUri.publicKey)
        assertEquals(fromConf.preSharedKey, fromUri.preSharedKey)
        assertEquals(fromConf.endpointHost, fromUri.endpointHost)
        assertEquals(fromConf.endpointPort, fromUri.endpointPort)
        assertEquals(fromConf.localAddresses, fromUri.localAddresses)
        assertEquals(fromConf.mtu, fromUri.mtu)
        assertEquals(fromConf.keepAlive, fromUri.keepAlive)
        assertEquals(fromConf.reserved, fromUri.reserved)
        assertEquals(fromConf.dns, fromUri.dns)
        assertEquals(fromConf.listenPort, fromUri.listenPort)
        assertEquals(fromConf.allowedIps, fromUri.allowedIps)
    }

    @Test
    fun `round-trip serialization with IPv6 endpoint`() {
        val original = WireGuardParsedConfig(
            remark = "IPv6 Peer",
            secretKey = samplePrivKey,
            publicKey = samplePubKey,
            endpointHost = "2001:db8::cafe",
            endpointPort = 51820,
            localAddresses = listOf("10.0.0.5/32"),
            mtu = 1420,
            keepAlive = 15,
            reserved = listOf(11, 22, 33),
            listenPort = 51820,
            allowedIps = listOf("0.0.0.0/0", "::/0")
        )

        val uri = original.toUri()
        assertTrue("Authority must contain bracketed IPv6", uri.contains("@[2001:db8::cafe]:51820"))

        val restored = WireGuardConfig.parse(uri)
        assertEquals(original, restored)
    }

    // ================================================== 4. Germany1 Conf Test

    @Test
    fun `germany1 conf parsing and round-trip preservation`() {
        val germany1Conf = """
            [Interface]
            PrivateKey = yMwfq4FOewHuMInFetjq6KcO/Ril2qF2USB1upSBlHk=
            Address = 10.96.0.207/32
            MTU = 1320
            DNS = 1.1.1.1

            [Peer]
            PublicKey = Vcrm9FNz2PCXaH0xK9qzG087FkidoO4b2LL1+Eo+zho=
            AllowedIPs = 0.0.0.0/0
            Endpoint = for4.nyv1122.shop:51901
            PersistentKeepalive = 25
        """.trimIndent()

        val parsed = WireGuardConfig.parse(germany1Conf, defaultRemark = "germany1")

        assertEquals("germany1", parsed.remark)
        assertEquals("yMwfq4FOewHuMInFetjq6KcO/Ril2qF2USB1upSBlHk=", parsed.secretKey)
        assertEquals(listOf("10.96.0.207/32"), parsed.localAddresses)
        assertEquals(1320, parsed.mtu)
        assertEquals("1.1.1.1", parsed.dns)
        assertEquals("Vcrm9FNz2PCXaH0xK9qzG087FkidoO4b2LL1+Eo+zho=", parsed.publicKey)
        assertEquals(listOf("0.0.0.0/0"), parsed.allowedIps)
        assertEquals("for4.nyv1122.shop", parsed.endpointHost)
        assertEquals(51901, parsed.endpointPort)
        assertEquals(25, parsed.keepAlive)

        val canonicalUri = parsed.toUri()
        val restored = WireGuardConfig.parse(canonicalUri)

        assertEquals(parsed.remark, restored.remark)
        assertEquals(parsed.secretKey, restored.secretKey)
        assertEquals(parsed.localAddresses, restored.localAddresses)
        assertEquals(parsed.mtu, restored.mtu)
        assertEquals(parsed.dns, restored.dns)
        assertEquals(parsed.publicKey, restored.publicKey)
        assertEquals(parsed.allowedIps, restored.allowedIps)
        assertEquals(parsed.endpointHost, restored.endpointHost)
        assertEquals(parsed.endpointPort, restored.endpointPort)
        assertEquals(parsed.keepAlive, restored.keepAlive)
    }

    // ================================================== 5. RFC 7748 X25519 & KeyUtils Tests

    @Test
    fun `WireGuardKeyUtils derivePublicKey produces deterministic 44-char base64 for sample private key`() {
        val privKey = "yMwfq4FOewHuMInFetjq6KcO/Ril2qF2USB1upSBlHk="
        val derived1 = WireGuardKeyUtils.derivePublicKey(privKey)
        val derived2 = WireGuardKeyUtils.derivePublicKey(privKey)

        assertNotNull(derived1)
        assertEquals(44, derived1!!.length)
        assertTrue(derived1.endsWith("="))
        assertEquals(derived1, derived2)
        assertEquals("82KcaBxbXFD6HrzNQGB8Ax+PV4YWowgbG5BKJXBGcwk=", derived1)
    }

    @Test
    fun `RFC 7748 Section 6_1 standard test vector matches expected hex and base64 public key`() {
        val aliceScalarHex = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        val expectedPubHex = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        val expectedPubBase64 = "hSDwCYkwp1R0i33ctD73Wg2/Og0mOBr066SpjqqbTmo="

        val scalarBytes = ByteArray(32) { i ->
            aliceScalarHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val computedPubBytes = WireGuardKeyUtils.scalarMultBase(scalarBytes)
        val computedPubHex = computedPubBytes.joinToString("") { "%02x".format(it) }

        assertEquals(expectedPubHex, computedPubHex)

        val derivedFromHex = WireGuardKeyUtils.derivePublicKey(aliceScalarHex)
        assertEquals(expectedPubBase64, derivedFromHex)
    }

    @Test
    fun `WireGuardKeyUtils generateKeyPair produces valid keypair where derived public key matches`() {
        val (privKey, pubKey) = WireGuardKeyUtils.generateKeyPair()

        assertEquals(44, privKey.length)
        assertEquals(44, pubKey.length)

        val derived = WireGuardKeyUtils.derivePublicKey(privKey)
        assertEquals(pubKey, derived)
    }

    // ================================================== 6. Detection and Validation Helpers

    @Test
    fun `isWireGuardConfig detects wireguard URI and conf text correctly`() {
        assertTrue(WireGuardConfig.isWireGuardConfig("wireguard://key@host:1234?publickey=pub"))
        assertTrue(WireGuardConfig.isWireGuardConfig("  WIREGUARD://key@host:1234?publickey=pub  "))

        val confText = """
            [Interface]
            PrivateKey = key
            [Peer]
            PublicKey = pub
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        assertTrue(WireGuardConfig.isWireGuardConfig(confText))

        assertFalse(WireGuardConfig.isWireGuardConfig("vless://uuid@host:443?type=tcp"))
        assertFalse(WireGuardConfig.isWireGuardConfig("trojan://pass@host:443"))
        assertFalse(WireGuardConfig.isWireGuardConfig("vmess://eyJhZGQiOiIxLTIifQ=="))
        assertFalse(WireGuardConfig.isWireGuardConfig("vpn.example.com"))
        assertFalse(WireGuardConfig.isWireGuardConfig(""))
        assertFalse(WireGuardConfig.isWireGuardConfig(null))
    }

    @Test
    fun `validation catches missing required fields and invalid listenPort`() {
        val invalidKey = WireGuardParsedConfig(
            secretKey = "   ",
            publicKey = samplePubKey,
            endpointHost = "1.2.3.4",
            endpointPort = 51820
        )
        assertTrue(invalidKey.validate().any { it.contains("Secret key", ignoreCase = true) })

        val invalidPort = WireGuardParsedConfig(
            secretKey = samplePrivKey,
            publicKey = samplePubKey,
            endpointHost = "1.2.3.4",
            endpointPort = 51820,
            listenPort = 70000
        )
        assertTrue(invalidPort.validate().any { it.contains("Listen port", ignoreCase = true) })
    }

    // ================================================== 7. XrayConfigGenerator Integration

    private fun wireguardProfile(
        uri: String = WireGuardParsedConfig(
            remark = "WG Profile",
            secretKey = samplePrivKey,
            publicKey = samplePubKey,
            preSharedKey = samplePsk,
            endpointHost = "wg.example.com",
            endpointPort = 51820,
            localAddresses = listOf("10.0.0.2/32"),
            mtu = 1360,
            keepAlive = 25,
            reserved = listOf(12, 34, 56)
        ).toUri()
    ) = VpnProfile(name = "WG Test", protocol = "WireGuard", serverAddress = uri)

    @Test
    fun `generateConfig generates full Xray session config for WireGuard`() {
        val profile = wireguardProfile()
        val jsonStr = XrayConfigGenerator.generateConfig(profile)
        val root = JSONObject(jsonStr)

        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }

        assertEquals("wireguard", proxy.getString("protocol"))
        assertFalse("WireGuard outbound must NOT have streamSettings", proxy.has("streamSettings"))
        assertFalse("WireGuard outbound must NOT have mux", proxy.has("mux"))

        val settings = proxy.getJSONObject("settings")
        assertEquals(samplePrivKey, settings.getString("secretKey"))
        assertEquals(1360, settings.getInt("mtu"))
        assertTrue("WireGuard outbound must set noKernelTun to true on Android", settings.getBoolean("noKernelTun"))

        val addrs = settings.getJSONArray("address")
        assertEquals(1, addrs.length())
        assertEquals("10.0.0.2/32", addrs.getString(0))

        val reservedArr = settings.getJSONArray("reserved")
        assertEquals(3, reservedArr.length())
        assertEquals(12, reservedArr.getInt(0))
        assertEquals(34, reservedArr.getInt(1))
        assertEquals(56, reservedArr.getInt(2))

        val peers = settings.getJSONArray("peers")
        assertEquals(1, peers.length())
        val peer = peers.getJSONObject(0)
        assertEquals("wg.example.com:51820", peer.getString("endpoint"))
        assertEquals(samplePubKey, peer.getString("publicKey"))
        assertEquals(samplePsk, peer.getString("preSharedKey"))
        assertEquals(25, peer.getInt("keepAlive"))
    }

    @Test
    fun `generatePingConfig generates valid trimmed ping config for WireGuard`() {
        val profile = wireguardProfile()
        val jsonStr = XrayConfigGenerator.generatePingConfig(profile)
        val root = JSONObject(jsonStr)

        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }

        assertEquals("wireguard", proxy.getString("protocol"))
        assertFalse("WireGuard ping outbound must NOT have streamSettings", proxy.has("streamSettings"))
        assertFalse("WireGuard ping outbound must NOT have mux", proxy.has("mux"))

        val settings = proxy.getJSONObject("settings")
        assertEquals(samplePrivKey, settings.getString("secretKey"))
        val peers = settings.getJSONArray("peers")
        assertEquals(1, peers.length())
        assertEquals("wg.example.com:51820", peers.getJSONObject(0).getString("endpoint"))
        assertEquals(samplePubKey, peers.getJSONObject(0).getString("publicKey"))
    }

    @Test
    fun `generateConfig works with raw conf text in serverAddress`() {
        val conf = """
            [Interface]
            PrivateKey = $samplePrivKey
            Address = 10.8.0.2/32

            [Peer]
            PublicKey = $samplePubKey
            Endpoint = 203.0.113.50:51820
        """.trimIndent()

        val profile = VpnProfile(name = "Conf Profile", protocol = "WireGuard", serverAddress = conf)
        val jsonStr = XrayConfigGenerator.generateConfig(profile)
        val root = JSONObject(jsonStr)

        val outbounds = root.getJSONArray("outbounds")
        val proxy = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "proxy" }

        assertEquals("wireguard", proxy.getString("protocol"))
        val settings = proxy.getJSONObject("settings")
        val peers = settings.getJSONArray("peers")
        assertEquals("203.0.113.50:51820", peers.getJSONObject(0).getString("endpoint"))
    }

    // ================================================== 8. Endpoint Extraction and Pinning Helpers

    @Test
    fun `extractServerEndpoint extracts host and port for all supported formats`() {
        val wgUri = "wireguard://$samplePrivKey@wg.example.com:51820?publickey=$samplePubKey"
        val wgEp = XrayConfigGenerator.extractServerEndpoint(wgUri)
        assertNotNull(wgEp)
        assertEquals("wg.example.com", wgEp!!.first)
        assertEquals(51820, wgEp.second)

        val wgConf = """
            [Interface]
            PrivateKey = $samplePrivKey
            [Peer]
            PublicKey = $samplePubKey
            Endpoint = 192.0.2.1:2408
        """.trimIndent()
        val confEp = XrayConfigGenerator.extractServerEndpoint(wgConf)
        assertNotNull(confEp)
        assertEquals("192.0.2.1", confEp!!.first)
        assertEquals(2408, confEp.second)
    }

    @Test
    fun `extractEndpointFromOutbound and updateProxyOutboundEndpoint work for WireGuard`() {
        val profile = wireguardProfile()
        val root = JSONObject(XrayConfigGenerator.generateConfig(profile))
        val proxy = root.getJSONArray("outbounds").getJSONObject(0)

        val ep = XrayConfigGenerator.extractEndpointFromOutbound(proxy)
        assertNotNull(ep)
        assertEquals("wg.example.com", ep!!.first)
        assertEquals(51820, ep.second)

        val updated = XrayConfigGenerator.updateProxyOutboundEndpoint(root, "198.51.100.99")
        assertTrue(updated)

        val updatedProxy = root.getJSONArray("outbounds").getJSONObject(0)
        val updatedEp = XrayConfigGenerator.extractEndpointFromOutbound(updatedProxy)
        assertNotNull(updatedEp)
        assertEquals("198.51.100.99", updatedEp!!.first)
        assertEquals(51820, updatedEp.second)
    }
}
