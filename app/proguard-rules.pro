# R8 / ProGuard rules for Nexum

# Keep domain & data models for Room, Moshi and JSON serialization
-keep class com.kairav.nexum.data.models.** { *; }

# Moshi
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Room Database
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Kotlin Coroutines & Reflection
-dontwarn kotlinx.coroutines.**

# Google Errorprone Annotations (used in Tink / Crypto)
-dontwarn com.google.errorprone.annotations.**
