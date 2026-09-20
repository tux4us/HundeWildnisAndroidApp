package com.hundewildnis.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DogProfileManager {
    private const val PREFS_NAME = "dog_profiles"
    private const val KEY_PROFILES = "profiles"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAllProfiles(context: Context): List<DogProfile> {
        val json = getPrefs(context).getString(KEY_PROFILES, "[]")
        val type = object : TypeToken<List<DogProfile>>() {}.type
        val profiles: List<DogProfile> = Gson().fromJson(json, type) ?: emptyList()
        // Migration: ältere Profile speichern gender/neutered ggf. noch als
        // deutschen Anzeigetext statt als stabilen Code (siehe GenderCode/NeuteredCode).
        return profiles.map { p ->
            p.copy(
                gender = GenderCode.migrate(p.gender),
                neutered = NeuteredCode.migrate(p.neutered)
            )
        }
    }

    fun saveProfile(context: Context, profile: DogProfile): Boolean {
        val profiles = getAllProfiles(context).toMutableList()

        val existingIndex = profiles.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }

        val json = Gson().toJson(profiles)
        getPrefs(context).edit().putString(KEY_PROFILES, json).apply()
        return true
    }

    fun deleteProfile(context: Context, profileId: String) {
        val profiles = getAllProfiles(context).toMutableList()
        profiles.removeAll { it.id == profileId }
        val json = Gson().toJson(profiles)
        getPrefs(context).edit().putString(KEY_PROFILES, json).apply()
    }

    fun getProfile(context: Context, profileId: String): DogProfile? {
        return getAllProfiles(context).find { it.id == profileId }
    }

    fun canAddMore(context: Context): Boolean {
        return true
    }

    fun getRemainingSlots(context: Context): Int {
        return 999
    }
}
