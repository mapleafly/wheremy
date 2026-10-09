# R8 规则（P6 发布准备）。三条都是"删了会在真机上炸"的那类，别顺手清掉：
#
# 1. kotlinx.serialization：备份包的四张表直接序列化 entity，生成出来的 `$$serializer` 被反射查找。
#    官方规则集的精简版（`kotlinx-serialization` 的 consumer rules 只覆盖了一部分场景）。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.wheremy.app.data.db.** {
    *** Companion;
}
-keepclasseswithmembers class com.wheremy.app.data.db.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wheremy.app.data.**$$serializer { *; }

# 2. Room 3：实体与 DAO 由注解处理器生成，按名字反射拿构造函数。
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keep @androidx.room3.Entity class * { *; }
-dontwarn androidx.room3.paging.**

# 3. Hilt 的 Worker：WorkManager 按类名实例化，HiltWorkerFactory 需要它不被裁掉。
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# 网络栈的既有豁免（OkHttp/Okio 的可选依赖）
-dontwarn okhttp3.**
-dontwarn okio.**
