package top.rootu.lampa.helpers

import android.content.Context
import android.content.SharedPreferences

/**
 * SubtitlePreferences - Helper class to manage subtitle-related SharedPreferences
 * 
 * Manages settings for external subtitle sources (e.g., OpenSubtitles) including:
 * - API credentials (API key, username, password)
 * - Language preferences for audio and subtitles
 */
object SubtitlePreferences {
    
    private const val PREFS_NAME = "subtitle_preferences"
    
    // Keys for SharedPreferences
    private const val KEY_API_KEY = "subtitle_source_apikey"
    private const val KEY_USERNAME = "subtitle_username"
    private const val KEY_PASSWORD = "subtitle_password"
    private const val KEY_PREFERRED_AUDIO_LANG = "preferred_audio_language"
    private const val KEY_PREFERRED_SUBTITLE_LANG = "preferred_subtitle_language"
    private const val KEY_STREMIO_ADDON_URL = "stremio_addon_url"
    
    // Default values
    private const val DEFAULT_LANGUAGE = "en"
    
    /**
     * Get SharedPreferences instance
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get subtitle source API key
     */
    fun getApiKey(context: Context): String? {
        return getPreferences(context).getString(KEY_API_KEY, null)
    }
    
    /**
     * Set subtitle source API key
     */
    fun setApiKey(context: Context, apiKey: String?) {
        getPreferences(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    /**
     * Get subtitle source username
     */
    fun getUsername(context: Context): String? {
        return getPreferences(context).getString(KEY_USERNAME, null)
    }
    
    /**
     * Set subtitle source username
     */
    fun setUsername(context: Context, username: String?) {
        getPreferences(context).edit().putString(KEY_USERNAME, username).apply()
    }
    
    /**
     * Get subtitle source password
     */
    fun getPassword(context: Context): String? {
        return getPreferences(context).getString(KEY_PASSWORD, null)
    }
    
    /**
     * Set subtitle source password
     */
    fun setPassword(context: Context, password: String?) {
        getPreferences(context).edit().putString(KEY_PASSWORD, password).apply()
    }
    
    /**
     * Get preferred audio language
     */
    fun getPreferredAudioLanguage(context: Context): String {
        return getPreferences(context).getString(KEY_PREFERRED_AUDIO_LANG, DEFAULT_LANGUAGE) 
            ?: DEFAULT_LANGUAGE
    }
    
    /**
     * Set preferred audio language
     */
    fun setPreferredAudioLanguage(context: Context, language: String) {
        getPreferences(context).edit().putString(KEY_PREFERRED_AUDIO_LANG, language).apply()
    }
    
    /**
     * Get preferred subtitle language
     */
    fun getPreferredSubtitleLanguage(context: Context): String {
        return getPreferences(context).getString(KEY_PREFERRED_SUBTITLE_LANG, DEFAULT_LANGUAGE) 
            ?: DEFAULT_LANGUAGE
    }
    
    /**
     * Set preferred subtitle language
     */
    fun setPreferredSubtitleLanguage(context: Context, language: String) {
        getPreferences(context).edit().putString(KEY_PREFERRED_SUBTITLE_LANG, language).apply()
    }
    
    /**
     * Get Stremio addon URL
     */
    fun getStremioAddonUrl(context: Context): String? {
        return getPreferences(context).getString(KEY_STREMIO_ADDON_URL, null)
    }
    
    /**
     * Set Stremio addon URL
     */
    fun setStremioAddonUrl(context: Context, url: String?) {
        getPreferences(context).edit().putString(KEY_STREMIO_ADDON_URL, url).apply()
    }
    
    /**
     * Check if subtitle credentials are configured
     * Returns true if either API key OR username+password OR Stremio addon URL is set
     */
    fun hasCredentials(context: Context): Boolean {
        val apiKey = getApiKey(context)
        val username = getUsername(context)
        val password = getPassword(context)
        val stremioUrl = getStremioAddonUrl(context)
        
        // Return true if API key is set OR both username and password are set OR Stremio addon URL is set
        return !apiKey.isNullOrEmpty() || 
               (!username.isNullOrEmpty() && !password.isNullOrEmpty()) ||
               !stremioUrl.isNullOrEmpty()
    }
    
    /**
     * Clear all subtitle preferences
     */
    fun clearAll(context: Context) {
        getPreferences(context).edit().clear().apply()
    }
}
