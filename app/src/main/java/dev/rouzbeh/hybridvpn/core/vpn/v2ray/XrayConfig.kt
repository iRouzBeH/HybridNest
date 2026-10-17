package dev.rouzbeh.hybridvpn.core.vpn.v2ray

data class XrayConfig(
    val remark: String = "",
    val coreConfigJson: String = "",
    val localSocksPort: Int = 10808,
    val localHttpPort: Int = 10809
)
