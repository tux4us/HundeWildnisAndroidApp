package com.hundewildnis.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.hundewildnis.app.databinding.ActivityPulscheckDogSelectBinding

class PulscheckDogSelectActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityPulscheckDogSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPulscheckDogSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        loadDogButtons()
    }

    private fun loadDogButtons() {
        val profiles = DogProfileManager.getAllProfiles(this)

        if (profiles.isEmpty()) {
            binding.tvNoDogs.visibility = View.VISIBLE
            binding.llDogButtons.visibility = View.GONE
            return
        }

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        profiles.forEach { profile ->
            val btn = MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(10) }

                text = "🐕 ${profile.name}"
                if (profile.breed.isNotEmpty()) {
                    text = "🐕 ${profile.name}  ·  ${profile.breed}"
                }
                textSize = 16f
                setTextColor(Color.WHITE)
                isAllCaps = false
                minHeight = dpToPx(64)
                cornerRadius = dpToPx(12)
                setBackgroundColor(Color.parseColor("#2d5016"))

                setOnClickListener {
                    val intent = Intent(this@PulscheckDogSelectActivity, PulscheckActivity::class.java)
                    intent.putExtra("profile", profile)
                    startActivity(intent)
                }
            }
            binding.llDogButtons.addView(btn)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        // Buttons neu aufbauen falls ein Hund hinzugekommen ist
        binding.llDogButtons.removeAllViews()
        loadDogButtons()
    }
}
