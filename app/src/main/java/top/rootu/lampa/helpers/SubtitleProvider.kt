package top.rootu.lampa.helpers

/**
 * SubtitleProvider interface for implementing multiple subtitle sources
 * 
 * Each provider should implement subtitle search and download functionality
 * for their respective service (OpenSubtitles, SubSource, SubDL, SubHero, etc.)
 */
interface SubtitleProvider {
    
    /**
     * Get the name of this subtitle provider
     */
    fun getName(): String
    
    /**
     * Check if this provider is enabled and configured
     */
    fun isEnabled(): Boolean
    
    /**
     * Search for subtitles matching the given query
     * 
     * @param query The search query (typically video filename)
     * @param imdbId Optional IMDB ID for more accurate results
     * @param language Desired subtitle language (ISO 639-1 code, e.g., "en", "vi")
     * @return List of subtitle search results, or empty list if none found
     */
    suspend fun search(query: String, imdbId: String?, language: String): List<SubtitleSearchResult>
    
    /**
     * Download a subtitle file from the given result
     * 
     * @param result The subtitle search result to download
     * @return Path to the downloaded subtitle file, or null on error
     */
    suspend fun download(result: SubtitleSearchResult): String?
}

/**
 * Data class representing a subtitle search result
 */
data class SubtitleSearchResult(
    val id: String,
    val name: String,
    val language: String,
    val downloads: Int = 0,
    val rating: Float = 0f,
    val downloadUrl: String,
    val provider: String
)
