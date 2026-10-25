# Shizuku API and its AIDL-generated binder classes must survive R8.
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Keep reflection metadata intact for the hidden-API bridges Shizuku uses.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

-keep class dev.pulseboost.pro.BuildConfig { *; }

# AIDL parcelables referenced by the Shizuku server bridge.
-keep class androidx.annotation.** { *; }
-dontwarn androidx.annotation.**
