package top.rootu.lampa

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
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
 * - Multiple Stremio addon URLs
 * 
 * Replaces in-playback dialogs with pre-configured settings
 */
class SettingsActivity : BaseActivity() {
    
    private lateinit var switchAlwaysUseInternalPlayer: SwitchCompat
    private lateinit var editApiKey: TextInputEditText
    private lateinit var editUsername: TextInputEditText
    private lateinit var editPassword: TextInputEditText
    private lateinit var addonUrlsContainer: LinearLayout
    private lateinit var editNewAddonUrl: TextInputEditText
    private lateinit var btnAddAddon: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    // Track current addon URLs
    private val currentAddonUrls = mutableListOf<String>()
    
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
        addonUrlsContainer = findViewById(R.id.addon_urls_container)
        editNewAddonUrl = findViewById(R.id.edit_new_addon_url)
        btnAddAddon = findViewById(R.id.btn_add_addon)
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
        
        // Load Stremio addon URLs
        currentAddonUrls.clear()
        currentAddonUrls.addAll(SubtitlePreferences.getStremioAddonUrls(this))
        refreshAddonUrlsList()
    }
    
    private fun setupListeners() {
        btnAddAddon.setOnClickListener {
            addAddonUrl()
        }
        
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun addAddonUrl() {
        val url = editNewAddonUrl.text.toString().trim()
        
        if (url.isBlank()) {
            App.toast(R.string.stremio_addon_empty_url, true)
            return
        }
        
        if (currentAddonUrls.contains(url)) {
            App.toast(R.string.stremio_addon_duplicate, true)
            return
        }
        
        currentAddonUrls.add(url)
        editNewAddonUrl.setText("")
        refreshAddonUrlsList()
    }
    
    private fun removeAddonUrl(url: String) {
        currentAddonUrls.remove(url)
        refreshAddonUrlsList()
    }
    
    private fun refreshAddonUrlsList() {
        addonUrlsContainer.removeAllViews()
        
        if (currentAddonUrls.isEmpty()) {
            // Show "no addons" message
            val noAddonsText = TextView(this).apply {
                text = getString(R.string.stremio_addon_no_addons)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
                setPadding(0, 16, 0, 16)
            }
            addonUrlsContainer.addView(noAddonsText)
        } else {
            // Show each addon URL with a remove button
            for (url in currentAddonUrls) {
                val itemLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                }
                
                val urlText = TextView(this).apply {
                    text = url
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
                }
                
                val removeButton = Button(this).apply {
                    text = getString(R.string.stremio_addon_remove)
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(8, 0, 0, 0)
                    }
                    setOnClickListener {
                        removeAddonUrl(url)
                    }
                }
                
                itemLayout.addView(urlText)
                itemLayout.addView(removeButton)
                addonUrlsContainer.addView(itemLayout)
            }
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
        
        // Save Stremio addon URLs
        SubtitlePreferences.setStremioAddonUrls(this, currentAddonUrls)
        
        // Show success message
        App.toast(R.string.settings_saved, false)
        
        // Close activity
        finish()
    }
}
