package dev.rouzbeh.hybridvpn.core.vpn.v2ray

import android.util.Base64
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object XrayConfigGenerator {

    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    /**
     * Config for a REAL VPN session only. The ping/delay-test path must use
     * [generatePingConfig] instead — mirroring upstream v2rayNG, which builds a
     * separate trimmed config for speed tests (its postProcessForSpeedtest
     * clears inbounds, routing rules, dns and mux). Feeding this full session
     * config to Libv2ray.measureOutboundDelay routes the core's own DNS lookups
     * back through the proxy under test, which broke ping entirely.
     */
    fun generateConfig(profile: VpnProfile): String {
        val root = JSONObject()

        root.put("log", JSONObject().apply { put("loglevel", "warning") })

        if (V2raySettings.SESSION_DNS_MODULE) {
            putSessionDns(root)
        } else {
            // Pre-dns-fix behaviour: flat system DNS, used until dns-step-3.
            root.put("dns", JSONObject().apply {
                put("servers", JSONArray().apply { put("8.8.8.8"); put("1.1.1.1") })
                put("queryStrategy", "UseIPv4")
                put("disableCache", false)
            })
        }

        val inbounds = JSONArray()

        // FIRST inbound: consumes raw IP packets from the Android tunFd
        inbounds.put(org.json.JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "tun")
            put("settings", org.json.JSONObject().apply {
                put("mtu", 1400)
                put("userLevel", 8)
            })
            put("sniffing", org.json.JSONObject().apply {
                put("enabled", true)
                put("destOverride", org.json.JSONArray().apply { put("http"); put("tls") })
                put("routeOnly", false)
            })
        })

        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("protocol", "socks")
            put("listen", "127.0.0.1")
            put("port", SOCKS_PORT)
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
                put("userLevel", 8)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply { put("http"); put("tls") })
                put("routeOnly", false)
            })
        })

        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("protocol", "http")
            put("listen", "127.0.0.1")
            put("port", HTTP_PORT)
            put("settings", JSONObject().apply {
                put("allowTransparent", false)
                put("userLevel", 8)
            })
        })
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(profile, forSession = true).apply { put("tag", "proxy") })
        outbounds.put(JSONObject().apply {
            put("tag", "direct"); put("protocol", "freedom")
            put("settings", JSONObject().put("domainStrategy", "UseIP"))
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block"); put("protocol", "blackhole")
            put("settings", JSONObject())
        })
        outbounds.put(JSONObject().apply {
            put("tag", "dns-out"); put("protocol", "dns")
            put("settings", JSONObject())
        })
        root.put("outbounds", outbounds)

        root.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", JSONArray().apply {
                if (V2raySettings.SESSION_DNS_MODULE) {
                    putSessionDnsRoutingRules(this)
                }
                put(JSONObject().apply {
                    put("type", "field"); put("port", "53")
                    put("network", "udp,tcp"); put("outboundTag", "dns-out")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().apply {
                        put("10.0.0.0/8"); put("172.16.0.0/12")
                        put("192.168.0.0/16"); put("127.0.0.0/8")
                        put("169.254.0.0/16")
                    })
                    put("outboundTag", "direct")
                })
                put(org.json.JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", org.json.JSONArray().apply {
                        put("tun-in")
                        put("socks-in")
                        put("http-in")
                    })
                    put("outboundTag", "proxy")
                })
            })
        })

        return root.toString()
    }

    /**
     * Built-in DNS module, mirroring v2rayNG's getDns() — real sessions only.
     *  - tag = "dns-module" so the module's own upstream queries can be
     *    routed (inboundTag "dns-module" -> proxy, "domestic-dns" -> direct).
     *  - the remote resolver is queried THROUGH the proxy (encrypted inside
     *    the VLESS tunnel) so ISP plaintext-UDP/53 interception cannot poison
     *    it — this is the difference that makes browsers work where the old
     *    "forward raw 53 to 1.1.1.1 over the direct net" approach failed.
     *  - domestic resolver for direct domains, skipFallback like v2rayNG.
     */
    private fun putSessionDns(root: JSONObject) {
        root.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put(V2raySettings.REMOTE_DNS)
                put(JSONObject().apply {
                    put("address", V2raySettings.DOMESTIC_DNS)
                    put("domains", JSONArray().apply { put("geosite:private") })
                    put("skipFallback", true)
                    put("tag", V2raySettings.TAG_DOMESTIC_DNS)
                })
            })
            put("queryStrategy", "UseIPv4")
            put("disableCache", false)
            put("tag", V2raySettings.TAG_DNS)
        })
    }

    /**
     * DNS-module upstream routing — real sessions only. The dns-out outbound
     * hijacks A/AAAA into the built-in module, whose outgoing queries carry
     * inboundTag "dns-module"/"domestic-dns".
     */
    private fun putSessionDnsRoutingRules(rules: JSONArray) {
        rules.put(JSONObject().apply {
            put("type", "field")
            put("inboundTag", JSONArray().apply { put(V2raySettings.TAG_DOMESTIC_DNS) })
            put("outboundTag", "direct")
        })
        rules.put(JSONObject().apply {
            put("type", "field")
            put("inboundTag", JSONArray().apply { put(V2raySettings.TAG_DNS) })
            put("outboundTag", "proxy")
        })
    }

    /**
     * Unconditional safety rule: DNS for the proxy server's own domain NEVER
     * goes through the proxy. Inserted FIRST in routing.rules by the service
     * once it has resolved the domain to an IP, so any lookup that escapes the
     * hosts pin is still answered directly instead of deadlocking on the
     * not-yet-connected outbound. No-op for IP literals.
     */
    fun serverDomainDirectRule(serverDomain: String): JSONObject? {
        val d = serverDomain.trim()
        if (d.isEmpty() || isPureIpAddress(d)) return null
        return JSONObject().apply {
            put("type", "field")
            put("domain", JSONArray().apply { put(d) })
            put("outboundTag", "direct")
        }
    }

    /**
     * Proxy-server-domain bootstrap (mirrors upstream v2rayNG
     * resolveOutboundDomainsToHosts): pins the already system-resolved IP into
     * dns.hosts and prepends the direct-domain rule, so the core's DNS module
     * can never resolve the server address through the proxy it bootstraps.
     * Mutates [root] in place; returns true when the domain needed pinning.
     */
    fun pinServerDomainResolution(root: JSONObject, serverDomain: String, resolvedIp: String): Boolean {
        val rule = serverDomainDirectRule(serverDomain) ?: return false
        val dnsObj = root.optJSONObject("dns") ?: return false
        val hosts = dnsObj.optJSONObject("hosts") ?: JSONObject().also { dnsObj.put("hosts", it) }
        hosts.put(serverDomain.trim(), resolvedIp)
        val rules = root.getJSONObject("routing").getJSONArray("rules")
        val shifted = JSONArray()
        shifted.put(rule)
        for (i in 0 until rules.length()) shifted.put(rules.get(i))
        root.getJSONObject("routing").put("rules", shifted)
        return true
    }

    /**
     * Config for the ping/delay-test path (Libv2ray.measureOutboundDelay).
     *
     * Mirrors upstream v2rayNG's getV2rayConfig4Speedtest, whose
     * postProcessForSpeedtest strips everything the delay test does not need:
     * inbounds.clear(), routing.rules.clear(), dns = null, mux = null. Our
     * equivalent keeps the same outbounds (proxy/direct/block/dns-out are
     * harmless without rules referencing them) but produces the pre-DNS-fix
     * structure exactly: flat system DNS, no "dns-module"/"domestic-dns"
     * routing rules, mux disabled. Feeding the session config here routed the
     * core's own upstream DNS through the proxy under test — a deadlock that
     * made every profile measure -1.
     */
    fun generatePingConfig(profile: VpnProfile): String {
        val root = JSONObject()

        root.put("log", JSONObject().apply { put("loglevel", "warning") })

        // Flat system DNS, identical to pre-dns-fix: the ephemeral test core
        // resolves the (domain) server address via the device resolver.
        root.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply { put("8.8.8.8"); put("1.1.1.1") })
            put("queryStrategy", "UseIPv4")
            put("disableCache", false)
        })

        // Same inbounds the session builder emits; Libv2ray ignores them for
        // delay tests (it dials through the proxy outbound directly).
        val inbounds = JSONArray()
        inbounds.put(JSONObject().apply {
            put("tag", "tun-in")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("mtu", 1400)
                put("userLevel", 8)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply { put("http"); put("tls") })
                put("routeOnly", false)
            })
        })
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("protocol", "socks")
            put("listen", "127.0.0.1")
            put("port", SOCKS_PORT)
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
                put("userLevel", 8)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply { put("http"); put("tls") })
                put("routeOnly", false)
            })
        })
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("protocol", "http")
            put("listen", "127.0.0.1")
            put("port", HTTP_PORT)
            put("settings", JSONObject().apply {
                put("allowTransparent", false)
                put("userLevel", 8)
            })
        })
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(profile, forSession = false).apply { put("tag", "proxy") })
        outbounds.put(JSONObject().apply {
            put("tag", "direct"); put("protocol", "freedom")
            put("settings", JSONObject().put("domainStrategy", "UseIP"))
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block"); put("protocol", "blackhole")
            put("settings", JSONObject())
        })
        outbounds.put(JSONObject().apply {
            put("tag", "dns-out"); put("protocol", "dns")
            put("settings", JSONObject())
        })
        root.put("outbounds", outbounds)

        root.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "field"); put("port", "53")
                    put("network", "udp,tcp"); put("outboundTag", "dns-out")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().apply {
                        put("10.0.0.0/8"); put("172.16.0.0/12")
                        put("192.168.0.0/16"); put("127.0.0.0/8")
                        put("169.254.0.0/16")
                    })
                    put("outboundTag", "direct")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("inboundTag", JSONArray().apply {
                        put("tun-in")
                        put("socks-in")
                        put("http-in")
                    })
                    put("outboundTag", "proxy")
                })
            })
        })

        return root.toString()
    }

    private fun buildProxyOutbound(profile: VpnProfile, forSession: Boolean): JSONObject {
        val address = profile.serverAddress.trim()
        return when {
            address.startsWith("vless://", true) ||
            address.startsWith("trojan://", true) -> buildVlessOrTrojanOutbound(address, forSession)
            address.startsWith("vmess://", true)  -> buildVmessOutbound(address, forSession)
            else -> error("Unsupported config scheme: ${address.substringBefore("://")}")
        }
    }

    private data class ShareLink(
        val credential: String,
        val host: String,
        val port: Int,
        val params: Map<String, String>
    )

    private fun parseShareLink(uriStr: String): ShareLink {
        val noScheme = uriStr.substringAfter("://")
        val noFragment = noScheme.substringBefore('#')
        val query = noFragment.substringAfter('?', "")
        val authority = noFragment.substringBefore('?')

        val at = authority.lastIndexOf('@')
        require(at > 0) { "Share link has no credential@host separator" }

        val credential = URLDecoder.decode(authority.substring(0, at), "UTF-8")
        var hostPort = authority.substring(at + 1)

        val host: String
        val portStr: String
        if (hostPort.startsWith("[")) {
            host = hostPort.substringAfter('[').substringBefore(']')
            portStr = hostPort.substringAfterLast("]:", "")
        } else {
            host = hostPort.substringBeforeLast(':')
            portStr = hostPort.substringAfterLast(':', "")
        }
        require(host.isNotBlank()) { "Share link has no host" }

        val params = query.split('&')
            .filter { it.contains('=') }
            .associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }

        return ShareLink(credential, host, portStr.toIntOrNull() ?: 443, params)
    }

    private fun buildVlessOrTrojanOutbound(uriStr: String, forSession: Boolean): JSONObject {
        val isTrojan = uriStr.startsWith("trojan://", true)
        val link = parseShareLink(uriStr)

        return JSONObject().apply {
            put("protocol", if (isTrojan) "trojan" else "vless")
            if (isTrojan) {
                put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                    put("address", link.host)
                    put("port", link.port)
                    put("password", link.credential)
                    put("level", 8)
                })))
            } else {
                put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", link.host)
                    put("port", link.port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", link.credential)
                        put("encryption", link.params["encryption"] ?: "none")
                        put("level", 8)
                        link.params["flow"]?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
                    }))
                })))
            }
            put("streamSettings", buildStreamSettings(link))
            put("mux", buildMux(forSession = forSession, isTrojan = isTrojan, normalizedNet = normalizedNetworkOf(link), hasVlessFlow = !isTrojan && hasVlessFlow(link)))
        }
    }

    /**
     * Mirrors v2rayNG updateOutboundWithGlobalSettings(): mux is forced OFF for
     * trojan and XHTTP transports; for a VLESS outbound that already has a flow
     * (e.g. xtls-rprx-vision) concurrency is -1 (single stream, no multiplexing).
     */
    private fun normalizedNetworkOf(link: ShareLink): String =
        when ((link.params["type"] ?: "tcp").lowercase()) {
            "xhttp", "splithttp", "x-http", "h2", "h3", "http" -> "xhttp"
            else -> (link.params["type"] ?: "tcp").lowercase()
        }

    private fun hasVlessFlow(link: ShareLink): Boolean =
        !link.params["flow"].isNullOrBlank()

    private fun buildMux(forSession: Boolean, isTrojan: Boolean, normalizedNet: String, hasVlessFlow: Boolean): JSONObject {
        // Ping/test outbounds never get mux — identical to pre-dns-fix, where
        // every outbound carried {"enabled": false} and nothing else.
        if (!forSession) return JSONObject().put("enabled", false)

        val enabled = V2raySettings.MUX_ENABLED && !isTrojan && normalizedNet != "xhttp"
        return JSONObject().apply {
            if (enabled) {
                put("enabled", true)
                put("concurrency", if (hasVlessFlow) -1 else V2raySettings.MUX_CONCURRENCY)
                put("xudpConcurrency", V2raySettings.MUX_XUDP_CONCURRENCY)
                put("xudpProxyUDP443", V2raySettings.MUX_XUDP_QUIC)
            } else {
                put("enabled", false)
                put("concurrency", -1)
            }
        }
    }

    private fun buildStreamSettings(link: ShareLink): JSONObject {
        val p = link.params
        val rawNet = p["type"] ?: "tcp"
        val securityRaw = p["security"]?.takeIf { it.isNotBlank() }
        // v2rayNG: security is "tls" or "reality", otherwise null/no security
        val security = when (securityRaw?.lowercase()) {
            "tls", "reality" -> securityRaw.lowercase()
            else -> null
        }

        // Normalize network like v2rayNG NetworkType (+ legacy aliases) but keep
        // xhttp as "xhttp" so we emit the canonical name; Xray accepts both
        // xhttp and splithttp (TransportProtocol.Build maps both to splithttp).
        val normalizedNet = when (rawNet.lowercase()) {
            "xhttp", "splithttp", "x-http" -> "xhttp"
            "tcp", "raw" -> "tcp"
            "ws", "websocket" -> "ws"
            "grpc" -> "grpc"
            "kcp", "mkcp" -> "kcp"
            "httpupgrade" -> "httpupgrade"
            // Legacy HTTP/H2 transports were removed in Xray ("HTTP transport ...
            // without header padding" -> use XHTTP stream-one). Map them to
            // XHTTP for forward-compat instead of emitting an invalid "h2".
            "h2", "h3", "http" -> "xhttp"
            else -> rawNet.lowercase()
        }

        // Pre-extract fields exactly like FmtBase.getItemFormQuery / CoreOutboundBuilder
        val hostParam = p["host"]?.takeIf { it.isNotBlank() }
        val pathParam = p["path"]?.takeIf { it.isNotBlank() }
        val modeParam = p["mode"]?.takeIf { it.isNotBlank() }
        val extraParam = p["extra"]?.takeIf { it.isNotBlank() }
        val serviceNameParam = p["serviceName"]?.takeIf { it.isNotBlank() }
        val authorityParam = p["authority"]?.takeIf { it.isNotBlank() }
        val headerType = p["headerType"]?.takeIf { it.isNotBlank() }

        // Determine sniExt from transport host the same way CoreOutboundBuilder
        // does: the return value of populateTransportSettings.
        // For each network we compute it and then derive TLS SNI from it.
        var sniExt: String? = null
        val streamObj = JSONObject().apply {
            put("network", normalizedNet)
            if (security != null) put("security", security) else put("security", "none")

            when (normalizedNet) {
                "tcp" -> {
                    if (headerType == "http") {
                        val requestHost = hostParam ?: link.host
                        put("tcpSettings", JSONObject().put("header", JSONObject().apply {
                            put("type", "http")
                            put("request", JSONObject().apply {
                                put("path", JSONArray().put(pathParam ?: "/"))
                                put("headers", JSONObject().put("Host", JSONArray().put(requestHost)))
                            })
                        }))
                        sniExt = requestHost
                    } else {
                        // Still emit a tcpSettings header type none for completeness
                        put("tcpSettings", JSONObject().put("header", JSONObject().apply { put("type", "none") }))
                        sniExt = hostParam
                    }
                }
                "ws" -> {
                    put("wsSettings", JSONObject().apply {
                        put("path", pathParam ?: "/")
                        // v2rayNG WsSettingsBean uses plain host field, not headers map
                        if (!hostParam.isNullOrBlank()) put("host", hostParam) else put("host", "")
                    })
                    sniExt = hostParam
                }
                "grpc" -> {
                    put("grpcSettings", JSONObject().apply {
                        put("serviceName", serviceNameParam.orEmpty())
                        put("authority", authorityParam.orEmpty())
                        put("multiMode", modeParam == "multi")
                        // Align with v2rayNG defaults
                        put("idle_timeout", 60)
                        put("health_check_timeout", 20)
                    })
                    sniExt = authorityParam
                }
                "httpupgrade" -> {
                    put("httpupgradeSettings", JSONObject().apply {
                        put("path", pathParam ?: "/")
                        put("host", hostParam.orEmpty())
                    })
                    sniExt = hostParam
                }
                "xhttp" -> {
                    val xPath = pathParam ?: "/"
                    val xHost = hostParam.orEmpty()
                    val xhttpObj = JSONObject().apply {
                        put("path", xPath)
                        put("host", xHost)
                        if (!modeParam.isNullOrBlank()) put("mode", modeParam)
                        // v2rayNG uses xhttpExtra -> extra (parsed JSON), NOT headers
                        if (!extraParam.isNullOrBlank()) {
                            // Try to parse as JSON object; if invalid keep raw string
                            // to match JsonUtil.parseString behaviour (null on invalid)
                            try {
                                val parsed = JSONObject(extraParam)
                                put("extra", parsed)
                            } catch (_: Exception) {
                                try {
                                    val parsedArr = JSONArray(extraParam)
                                    put("extra", parsedArr)
                                } catch (_: Exception) {
                                    // Invalid JSON -> omit, matching v2rayNG null
                                }
                            }
                        }
                    }
                    // Emit both keys for Xray compat: infra/conf copies xhttpSettings->splithttpSettings
                    put("xhttpSettings", xhttpObj)
                    put("splithttpSettings", JSONObject(xhttpObj.toString()))
                    sniExt = hostParam
                }
                "kcp", "mkcp" -> {
                    put("kcpSettings", JSONObject().apply {
                        put("uplinkCapacity", 5)
                        put("downlinkCapacity", 20)
                        put("congestion", true)
                    })
                    sniExt = null
                }
            }

            // --- TLS / REALITY (populateTlsSettings) ---
            if (security != null) {
                // allowInsecure: support "insecure", "allowInsecure", "allow_insecure" like FmtBase
                val allowInsecureRaw = when {
                    p["insecure"] == "1" || p["allowInsecure"] == "1" || p["allow_insecure"] == "1" -> true
                    p["insecure"] == "0" || p["allowInsecure"] == "0" || p["allow_insecure"] == "0" -> false
                    else -> false
                }
                val pinnedCA = p["pcs"]?.takeIf { it.isNotBlank() }
                val allowInsecure = allowInsecureRaw && pinnedCA.isNullOrEmpty()

                // SNI resolution: mirrors CoreOutboundBuilder.populateTlsSettings
                val sniParam = p["sni"]?.takeIf { it.isNotBlank() }
                val sniValue: String? = if (sniParam.isNullOrBlank()) {
                    when {
                        !sniExt.isNullOrBlank() && isDomainName(sniExt) -> sniExt
                        isDomainName(link.host) -> link.host
                        else -> sniExt
                    }
                } else sniParam

                val fingerprint = p["fp"]?.takeIf { it.isNotBlank() }
                val alpnRaw = p["alpn"]?.takeIf { it.isNotBlank() }
                val alpnList = alpnRaw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                val ech = p["ech"]?.takeIf { it.isNotBlank() }
                val vcn = p["vcn"]?.takeIf { it.isNotBlank() }
                val pcs = p["pcs"]?.takeIf { it.isNotBlank() }
                val pbk = p["pbk"]?.takeIf { it.isNotBlank() }
                val sid = p["sid"]?.takeIf { it.isNotBlank() }
                val spx = p["spx"]?.takeIf { it.isNotBlank() } ?: "/"
                val pqv = p["pqv"]?.takeIf { it.isNotBlank() }

                if (security == "tls") {
                    put("tlsSettings", JSONObject().apply {
                        put("serverName", sniValue ?: "")
                        put("allowInsecure", allowInsecure)
                        if (!fingerprint.isNullOrBlank()) put("fingerprint", fingerprint)
                        if (!alpnList.isNullOrEmpty()) put("alpn", JSONArray(alpnList))
                        if (!ech.isNullOrBlank()) put("echConfigList", ech)
                        if (!vcn.isNullOrBlank()) put("verifyPeerCertByName", vcn)
                        if (!pcs.isNullOrBlank()) put("pinnedPeerCertSha256", pcs)
                        // Reality-only fields are not set for TLS, but keep null-safe
                    })
                } else { // reality
                    put("realitySettings", JSONObject().apply {
                        put("serverName", sniValue ?: "")
                        put("fingerprint", fingerprint ?: "chrome")
                        put("publicKey", pbk.orEmpty())
                        put("shortId", sid.orEmpty())
                        put("spiderX", spx)
                        if (!pqv.isNullOrBlank()) put("mldsa65Verify", pqv)
                        // TLS-specific fields that also belong in realitySettings
                        put("allowInsecure", allowInsecure)
                        if (!alpnList.isNullOrEmpty()) put("alpn", JSONArray(alpnList))
                        if (!ech.isNullOrBlank()) put("echConfigList", ech)
                        if (!vcn.isNullOrBlank()) put("verifyPeerCertByName", vcn)
                        if (!pcs.isNullOrBlank()) put("pinnedPeerCertSha256", pcs)
                    })
                }
            }

            // finalmask: fm param -> finalmask (mirrors populateTransportSettings finalMask)
            p["fm"]?.takeIf { it.isNotBlank() }?.let { fmRaw ->
                try {
                    // fm is expected to be a JSON object string
                    val fmJson = JSONObject(fmRaw)
                    put("finalmask", fmJson)
                } catch (_: Exception) {
                    // Try raw as is (could be already JSON array/object fragment)
                    try { put("finalmask", JSONObject("""{"tcp":$fmRaw}""")) } catch (_: Exception) {}
                }
            }
        }
        return streamObj
    }

    private fun isDomainName(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val v = value.trim()
        // mirrored approximation of Utils.isDomainName: not pure IP and looks like domain
        if (isPureIpAddress(v)) return false
        // simple domain check: contains dot and valid chars
        return v.contains('.') && Regex("^[A-Za-z0-9.-]+$").matches(v)
    }

    private fun isPureIpAddress(value: String): Boolean {
        val v = value.trim().removePrefix("[").removeSuffix("]")
        // IPv4
        if (Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}$").matches(v)) {
            return v.split('.').all { it.toIntOrNull() in 0..255 }
        }
        // IPv6 heuristic: contains colon and hex chars
        return v.contains(':') && Regex("^[0-9a-fA-F:]+$").matches(v)
    }

    private fun buildVmessOutbound(vmess: String, forSession: Boolean): JSONObject {
        val b64 = vmess.substringAfter("://").substringBefore('#')
        val decoded = String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE))
        val json = JSONObject(decoded)
        val add = json.optString("add")
        val port = json.optInt("port", 443)

        val fakeParams = mutableMapOf<String, String>().apply {
            put("type", json.optString("net", "tcp"))
            put("security", json.optString("tls", "none").ifBlank { "none" })
            json.optString("sni").takeIf { it.isNotBlank() }?.let { put("sni", it) }
            json.optString("host").takeIf { it.isNotBlank() }?.let { put("host", it) }
            json.optString("path").takeIf { it.isNotBlank() }?.let { put("path", it) }
            json.optString("fp").takeIf { it.isNotBlank() }?.let { put("fp", it) }
            json.optString("alpn").takeIf { it.isNotBlank() }?.let { put("alpn", it) }
            json.optString("type").takeIf { it.isNotBlank() }?.let { put("headerType", it) }
        }

        return JSONObject().apply {
            put("protocol", "vmess")
            put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                put("address", add); put("port", port)
                put("users", JSONArray().put(JSONObject().apply {
                    put("id", json.optString("id"))
                    put("alterId", json.optInt("aid", 0))
                    put("security", json.optString("scy", "auto").ifBlank { "auto" })
                    put("level", 8)
                }))
            })))
            put("streamSettings", buildStreamSettings(ShareLink("", add, port, fakeParams)))
            put("mux", buildMux(forSession = forSession, isTrojan = false, normalizedNet = normalizedNetworkOf(ShareLink("", add, port, fakeParams)), hasVlessFlow = false))
        }
    }
}
