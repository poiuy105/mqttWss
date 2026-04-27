# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/user/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# BouncyCastle - 保持所有BC类不被混淆
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Conscrypt - OkHttp依赖
-dontwarn org.conscrypt.**
-keep class org.conscrypt.** { *; }

# OkHttp - 保持OkHttp相关类
-dontwarn okhttp3.internal.platform.BouncyCastlePlatform
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# MQTT客户端
-keep class org.eclipse.paho.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
