package com.hundewildnis.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Base Activity mit Screenshot-/Screen-Recording-Schutz
 * Alle Activities sollten von dieser Klasse erben
 */
abstract class SecureBaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE: Blockiert Screenshots & Screen Recording
        /*window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )*/

    }
}