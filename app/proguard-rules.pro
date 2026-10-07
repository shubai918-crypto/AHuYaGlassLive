# ==========================================
# AHuYaGlassLive 基础混淆规则
# ==========================================

# 1. OkHttp 网络库 (虎牙流解析和 API 请求必备)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.internal.platform.** { *; }

# 2. Kotlin Coroutines (协程，弹幕和流解析必备)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# 3. Media3 ExoPlayer (视频播放器)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }

# 4. Kyant0 Backdrop & Jetpack Compose (液态玻璃 UI)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class io.github.kyant0.** { *; }

# 5. 保留 Kotlin 元数据
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# 6. 通用基础保留
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
