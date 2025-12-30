package top.rootu.lampa

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.Util

/**
 * PlayerActivity - Full-screen video player using ExoPlayer
 * 
 * This activity provides native video playback for HTTP/HTTPS streams,
 * particularly for handling .mkv files that cannot be played in WebView.
 * 
 * Usage:
 * - Pass video URL via Intent extra: EXTRA_VIDEO_URL
 * - Optionally pass video title: EXTRA_VIDEO_TITLE
 */
class PlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playWhenReady = true
    private var currentPosition = 0L
    private var currentMediaItemIndex = 0

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_TITLE = "video_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Setup full-screen immersive mode
        setupFullscreen()

        // Initialize PlayerView
        playerView = findViewById(R.id.player_view)

        // Get video URL from intent
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)

        if (videoUrl.isNullOrEmpty()) {
            Log.e(TAG, "No video URL provided")
            App.toast(R.string.invalid_url, true)
            finish()
            return
        }

        Log.d(TAG, "Starting playback for: $videoUrl")
        if (!videoTitle.isNullOrEmpty()) {
            Log.d(TAG, "Video title: $videoTitle")
        }
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            releasePlayer()
        }
    }

    @SuppressLint("InlinedApi")
    private fun setupFullscreen() {
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = 
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun initializePlayer() {
        if (player != null) return

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return

        // Create ExoPlayer instance
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView?.player = exoPlayer

            // Create media item from URL
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)

            // Restore playback state
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.seekTo(currentMediaItemIndex, currentPosition)

            // Add listener for playback events
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "Playback ended")
                            finish()
                        }
                        Player.STATE_READY -> {
                            Log.d(TAG, "Player ready")
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "Buffering...")
                        }
                        Player.STATE_IDLE -> {
                            Log.d(TAG, "Player idle")
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Playback error: ${error.message}", error)
                    App.toast(R.string.playback_error, true)
                    finish()
                }
            })

            // Prepare the player
            exoPlayer.prepare()
        }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playWhenReady = exoPlayer.playWhenReady
            currentPosition = exoPlayer.currentPosition
            currentMediaItemIndex = exoPlayer.currentMediaItemIndex
            exoPlayer.release()
        }
        player = null
    }
}
