# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep NeteaseApi and related classes
-keep class com.hypermusic.miuix.data.api.** { *; }
-keep class com.hypermusic.miuix.data.model.** { *; }
-keep class com.hypermusic.miuix.service.** { *; }

# Keep Miuix classes
-keep class top.yukonga.miuix.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }

# Keep ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ML Kit discovers barcode components through Firebase registrars at runtime.
# Preserve the registered classes and their factories in optimized builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep MediaSession
-keep class androidx.media.** { *; }
-keep class androidx.media2.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
