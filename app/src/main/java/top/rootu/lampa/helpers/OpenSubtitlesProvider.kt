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
 * with API Key authentication.
 * 
 * Features:
 * - Query-based search with language and IMDB ID filtering
 * - API key authentication (simpler than username/password)
 * - Automatic subtitle download and extraction
 */
class OpenSubtitlesProvider(private val context: Context) : SubtitleProvider {
    
    companion object {
        private const val TAG = "OpenSubtitlesProvider"
        private const val API_BASE_URL = "https://api.opensubtitles.com/api/v1"
        private const val USER_AGENT = "LAMPA Android v1.0"
        private const val SUBTITLE_CACHE_DIR = "subtitle_cache"
        
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
    
    override fun getName(): String = "OpenSubtitles"
    
    override fun isEnabled(): Boolean {
        val apiKey = SubtitlePreferences.getApiKey(context)
        return !apiKey.isNullOrEmpty()
    }
    
    override suspend fun search(
        query: String,
        imdbId: String?,
        language: String
    ): List<SubtitleSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching subtitles for: $query (IMDB: $imdbId, Lang: $language)")
            
            if (!isEnabled()) {
                Log.w(TAG, "Provider not enabled - missing credentials")
                return@withContext emptyList()
            }
            
            // Get API key
            val apiKey = SubtitlePreferences.getApiKey(context) ?: run {
                Log.e(TAG, "No API key configured")
                return@withContext emptyList()
            }
            
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
            
            val request = Request.Builder()
                .url("$API_BASE_URL/subtitles$searchParams")
                .header("Api-Key", apiKey)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Search failed: ${response.code()}")
                return@withContext emptyList()
            }
            
            val body = response.body()?.string() ?: return@withContext emptyList()
            val jsonResponse = JSONObject(body)
            
            val results = mutableListOf<SubtitleSearchResult>()
            
            if (jsonResponse.has("data")) {
                val dataArray = jsonResponse.getJSONArray("data")
                
                for (i in 0 until dataArray.length().coerceAtMost(5)) {
                    val item = dataArray.getJSONObject(i)
                    val attributes = item.getJSONObject("attributes")
                    val files = attributes.optJSONArray("files")
                    
                    if (files != null && files.length() > 0) {
                        val file = files.getJSONObject(0)
                        val fileId = file.optInt("file_id", 0)
                        
                        if (fileId > 0) {
                            results.add(SubtitleSearchResult(
                                id = fileId.toString(),
                                name = attributes.optString("release", "Unknown"),
                                language = attributes.optString("language", language),
                                downloads = attributes.optInt("download_count", 0),
                                rating = attributes.optDouble("ratings", 0.0).toFloat(),
                                downloadUrl = "$API_BASE_URL/download",
                                provider = getName()
                            ))
                        }
                    }
                }
            }
            
            Log.d(TAG, "Found ${results.size} results")
            return@withContext results
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching subtitles", e)
            return@withContext emptyList()
        }
    }
    
    override suspend fun download(result: SubtitleSearchResult): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading subtitle: ${result.name}")
            
            val apiKey = SubtitlePreferences.getApiKey(context) ?: run {
                Log.e(TAG, "No API key configured")
                return@withContext null
            }
            
            // Create download request
            val requestBody = JSONObject().apply {
                put("file_id", result.id.toInt())
            }.toString()
            
            val mediaType = MediaType.parse("application/json")
            val request = Request.Builder()
                .url(result.downloadUrl)
                .header("Api-Key", apiKey)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(RequestBody.create(mediaType, requestBody))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Download request failed: ${response.code()}")
                return@withContext null
            }
            
            val body = response.body()?.string() ?: return@withContext null
            val jsonResponse = JSONObject(body)
            
            val downloadLink = jsonResponse.optString("link")
            if (downloadLink.isEmpty()) {
                Log.e(TAG, "No download link in response")
                return@withContext null
            }
            
            // Download the actual subtitle file
            val fileRequest = Request.Builder()
                .url(downloadLink)
                .build()
            
            val fileResponse = httpClient.newCall(fileRequest).execute()
            
            if (!fileResponse.isSuccessful) {
                Log.e(TAG, "File download failed: ${fileResponse.code()}")
                return@withContext null
            }
            
            val fileBody = fileResponse.body() ?: return@withContext null
            
            // Create cache directory
            val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Save subtitle file
            val timestamp = System.currentTimeMillis()
            val subtitleFile = File(cacheDir, "subtitle_${result.language}_${timestamp}.srt")
            
            // Handle potential gzip compression
            val inputStream = fileBody.byteStream()
            val finalStream = if (fileResponse.header("Content-Encoding") == "gzip") {
                GZIPInputStream(inputStream)
            } else {
                inputStream
            }
            
            FileOutputStream(subtitleFile).use { output ->
                finalStream.use { input ->
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
