# R8 for release (F-28, U-31). The rules keep what is reached by name at run time.

# The app's data layer: Room entities and DAOs, backup JSON field names.
-keep class ru.maxrubtsoff.bankagg.data.** { *; }

# Native libraries look their Java classes, fields and callbacks up by name (JNI).
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class ai.onnxruntime.** { *; }
-keep class net.zetetic.database.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Widget, receivers, workers and the notification listener are named in the manifest
# or by WorkManager; AAPT keeps manifest entries, workers are kept here.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Optional parts of libraries that the app doesn't ship.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# F-54: no debug/info logging in release (Log.w with failure types stays).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
