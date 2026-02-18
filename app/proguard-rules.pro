# ── NetFlow Predict ProGuard Rules ─────────────────────────────────────────────

# ── General ────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ─────────────────────────────────────────────────────────────────────
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ── Room ───────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ── Gson ───────────────────────────────────────────────────────────────────────
# Gson uses generic type information stored in a class file when working with
# fields. Proguard removes such information by default, so configure it to keep
# all of it.
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# Prevent proguard from stripping interface information from TypeAdapter,
# TypeAdapterFactory, JsonSerializer, JsonDeserializer instances
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Gson-serialized data classes in PredictionWorker and Repositories
-keep class com.netflow.predict.worker.PredictionWorker$AppRiskJson { *; }
-keep class com.netflow.predict.worker.PredictionWorker$DomainRiskJson { *; }
-keep class com.netflow.predict.data.repository.TrafficRepository$AppRiskJsonData { *; }
-keep class com.netflow.predict.data.repository.TrafficRepository$DomainRiskJsonData { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── WorkManager ────────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class androidx.work.WorkerParameters { *; }

# ── DataStore ──────────────────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
  <fields>;
}

# ── Coroutines ─────────────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Compose ────────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Data model classes (keep for reflection-based serialization) ───────────────
-keep class com.netflow.predict.data.model.** { *; }
-keep class com.netflow.predict.data.local.entity.** { *; }
