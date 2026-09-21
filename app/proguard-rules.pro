# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the ProGuard
# include property in project.properties.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# ---- Retrofit ----
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# ---- Gson ----
-keepattributes Signature
-keep class com.dsh.android.data.remote.model.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation class * extends com.google.gson.TypeAdapter
-keep,allowobfuscation class * implements com.google.gson.TypeAdapterFactory
-keep,allowobfuscation class * implements com.google.gson.JsonSerializer
-keep,allowobfuscation class * implements com.google.gson.JsonDeserializer

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ---- Hilt / Dagger ----
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- Kotlin Serialization (if used) ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
