package com.hundewildnis.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.hundewildnis.app.databinding.ActivityYoutubePlayerBinding
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener

class YouTubePlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYoutubePlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityYoutubePlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoId = intent.getStringExtra("VIDEO_ID") ?: ""
        val title = intent.getStringExtra("TITLE") ?: "Video"

        if (videoId.isEmpty()) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            this.title = title
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        lifecycle.addObserver(binding.youtubePlayerView)

        binding.youtubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.loadVideo(videoId, 0f)
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}