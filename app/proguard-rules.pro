# 寄意 ProGuard/R8 规则

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.bicy.note.**$$serializer { *; }
-keepclassmembers class com.bicy.note.** {
    *** Companion;
}
-keepclasseswithmembers class com.bicy.note.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留数据模型（序列化需要）
-keep class com.bicy.note.data.model.** { *; }

# BroadcastReceivers
-keep class com.bicy.note.ui.ScheduledDndReceiver { *; }
-keep class com.bicy.note.ui.ScheduledAlarmReceiver { *; }

# Services
-keep class com.bicy.note.ui.QuickNoteService { *; }
-keep class com.bicy.note.ui.AppNotificationListener { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
