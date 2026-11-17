# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/rouzbeh/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any custom rules here to prevent over-minification
-keep class dev.rouzbeh.hybridvpn.** { *; }
-keepclassmembers enum * { *; }

# JNI & Native Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Gomobile / Xray Core bindings
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**

# Google ML Kit & Barcode Scanning components
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.components.** { *; }
-dontwarn com.google.firebase.components.**
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# Preserve attributes for reflection, generics, and annotations
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# JSON / Gson / Serialization support
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
