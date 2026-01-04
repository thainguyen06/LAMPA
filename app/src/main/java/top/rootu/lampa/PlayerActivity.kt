package top.rootu.lampa

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import top.rootu.lampa.helpers.Prefs.aspectRatio
import top.rootu.lampa.helpers.SubtitleDebugHelper
import top.rootu.lampa.helpers.SubtitleDownloader
import top.rootu.lampa.helpers.SubtitlePreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PlayerActivity - Full-screen video player using LibVLC
 * 
 * This activity provides native video playback with software decoding support
 * for advanced formats (EAC3, HEVC, etc.) that ExoPlayer cannot handle.
 * 
 * Features:
 * - LibVLC-based playback with software decoding
 * - Subtitle and Audio track selection
 * - External subtitle URL support
 * - Custom subtitle styling (font size, color, background)
 * - Back button for easy exit
 * 
 * Usage:
 * - Pass video URL via Intent extra: EXTRA_VIDEO_URL
 * - Optionally pass video title: EXTRA_VIDEO_TITLE
 * - Optionally pass subtitle URL: EXTRA_SUBTITLE_URL
 */
class PlayerActivity : BaseActivity() {

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    
    // Lifecycle observer to ensure proper cleanup
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            super.onPause(owner)
            Log.d(TAG, "Lifecycle: onPause - pausing media player")
            mediaPlayer?.pause()
        }
        
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            Log.d(TAG, "Lifecycle: onStop - detaching views")
            // Detach views when activity is stopped to prevent memory leaks
            mediaPlayer?.detachViews()
        }
        
        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            Log.d(TAG, "Lifecycle: onDestroy - releasing player")
            releasePlayer()
        }
    }
    
    // UI components
    private var btnBack: ImageButton? = null
    private var btnPlayPause: ImageButton? = null
    private var btnTrackSelection: ImageButton? = null
    private var btnAspectRatio: ImageButton? = null
    private var btnSubtitleSettings: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var tvCurrentTime: TextView? = null
    private var tvDurationEnds: TextView? = null
    private var tvSystemTime: TextView? = null
    private var loadingSpinner: View? = null
    private var videoTitle: TextView? = null
    private var topControls: View? = null
    private var bottomControls: View? = null
    
    // Subtitle settings
    private var subtitleFontSize = 16 // Medium
    private var subtitleColor = 0xFFFFFF // White
    private var subtitleBackground = 0x00000000 // Transparent
    
    // External subtitle support
    private var subtitleDownloader: SubtitleDownloader? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // Store subtitle URL passed via intent for later loading
    private var pendingSubtitleUrl: String? = null
    
    // Debounce state for searchAndLoadExternalSubtitles
    private var lastSubtitleSearchTimestamp: Long = 0
    private val subtitleSearchDebounceMs = 2000L // 2 seconds debounce window
    
    // Retry state for network stream errors
    private var videoUrl: String? = null
    private var retryCount = 0
    private val maxRetries = MAX_RETRY_ATTEMPTS
    private val retryDelayMs = INITIAL_RETRY_DELAY_MS
    
    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable {
        hideControls()
    }
    private val systemTimeRunnable = object : Runnable {
        override fun run() {
            updateSystemTime()
            handler.postDelayed(this, SYSTEM_TIME_UPDATE_INTERVAL)
        }
    }

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_SUBTITLE_URL = "subtitle_url"
        
        private const val SEEK_TIME_MS = 10000L // 10 seconds
        private const val CONTROLS_HIDE_DELAY = 3000L // 3 seconds
        private const val TRACK_LOADING_DELAY_MS = 2000L // 2 seconds - Wait for tracks to load
        private const val SUBTITLE_TRACK_REGISTRATION_DELAY_MS = 1500L // 1.5 seconds - Wait for subtitle track to register after addSlave (increased from 0.5s)
        private const val SUBTITLE_TRACK_RETRY_DELAY_MS = 1000L // 1 second - Delay between subtitle track selection retries
        private const val SUBTITLE_TRACK_MAX_RETRIES = 3 // Maximum retries for subtitle track selection
        private const val SYSTEM_TIME_UPDATE_INTERVAL = 60000L // 1 minute
        private const val MAX_RETRY_ATTEMPTS = 3 // Maximum number of retry attempts for network errors
        private const val INITIAL_RETRY_DELAY_MS = 2000L // Initial retry delay (2 seconds)
        private const val ERROR_MESSAGE_DISPLAY_TIME_MS = 1000L // Time to display error before closing (1 second)
        
        // LibVLC 3.6.0 Media event type constants
        // Note: Media class uses integer constants, not a nested Event class like MediaPlayer
        // Reference: org.videolan.libvlc.Media event types
        private const val MEDIA_EVENT_PARSED_CHANGED = 3 // ParsedChanged: fired when media parsing is complete
        
        /**
         * Get radio button ID for the given aspect ratio string
         */
        private fun getAspectRatioRadioId(aspectRatio: String?): Int {
            return when (aspectRatio) {
                null -> R.id.aspect_ratio_fit // Best fit - maintains aspect ratio
                "" -> R.id.aspect_ratio_fill // Fill screen - forces to fill
                "16:9" -> R.id.aspect_ratio_16_9
                "4:3" -> R.id.aspect_ratio_4_3
                "21:9" -> R.id.aspect_ratio_21_9
                else -> R.id.aspect_ratio_fit // Default to fit if unknown
            }
        }
        
        /**
         * Get display name for the given aspect ratio string
         */
        private fun getAspectRatioDisplayName(aspectRatio: String?): String {
            return when (aspectRatio) {
                null -> "Best Fit"
                "" -> "Fill Screen"
                else -> aspectRatio
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        // Register lifecycle observer for proper cleanup
        lifecycle.addObserver(lifecycleObserver)

        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Setup full-screen immersive mode
        setupFullscreen()

        // Initialize UI components
        initializeUI()

        // Get video URL from intent
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val videoTitleText = intent.getStringExtra(EXTRA_VIDEO_TITLE)
        val subtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE_URL)

        if (videoUrl.isNullOrEmpty()) {
            Log.e(TAG, "No video URL provided")
            App.toast(R.string.invalid_url, true)
            finish()
            return
        }
        
        // Store video URL for retry attempts
        this.videoUrl = videoUrl

        Log.d(TAG, "Starting playback for: $videoUrl")
        if (!videoTitleText.isNullOrEmpty()) {
            Log.d(TAG, "Video title: $videoTitleText")
            videoTitle?.text = videoTitleText
        }
        if (!subtitleUrl.isNullOrEmpty()) {
            Log.d(TAG, "External subtitle URL: $subtitleUrl")
            // Store subtitle URL to load after player starts
            pendingSubtitleUrl = subtitleUrl
        }

        // Initialize LibVLC and start playback
        initializePlayer(videoUrl, null) // Pass null for subtitle, we'll load it after playback starts
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

    private fun initializeUI() {
        videoLayout = findViewById(R.id.vlc_video_layout)
        btnBack = findViewById(R.id.btn_back)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnTrackSelection = findViewById(R.id.btn_track_selection)
        btnAspectRatio = findViewById(R.id.btn_aspect_ratio)
        btnSubtitleSettings = findViewById(R.id.btn_subtitle_settings)
        seekBar = findViewById(R.id.player_seekbar)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvDurationEnds = findViewById(R.id.tv_duration_ends)
        tvSystemTime = findViewById(R.id.tv_system_time)
        loadingSpinner = findViewById(R.id.loading_spinner)
        videoTitle = findViewById(R.id.video_title)
        topControls = findViewById(R.id.top_controls)
        bottomControls = findViewById(R.id.bottom_controls)
        
        // Initialize subtitle downloader
        subtitleDownloader = SubtitleDownloader(this)
        
        // Start system time updater
        startSystemTimeUpdater()

        // Set up button click listeners
        btnBack?.setOnClickListener {
            finish()
        }

        btnPlayPause?.setOnClickListener {
            togglePlayPause()
        }

        btnTrackSelection?.setOnClickListener {
            showTrackSelectionDialog()
        }

        btnAspectRatio?.setOnClickListener {
            showAspectRatioDialog()
        }

        btnSubtitleSettings?.setOnClickListener {
            showSubtitleSettingsDialog()
        }
        
        // Long press on subtitle settings button to export subtitle debug logs
        btnSubtitleSettings?.setOnLongClickListener {
            showSubtitleDebugMenu()
            true
        }

        // Seek bar listener
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.time = progress.toLong()
                    updateEndsAtTime()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                scheduleHideControls()
            }
        })

        // Toggle controls on video layout click
        videoLayout?.setOnClickListener {
            toggleControls()
        }
    }

    private fun initializePlayer(videoUrl: String, subtitleUrl: String?) {
        try {
            // Show loading spinner
            loadingSpinner?.visibility = View.VISIBLE
            
            // Initialize LibVLC
            val options = ArrayList<String>().apply {
                add("--aout=opensles")
                add("--audio-time-stretch") // Better audio sync
                add("-vvv") // Verbose logging for debugging
                
                // Network stream optimization for high latency/unstable connections
                // Increased caching values to handle extreme latency (up to 14+ seconds observed in logs)
                // Higher values = more buffering but better stability and fewer dropped frames
                add("--network-caching=10000") // 10 seconds cache for network streams (increased from 5s)
                add("--live-caching=10000") // 10 seconds cache for live streams (increased from 5s)
                
                // Clock synchronization options to handle audio/video desync
                // These settings help when audio/video streams are severely out of sync
                add("--clock-jitter=5000") // Allow up to 5 second jitter before correction
                add("--clock-synchro=0") // 0 = default (auto sync based on timestamps), helps with streams that have timing issues
                
                // Audio synchronization
                add("--audio-desync=0") // No additional audio delay - let VLC handle sync automatically
                
                // H.264 decoder options to handle non-IDR frames better
                // These help when stream has missing keyframes (IDR frames)
                add("--avcodec-skiploopfilter=0") // 0 = none (don't skip), ensures proper frame decoding
                add("--avcodec-skip-frame=0") // 0 = none, decode all frames including non-IDR
                add("--avcodec-skip-idct=0") // 0 = none, don't skip IDCT for better quality
                
                // Additional options to handle streams with non-IDR frames (Telesync/TS files)
                // These help when the stream starts with non-IDR frames and VLC can't find initial keyframe
                add("--avcodec-hurry-up") // Allow decoder to skip non-reference frames for faster seeking to keyframes
                add("--avcodec-fast") // Enable fast decoding (less quality checks, faster keyframe detection)
                add("--no-avcodec-dr") // Disable direct rendering to avoid artifacts with corrupted frames
                
                // Connection timeout settings to detect issues faster
                add("--http-reconnect") // Enable automatic HTTP reconnection
                
                // Reduce buffering to improve responsiveness for local files
                add("--file-caching=300") // 300ms for local files
            }
            
            libVLC = LibVLC(this, options)
            
            // Create media player
            mediaPlayer = MediaPlayer(libVLC).apply {
                // Attach video layout
                videoLayout?.let { layout ->
                    attachViews(layout, null, false, false)
                }

                // Set event listener
                setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            Log.d(TAG, "Playback started")
                            runOnUiThread {
                                loadingSpinner?.visibility = View.GONE
                                updatePlayPauseButton()
                                startProgressUpdate()
                                // Auto-select preferred audio/subtitle tracks
                                autoSelectPreferredTracks()
                                // Restore saved aspect ratio after player is ready
                                aspectRatio?.let { savedRatio ->
                                    setAspectRatio(savedRatio)
                                    Log.d(TAG, "Restored aspect ratio: $savedRatio")
                                }
                                
                                // Load pending subtitle URL if provided
                                pendingSubtitleUrl?.let { subtitleUrl ->
                                    loadSubtitleFromUrl(subtitleUrl)
                                    pendingSubtitleUrl = null // Clear after loading
                                }
                            }
                        }
                        MediaPlayer.Event.Paused -> {
                            Log.d(TAG, "Playback paused")
                            runOnUiThread {
                                updatePlayPauseButton()
                            }
                        }
                        MediaPlayer.Event.EndReached -> {
                            Log.d(TAG, "Playback ended")
                            runOnUiThread {
                                finish()
                            }
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            Log.e(TAG, "Playback error encountered")
                            runOnUiThread {
                                handlePlaybackError()
                            }
                        }
                        MediaPlayer.Event.LengthChanged -> {
                            runOnUiThread {
                                updateDuration()
                            }
                        }
                        MediaPlayer.Event.Buffering -> {
                            val buffering = event.buffering
                            runOnUiThread {
                                if (buffering < 100f) {
                                    loadingSpinner?.visibility = View.VISIBLE
                                } else {
                                    loadingSpinner?.visibility = View.GONE
                                }
                            }
                        }
                        MediaPlayer.Event.ESAdded -> {
                            // ES (Elementary Stream) added - new track available
                            // Note: This event fires for ANY track type (audio, video, subtitle)
                            // Check if this is a subtitle track by inspecting track counts
                            val subtitleCount = mediaPlayer?.spuTracks?.size ?: 0
                            
                            Log.d(TAG, "ESAdded event: New track added to player")
                            SubtitleDebugHelper.logInfo("PlayerActivity", "ESAdded event detected")
                            
                            // Log current track counts for debugging
                            val audioCount = mediaPlayer?.audioTracks?.size ?: 0
                            val videoCount = mediaPlayer?.videoTracksCount ?: 0
                            SubtitleDebugHelper.logDebug("PlayerActivity", "Current tracks - Audio: $audioCount, Video: $videoCount, Subtitle: $subtitleCount")
                            
                            runOnUiThread {
                                refreshTracks()
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Create and configure media
            val media = Media(libVLC, Uri.parse(videoUrl)).apply {
                // Add hardware decoding options (will fallback to software if needed)
                addOption(":codec=all")
                
                // Network caching - match LibVLC global settings
                // This ensures consistent behavior across the media pipeline
                addOption(":network-caching=10000") // Increased to 10 seconds to match LibVLC settings
                
                // HTTP specific options for better stream handling
                addOption(":http-reconnect") // Enable HTTP reconnection on errors
                addOption(":http-continuous") // Enable continuous HTTP streaming
                
                // Note: External subtitles are now loaded after playback starts using addSlave()
                // This ensures proper subtitle transfer to the player
                
                // Add Media.EventListener to handle parsed tracks
                setEventListener { mediaEvent ->
                    when (mediaEvent.type) {
                        MEDIA_EVENT_PARSED_CHANGED -> {
                            // Parsing is complete, tracks are now available
                            val isParsed = isParsed()
                            Log.d(TAG, "Media ParsedChanged, isParsed: $isParsed")
                            
                            if (isParsed) {
                                Log.d(TAG, "Media parsing complete, tracks available")
                                runOnUiThread {
                                    refreshTracks()
                                    
                                    // Search for external subtitles if no subtitle URL provided
                                    if (subtitleUrl.isNullOrEmpty()) {
                                        searchAndLoadExternalSubtitles(videoUrl)
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
                
                // Parse media to detect tracks
                parseAsync()
            }

            // Set media and start playback
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize player", e)
            App.toast(R.string.playback_error, true)
            finish()
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            updatePlayPauseButton()
        }
    }

    private fun updatePlayPauseButton() {
        mediaPlayer?.let { player ->
            btnPlayPause?.setImageResource(
                if (player.isPlaying) {
                    android.R.drawable.ic_media_pause
                } else {
                    android.R.drawable.ic_media_play
                }
            )
        }
    }

    private fun seekRelative(timeMs: Long) {
        mediaPlayer?.let { player ->
            val newTime = (player.time + timeMs).coerceIn(0, player.length)
            player.time = newTime
            updateProgress()
        }
    }

    private fun startSystemTimeUpdater() {
        handler.post(systemTimeRunnable)
    }

    private fun updateSystemTime() {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date())
        tvSystemTime?.text = currentTime
    }

    private fun updateEndsAtTime() {
        mediaPlayer?.let { player ->
            val currentPosition = player.time
            val totalDuration = player.length
            
            if (totalDuration > 0) {
                val remainingTime = totalDuration - currentPosition
                val currentSystemTimeMillis = System.currentTimeMillis()
                val endsAtMillis = currentSystemTimeMillis + remainingTime
                
                // Format duration as HH:mm:ss
                val durationText = formatTime(totalDuration)
                
                // Format ends at time as HH:mm
                val endsAtFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val endsAtText = endsAtFormat.format(Date(endsAtMillis))
                
                tvDurationEnds?.text = "$durationText | Ends at $endsAtText"
            }
        }
    }

    private fun startProgressUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                updateProgress()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun updateProgress() {
        mediaPlayer?.let { player ->
            val currentTime = player.time
            val totalTime = player.length
            
            seekBar?.max = totalTime.toInt()
            seekBar?.progress = currentTime.toInt()
            
            tvCurrentTime?.text = formatTime(currentTime)
            updateEndsAtTime()
        }
    }

    private fun updateDuration() {
        mediaPlayer?.let { player ->
            val totalTime = player.length
            seekBar?.max = totalTime.toInt()
            updateEndsAtTime()
        }
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun toggleControls() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        isControlsVisible = true
        topControls?.visibility = View.VISIBLE
        bottomControls?.visibility = View.VISIBLE
        btnPlayPause?.visibility = View.VISIBLE
        scheduleHideControls()
    }

    private fun hideControls() {
        isControlsVisible = false
        topControls?.visibility = View.GONE
        bottomControls?.visibility = View.GONE
        btnPlayPause?.visibility = View.GONE
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY)
    }

    private fun showTrackSelectionDialog() {
        val player = mediaPlayer ?: return
        
        val dialog = Dialog(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val audioGroup = dialog.findViewById<RadioGroup>(R.id.audio_tracks_group)
        val subtitleGroup = dialog.findViewById<RadioGroup>(R.id.subtitle_tracks_group)
        val closeButton = dialog.findViewById<Button>(R.id.btn_close_tracks)
        val btnViewList = dialog.findViewById<ImageButton>(R.id.btn_view_list)
        val btnViewGrid = dialog.findViewById<ImageButton>(R.id.btn_view_grid)
        
        // Track current view mode (default is list)
        var isGridMode = false
        
        // Function to update view mode buttons appearance
        fun updateViewModeButtons() {
            if (isGridMode) {
                btnViewList?.setColorFilter(0xFF888888.toInt())
                btnViewGrid?.setColorFilter(0xFFFFFFFF.toInt())
            } else {
                btnViewList?.setColorFilter(0xFFFFFFFF.toInt())
                btnViewGrid?.setColorFilter(0xFF888888.toInt())
            }
        }
        
        // Function to populate tracks based on current view mode
        fun refreshTracks() {
            populateAudioTracks(audioGroup, player, isGridMode)
            populateSubtitleTracks(subtitleGroup, player, isGridMode)
        }
        
        // Initial population in list mode
        refreshTracks()
        updateViewModeButtons()
        
        // View mode toggle handlers
        btnViewList?.setOnClickListener {
            if (isGridMode) {
                isGridMode = false
                updateViewModeButtons()
                refreshTracks()
            }
        }
        
        btnViewGrid?.setOnClickListener {
            if (!isGridMode) {
                isGridMode = true
                updateViewModeButtons()
                refreshTracks()
            }
        }
        
        // Handle audio track selection
        audioGroup.setOnCheckedChangeListener { _, checkedId ->
            val trackIndex = checkedId - 1000
            selectAudioTrack(trackIndex)
        }
        
        // Handle subtitle track selection
        subtitleGroup.setOnCheckedChangeListener { _, checkedId ->
            val trackIndex = checkedId - 2000
            selectSubtitleTrack(trackIndex)
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun populateAudioTracks(audioGroup: RadioGroup, player: MediaPlayer, isGridMode: Boolean) {
        audioGroup.removeAllViews()
        
        // Set orientation based on mode
        audioGroup.orientation = if (isGridMode) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
        
        // Add null check to prevent crash when tracks are not yet loaded
        val audioTracks = player.audioTracks ?: emptyArray()
        val currentTrack = player.audioTrack
        
        if (audioTracks.isEmpty()) {
            val noTracksText = TextView(this).apply {
                text = getString(R.string.track_disabled)
                textSize = 14f
                setTextColor(0xFFCCCCCC.toInt())
            }
            audioGroup.addView(noTracksText)
        } else {
            audioTracks.forEachIndexed { index, trackDescription ->
                val trackName = trackDescription.name ?: getString(R.string.track_unknown)
                
                val radioButton = RadioButton(this).apply {
                    id = 1000 + index
                    text = if (isGridMode) {
                        // In grid mode, show shortened track names
                        val parts = trackName.split(" - ", " ", limit = 2)
                        if (parts.isNotEmpty()) parts[0] else trackName
                    } else {
                        trackName
                    }
                    textSize = if (isGridMode) 14f else 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    isChecked = (trackDescription.id == currentTrack)
                    
                    // In grid mode, make buttons more compact
                    if (isGridMode) {
                        val params = RadioGroup.LayoutParams(
                            RadioGroup.LayoutParams.WRAP_CONTENT,
                            RadioGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(8, 4, 8, 4)
                        }
                        layoutParams = params
                    }
                }
                audioGroup.addView(radioButton)
            }
        }
    }

    private fun populateSubtitleTracks(subtitleGroup: RadioGroup, player: MediaPlayer, isGridMode: Boolean) {
        subtitleGroup.removeAllViews()
        
        // Set orientation based on mode
        subtitleGroup.orientation = if (isGridMode) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
        
        // Add "Disabled" option
        val disabledButton = RadioButton(this).apply {
            id = 2000
            text = getString(R.string.track_disabled)
            textSize = if (isGridMode) 14f else 16f
            setTextColor(0xFFFFFFFF.toInt())
            
            // In grid mode, make buttons more compact
            if (isGridMode) {
                val params = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.WRAP_CONTENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 4, 8, 4)
                }
                layoutParams = params
            }
        }
        subtitleGroup.addView(disabledButton)
        
        // Add null check to prevent crash when tracks are not yet loaded
        val spuTracks = player.spuTracks ?: emptyArray()
        val currentTrack = player.spuTrack
        
        if (currentTrack == -1) {
            disabledButton.isChecked = true
        }
        
        spuTracks.forEachIndexed { index, trackDescription ->
            val trackName = trackDescription.name ?: getString(R.string.track_unknown)
            
            val radioButton = RadioButton(this).apply {
                id = 2001 + index
                text = if (isGridMode) {
                    // In grid mode, show shortened track names
                    val parts = trackName.split(" - ", " ", limit = 2)
                    if (parts.isNotEmpty()) parts[0] else trackName
                } else {
                    trackName
                }
                textSize = if (isGridMode) 14f else 16f
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = (trackDescription.id == currentTrack)
                
                // In grid mode, make buttons more compact
                if (isGridMode) {
                    val params = RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.WRAP_CONTENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(8, 4, 8, 4)
                    }
                    layoutParams = params
                }
            }
            subtitleGroup.addView(radioButton)
        }
    }

    private fun selectAudioTrack(trackIndex: Int) {
        mediaPlayer?.let { player ->
            val audioTracks = player.audioTracks
            if (audioTracks != null && trackIndex in audioTracks.indices) {
                player.audioTrack = audioTracks[trackIndex].id
                Log.d(TAG, "Selected audio track: $trackIndex")
            }
        }
    }

    private fun selectSubtitleTrack(trackIndex: Int) {
        mediaPlayer?.let { player ->
            if (trackIndex == 0) {
                // Disable subtitles
                player.spuTrack = -1
                Log.d(TAG, "Subtitles disabled")
            } else {
                val spuTracks = player.spuTracks
                if (spuTracks != null) {
                    val actualIndex = trackIndex - 1
                    if (actualIndex in spuTracks.indices) {
                        player.spuTrack = spuTracks[actualIndex].id
                        Log.d(TAG, "Selected subtitle track: $actualIndex")
                        applySubtitleSettings()
                    } else {
                        Log.w(TAG, "Invalid subtitle track index: $actualIndex")
                    }
                } else {
                    Log.w(TAG, "Subtitle tracks not yet available")
                }
            }
        }
    }

    private fun showSubtitleSettingsDialog() {
        val dialog = Dialog(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        dialog.setContentView(R.layout.dialog_subtitle_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val fontSizeGroup = dialog.findViewById<RadioGroup>(R.id.font_size_group)
        val fontColorGroup = dialog.findViewById<RadioGroup>(R.id.font_color_group)
        val backgroundGroup = dialog.findViewById<RadioGroup>(R.id.background_group)
        val applyButton = dialog.findViewById<Button>(R.id.btn_apply_subtitle_settings)
        
        // Set current selections
        when (subtitleFontSize) {
            12 -> fontSizeGroup.check(R.id.font_size_small)
            16 -> fontSizeGroup.check(R.id.font_size_medium)
            20 -> fontSizeGroup.check(R.id.font_size_large)
        }
        
        when (subtitleColor) {
            0xFFFFFF -> fontColorGroup.check(R.id.font_color_white)
            0xFFFF00 -> fontColorGroup.check(R.id.font_color_yellow)
            0x00FFFF -> fontColorGroup.check(R.id.font_color_cyan)
        }
        
        when (subtitleBackground) {
            0x00000000 -> backgroundGroup.check(R.id.background_transparent)
            0xFF000000.toInt() -> backgroundGroup.check(R.id.background_black)
            0x80000000.toInt() -> backgroundGroup.check(R.id.background_semitransparent)
        }
        
        applyButton.setOnClickListener {
            // Get selected font size
            subtitleFontSize = when (fontSizeGroup.checkedRadioButtonId) {
                R.id.font_size_small -> 12
                R.id.font_size_large -> 20
                else -> 16 // Medium
            }
            
            // Get selected font color
            subtitleColor = when (fontColorGroup.checkedRadioButtonId) {
                R.id.font_color_yellow -> 0xFFFF00
                R.id.font_color_cyan -> 0x00FFFF
                else -> 0xFFFFFF // White
            }
            
            // Get selected background
            subtitleBackground = when (backgroundGroup.checkedRadioButtonId) {
                R.id.background_black -> 0xFF000000.toInt()
                R.id.background_semitransparent -> 0x80000000.toInt()
                else -> 0x00000000 // Transparent
            }
            
            applySubtitleSettings()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun applySubtitleSettings() {
        // LibVLC subtitle styling is applied via media options or through
        // the rendering pipeline. For this implementation, we'll use Media options
        // that need to be set before playback starts. For dynamic changes during
        // playback, we would need to use the subtitle renderer settings which
        // LibVLC doesn't expose directly in a simple way.
        // 
        // As a workaround, we can store these preferences and apply them on next
        // video playback, or use advanced LibVLC features with native rendering.
        Log.d(TAG, "Subtitle settings: size=$subtitleFontSize, color=${Integer.toHexString(subtitleColor)}, bg=${Integer.toHexString(subtitleBackground)}")
        
        // Note: For dynamic subtitle styling in LibVLC Android, you would typically
        // need to implement custom subtitle rendering or use the setScale and
        // other methods available on the MediaPlayer. This is a simplified version.
    }

    private fun showAspectRatioDialog() {
        val dialog = Dialog(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
        dialog.setContentView(R.layout.dialog_aspect_ratio)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val aspectRatioGroup = dialog.findViewById<RadioGroup>(R.id.aspect_ratio_group)
        val closeButton = dialog.findViewById<Button>(R.id.btn_close_aspect_ratio)
        
        // Set current selection based on saved preference
        val currentRatio = aspectRatio
        aspectRatioGroup.check(getAspectRatioRadioId(currentRatio))
        
        // Handle aspect ratio selection
        aspectRatioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.aspect_ratio_fit -> setAspectRatio(null) // Best fit - maintains aspect ratio
                R.id.aspect_ratio_fill -> setAspectRatio("") // Fill screen - forces to fill
                R.id.aspect_ratio_16_9 -> setAspectRatio("16:9")
                R.id.aspect_ratio_4_3 -> setAspectRatio("4:3")
                R.id.aspect_ratio_21_9 -> setAspectRatio("21:9")
            }
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setAspectRatio(aspectRatio: String?) {
        mediaPlayer?.let { player ->
            try {
                // LibVLC aspectRatio behavior:
                // - null: Best fit (maintains original aspect ratio)
                // - "": Force fill screen (may stretch)
                // - "width:height": Force specific aspect ratio
                player.aspectRatio = aspectRatio
                val ratioText = getAspectRatioDisplayName(aspectRatio)
                Log.d(TAG, "Aspect ratio set to: $ratioText")
                
                // Save the selected aspect ratio
                this.aspectRatio = aspectRatio
            } catch (e: Exception) {
                Log.e(TAG, "Error setting aspect ratio", e)
            }
        }
    }

    private fun searchAndLoadExternalSubtitles(videoUrl: String) {
        val currentTime = System.currentTimeMillis()
        
        // Debounce: ignore calls within 2 seconds of the last one
        if (currentTime - lastSubtitleSearchTimestamp < subtitleSearchDebounceMs) {
            Log.d(TAG, "searchAndLoadExternalSubtitles: Debounced - ignoring call (too soon after previous)")
            SubtitleDebugHelper.logInfo("PlayerActivity", "searchAndLoadExternalSubtitles: Debounced call ignored")
            return
        }
        
        lastSubtitleSearchTimestamp = currentTime
        
        Log.d(TAG, "searchAndLoadExternalSubtitles called for: $videoUrl")
        SubtitleDebugHelper.logInfo("PlayerActivity", "searchAndLoadExternalSubtitles called for: $videoUrl")
        
        // Check if credentials are configured
        if (!SubtitlePreferences.hasCredentials(this)) {
            Log.w(TAG, "No subtitle source credentials configured, skipping external subtitle search")
            SubtitleDebugHelper.logWarning("PlayerActivity", "No subtitle source credentials configured")
            return
        }
        
        Log.d(TAG, "Subtitle credentials found, proceeding with search")
        SubtitleDebugHelper.logInfo("PlayerActivity", "Credentials found, proceeding with search")
        
        // Get preferred subtitle language
        val preferredLang = SubtitlePreferences.getPreferredSubtitleLanguage(this)
        Log.d(TAG, "Preferred subtitle language: $preferredLang")
        SubtitleDebugHelper.logDebug("PlayerActivity", "Preferred subtitle language: $preferredLang")
        
        // Extract video filename from URL
        val videoFilename = videoUrl.substringAfterLast('/').substringBefore('?')
        Log.d(TAG, "Extracted video filename: $videoFilename")
        SubtitleDebugHelper.logDebug("PlayerActivity", "Video filename: $videoFilename")
        
        // Launch async task to search and download subtitles
        coroutineScope.launch {
            try {
                Log.d(TAG, "Starting external subtitle search...")
                SubtitleDebugHelper.logInfo("PlayerActivity", "Launching coroutine for subtitle search")
                
                // Search and download subtitles
                val subtitlePath = subtitleDownloader?.searchAndDownload(
                    videoFilename = videoFilename,
                    imdbId = null, // Could be passed from intent if available
                    language = preferredLang
                )
                
                if (subtitlePath != null) {
                    Log.d(TAG, "External subtitle downloaded: $subtitlePath")
                    SubtitleDebugHelper.logInfo("PlayerActivity", "Subtitle downloaded successfully: $subtitlePath")
                    
                    runOnUiThread {
                        // Use the robust addAndSelectSubtitle function
                        val success = addAndSelectSubtitle(subtitlePath)
                        if (!success) {
                            App.toast(R.string.subtitle_load_failed, true)
                        }
                    }
                } else {
                    Log.d(TAG, "No external subtitle found")
                    SubtitleDebugHelper.logWarning("PlayerActivity", "No subtitle found - searchAndDownload returned null")
                    // No need to show toast or wrap in runOnUiThread for logging
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading external subtitles", e)
                SubtitleDebugHelper.logError("PlayerActivity", "Exception in subtitle search coroutine: ${e.message}", e)
            }
        }
    }

    /**
     * Load subtitle from a direct URL after playback has started
     * This method uses addSlave() which works for already-playing media
     */
    private fun loadSubtitleFromUrl(subtitleUrl: String) {
        Log.d(TAG, "Loading subtitle from URL: $subtitleUrl")
        
        handler.postDelayed({
            // Use the robust addAndSelectSubtitle function
            val success = addAndSelectSubtitle(subtitleUrl)
            if (!success) {
                App.toast(R.string.subtitle_load_failed, true)
            }
        }, SUBTITLE_TRACK_REGISTRATION_DELAY_MS)
    }

    private fun autoSelectPreferredTracks() {
        val player = mediaPlayer ?: return
        
        // Wait a bit for tracks to be loaded
        handler.postDelayed({
            try {
                // Auto-select preferred audio language
                val preferredAudioLang = SubtitlePreferences.getPreferredAudioLanguage(this)
                val audioTracks = player.audioTracks
                
                if (audioTracks != null) {
                    audioTracks.forEachIndexed { index, track ->
                        val trackName = track.name?.lowercase() ?: ""
                        if (trackName.contains(preferredAudioLang)) {
                            player.audioTrack = track.id
                            Log.d(TAG, "Auto-selected audio track: $trackName")
                            return@forEachIndexed
                        }
                    }
                }
                
                // Auto-select preferred subtitle language
                val preferredSubLang = SubtitlePreferences.getPreferredSubtitleLanguage(this)
                val spuTracks = player.spuTracks
                
                if (spuTracks != null) {
                    spuTracks.forEachIndexed { index, track ->
                        val trackName = track.name?.lowercase() ?: ""
                        if (trackName.contains(preferredSubLang)) {
                            player.spuTrack = track.id
                            Log.d(TAG, "Auto-selected subtitle track: $trackName")
                            return@forEachIndexed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-selecting tracks", e)
            }
        }, TRACK_LOADING_DELAY_MS)
    }

    private fun refreshTracks() {
        val player = mediaPlayer ?: return
        
        Log.d(TAG, "Refreshing tracks after media parse")
        
        try {
            val audioTracks = player.audioTracks
            val spuTracks = player.spuTracks
            
            if (audioTracks != null) {
                Log.d(TAG, "Audio tracks available: ${audioTracks.size}")
            }
            
            if (spuTracks != null) {
                Log.d(TAG, "Subtitle tracks available: ${spuTracks.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing tracks", e)
        }
    }
    
    /**
     * Show subtitle debug menu with options to export logs or trigger diagnostic crash
     */
    private fun showSubtitleDebugMenu() {
        val dialog = Dialog(this, R.style.TransparentDialog)
        dialog.setContentView(R.layout.dialog_subtitle_debug)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val exportLogsButton = dialog.findViewById<Button>(R.id.btn_export_logs)
        val triggerCrashButton = dialog.findViewById<Button>(R.id.btn_trigger_crash)
        val clearLogsButton = dialog.findViewById<Button>(R.id.btn_clear_logs)
        val closeButton = dialog.findViewById<Button>(R.id.btn_close_debug)
        
        exportLogsButton.setOnClickListener {
            val logPath = SubtitleDebugHelper.exportLogsToFile(this)
            if (logPath != null) {
                App.toast("Subtitle logs exported to: $logPath", true)
            } else {
                App.toast("Failed to export logs", true)
            }
            dialog.dismiss()
        }
        
        triggerCrashButton.setOnClickListener {
            dialog.dismiss()
            // Trigger diagnostic crash with subtitle logs
            handler.postDelayed({
                SubtitleDebugHelper.triggerDiagnosticCrash()
            }, 500)
        }
        
        clearLogsButton.setOnClickListener {
            SubtitleDebugHelper.clearLogs()
            App.toast("Subtitle debug logs cleared", false)
            dialog.dismiss()
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Retry selecting a subtitle track until it's detected or max retries reached
     * This handles the case where LibVLC needs time to register the new subtitle track
     */
    private fun retrySubtitleTrackSelection(
        previousTrackCount: Int,
        retryAttempt: Int = 0
    ) {
        if (retryAttempt >= SUBTITLE_TRACK_MAX_RETRIES) {
            Log.w(TAG, "Max subtitle track selection retries reached ($SUBTITLE_TRACK_MAX_RETRIES)")
            SubtitleDebugHelper.logWarning("PlayerActivity", "Max retries reached - subtitle track not detected")
            // Notify user that subtitle loading failed after multiple attempts
            runOnUiThread {
                App.toast(R.string.subtitle_load_failed, true)
            }
            return
        }
        
        // Refresh tracks to get the new subtitle
        refreshTracks()
        
        // Try to auto-select the newly added subtitle
        val spuTracks = mediaPlayer?.spuTracks
        if (spuTracks != null && spuTracks.size > previousTrackCount) {
            // Success! Select the last track (newly added one)
            val newTrack = spuTracks.last()
            mediaPlayer?.spuTrack = newTrack.id
            Log.d(TAG, "Auto-selected new subtitle track on attempt ${retryAttempt + 1}: ${newTrack.name}")
            SubtitleDebugHelper.logInfo("PlayerActivity", "Auto-selected subtitle track (attempt ${retryAttempt + 1}): ${newTrack.name}")
            App.toast(R.string.subtitle_loaded, false)
        } else {
            // Not detected yet, retry after delay
            Log.d(TAG, "Subtitle track not detected yet (attempt ${retryAttempt + 1}/$SUBTITLE_TRACK_MAX_RETRIES), retrying...")
            SubtitleDebugHelper.logDebug("PlayerActivity", "Track not detected, retry ${retryAttempt + 1}/$SUBTITLE_TRACK_MAX_RETRIES")
            
            handler.postDelayed({
                retrySubtitleTrackSelection(previousTrackCount, retryAttempt + 1)
            }, SUBTITLE_TRACK_RETRY_DELAY_MS)
        }
    }
    
    /**
     * Robustly add and select a subtitle track
     * 
     * This function:
     * 1. Calls addSlave to add the subtitle
     * 2. Waits for the ESAdded event (via polling for track count increase)
     * 3. Explicitly sets the new track as current using setSpuTrack
     * 
     * @param subtitlePath The file path or URI to the subtitle file
     * @return True if successful, false otherwise
     */
    private fun addAndSelectSubtitle(subtitlePath: String): Boolean {
        try {
            // Verify the subtitle file exists (if it's a local file)
            if (subtitlePath.startsWith("/")) {
                val subtitleFile = File(subtitlePath)
                if (!subtitleFile.exists()) {
                    Log.e(TAG, "Subtitle file does not exist: $subtitlePath")
                    SubtitleDebugHelper.logError("PlayerActivity", "Subtitle file not found: $subtitlePath")
                    return false
                }
                
                Log.d(TAG, "Subtitle file exists: ${subtitleFile.absolutePath}, size: ${subtitleFile.length()} bytes")
                SubtitleDebugHelper.logDebug("PlayerActivity", "File exists, size: ${subtitleFile.length()} bytes")
            }
            
            // Store track count BEFORE adding to detect new track after registration delay
            val previousTrackCount = mediaPlayer?.spuTracks?.size ?: 0
            
            // Convert file path to proper URI format for LibVLC
            val subtitleUri = when {
                subtitlePath.startsWith("http://") || subtitlePath.startsWith("https://") -> {
                    // Network URL - use as-is
                    subtitlePath
                }
                subtitlePath.startsWith("file://") -> {
                    // Already a file URI - validate format
                    subtitlePath
                }
                subtitlePath.startsWith("/") -> {
                    // Local file path - convert to proper URI
                    val subtitleFile = File(subtitlePath)
                    Uri.fromFile(subtitleFile).toString()
                }
                else -> {
                    // Unknown format
                    Log.w(TAG, "Unknown subtitle path format: $subtitlePath")
                    SubtitleDebugHelper.logWarning("PlayerActivity", "Unknown path format: $subtitlePath")
                    subtitlePath
                }
            }
            
            Log.d(TAG, "Subtitle file path: $subtitlePath")
            Log.d(TAG, "Subtitle URI generated: $subtitleUri")
            SubtitleDebugHelper.logInfo("PlayerActivity", "File path: $subtitlePath")
            SubtitleDebugHelper.logInfo("PlayerActivity", "Generated URI: $subtitleUri")
            
            // Validate URI format before passing to LibVLC
            if (subtitlePath.startsWith("/") && !subtitleUri.startsWith("file://")) {
                Log.e(TAG, "Invalid subtitle URI format: $subtitleUri")
                SubtitleDebugHelper.logError("PlayerActivity", "Invalid URI format generated: $subtitleUri")
                return false
            }
            
            // Use addSlave to add subtitle to already playing media
            // Type 0 = Subtitle, 1 = Audio
            // select = true means VLC should try to auto-select this track
            val added = mediaPlayer?.addSlave(0, subtitleUri, true)
            
            // Log VLC result for debugging
            Log.d(TAG, "VLC addSlave() Result: ${added ?: false}")
            SubtitleDebugHelper.logInfo("PlayerActivity", "VLC addSlave() Result: ${added ?: false}")
            
            if (added == true) {
                Log.d(TAG, "Subtitle slave added successfully")
                SubtitleDebugHelper.logInfo("PlayerActivity", "Subtitle slave added successfully to LibVLC")
                
                // Wait a moment for the track to be registered, then retry selection
                // LibVLC needs time to parse and register the new subtitle track
                handler.postDelayed({
                    retrySubtitleTrackSelection(previousTrackCount)
                }, SUBTITLE_TRACK_REGISTRATION_DELAY_MS)
                
                return true
            } else {
                Log.e(TAG, "Failed to add subtitle slave")
                SubtitleDebugHelper.logError("PlayerActivity", "addSlave() returned false")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding subtitle to player", e)
            SubtitleDebugHelper.logError("PlayerActivity", "Exception while adding subtitle to player: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Handle playback errors with retry logic for network stream issues
     * 
     * VLC can encounter various network errors:
     * - Cancellation (0x8): Often caused by network timeouts or connection issues
     * - Connection errors: Network unavailability or server issues
     * 
     * This method implements exponential backoff retry for recoverable errors.
     */
    private fun handlePlaybackError() {
        loadingSpinner?.visibility = View.GONE
        
        val currentVideoUrl = this.videoUrl
        
        // Check if this is a network stream and we haven't exceeded retry limit
        if (currentVideoUrl != null && 
            (currentVideoUrl.startsWith("http://") || currentVideoUrl.startsWith("https://")) &&
            retryCount < maxRetries) {
            
            retryCount++
            Log.w(TAG, "Network stream error detected. Retry attempt $retryCount of $maxRetries")
            
            // Show retry message to user
            App.toast("Connection error. Retrying ($retryCount/$maxRetries)...", false)
            
            // Calculate exponential backoff delay: 2s, 4s, 8s
            // Formula: baseDelay * 2^(retryCount-1)
            val currentRetryDelay = retryDelayMs * (1 shl (retryCount - 1))
            
            Log.d(TAG, "Retrying playback in ${currentRetryDelay}ms")
            
            // Release current player before retry
            releasePlayer()
            
            // Schedule retry with exponential backoff
            handler.postDelayed({
                try {
                    Log.d(TAG, "Attempting to restart playback (attempt $retryCount)")
                    initializePlayer(currentVideoUrl, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during retry attempt", e)
                    showFinalError()
                }
            }, currentRetryDelay)
            
        } else {
            // Non-network stream or max retries exceeded
            if (retryCount >= maxRetries) {
                Log.e(TAG, "Max retry attempts ($maxRetries) exceeded")
            }
            showFinalError()
        }
    }
    
    /**
     * Show final error message and close the activity
     */
    private fun showFinalError() {
        App.toast(R.string.playback_error, true)
        handler.postDelayed({
            finish()
        }, ERROR_MESSAGE_DISPLAY_TIME_MS)
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        // Only play if player is initialized
        mediaPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        // Duplicate of lifecycle observer for backward compatibility
        // Ensures cleanup happens even on older Android versions
        // where lifecycle observers might not be fully supported
        mediaPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        // Remove all callbacks to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
        // Duplicate of lifecycle observer for defensive programming
        // Ensures detachment happens as a safety net
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister lifecycle observer
        lifecycle.removeObserver(lifecycleObserver)
        // Duplicate of lifecycle observer for defensive programming
        // Ensures cleanup happens as final safety net
        releasePlayer()
    }

    private fun releasePlayer() {
        // Remove all pending callbacks to prevent leaks
        handler.removeCallbacksAndMessages(null)
        
        mediaPlayer?.let { player ->
            try {
                // Stop playback if still playing
                if (player.isPlaying) {
                    player.stop()
                }
                // Detach views to prevent memory leaks
                player.detachViews()
                // Release the player resources
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing media player", e)
            }
        }
        mediaPlayer = null
        
        libVLC?.let {
            try {
                // Release LibVLC instance
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing LibVLC", e)
            }
        }
        libVLC = null
        
        Log.d(TAG, "Player released successfully")
    }
}
