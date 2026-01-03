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
 * - Search for subtitles from multiple external sources (OpenSubtitles, SubSource, SubDL, SubHero)
 * - Download subtitle files to local cache
 * - Manage subtitle file lifecycle
 * 
 * Uses a provider-based architecture to support multiple subtitle sources.
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
    
    // Initialize all available subtitle providers
    // Stremio addons are prioritized first for reliability
    private val providers: List<SubtitleProvider> by lazy {
        val providerList = mutableListOf<SubtitleProvider>()
        
        // Add a StremioAddonProvider for each configured addon URL
        val stremioUrls = SubtitlePreferences.getStremioAddonUrls(context)
        for (url in stremioUrls) {
            providerList.add(StremioAddonProvider(context, url))
        }
        
        // Add other providers
        providerList.add(OpenSubtitlesProvider(context))
        providerList.add(SubSourceProvider(context))
        providerList.add(SubDLProvider(context))
        providerList.add(SubHeroProvider(context))
        
        providerList
    }
    
    /**
     * Search and download subtitles for a video
     * Iterates through all enabled providers until a subtitle is found
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
            SubtitleDebugHelper.logInfo("SubtitleDownloader", "=== Starting subtitle search ===")
            SubtitleDebugHelper.logInfo("SubtitleDownloader", "Video: '$videoFilename', IMDB: '$imdbId', Language: '$language'")
            
            // Iterate through all enabled providers
            for (provider in providers) {
                if (!provider.isEnabled()) {
                    Log.d(TAG, "Provider ${provider.getName()} is disabled, skipping")
                    SubtitleDebugHelper.logDebug("SubtitleDownloader", "Provider ${provider.getName()} is disabled, skipping")
                    continue
                }
                
                Log.d(TAG, "Trying provider: ${provider.getName()}")
                SubtitleDebugHelper.logInfo("SubtitleDownloader", "Attempting provider: ${provider.getName()}")
                
                try {
                    // Search for subtitles using this provider
                    val results = provider.search(videoFilename, imdbId, language)
                    
                    if (results.isNotEmpty()) {
                        Log.d(TAG, "Found ${results.size} results from ${provider.getName()}")
                        SubtitleDebugHelper.logInfo("SubtitleDownloader", "Provider ${provider.getName()} found ${results.size} results")
                        
                        // Try to download the first (best) result
                        val subtitlePath = provider.download(results.first())
                        
                        if (subtitlePath != null) {
                            Log.d(TAG, "Successfully downloaded subtitle from ${provider.getName()}")
                            SubtitleDebugHelper.logInfo("SubtitleDownloader", "=== SUCCESS: Downloaded from ${provider.getName()} ===")
                            return@withContext subtitlePath
                        } else {
                            SubtitleDebugHelper.logWarning("SubtitleDownloader", "Download failed from ${provider.getName()}")
                        }
                    } else {
                        Log.d(TAG, "No results from ${provider.getName()}")
                        SubtitleDebugHelper.logDebug("SubtitleDownloader", "Provider ${provider.getName()} returned no results")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error with provider ${provider.getName()}", e)
                    SubtitleDebugHelper.logError("SubtitleDownloader", "Provider ${provider.getName()} threw exception: ${e.message}", e)
                    // Continue to next provider
                }
            }
            
            Log.d(TAG, "No subtitles found from any provider")
            SubtitleDebugHelper.logWarning("SubtitleDownloader", "=== FAILED: No subtitles found from any provider ===")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching/downloading subtitles", e)
            SubtitleDebugHelper.logError("SubtitleDownloader", "Fatal error in searchAndDownload: ${e.message}", e)
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
                Log.e(TAG, "Failed to download subtitle: ${response.code()}")
                return@withContext null
            }
            
            val body = response.body() ?: return@withContext null
            
            // Create cache directory if it doesn't exist
            val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    Log.e(TAG, "Failed to create cache directory: ${cacheDir.absolutePath}")
                    return@withContext null
                }
                Log.d(TAG, "Created cache directory: ${cacheDir.absolutePath}")
            }
            
            // Generate a unique filename
            val timestamp = System.currentTimeMillis()
            val extension = getExtensionFromUrl(subtitleUrl) ?: "srt"
            val subtitleFile = File(cacheDir, "subtitle_${language}_${timestamp}.$extension")
            
            // Ensure parent directory exists before creating FileOutputStream
            subtitleFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    Log.e(TAG, "Failed to create parent directory for subtitle file: ${parent.absolutePath}")
                    return@withContext null
                }
            }
            
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
