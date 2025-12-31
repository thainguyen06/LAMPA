package top.rootu.lampa.helpers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * SubtitleDownloader - Helper class to search and download external subtitles
 * 
 * Provides functionality to:
 * - Search for subtitles from external sources (OpenSubtitles, etc.)
 * - Download subtitle files to local cache
 * - Manage subtitle file lifecycle
 * 
 * Note: This is a placeholder implementation. Full OpenSubtitles API integration
 * would require proper authentication, rate limiting, and error handling.
 */
class SubtitleDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "SubtitleDownloader"
        private const val SUBTITLE_CACHE_DIR = "subtitle_cache"
        
        // Shared OkHttpClient instance for efficiency
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
    
    /**
     * Search and download subtitles for a video
     * 
     * @param videoFilename The filename of the video (used for matching)
     * @param imdbId The IMDB ID of the content (if available)
     * @param language The desired subtitle language (ISO 639-1 code, e.g., "en", "vi")
     * @return Path to the downloaded subtitle file, or null if not found
     */
    suspend fun searchAndDownload(
        videoFilename: String,
        imdbId: String?,
        language: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching subtitles for: $videoFilename (IMDB: $imdbId, Lang: $language)")
            
            // Get credentials from preferences
            val apiKey = SubtitlePreferences.getApiKey(context)
            val username = SubtitlePreferences.getUsername(context)
            val password = SubtitlePreferences.getPassword(context)
            
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "No API credentials configured")
                return@withContext null
            }
            
            // Placeholder: In a full implementation, you would:
            // 1. Authenticate with OpenSubtitles API using credentials
            // 2. Search for subtitles using IMDB ID or video filename hash
            // 3. Filter results by language
            // 4. Download the best matching subtitle
            // 5. Save to cache directory
            
            Log.d(TAG, "Subtitle search placeholder - API integration needed")
            
            // For now, return null to indicate no subtitle found
            // In production, this would download and return the subtitle file path
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching/downloading subtitles", e)
            return@withContext null
        }
    }
    
    /**
     * Download a subtitle file from a direct URL
     * 
     * @param subtitleUrl The URL of the subtitle file
     * @param language The language code for the subtitle
     * @return Path to the downloaded subtitle file, or null on error
     */
    suspend fun downloadFromUrl(
        subtitleUrl: String,
        language: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading subtitle from URL: $subtitleUrl")
            
            val request = Request.Builder()
                .url(subtitleUrl)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download subtitle: ${response.code}")
                return@withContext null
            }
            
            val body = response.body ?: return@withContext null
            
            // Create cache directory if it doesn't exist
            val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Log.e(TAG, "Failed to create cache directory")
                    return@withContext null
                }
            }
            
            // Generate a unique filename
            val timestamp = System.currentTimeMillis()
            val extension = getExtensionFromUrl(subtitleUrl) ?: "srt"
            val subtitleFile = File(cacheDir, "subtitle_${language}_${timestamp}.$extension")
            
            // Write the subtitle content to file
            FileOutputStream(subtitleFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Subtitle downloaded successfully: ${subtitleFile.absolutePath}")
            return@withContext subtitleFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subtitle from URL", e)
            return@withContext null
        }
    }
    
    /**
     * Clear old subtitle files from cache
     */
    fun clearCache() {
        try {
            val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                        Log.d(TAG, "Deleted cached subtitle: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing subtitle cache", e)
        }
    }
    
    /**
     * Get file extension from URL
     */
    private fun getExtensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val lastDot = path.lastIndexOf('.')
        return if (lastDot >= 0) {
            path.substring(lastDot + 1).lowercase()
        } else {
            null
        }
    }
}
