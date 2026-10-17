package dev.rouzbeh.hybridvpn.core.vpn.v2ray

import android.util.Base64
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object XrayConfigGenerator {

    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    fun generateConfig(profile: VpnProfile): String {
        val root = JSONObject()

        root.put("log", JSONObject().apply { put("loglevel", "warning") })

        root.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply { put("8.8.8.8"); put("1.1.1.1") })
            put("queryStrategy", "UseIPv4")
            put("disableCache", false)
        })

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
        outbounds.put(buildProxyOutbound(profile).apply { put("tag", "proxy") })
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

    private fun buildProxyOutbound(profile: VpnProfile): JSONObject {
        val address = profile.serverAddress.trim()
        return when {
            address.startsWith("vless://", true) ||
            address.startsWith("trojan://", true) -> buildVlessOrTrojanOutbound(address)
            address.startsWith("vmess://", true)  -> buildVmessOutbound(address)
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

    private fun buildVlessOrTrojanOutbound(uriStr: String): JSONObject {
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
            put("mux", JSONObject().put("enabled", false))
        }
    }

    private fun buildStreamSettings(link: ShareLink): JSONObject {
        val p = link.params
        val net = p["type"] ?: "tcp"
        val security = p["security"] ?: "none"
        val sni = p["sni"]?.takeIf { it.isNotBlank() }
            ?: p["host"]?.takeIf { it.isNotBlank() }
            ?: link.host

        return JSONObject().apply {
            put("network", net)
            put("security", security)

            when (security) {
                "tls" -> put("tlsSettings", JSONObject().apply {
                    put("serverName", sni)
                    put("allowInsecure", p["allowInsecure"] == "1")
                    put("fingerprint", p["fp"] ?: "chrome")
                    p["alpn"]?.takeIf { it.isNotBlank() }
                        ?.let { put("alpn", JSONArray(it.split(","))) }
                })
                "reality" -> put("realitySettings", JSONObject().apply {
                    put("serverName", sni)
                    put("publicKey", p["pbk"].orEmpty())
                    put("shortId", p["sid"].orEmpty())
                    put("spiderX", p["spx"] ?: "/")
                    put("fingerprint", p["fp"] ?: "chrome")
                })
            }

            when (net) {
                "tcp" -> if (p["headerType"] == "http") {
                    put("tcpSettings", JSONObject().put("header", JSONObject().apply {
                        put("type", "http")
                        put("request", JSONObject().apply {
                            put("path", JSONArray().put(p["path"] ?: "/"))
                            put("headers", JSONObject().put("Host",
                                JSONArray().put(p["host"] ?: link.host)))
                        })
                    }))
                }
                "ws" -> put("wsSettings", JSONObject().apply {
                    put("path", p["path"]?.takeIf { it.isNotBlank() } ?: "/")
                    put("headers", JSONObject().put("Host", p["host"] ?: sni))
                })
                "grpc" -> put("grpcSettings", JSONObject().apply {
                    put("serviceName", p["serviceName"].orEmpty())
                    put("multiMode", p["mode"] == "multi")
                })
                "httpupgrade" -> put("httpupgradeSettings", JSONObject().apply {
                    put("path", p["path"]?.takeIf { it.isNotBlank() } ?: "/")
                    put("host", p["host"] ?: sni)
                })
            }
        }
    }

    private fun buildVmessOutbound(vmess: String): JSONObject {
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
            put("mux", JSONObject().put("enabled", false))
        }
    }
}
