package com.hundewildnis.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BootReceiver – startet LocationShareService nach Geräteneustart automatisch,
 * wenn die Standortfreigabe beim letzten Lauf aktiv war.
 *
 * Aktivierungszustand wird in SharedPreferences persistiert, da
 * LocationShareService.isServiceRunning eine In-Memory-Variable ist
 * und nach Reboot immer false ist.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "location_share_prefs"
        const val KEY_SERVICE_WAS_ACTIVE = "service_was_active"

        /** Speichert den Aktivierungszustand persistent. Wird vom Service aufgerufen. */
        fun setServiceActive(context: Context, active: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_WAS_ACTIVE, active)
                .apply()
        }

        fun wasServiceActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_WAS_ACTIVE, false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        if (!wasServiceActive(context)) {
            Log.d(TAG, "Standortfreigabe war inaktiv – kein Auto-Start")
            return
        }

        Log.d(TAG, "Gerät neu gestartet – starte LocationShareService")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationShareService::class.java)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Auto-Start fehlgeschlagen: ${e.message}")
        }
    }
}
