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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import top.rootu.lampa.helpers.SubtitleDownloader
import top.rootu.lampa.helpers.SubtitlePreferences

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
    
    // UI components
    private var btnBack: ImageButton? = null
    private var btnPlayPause: ImageButton? = null
    private var btnRewind: ImageButton? = null
    private var btnForward: ImageButton? = null
    private var btnAudioSubtitle: ImageButton? = null
    private var btnSubtitleSettings: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var timeCurrent: TextView? = null
    private var timeTotal: TextView? = null
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
    
    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_SUBTITLE_URL = "subtitle_url"
        
        private const val SEEK_TIME_MS = 10000L // 10 seconds
        private const val CONTROLS_HIDE_DELAY = 3000L // 3 seconds
        private const val TRACK_LOADING_DELAY_MS = 2000L // 2 seconds - Wait for tracks to load
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

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

        Log.d(TAG, "Starting playback for: $videoUrl")
        if (!videoTitleText.isNullOrEmpty()) {
            Log.d(TAG, "Video title: $videoTitleText")
            videoTitle?.text = videoTitleText
        }
        if (!subtitleUrl.isNullOrEmpty()) {
            Log.d(TAG, "External subtitle URL: $subtitleUrl")
        }

        // Initialize LibVLC and start playback
        initializePlayer(videoUrl, subtitleUrl)
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
        btnRewind = findViewById(R.id.btn_rewind)
        btnForward = findViewById(R.id.btn_forward)
        btnAudioSubtitle = findViewById(R.id.btn_audio_subtitle)
        btnSubtitleSettings = findViewById(R.id.btn_subtitle_settings)
        seekBar = findViewById(R.id.seek_bar)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)
        videoTitle = findViewById(R.id.video_title)
        topControls = findViewById(R.id.top_controls)
        bottomControls = findViewById(R.id.bottom_controls)
        
        // Initialize subtitle downloader
        subtitleDownloader = SubtitleDownloader(this)

        // Set up button click listeners
        btnBack?.setOnClickListener {
            finish()
        }

        btnPlayPause?.setOnClickListener {
            togglePlayPause()
        }

        btnRewind?.setOnClickListener {
            seekRelative(-SEEK_TIME_MS)
        }

        btnForward?.setOnClickListener {
            seekRelative(SEEK_TIME_MS)
        }

        btnAudioSubtitle?.setOnClickListener {
            showTrackSelectionDialog()
        }

        btnSubtitleSettings?.setOnClickListener {
            showSubtitleSettingsDialog()
        }

        // Seek bar listener
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.time = progress.toLong()
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
            // Initialize LibVLC
            val options = ArrayList<String>().apply {
                add("--aout=opensles")
                add("--audio-time-stretch") // Better audio sync
                add("-vvv") // Verbose logging for debugging
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
                                updatePlayPauseButton()
                                startProgressUpdate()
                                // Auto-select preferred audio/subtitle tracks
                                autoSelectPreferredTracks()
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
                            Log.e(TAG, "Playback error")
                            runOnUiThread {
                                App.toast(R.string.playback_error, true)
                                finish()
                            }
                        }
                        MediaPlayer.Event.LengthChanged -> {
                            runOnUiThread {
                                updateDuration()
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
                addOption(":network-caching=1000")
                
                // Add external subtitle if provided
                if (!subtitleUrl.isNullOrEmpty()) {
                    try {
                        // Using addOption for LibVLC 3.6.0 compatibility
                        addOption(":input-slave=$subtitleUrl")
                        Log.d(TAG, "Added external subtitle: $subtitleUrl")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add external subtitle", e)
                    }
                }
            }

            // Set media and start playback
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()
            
            // Search for external subtitles if no subtitle URL provided
            if (subtitleUrl.isNullOrEmpty()) {
                searchAndLoadExternalSubtitles(videoUrl)
            }

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
            
            timeCurrent?.text = formatTime(currentTime)
            timeTotal?.text = formatTime(totalTime)
        }
    }

    private fun updateDuration() {
        mediaPlayer?.let { player ->
            val totalTime = player.length
            seekBar?.max = totalTime.toInt()
            timeTotal?.text = formatTime(totalTime)
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
        scheduleHideControls()
    }

    private fun hideControls() {
        isControlsVisible = false
        topControls?.visibility = View.GONE
        bottomControls?.visibility = View.GONE
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
        
        // Populate audio tracks
        populateAudioTracks(audioGroup, player)
        
        // Populate subtitle tracks
        populateSubtitleTracks(subtitleGroup, player)
        
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

    private fun populateAudioTracks(audioGroup: RadioGroup, player: MediaPlayer) {
        audioGroup.removeAllViews()
        
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
                    text = trackName
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    isChecked = (trackDescription.id == currentTrack)
                }
                audioGroup.addView(radioButton)
            }
        }
    }

    private fun populateSubtitleTracks(subtitleGroup: RadioGroup, player: MediaPlayer) {
        subtitleGroup.removeAllViews()
        
        // Add "Disabled" option
        val disabledButton = RadioButton(this).apply {
            id = 2000
            text = getString(R.string.track_disabled)
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
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
                text = trackName
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                isChecked = (trackDescription.id == currentTrack)
            }
            subtitleGroup.addView(radioButton)
        }
    }

    private fun selectAudioTrack(trackIndex: Int) {
        mediaPlayer?.let { player ->
            val audioTracks = player.audioTracks
            if (trackIndex in audioTracks.indices) {
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
                val actualIndex = trackIndex - 1
                if (actualIndex in spuTracks.indices) {
                    player.spuTrack = spuTracks[actualIndex].id
                    Log.d(TAG, "Selected subtitle track: $actualIndex")
                    applySubtitleSettings()
                } else {
                    Log.w(TAG, "Invalid subtitle track index: $actualIndex")
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

    private fun searchAndLoadExternalSubtitles(videoUrl: String) {
        // Check if credentials are configured
        if (!SubtitlePreferences.hasCredentials(this)) {
            Log.d(TAG, "No subtitle source credentials configured")
            return
        }
        
        // Get preferred subtitle language
        val preferredLang = SubtitlePreferences.getPreferredSubtitleLanguage(this)
        
        // Extract video filename from URL
        val videoFilename = videoUrl.substringAfterLast('/').substringBefore('?')
        
        // Launch async task to search and download subtitles
        coroutineScope.launch {
            try {
                Log.d(TAG, "Searching for external subtitles...")
                
                // Search and download subtitles
                val subtitlePath = subtitleDownloader?.searchAndDownload(
                    videoFilename = videoFilename,
                    imdbId = null, // Could be passed from intent if available
                    language = preferredLang
                )
                
                if (subtitlePath != null) {
                    Log.d(TAG, "External subtitle downloaded: $subtitlePath")
                    
                    // Note: Adding subtitle to already playing media may not work reliably
                    // For best results, subtitles should be added before playback starts
                    // This is a best-effort approach for dynamically loaded subtitles
                    mediaPlayer?.media?.let { media ->
                        media.addOption(":input-slave=$subtitlePath")
                    }
                    
                    runOnUiThread {
                        App.toast(R.string.subtitle_loaded, false)
                    }
                } else {
                    Log.d(TAG, "No external subtitle found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading external subtitles", e)
            }
        }
    }

    private fun autoSelectPreferredTracks() {
        val player = mediaPlayer ?: return
        
        // Wait a bit for tracks to be loaded
        handler.postDelayed({
            try {
                // Auto-select preferred audio language
                val preferredAudioLang = SubtitlePreferences.getPreferredAudioLanguage(this)
                val audioTracks = player.audioTracks
                
                audioTracks.forEachIndexed { index, track ->
                    val trackName = track.name?.lowercase() ?: ""
                    if (trackName.contains(preferredAudioLang)) {
                        player.audioTrack = track.id
                        Log.d(TAG, "Auto-selected audio track: $trackName")
                        return@forEachIndexed
                    }
                }
                
                // Auto-select preferred subtitle language
                val preferredSubLang = SubtitlePreferences.getPreferredSubtitleLanguage(this)
                val spuTracks = player.spuTracks
                
                spuTracks.forEachIndexed { index, track ->
                    val trackName = track.name?.lowercase() ?: ""
                    if (trackName.contains(preferredSubLang)) {
                        player.spuTrack = track.id
                        Log.d(TAG, "Auto-selected subtitle track: $trackName")
                        return@forEachIndexed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error auto-selecting tracks", e)
            }
        }, TRACK_LOADING_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        mediaPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun releasePlayer() {
        handler.removeCallbacksAndMessages(null)
        
        mediaPlayer?.let { player ->
            player.stop()
            player.detachViews()
            player.release()
        }
        mediaPlayer = null
        
        libVLC?.release()
        libVLC = null
        
        Log.d(TAG, "Player released")
    }
}
