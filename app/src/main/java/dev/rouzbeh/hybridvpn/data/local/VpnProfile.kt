package dev.rouzbeh.hybridvpn.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vpn_profiles")
data class VpnProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val protocol: String,
    val serverAddress: String,
    val username: String = "",
    val password: String = "",
    val isActive: Boolean = false,
    val countryCode: String = "un", // Default to unknown
    val subName: String = "",
    val ping: Long = 0L,
    val mAllowedAppsVpn: String? = null,
    val mAllowedAppsVpnAreDisallowed: Boolean = false
)
