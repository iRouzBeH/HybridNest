package dev.rouzbeh.hybridvpn.core.model

data class NetworkInfo(
    val ipAddress: String,
    val location: String,
    val countryCode: String // مثلاً "de", "ir", "us"
)