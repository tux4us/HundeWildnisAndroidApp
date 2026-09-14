package com.hundewildnis.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Zentrale Verwaltung der App-Sprache.
 *
 * Nutzt AppCompatDelegate.setApplicationLocales() (AndroidX Per-App Language
 * Preferences). Funktioniert rückwärtskompatibel ab API 24 und speichert die
 * gewählte Sprache automatisch persistent (autoStoreLocales, siehe Manifest).
 *
 * Erweiterung um weitere Sprachen (z. B. Slowakisch): einfach einen weiteren
 * Eintrag in [SupportedLanguage] ergänzen – keine Änderung an der
 * Umschalt-Logik selbst nötig.
 */
object LanguageManager {

    enum class SupportedLanguage(val tag: String, val displayName: String) {
        ENGLISH("en", "English"),
        GERMAN("de", "Deutsch")
    }

    /** Setzt die App-Sprache. */
    fun setLanguage(language: SupportedLanguage) {
        val localeList = LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /** Liefert die aktuell aktive Sprache. */
    fun getCurrentLanguage(): SupportedLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        
        // 1. App-spezifische Einstellung prüfen
        if (!appLocales.isEmpty) {
            val appTag = appLocales[0]?.language?.lowercase()
            // Robustere Suche: auch Präfixe matchen (z.B. "en" für "en-US")
            val found = SupportedLanguage.values().find { appTag == it.tag || appTag?.startsWith("${it.tag}_") == true }
            if (found != null) return found
        }

        // 2. Fallback auf System-Sprache (ISO 639-1 Code)
        val systemTag = Locale.getDefault().language.lowercase()
        return SupportedLanguage.values().find { systemTag == it.tag || systemTag.startsWith("${it.tag}_") }
            ?: SupportedLanguage.GERMAN
    }
}
