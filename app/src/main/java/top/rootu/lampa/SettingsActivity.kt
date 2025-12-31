package top.rootu.lampa

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.textfield.TextInputEditText
import top.rootu.lampa.helpers.Prefs.appPlayer
import top.rootu.lampa.helpers.Prefs.PLAYER_LAMPA
import top.rootu.lampa.helpers.SubtitlePreferences

/**
 * SettingsActivity - Application settings screen
 * 
 * Provides centralized configuration for:
 * - Player preferences (internal vs external)
 * - Subtitle credentials (OpenSubtitles API)
 * 
 * Replaces in-playback dialogs with pre-configured settings
 */
class SettingsActivity : BaseActivity() {
    
    private lateinit var switchAlwaysUseInternalPlayer: SwitchCompat
    private lateinit var editApiKey: TextInputEditText
    private lateinit var editUsername: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Initialize views
        initializeViews()
        
        // Load current settings
        loadSettings()
        
        // Set up button listeners
        setupListeners()
    }
    
    private fun initializeViews() {
        switchAlwaysUseInternalPlayer = findViewById(R.id.switch_always_use_internal_player)
        editApiKey = findViewById(R.id.edit_api_key)
        editUsername = findViewById(R.id.edit_username)
        editPassword = findViewById(R.id.edit_password)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
    }
    
    private fun loadSettings() {
        // Load player preference
        val currentPlayer = appPlayer
        switchAlwaysUseInternalPlayer.isChecked = (currentPlayer == PLAYER_LAMPA)
        
        // Load subtitle credentials
        editApiKey.setText(SubtitlePreferences.getApiKey(this) ?: "")
        editUsername.setText(SubtitlePreferences.getUsername(this) ?: "")
        editPassword.setText(SubtitlePreferences.getPassword(this) ?: "")
    }
    
    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun saveSettings() {
        // Save player preference
        if (switchAlwaysUseInternalPlayer.isChecked) {
            this.appPlayer = PLAYER_LAMPA
        } else {
            // Clear the preference so user gets prompted each time
            this.appPlayer = ""
        }
        
        // Save subtitle credentials
        SubtitlePreferences.setApiKey(this, editApiKey.text.toString().trim())
        SubtitlePreferences.setUsername(this, editUsername.text.toString().trim())
        SubtitlePreferences.setPassword(this, editPassword.text.toString().trim())
        
        // Show success message
        App.toast(R.string.settings_saved, false)
        
        // Close activity
        finish()
    }
}
