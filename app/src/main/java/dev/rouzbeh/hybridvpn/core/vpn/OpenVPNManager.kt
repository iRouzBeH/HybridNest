package dev.rouzbeh.hybridvpn.core.vpn

import android.util.Log

/**
 * Manages OpenVPN connections via JNI
 */
object OpenVPNManager {

    init {
        try {
            System.loadLibrary("hybridvpn")
            Log.d("OpenVPNManager", "Native library 'cybernest' loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("OpenVPNManager", "Failed to load native library: ${e.message}")
        }
    }

    /**
     * Starts the OpenVPN tunnel
     * @param config The .ovpn configuration string
     * @param username Optional username
     * @param password Optional password
     * @return true if successful
     */
    external fun startTunnel(config: String, username: String, password: String): Boolean

    /**
     * Stops the OpenVPN tunnel
     */
    external fun stopTunnel()
}
