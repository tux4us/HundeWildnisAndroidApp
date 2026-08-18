package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
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
            binding.gridDogSelection.visibility = View.GONE
            return
        }

        binding.tvNoDogs.visibility = View.GONE
        binding.gridDogSelection.visibility = View.VISIBLE

        val tiles = profiles.map { profile ->
            StartTile(
                id = profile.id,
                iconRes = R.drawable.ic_dog,
                label = if (profile.breed.isNotEmpty()) "${profile.name}\n${profile.breed}" else profile.name
            )
        }

        val colors = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridDogSelection.layoutManager = GridLayoutManager(this, 2)
        binding.gridDogSelection.adapter = StartTileAdapter(
            tiles = tiles,
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = { dogId ->
                val profile = profiles.find { it.id == dogId }
                profile?.let {
                    val intent = Intent(this, PulscheckActivity::class.java)
                    intent.putExtra("profile", it)
                    startActivity(intent)
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        loadDogButtons()
    }
}
