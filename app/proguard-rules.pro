# ════════════════════════════════════════════════════════════
# ALLGEMEINE OPTIMIERUNGEN
# ════════════════════════════════════════════════════════════

# Wir entfernen die Regel, die ALLES behält:
# -keep class com.hundewildnis.app.** { *; } <-- GELÖSCHT

# Behalte Klassen, die für Android-System-Callbacks wichtig sind
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

-keep class com.hundewildnis.app.SecureBaseActivity { *; }

# ════════════════════════════════════════════════════════════
# DATENMODELLE & GSON (WICHTIG!)
# ════════════════════════════════════════════════════════════

# Da Gson Reflection nutzt, müssen wir Datenklassen behalten, 
# damit Feldnamen nicht verschleiert werden (sonst bricht das JSON-Format).

# 1. Gson Basiskonfiguration
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 2. Behalte alle Klassen mit @SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 3. Gezielte Keep-Regeln für Backup-Modelle
# Wir müssen sowohl die Klassen als auch deren Mitglieder behalten,
# da viele Modelle kein @SerializedName nutzen.
-keep class com.hundewildnis.app.DogProfile { *; }
-keep class com.hundewildnis.app.BackupData { *; }
-keep class com.hundewildnis.app.BackupMetadata { *; }
-keep class com.hundewildnis.app.BackupItemCount { *; }
-keep class com.hundewildnis.app.HeatCycleEntry { *; }
-keep class com.hundewildnis.app.TimelineUserEntry { *; }
-keep class com.hundewildnis.app.TimelineLocation { *; }
-keep class com.hundewildnis.app.VitalRecord { *; }
-keep class com.hundewildnis.app.EmergencyReport { *; }
-keep class com.hundewildnis.app.TrainingSession { *; }
-keep class com.hundewildnis.app.TrainingProgress { *; }
-keep class com.hundewildnis.app.Milestone { *; }
-keep class com.hundewildnis.app.FavoritePOI { *; }
-keep class com.hundewildnis.app.NasenarbeitSession { *; }
-keep class com.hundewildnis.app.NasenarbeitFortschritt { *; }
-keep class com.hundewildnis.app.WeatherFavorite { *; }
-keep class com.hundewildnis.app.IntervalTimerSession { *; }
-keep class com.hundewildnis.app.PlannedSession { *; }
-keep class com.hundewildnis.app.NoteSession { *; }
-keep class com.hundewildnis.app.SpeedSession { *; }
-keep class com.hundewildnis.app.VoiceSession { *; }
-keep class com.hundewildnis.app.CourseBlock { *; }
-keep class com.hundewildnis.app.CourseLesson { *; }
-keep class com.hundewildnis.app.CourseIndexEntry { *; }
-keep class com.hundewildnis.app.DogCharacter { *; }
-keep class com.hundewildnis.app.SpielSession { *; }
-keep class com.hundewildnis.app.SpielStatistik { *; }
-keep class com.hundewildnis.app.SpielAktivitaet { *; }
-keep enum com.hundewildnis.app.SpielOrt { *; }
-keep enum com.hundewildnis.app.SpielZeitbudget { *; }
-keep enum com.hundewildnis.app.SpielMaterial { *; }
-keep enum com.hundewildnis.app.SpielFokus { *; }
-keep enum com.hundewildnis.app.SpielEignung { *; }
-keep class com.hundewildnis.app.GelaendeAbschnitt { *; }
-keep class com.hundewildnis.app.NasenarbeitParameter { *; }
-keep class com.hundewildnis.app.BackupContainer { *; }
-keep class com.hundewildnis.app.SelectiveBackupContainer { *; }

# NEU: Standortfreigabe & WebDAV Modelle
-keep class com.hundewildnis.app.LocationPayload { *; }
-keep class com.hundewildnis.app.RouteData { *; }
-keep class com.hundewildnis.app.SurfaceSummary { *; }
-keep class com.hundewildnis.app.WebDavPreferences$WebDavConfig { *; }
-keep class com.hundewildnis.app.POI { *; }
-keep class com.hundewildnis.app.POICategories$Category { *; }

# 4. Enums in diesen Modellen schützen (Gson nutzt deren Namen)
-keepclassmembers enum com.hundewildnis.app.** { *; }

# 5. Behalte alle Parcelable-Klassen (für Intents/Bundles)
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ════════════════════════════════════════════════════════════
# LOGGING ENTFERNEN (Performance-Boost)
# ════════════════════════════════════════════════════════════
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# SLF4J
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Feed- und Podcast-Datenmodelle werden mit Gson gespeichert: Klassen und Feldnamen behalten
-keep class com.hundewildnis.app.feed.FeedData { *; }
-keep class com.hundewildnis.app.feed.FeedSub { *; }
-keep class com.hundewildnis.app.feed.FeedArticle { *; }
-keep class com.hundewildnis.app.podcast.PodcastData { *; }
-keep class com.hundewildnis.app.podcast.Podcast { *; }
-keep class com.hundewildnis.app.podcast.Episode { *; }
