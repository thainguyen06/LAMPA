package top.rootu.lampa.helpers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * OpenSubtitlesProvider - OpenSubtitles.org subtitle provider
 * 
 * Implements subtitle search and download using OpenSubtitles API v1
 * with dual authentication support.
 * 
 * Official Documentation:
 * - API Reference: https://opensubtitles.stoplight.io/docs/opensubtitles-api
 * - Getting Started: https://opensubtitles.stoplight.io/docs/opensubtitles-api/e3750fd63a100-getting-started
 * - Vietnamese Guide: https://apidog.com/vi/blog/opensubtitles-api-vi/
 * 
 * Authentication Methods (both supported):
 * 1. API Key: Direct API key in header (simpler, recommended)
 * 2. JWT Token: Username/password login to obtain token (fallback)
 * 
 * Features:
 * - Query-based search with language and IMDB ID filtering
 * - Dual authentication support (API key or username/password)
 * - Automatic subtitle download and extraction
 * 
 * Requirements:
 * - Either API Key OR Username+Password must be set in SubtitlePreferences
 * - User-Agent header is required by the API
 * - API has rate limits (check documentation for details)
 */
class OpenSubtitlesProvider(private val context: Context) : SubtitleProvider {
    
    companion object {
        private const val TAG = "OpenSubtitlesProvider"
        private const val API_BASE_URL = "https://api.opensubtitles.com/api/v1"
        // User-Agent format as per OpenSubtitles API documentation
        // Format: AppName vVersion (contact email or website)
        private const val USER_AGENT = "LAMPA v1.0"
        private const val SUBTITLE_CACHE_DIR = "subtitle_cache"
        
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
    
    // JWT token caching for username/password authentication
    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0
    
    override fun getName(): String = "OpenSubtitles"
    
    override fun isEnabled(): Boolean {
        val apiKey = SubtitlePreferences.getApiKey(context)
        val username = SubtitlePreferences.getUsername(context)
        val password = SubtitlePreferences.getPassword(context)
        
        // Enabled if either API key OR username+password is configured
        return !apiKey.isNullOrEmpty() || 
               (!username.isNullOrEmpty() && !password.isNullOrEmpty())
    }
    
    /**
     * Get authentication token for API requests
     * Priority: API Key > JWT Token (from username/password)
     */
    private suspend fun getAuthToken(): String? = withContext(Dispatchers.IO) {
        try {
            // First, try to use API key if available
            val apiKey = SubtitlePreferences.getApiKey(context)
            if (!apiKey.isNullOrEmpty()) {
                Log.d(TAG, "Using API key authentication")
                return@withContext apiKey
            }
            
            // Fall back to username/password JWT authentication
            val username = SubtitlePreferences.getUsername(context)
            val password = SubtitlePreferences.getPassword(context)
            
            if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
                Log.e(TAG, "No credentials configured")
                return@withContext null
            }
            
            // Check if we have a valid cached JWT token
            if (cachedToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                Log.d(TAG, "Using cached JWT token")
                return@withContext cachedToken
            }
            
            Log.d(TAG, "Authenticating with username/password to get JWT token")
            
            // Login to get JWT token
            val requestBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }.toString()
            
            val mediaType = MediaType.parse("application/json")
            val request = Request.Builder()
                .url("$API_BASE_URL/login")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(RequestBody.create(mediaType, requestBody))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body()?.string()
                Log.e(TAG, "Authentication failed: ${response.code()} - $errorBody")
                return@withContext null
            }
            
            val body = response.body()?.string() ?: return@withContext null
            val jsonResponse = JSONObject(body)
            
            val token = jsonResponse.optString("token")
            if (token.isEmpty()) {
                Log.e(TAG, "No token in authentication response")
                return@withContext null
            }
            
            // Cache token (valid for 24 hours typically)
            cachedToken = token
            tokenExpiryTime = System.currentTimeMillis() + (23 * 60 * 60 * 1000) // 23 hours
            
