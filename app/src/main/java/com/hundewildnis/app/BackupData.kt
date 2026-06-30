package com.hundewildnis.app

import com.google.gson.annotations.SerializedName

data class BackupData(
    @SerializedName("version")       val version: String,
    @SerializedName("backupVersion") val backupVersion: Int = 6,   // ← Version 6
    @SerializedName("timestamp")     val timestamp: Long,
    @SerializedName("dogs")          val dogs: List<DogProfile>,
    @SerializedName("settings")      val settings: Map<String, String>,
    @SerializedName("notes")         val notes: List<NoteSession>?,
    @SerializedName("speedSessions") val speedSessions: List<SpeedSession>?,
    @SerializedName("voiceSessions") val voiceSessions: List<VoiceSession>?,
    @SerializedName("emergencyReports") val emergencyReports: List<EmergencyReport>?,
    @SerializedName("photoMapping")  val photoMapping: Map<String, String>,
    @SerializedName("trainingSessions")  val trainingSessions: List<TrainingSession>?,
    @SerializedName("trainingProgress")  val trainingProgress: List<TrainingProgress>?,
    @SerializedName("trainingMilestones") val trainingMilestones: List<Milestone>?,
    // ✅ NEU: Favoriten
    @SerializedName("favorites")     val favorites: List<FavoritePOI>? = null,
    // ✅ NEU: Nasenarbeit
    @SerializedName("nasenarbeitSessions")   val nasenarbeitSessions: List<NasenarbeitSession>? = null,
    @SerializedName("nasenarbeitFortschritt") val nasenarbeitFortschritt: List<NasenarbeitFortschritt>? = null,
    // ✅ NEU: Wetter-Favoriten
    @SerializedName("weatherFavorites") val weatherFavorites: List<WeatherFavorite>? = null,
    // ✅ NEU: Vitalwerte
    @SerializedName("vitalRecords") val vitalRecords: List<VitalRecord>? = null,
    // ✅ NEU: Intervall-Timer
    @SerializedName("intervalTimerSessions") val intervalTimerSessions: List<IntervalTimerSession>? = null,
    // ✅ NEU: Gefahrenpflanzen-Fotos (plantId → Liste absoluter Pfade)
    @SerializedName("plantPhotoMap") val plantPhotoMap: Map<String, List<String>>? = null


)

data class BackupMetadata(
    @SerializedName("appVersion")    val appVersion: String,
    @SerializedName("backupVersion") val backupVersion: Int,
    @SerializedName("timestamp")     val timestamp: Long,
    @SerializedName("deviceName")    val deviceName: String,
    @SerializedName("itemCount")     val itemCount: BackupItemCount
)

data class BackupItemCount(
    @SerializedName("dogs")               val dogs: Int,
    @SerializedName("photos")             val photos: Int,
    @SerializedName("notes")             val notes: Int,
    @SerializedName("speedSessions")     val speedSessions: Int = 0,
    @SerializedName("voiceSessions")     val voiceSessions: Int = 0,
    @SerializedName("emergencyReports")  val emergencyReports: Int = 0,
    @SerializedName("trainingSessions")  val trainingSessions: Int = 0,
    @SerializedName("trainingProgress")  val trainingProgress: Int = 0,
    @SerializedName("trainingMilestones") val trainingMilestones: Int = 0,
    // ✅ NEU
    @SerializedName("favorites")         val favorites: Int = 0,
    // ✅ NEU: Nasenarbeit
    @SerializedName("nasenarbeitSessions")    val nasenarbeitSessions: Int = 0,
    @SerializedName("nasenarbeitFortschritt") val nasenarbeitFortschritt: Int = 0,
    // ✅ NEU: Wetter-Favoriten
    @SerializedName("weatherFavorites")       val weatherFavorites: Int = 0,
    // ✅ NEU: Vitalwerte
    @SerializedName("vitalRecords")           val vitalRecords: Int = 0,
    // ✅ NEU: Intervall-Timer
    @SerializedName("intervalTimerSessions") val intervalTimerSessions: Int = 0,
    // ✅ NEU: Gefahrenpflanzen-Fotos (plantId → Liste absoluter Pfade)
    @SerializedName("plantPhotos") val plantPhotos: Int = 0


)