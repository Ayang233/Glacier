# Glacier R8 rules

# --- Kotlin serialization (顶层路由的 BackStack 需要反序列化) ---
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.ayang.glacier.**$$serializer { *; }
-keepclassmembers class com.ayang.glacier.** {
    *** Companion;
}
-keepclasseswithmembers class com.ayang.glacier.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# --- WorkManager 通过反射实例化 Worker ---
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- 组件由系统按清单实例化 ---
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends android.service.quicksettings.TileService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# --- 可选依赖：没有 Xposed 环境时只有编译期引用 ---
-dontwarn io.github.libxposed.**
-keep class com.ayang.glacier.xposed.** { *; }

# --- 第三方 API 用了反射/隐藏方法，保留其公开签名 ---
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.rosan.dhizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }

# Hidden framework class used by Dhizuku (reflection path, not a runtime dependency)
-dontwarn android.app.ActivityThread
