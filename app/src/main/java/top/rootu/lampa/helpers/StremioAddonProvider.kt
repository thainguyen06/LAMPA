package top.rootu.lampa.helpers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * StremioAddonProvider - Stremio addon subtitle provider
 * 
 * Implements subtitle search and download using Stremio's addon API.
 * Stremio addons provide a standardized interface for content discovery
 * including subtitles.
 * 
 * Stremio Addon Protocol:
 * - Manifest: {addon_url}/manifest.json
 * - Subtitles: {addon_url}/subtitles/{type}/{id}.json
 * 
 * Features:
 * - Supports any Stremio addon that provides subtitles
 * - Configurable addon URL per instance
 * - Standard JSON-based API
 * - No authentication required for most addons
 * 
 * Requirements:
 * - Valid Stremio addon URL must be provided
 * - Addon must support subtitle resources
 * 
 * Popular Stremio Subtitle Addons:
 * - OpenSubtitles: https://opensubtitles-v3.strem.io
 * - Subscene: https://subscene.strem.io
 */
class StremioAddonProvider(
    private val context: Context,
    private val addonUrl: String
) : SubtitleProvider {
    
    companion object {
        private const val TAG = "StremioAddonProvider"
        private const val SUBTITLE_CACHE_DIR = "subtitle_cache"
        
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
    
    override fun getName(): String {
        // Extract domain name from URL for better readability
        // Use the processed base URL to ensure consistent naming
        val baseUrl = getAddonUrl()
        val domain = try {
            java.net.URL(baseUrl).host
        } catch (e: Exception) {
            baseUrl
        }
        return "Stremio Addon ($domain)"
    }
    
    override fun isEnabled(): Boolean {
        // Provider is enabled if addon URL is not empty
        return addonUrl.isNotEmpty()
    }
    
    /**
     * Get the configured Stremio addon base URL
     * Handles both formats:
     * - Base URL: https://opensubtitles-v3.strem.io
     * - Manifest URL: https://opensubtitles-v3.strem.io/manifest.json
     */
    private fun getAddonUrl(): String {
        var url = addonUrl.trim()
        
        // Remove trailing slash if present
        url = url.trimEnd('/')
        
        // If URL ends with /manifest.json, remove it to get base URL
        if (url.endsWith("/manifest.json", ignoreCase = true)) {
            url = url.substring(0, url.length - "/manifest.json".length)
        }
        
        return url
    }
    
    /**
     * Verify the addon supports subtitles by checking its manifest
     */
    private suspend fun verifyAddonSupportsSubtitles(addonUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying addon manifest at: $addonUrl/manifest.json")
            
            val request = Request.Builder()
                .url("$addonUrl/manifest.json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch manifest: ${response.code()}")
                return@withContext false
            }
            
            val body = response.body()?.string() ?: return@withContext false
            val manifest = JSONObject(body)
            
            // Check if addon provides subtitle resources
            if (manifest.has("resources")) {
                val resources = manifest.getJSONArray("resources")
                for (i in 0 until resources.length()) {
                    val resource = resources.getString(i)
                    if (resource == "subtitles" || resource.startsWith("subtitles")) {
                        Log.d(TAG, "Addon supports subtitles")
                        return@withContext true
                    }
                }
            }
            
            Log.w(TAG, "Addon does not support subtitles resource")
            return@withContext false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying addon manifest", e)
            return@withContext false
        }
    }
    
    override suspend fun search(
        query: String,
        imdbId: String?,
        language: String
    ): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        try {
            val baseAddonUrl = getAddonUrl()
            
            Log.d(TAG, "Searching subtitles via Stremio addon: $baseAddonUrl")
            Log.d(TAG, "Query: $query, IMDB: $imdbId, Language: $language")
            
            // Verify addon supports subtitles
            if (!verifyAddonSupportsSubtitles(baseAddonUrl)) {
                Log.e(TAG, "Addon does not support subtitles")
                return@withContext emptyList()
            }
            
            // For Stremio, we need IMDB ID or use a search query
            // Most subtitle addons expect: /subtitles/{type}/{id}.json
            // Where type is usually "movie" or "series", and id is the IMDB ID
            
            val subtitleEndpoint = if (!imdbId.isNullOrEmpty()) {
                // Use IMDB ID if available (more accurate)
                // Assume movie type for now, could be enhanced to detect type
                "$baseAddonUrl/subtitles/movie/$imdbId.json"
            } else {
                // Some addons support search by query
                // Try common patterns
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                "$baseAddonUrl/subtitles/search/$encodedQuery.json"
            }
            
            Log.d(TAG, "Calling Stremio addon API: $subtitleEndpoint")
            
            val request = Request.Builder()
                .url(subtitleEndpoint)
                .build()
            
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Stremio addon API response code: ${response.code()}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Subtitle search failed: ${response.code()}")
                return@withContext emptyList()
            }
            
            val body = response.body()?.string() ?: return@withContext emptyList()
            val jsonResponse = JSONObject(body)
            
            val results = mutableListOf<SubtitleSearchResult>()
            
            // Stremio addon response format:
            // { "subtitles": [ { "id": "...", "url": "...", "lang": "..." } ] }
            if (jsonResponse.has("subtitles")) {
                val subtitlesArray = jsonResponse.getJSONArray("subtitles")
                
                for (i in 0 until subtitlesArray.length()) {
                    val subtitle = subtitlesArray.getJSONObject(i)
                    
                    val subtitleLang = subtitle.optString("lang", "")
                    
                    // Filter by language if specified
                    if (language.isNotEmpty() && subtitleLang.isNotEmpty() && 
                        !subtitleLang.equals(language, ignoreCase = true)) {
                        continue
                    }
                    
                    val id = subtitle.optString("id", i.toString())
                    val url = subtitle.optString("url", "")
                    
                    if (url.isNotEmpty()) {
                        results.add(SubtitleSearchResult(
                            id = id,
                            name = subtitle.optString("label", query),
                            language = subtitleLang.ifEmpty { language },
                            downloads = 0, // Stremio addons don't typically provide download count
                            rating = 0f,   // Stremio addons don't typically provide ratings
                            downloadUrl = url,
                            provider = getName()
                        ))
                    }
                }
            }
            
            Log.d(TAG, "Found ${results.size} subtitle(s) from Stremio addon")
            return@withContext results
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching subtitles via Stremio addon", e)
            return@withContext emptyList()
        }
    }
    
    override suspend fun download(result: SubtitleSearchResult): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading subtitle from: ${result.downloadUrl}")
            
            val request = Request.Builder()
                .url(result.downloadUrl)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Subtitle download failed: ${response.code()}")
                return@withContext null
            }
            
            val fileBody = response.body() ?: return@withContext null
            
            // Create cache directory
            val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Determine file extension from content type or URL
            val contentType = response.header("Content-Type", "")
            val extension = when {
                contentType?.contains("srt") == true -> "srt"
                contentType?.contains("vtt") == true -> "vtt"
                contentType?.contains("ass") == true -> "ass"
                result.downloadUrl.endsWith(".srt", ignoreCase = true) -> "srt"
                result.downloadUrl.endsWith(".vtt", ignoreCase = true) -> "vtt"
                result.downloadUrl.endsWith(".ass", ignoreCase = true) -> "ass"
                else -> "srt" // Default to SRT
            }
            
            // Save subtitle file
            val timestamp = System.currentTimeMillis()
            val subtitleFile = File(cacheDir, "subtitle_${result.language}_${timestamp}.$extension")
            
            FileOutputStream(subtitleFile).use { output ->
                fileBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Subtitle downloaded successfully: ${subtitleFile.absolutePath}")
            return@withContext subtitleFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subtitle", e)
            return@withContext null
        }
    }
}
