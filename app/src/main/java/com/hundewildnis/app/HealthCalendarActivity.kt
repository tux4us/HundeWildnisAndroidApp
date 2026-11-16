package com.hundewildnis.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Vollständige Liste des Gesundheitskalenders eines Hundes:
 * Beobachtungen (bearbeitbar) und Notfallmeldungen (nur Anzeige, Tipp öffnet das PDF).
 */
class HealthCalendarActivity : SecureBaseActivity() {

    private lateinit var dogId: String
    private lateinit var llList: LinearLayout
    private lateinit var tvSummary: TextView

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_calendar)

        dogId = intent.getStringExtra(EXTRA_DOG_ID) ?: return finish()
        val name = intent.getStringExtra(EXTRA_DOG_NAME).orEmpty()

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = getString(R.string.health_calendar_title_format, name)
            setDisplayHomeAsUpEnabled(true)
        }

        llList = findViewById(R.id.llHealthCalList)
        tvSummary = findViewById(R.id.tvHealthCalSummary)
        findViewById<Button>(R.id.btnHealthCalAdd).setOnClickListener {
            HealthObservationActivity.startForNew(this, dogId, editLauncher)
        }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val profile = DogProfileManager.getProfile(this, dogId) ?: return finish()
        tvSummary.text = HealthObservationManager.summaryLabel(this, profile)

        llList.removeAllViews()
        val items = HealthObservationManager.calendarItems(this, profile)
        if (items.isEmpty()) {
            llList.addView(HealthCalendarUi.infoText(this, getString(R.string.health_empty_hint)))
            return
        }
        items.forEach { item ->
            llList.addView(
                HealthCalendarUi.buildRow(
                    context = this,
                    item = item,
                    onObservationClick = { HealthObservationActivity.startForEdit(this, dogId, it, editLauncher) },
                    onEmergencyClick = { HealthCalendarUi.openEmergencyPdf(this, it) }
                )
            )
        }
    }

    companion object {
        const val EXTRA_DOG_ID = "dog_id"
        const val EXTRA_DOG_NAME = "dog_name"
    }
}
