package dev.rouzbeh.hybridvpn.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.rouzbeh.hybridvpn.core.utils.LogRedactor

/**
 * Process-scoped log buffer. Survives Activity/ViewModel recreation so
 * Live Log is not wiped when the user exits and re-enters the app while
 * the VPN tunnel remains up. Only cleared explicitly when a new
 * connection is about to be established (CONNECTING).
 */
object LogStore {
    private const val MAX_LINES = 500

    private val _openConnectLogs = MutableStateFlow<List<String>>(emptyList())
    private val _v2rayLogs = MutableStateFlow<List<String>>(emptyList())
    private val _dnsLogs = MutableStateFlow<List<String>>(emptyList())

    val openConnectLogs: StateFlow<List<String>> = _openConnectLogs.asStateFlow()
    val v2rayLogs: StateFlow<List<String>> = _v2rayLogs.asStateFlow()
    val dnsLogs: StateFlow<List<String>> = _dnsLogs.asStateFlow()

    fun clear(protocol: String) {
        when (protocol) {
            "OpenConnect" -> _openConnectLogs.value = emptyList()
            "DNS" -> _dnsLogs.value = emptyList()
            else -> _v2rayLogs.value = emptyList()
        }
    }

    fun append(protocol: String, rawLine: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val formatted = "[$time] ${LogRedactor.redact(rawLine)}"
        when (protocol) {
            "OpenConnect" -> _openConnectLogs.value = (_openConnectLogs.value + formatted).takeLast(MAX_LINES)
            "DNS" -> _dnsLogs.value = (_dnsLogs.value + formatted).takeLast(MAX_LINES)
            else -> _v2rayLogs.value = (_v2rayLogs.value + formatted).takeLast(MAX_LINES)
        }
    }
}
