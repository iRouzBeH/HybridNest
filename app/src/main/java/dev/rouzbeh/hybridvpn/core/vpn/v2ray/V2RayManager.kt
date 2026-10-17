package dev.rouzbeh.hybridvpn.core.vpn.v2ray

import android.content.Context
import android.util.Log
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object V2RayManager {
    private const val TAG = "V2RayManager"
    private val running = AtomicBoolean(false)
    @Volatile private var envInitialized = false

    private val coreExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "xray-core-thread").apply { isDaemon = false }
    }

    @Volatile private var coreController: CoreController? = null

    fun isRunning(): Boolean = running.get()

    private fun validate(configJson: String) {
        val root = org.json.JSONObject(configJson)
        val inbounds = root.optJSONArray("inbounds") ?: error("config has no inbounds array")
        require(inbounds.length() > 0) { "config has zero inbounds" }

        for (i in 0 until inbounds.length()) {
            val ib = inbounds.getJSONObject(i)
            val proto = ib.optString("protocol")
            require(proto in setOf(
                "tun", "socks", "http", "dokodemo-door", "vmess", "vless",
                "trojan", "shadowsocks", "wireguard"
            )) { "unsupported inbound protocol '$proto'" }
            
            // tun inbound does not need a port
            if (proto != "tun") {
                require(ib.has("port")) { "inbound '$proto' is missing a port" }
            }
        }
        
        require((0 until inbounds.length()).any {
            inbounds.getJSONObject(it).optString("protocol") == "tun"
        }) { "no tun inbound: raw packets from tunFd would be dropped" }

        val outbounds = root.optJSONArray("outbounds") ?: error("config has no outbounds array")
        require((0 until outbounds.length()).any {
            outbounds.getJSONObject(it).optString("tag") == "proxy"
        }) { "no outbound tagged 'proxy'" }
    }

    @Synchronized
    fun startCore(
        context: Context,
        configJson: String,
        tunFd: Int,
        callbacks: CoreCallbackHandler
    ): Boolean {
        if (running.get()) return true
        if (tunFd < 0) {
            callbacks.onEmitStatus(-1L, "START FAILED: invalid tun fd ($tunFd)")
            return false
        }

        return try {
            validate(configJson)

            if (!envInitialized) {
                val assetDir = context.filesDir.absolutePath
                Libv2ray.initCoreEnv(assetDir, "") 
                envInitialized = true
                Log.i(TAG, "Xray version: ${runCatching { Libv2ray.checkVersionX() }.getOrNull()}")
            }

            File(context.cacheDir, "xray_config.json").writeText(configJson)

            val controller = Libv2ray.newCoreController(callbacks)
            coreController = controller
            running.set(true)
            Log.d(TAG, "XRAY: CoreController assigned: $controller")

            coreExecutor.execute {
                try {
                    Log.i(TAG, "startLoop(fd=$tunFd)")
                    controller.startLoop(configJson, tunFd)
                    Log.i(TAG, "startLoop returned")
                } catch (t: Throwable) {
                    Log.e(TAG, "Xray core error", t)
                    callbacks.onEmitStatus(-1L, "CORE ERROR: ${t.message}")
                }
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start core", t)
            callbacks.onEmitStatus(-1L, "START FAILED: ${t.message}")
            synchronized(V2RayManager) {
                running.set(false)
                coreController = null
            }
            false
        }
    }

    @Synchronized
    fun stopCore() {
        Log.d(TAG, "XRAY: Stopping core...")
        val c = coreController
        if (c == null) {
            Log.w(TAG, "XRAY: stopCore() called but coreController is already null")
            return
        }

        val thread = Thread {
            try { 
                Log.d(TAG, "XRAY: Calling stopLoop()...")
                c.stopLoop() 
                Log.d(TAG, "XRAY: stopLoop() returned")
            } catch (t: Throwable) { 
                Log.e(TAG, "XRAY: stopLoop failed", t) 
            } finally {
                synchronized(V2RayManager) {
                    if (coreController === c) {
                        Log.d(TAG, "XRAY: Explicitly clearing coreController and running flag AFTER stopLoop")
                        coreController = null
                        running.set(false)
                    }
                }
            }
        }
        thread.start()
        try {
            thread.join(3000)
        } catch (e: InterruptedException) {
            Log.w(TAG, "XRAY: Wait for stopLoop interrupted", e)
        }
    }

    fun measureRealPing(): Long {
        val proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809))
        val url = java.net.URL("https://cp.cloudflare.com/generate_204")

        return try {
            // 1. Warm-up Call: Discard result to stabilize connection/handshake
            val warmUp = url.openConnection(proxy) as java.net.HttpURLConnection
            warmUp.connectTimeout = 3000
            warmUp.readTimeout = 3000
            warmUp.requestMethod = "GET"
            warmUp.connect()
            warmUp.responseCode
            warmUp.disconnect()

            // 2. Real Call: Measure latency
            val start = System.currentTimeMillis()
            val connection = url.openConnection(proxy) as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            connection.connect()
            
            val code = connection.responseCode
            if (code == 204 || code == 200) {
                System.currentTimeMillis() - start
            } else {
                -1L
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Real Ping failed: ${e.message}")
            -1L
        }
    }
}