            Log.d(TAG, "JWT authentication successful")
            return@withContext token
            
        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            return@withContext null
        }
    }
    
    override suspend fun search(
        query: String,
        imdbId: String?,
        language: String
    ): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching subtitles for: $query (IMDB: $imdbId, Lang: $language)")
            SubtitleDebugHelper.logInfo(getName(), "Starting search: query='$query', imdbId='$imdbId', lang='$language'")
            
            if (!isEnabled()) {
                Log.w(TAG, "Provider not enabled - missing credentials")
                SubtitleDebugHelper.logWarning(getName(), "Provider not enabled - missing credentials")
                return@withContext emptyList()
            }
            
            SubtitleDebugHelper.logDebug(getName(), "Provider is enabled, attempting authentication")
            
            // Get authentication token (API key or JWT token)
            val authToken = getAuthToken() ?: run {
                Log.e(TAG, "Failed to get authentication token")
                SubtitleDebugHelper.logError(getName(), "Failed to get authentication token - check API key or username/password")
                return@withContext emptyList()
            }
            
            SubtitleDebugHelper.logDebug(getName(), "Authentication successful")
            
            // Build search parameters with proper URL encoding
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchParams = buildString {
                append("?query=$encodedQuery")
                append("&languages=$language")
                if (!imdbId.isNullOrEmpty()) {
                    val encodedImdbId = java.net.URLEncoder.encode(imdbId, "UTF-8")
                    append("&imdb_id=$encodedImdbId")
                }
            }
            
            val searchUrl = "$API_BASE_URL/subtitles$searchParams"
            Log.d(TAG, "Calling OpenSubtitles API: $searchUrl")
            SubtitleDebugHelper.logInfo(getName(), "API URL: $searchUrl")
            
            val request = Request.Builder()
                .url(searchUrl)
                .header("Api-Key", authToken)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            
            Log.d(TAG, "Making search request to OpenSubtitles...")
            SubtitleDebugHelper.logDebug(getName(), "Sending HTTP request...")
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "OpenSubtitles API response code: ${response.code()}")
            SubtitleDebugHelper.logInfo(getName(), "HTTP response code: ${response.code()}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body()?.string()
                Log.e(TAG, "Search failed: ${response.code()} - $errorBody")
                SubtitleDebugHelper.logError(getName(), "Search failed: HTTP ${response.code()} - $errorBody")
                return@withContext emptyList()
            }
            
            val body = response.body()?.string() ?: run {
                SubtitleDebugHelper.logError(getName(), "Response body is empty")
                return@withContext emptyList()
            }
            
            SubtitleDebugHelper.logDebug(getName(), "Response body length: ${body.length} bytes")
            
            val jsonResponse = JSONObject(body)
            
            val results = mutableListOf<SubtitleSearchResult>()
            
            if (jsonResponse.has("data")) {
                val dataArray = jsonResponse.getJSONArray("data")
                SubtitleDebugHelper.logInfo(getName(), "Found ${dataArray.length()} subtitle entries in response")
                
                for (i in 0 until dataArray.length().coerceAtMost(5)) {
                    val item = dataArray.getJSONObject(i)
                    val attributes = item.getJSONObject("attributes")
                    val files = attributes.optJSONArray("files")
                    
                    if (files != null && files.length() > 0) {
                        val file = files.getJSONObject(0)
                        val fileId = file.optInt("file_id", 0)
                        
                        if (fileId > 0) {
                            val releaseName = attributes.optString("release", "Unknown")
                            results.add(SubtitleSearchResult(
                                id = fileId.toString(),
                                name = releaseName,
                                language = attributes.optString("language", language),
                                downloads = attributes.optInt("download_count", 0),
                                rating = attributes.optDouble("ratings", 0.0).toFloat(),
                                downloadUrl = "$API_BASE_URL/download",
                                provider = getName()
                            ))
                            SubtitleDebugHelper.logDebug(getName(), "Added result: fileId=$fileId, release='$releaseName'")
                        }
                    }
                }
            } else {
                SubtitleDebugHelper.logWarning(getName(), "Response has no 'data' field")
            }
            
            Log.d(TAG, "Found ${results.size} results")
            SubtitleDebugHelper.logInfo(getName(), "Search completed with ${results.size} results")
            return@withContext results
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching subtitles", e)
            SubtitleDebugHelper.logError(getName(), "Exception during search: ${e.message}", e)
            return@withContext emptyList()
        }
    }
    
    override suspend fun download(result: SubtitleSearchResult): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading subtitle: ${result.name}")
            SubtitleDebugHelper.logInfo(getName(), "Starting download: name='${result.name}', id='${result.id}'")
            
            // Get authentication token (API key or JWT token)
            val authToken = getAuthToken() ?: run {
                Log.e(TAG, "Failed to get authentication token")
                SubtitleDebugHelper.logError(getName(), "Failed to get authentication token for download")
                return@withContext null
            }
            
            // Create download request
            val requestBody = JSONObject().apply {
                put("file_id", result.id.toInt())
            }.toString()
            
            SubtitleDebugHelper.logDebug(getName(), "Requesting download link for file_id=${result.id}")
            
            val mediaType = MediaType.parse("application/json")
            val request = Request.Builder()
                .url(result.downloadUrl)
                .header("Api-Key", authToken)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(RequestBody.create(mediaType, requestBody))
                .build()
            
            val response = httpClient.newCall(request).execute()
            SubtitleDebugHelper.logInfo(getName(), "Download request response: HTTP ${response.code()}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body()?.string()
                Log.e(TAG, "Download request failed: ${response.code()} - $errorBody")
                SubtitleDebugHelper.logError(getName(), "Download request failed: HTTP ${response.code()} - $errorBody")
                return@withContext null
            }
            
            val body = response.body()?.string() ?: run {
                SubtitleDebugHelper.logError(getName(), "Download response body is empty")
                return@withContext null
            }
            
            val jsonResponse = JSONObject(body)
            
            val downloadLink = jsonResponse.optString("link")
            if (downloadLink.isEmpty()) {
                Log.e(TAG, "No download link in response")
                SubtitleDebugHelper.logError(getName(), "No download link in API response")
                return@withContext null
            }
            
            SubtitleDebugHelper.logDebug(getName(), "Got download link: $downloadLink")
            
            // Download the actual subtitle file
            val fileRequest = Request.Builder()
                .url(downloadLink)
                .build()
            
            val fileResponse = httpClient.newCall(fileRequest).execute()
            SubtitleDebugHelper.logInfo(getName(), "File download response: HTTP ${fileResponse.code()}")
            
            if (!fileResponse.isSuccessful) {
                Log.e(TAG, "File download failed: ${fileResponse.code()}")
                SubtitleDebugHelper.logError(getName(), "File download failed: HTTP ${fileResponse.code()}")
                return@withContext null
            }
            
            val fileBody = fileResponse.body() ?: run {
                SubtitleDebugHelper.logError(getName(), "File response body is empty")
                return@withContext null
            }
            
            // Create cache directory
            val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
            if (!cacheDir.exists()) {
                val created = cacheDir.mkdirs()
                SubtitleDebugHelper.logDebug(getName(), "Cache directory created: $created")
            }
            
            // Save subtitle file
            val timestamp = System.currentTimeMillis()
            val subtitleFile = File(cacheDir, "subtitle_${result.language}_${timestamp}.srt")
            
            // Handle potential gzip compression
            val inputStream = fileBody.byteStream()
            val finalStream = if (fileResponse.header("Content-Encoding") == "gzip") {
                SubtitleDebugHelper.logDebug(getName(), "Response is gzip compressed")
                GZIPInputStream(inputStream)
            } else {
                inputStream
            }
            
            var bytesWritten = 0L
            FileOutputStream(subtitleFile).use { output ->
                finalStream.use { input ->
                    bytesWritten = input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Subtitle downloaded successfully: ${subtitleFile.absolutePath}")
            SubtitleDebugHelper.logInfo(getName(), "Download successful: ${subtitleFile.absolutePath} (${bytesWritten} bytes)")
            return@withContext subtitleFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subtitle", e)
            SubtitleDebugHelper.logError(getName(), "Exception during download: ${e.message}", e)
            return@withContext null
        }
    }
}
