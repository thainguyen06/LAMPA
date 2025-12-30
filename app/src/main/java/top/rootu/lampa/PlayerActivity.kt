package top.rootu.lampa

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.util.Util

/**
 * PlayerActivity - Full-screen video player using ExoPlayer
 * 
 * This activity provides native video playback for HTTP/HTTPS streams,
 * particularly for handling .mkv files that cannot be played in WebView.
 * 
 * Features:
 * - Subtitle and Audio track selection
 * - Custom HTML-like UI with track selection dialog
 * 
 * Usage:
 * - Pass video URL via Intent extra: EXTRA_VIDEO_URL
 * - Optionally pass video title: EXTRA_VIDEO_TITLE
 */
class PlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var playerView: StyledPlayerView? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playWhenReady = true
    private var currentPosition = 0L
    private var currentMediaItemIndex = 0

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_TITLE = "video_title"
        
        // Radio button ID offsets for track selection
        private const val AUDIO_TRACK_ID_OFFSET = 1000
        private const val SUBTITLE_TRACK_ID_OFFSET = 2000
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
        
        // Set up track selection button click listener
        setupTrackSelectionButton()
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

    private fun setupTrackSelectionButton() {
        playerView?.findViewById<ImageButton>(R.id.exo_track_selection)?.setOnClickListener {
            showTrackSelectionDialog()
        }
        
        // Set video title if available
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)
        if (!videoTitle.isNullOrEmpty()) {
            playerView?.findViewById<TextView>(R.id.exo_title)?.text = videoTitle
        }
    }

    private fun initializePlayer() {
        if (player != null) return

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: return

        // Create track selector for managing audio and subtitle tracks
        trackSelector = DefaultTrackSelector(this).apply {
            // Allow automatic quality selection based on network conditions
            setParameters(buildUponParameters())
        }

        // Create ExoPlayer instance with track selector
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector!!)
            .build().also { exoPlayer ->
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
                
                override fun onTracksChanged(tracks: Tracks) {
                    Log.d(TAG, "Tracks changed - Audio: ${tracks.containsType(com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO)}, Text: ${tracks.containsType(com.google.android.exoplayer2.C.TRACK_TYPE_TEXT)}")
                }
            })

            // Prepare the player
            exoPlayer.prepare()
        }
        
        // Re-setup track selection button after player is initialized
        setupTrackSelectionButton()
    }

    private fun showTrackSelectionDialog() {
        val exoPlayer = player ?: return
        val selector = trackSelector ?: return
        
        val dialog = Dialog(this, R.style.Theme_AppCompat_Dialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val audioGroup = dialog.findViewById<RadioGroup>(R.id.audio_tracks_group)
        val subtitleGroup = dialog.findViewById<RadioGroup>(R.id.subtitle_tracks_group)
        val closeButton = dialog.findViewById<Button>(R.id.btn_close_tracks)
        
        // Get current tracks
        val currentTracks = exoPlayer.currentTracks
        val parameters = selector.parameters
        
        // Populate audio tracks
        populateAudioTracks(audioGroup, currentTracks, parameters)
        
        // Populate subtitle tracks
        populateSubtitleTracks(subtitleGroup, currentTracks, parameters)
        
        // Handle audio track selection
        audioGroup.setOnCheckedChangeListener { _, checkedId ->
            val trackIndex = checkedId - AUDIO_TRACK_ID_OFFSET
            selectAudioTrack(trackIndex)
        }
        
        // Handle subtitle track selection
        subtitleGroup.setOnCheckedChangeListener { _, checkedId ->
            val trackIndex = checkedId - SUBTITLE_TRACK_ID_OFFSET
            selectSubtitleTrack(trackIndex)
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun populateAudioTracks(audioGroup: RadioGroup, tracks: Tracks, parameters: DefaultTrackSelector.Parameters) {
        audioGroup.removeAllViews()
        
        var audioTrackIndex = 0
        var hasAudioTracks = false
        
        for (trackGroupInfo in tracks.groups) {
            if (trackGroupInfo.type == com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO) {
                hasAudioTracks = true
                val trackGroup = trackGroupInfo.mediaTrackGroup
                
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(i)
                    val trackName = format.language ?: getString(R.string.track_unknown)
                    
                    val radioButton = RadioButton(this).apply {
                        id = AUDIO_TRACK_ID_OFFSET + audioTrackIndex
                        text = trackName
                        textSize = 16f
                        setTextColor(0xFFFFFFFF.toInt())
                        isChecked = trackGroupInfo.isTrackSelected(i)
                    }
                    audioGroup.addView(radioButton)
                    audioTrackIndex++
                }
            }
        }
        
        if (!hasAudioTracks) {
            val noTracksText = TextView(this).apply {
                text = getString(R.string.track_disabled)
                textSize = 14f
                setTextColor(0xFFCCCCCC.toInt())
            }
            audioGroup.addView(noTracksText)
        }
    }
    
    private fun populateSubtitleTracks(subtitleGroup: RadioGroup, tracks: Tracks, parameters: DefaultTrackSelector.Parameters) {
        subtitleGroup.removeAllViews()
        
        // Add "Disabled" option for subtitles
        val disabledButton = RadioButton(this).apply {
            id = SUBTITLE_TRACK_ID_OFFSET
            text = getString(R.string.track_disabled)
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        subtitleGroup.addView(disabledButton)
        
        var subtitleTrackIndex = 1
        var hasSubtitleTracks = false
        var anySubtitleSelected = false
        
        for (trackGroupInfo in tracks.groups) {
            if (trackGroupInfo.type == com.google.android.exoplayer2.C.TRACK_TYPE_TEXT) {
                hasSubtitleTracks = true
                val trackGroup = trackGroupInfo.mediaTrackGroup
                
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(i)
                    val trackName = format.language ?: getString(R.string.track_unknown)
                    
                    val isSelected = trackGroupInfo.isTrackSelected(i)
                    if (isSelected) anySubtitleSelected = true
                    
                    val radioButton = RadioButton(this).apply {
                        id = SUBTITLE_TRACK_ID_OFFSET + subtitleTrackIndex
                        text = trackName
                        textSize = 16f
                        setTextColor(0xFFFFFFFF.toInt())
                        isChecked = isSelected
                    }
                    subtitleGroup.addView(radioButton)
                    subtitleTrackIndex++
                }
            }
        }
        
        // If no subtitle is selected and we have tracks, select disabled
        if (hasSubtitleTracks && !anySubtitleSelected) {
            disabledButton.isChecked = true
        }
        
        if (!hasSubtitleTracks) {
            disabledButton.isChecked = true
        }
    }
    
    private fun selectAudioTrack(trackIndex: Int) {
        val selector = trackSelector ?: return
        val exoPlayer = player ?: return
        
        Log.d(TAG, "Selecting audio track: $trackIndex")
        
        // Build parameters to override audio track selection
        var currentTrackIndex = 0
        for (trackGroupInfo in exoPlayer.currentTracks.groups) {
            if (trackGroupInfo.type == com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO) {
                val trackGroup = trackGroupInfo.mediaTrackGroup
                
                if (trackIndex >= currentTrackIndex && trackIndex < currentTrackIndex + trackGroup.length) {
                    val indexInGroup = trackIndex - currentTrackIndex
                    
                    selector.setParameters(
                        selector.buildUponParameters()
                            .clearOverridesOfType(com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO)
                            .addOverride(
                                com.google.android.exoplayer2.trackselection.TrackSelectionOverride(
                                    trackGroup, listOf(indexInGroup)
                                )
                            )
                    )
                    return
                }
                
                currentTrackIndex += trackGroup.length
            }
        }
    }
    
    private fun selectSubtitleTrack(trackIndex: Int) {
        val selector = trackSelector ?: return
        val exoPlayer = player ?: return
        
        Log.d(TAG, "Selecting subtitle track: $trackIndex")
        
        if (trackIndex == 0) {
            // Disable subtitles
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(com.google.android.exoplayer2.C.TRACK_TYPE_TEXT)
            )
            return
        }
        
        // Enable specific subtitle track
        var currentTrackIndex = 1 // Start at 1 because 0 is "disabled"
        for (trackGroupInfo in exoPlayer.currentTracks.groups) {
            if (trackGroupInfo.type == com.google.android.exoplayer2.C.TRACK_TYPE_TEXT) {
                val trackGroup = trackGroupInfo.mediaTrackGroup
                
                if (trackIndex >= currentTrackIndex && trackIndex < currentTrackIndex + trackGroup.length) {
                    val indexInGroup = trackIndex - currentTrackIndex
                    
                    selector.setParameters(
                        selector.buildUponParameters()
                            .clearOverridesOfType(com.google.android.exoplayer2.C.TRACK_TYPE_TEXT)
                            .addOverride(
                                com.google.android.exoplayer2.trackselection.TrackSelectionOverride(
                                    trackGroup, listOf(indexInGroup)
                                )
                            )
                    )
                    return
                }
                
                currentTrackIndex += trackGroup.length
            }
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
        trackSelector = null
    }
}
