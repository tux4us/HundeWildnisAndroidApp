# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Grundlegende Regeln
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# App-spezifische Klassen behalten
-keep class com.hundewildnis.app.** { *; }

# WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Gson/JSON
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.** { *; }

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Remove logging in production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ════════════════════════════════════════════════════════════
# SLF4J - FIX FOR MISSING StaticLoggerBinder
# ════════════════════════════════════════════════════════════
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# ════════════════════════════════════════════════════════════
# JSOUP - FIX FÜR RE2J FEHLER
# ════════════════════════════════════════════════════════════
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-keep class org.jsoup.** { *; }
-dontnote com.google.re2j.**

# ════════════════════════════════════════════════════════════
# KOTLIN & COROUTINES
# ════════════════════════════════════════════════════════════
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ════════════════════════════════════════════════════════════
# ITEXT 7 - PDF GENERATION COMPREHENSIVE FIX
# ════════════════════════════════════════════════════════════
-keep class com.itextpdf.** { *; }
-keep interface com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# iText 7 nutzt Reflection für viele interne Komponenten
-keepclassmembers class com.itextpdf.** {
    public <fields>;
    public <methods>;
}

# Verhindert Warnungen über fehlende optionale Abhängigkeiten
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.xml.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**
