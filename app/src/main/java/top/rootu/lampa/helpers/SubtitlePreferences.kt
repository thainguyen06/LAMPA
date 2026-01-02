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
    private const val KEY_STREMIO_ADDON_URL = "stremio_addon_url" // Legacy single URL
    private const val KEY_STREMIO_ADDON_URLS = "stremio_addon_urls" // Multiple URLs
    
    // Default values
    private const val DEFAULT_LANGUAGE = "en"
    private const val ADDON_URL_SEPARATOR = "|"
    
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
     * Get Stremio addon URL (legacy - returns first addon if multiple exist)
     * @deprecated Use getStremioAddonUrls() for multiple addon support
     */
    @Deprecated("Use getStremioAddonUrls() instead")
    fun getStremioAddonUrl(context: Context): String? {
        val urls = getStremioAddonUrls(context)
        return if (urls.isNotEmpty()) urls[0] else null
    }
    
    /**
     * Set Stremio addon URL (legacy - replaces all addons with single URL)
     * @deprecated Use addStremioAddonUrl() or setStremioAddonUrls() for multiple addon support
     */
    @Deprecated("Use addStremioAddonUrl() or setStremioAddonUrls() instead")
    fun setStremioAddonUrl(context: Context, url: String?) {
        if (url.isNullOrEmpty()) {
            clearStremioAddonUrls(context)
        } else {
            setStremioAddonUrls(context, listOf(url))
        }
    }
    
    /**
     * Get all Stremio addon URLs
     * Returns a list of configured addon URLs
     */
    fun getStremioAddonUrls(context: Context): List<String> {
        val prefs = getPreferences(context)
        
        // First check new multi-URL storage
        val multiUrlString = prefs.getString(KEY_STREMIO_ADDON_URLS, null)
        if (!multiUrlString.isNullOrEmpty()) {
            return multiUrlString.split(ADDON_URL_SEPARATOR)
                .filter { it.isNotEmpty() }
        }
        
        // Fall back to legacy single URL storage for backward compatibility
        val legacyUrl = prefs.getString(KEY_STREMIO_ADDON_URL, null)
        if (!legacyUrl.isNullOrEmpty()) {
            // Migrate legacy URL to new format
            setStremioAddonUrls(context, listOf(legacyUrl))
            // Clear legacy key
            prefs.edit().remove(KEY_STREMIO_ADDON_URL).apply()
            return listOf(legacyUrl)
        }
        
        return emptyList()
    }
    
    /**
     * Set all Stremio addon URLs, replacing any existing ones
     */
    fun setStremioAddonUrls(context: Context, urls: List<String>) {
        val filteredUrls = urls.filter { it.isNotEmpty() }.distinct()
        val urlString = filteredUrls.joinToString(ADDON_URL_SEPARATOR)
        getPreferences(context).edit()
            .putString(KEY_STREMIO_ADDON_URLS, urlString)
            .remove(KEY_STREMIO_ADDON_URL) // Clear legacy key
            .apply()
    }
    
    /**
     * Add a new Stremio addon URL
     */
    fun addStremioAddonUrl(context: Context, url: String) {
        if (url.isEmpty()) return
        
        val currentUrls = getStremioAddonUrls(context).toMutableList()
        if (!currentUrls.contains(url)) {
            currentUrls.add(url)
            setStremioAddonUrls(context, currentUrls)
        }
    }
    
    /**
     * Remove a Stremio addon URL
     */
    fun removeStremioAddonUrl(context: Context, url: String) {
        val currentUrls = getStremioAddonUrls(context).toMutableList()
        currentUrls.remove(url)
        setStremioAddonUrls(context, currentUrls)
    }
    
    /**
     * Clear all Stremio addon URLs
     */
    fun clearStremioAddonUrls(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_STREMIO_ADDON_URLS)
            .remove(KEY_STREMIO_ADDON_URL)
            .apply()
    }
    
    /**
     * Check if subtitle credentials are configured
     * Returns true if either API key OR username+password OR Stremio addon URL(s) are set
     */
    fun hasCredentials(context: Context): Boolean {
        val apiKey = getApiKey(context)
        val username = getUsername(context)
        val password = getPassword(context)
        val stremioUrls = getStremioAddonUrls(context)
        
        // Return true if API key is set OR both username and password are set OR Stremio addon URLs are set
        return !apiKey.isNullOrEmpty() || 
               (!username.isNullOrEmpty() && !password.isNullOrEmpty()) ||
               stremioUrls.isNotEmpty()
    }
    
    /**
     * Clear all subtitle preferences
     */
    fun clearAll(context: Context) {
        getPreferences(context).edit().clear().apply()
    }
}
