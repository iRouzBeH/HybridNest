# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/rouzbeh/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any custom rules here to prevent over-minification
-keep class dev.rouzbeh.hybridvpn.data.local.** { *; }
-keep class dev.rouzbeh.hybridvpn.service.** { *; }

# JNI & Native Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Gomobile / Xray Core bindings
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# Core VPN engines, models, and routing logic
-keep class dev.rouzbeh.hybridvpn.core.vpn.** { *; }
-keep class dev.rouzbeh.hybridvpn.core.routing.** { *; }
-keep class dev.rouzbeh.hybridvpn.core.model.** { *; }
