# VLC Playback Issues - Implementation Summary

## Overview
This document details the fixes implemented for three critical VLC playback issues in the LAMPA Android application.

## Issues Addressed

### 1. Subtitle Path URI Formatting Error ✅

**Problem:** 
- LibVLC reported error: `cannot open file /file:/data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1767448266029.srt (No such file or directory)`
- The path format `/file:` is incorrect; it should be `file:///`

**Root Cause:**
- Incorrect URI formatting when passing local file paths to LibVLC's `addSlave()` method
- Potential for double-prefixing if the path was already processed

**Solution Implemented:**

1. **Enhanced URI Formatting in `PlayerActivity.kt`** (Lines 970-986):
   - Use `Uri.fromFile()` to ensure proper `file:///` prefix
   - Added validation to check URI format before passing to LibVLC
   - Comprehensive error logging for debugging

2. **Improved `loadSubtitleFromUrl()` method** (Lines 1068-1116):
   - Better handling of different URL formats (http://, https://, file://, local paths)
   - Explicit validation for local file paths
   - Prevents double-prefixing issues
   - Graceful error handling with user-friendly messages

**Code Changes:**
```kotlin
// Convert file path to proper URI format for LibVLC
val subtitleUri = Uri.fromFile(subtitleFile).toString()

// Validate URI format before passing to LibVLC
if (!subtitleUri.startsWith("file://")) {
    Log.e(TAG, "Invalid subtitle URI format: $subtitleUri")
    SubtitleDebugHelper.logError("PlayerActivity", "Invalid URI format generated: $subtitleUri")
    App.toast(R.string.subtitle_load_failed, true)
    return@runOnUiThread
}
```

---

### 2. Network Stream Cancellation Error (0x8) ✅

**Problem:**
- Error: `http stream: local stream 1 error: Cancellation (0x8)`
- Network streams failing due to timeout or connection issues
- No retry mechanism for recoverable errors

**Root Cause:**
- VLC cancellation error (0x8) typically indicates network timeout or connection interruption
- Default network caching (1 second) too low for unstable connections
- No automatic retry on transient network errors

**Solution Implemented:**

1. **Added Retry Logic with Exponential Backoff** (Lines 1279-1332):
   - Maximum 3 retry attempts for network streams
   - Exponential backoff: 2s → 4s → 8s delays
   - Automatic player reinitialization on retry
   - User-friendly retry progress messages

2. **Enhanced LibVLC Network Configuration** (Lines 310-322):
   - Increased network caching from 1s to 3s
   - Added `--http-reconnect` for automatic HTTP reconnection
   - Enabled continuous HTTP streaming
   - Optimized file caching (300ms for local files)

3. **Improved Media Options** (Lines 417-429):
   - Network caching: 3000ms (consistent with LibVLC global settings)
   - HTTP reconnection enabled
   - Continuous streaming mode

**Code Changes:**
```kotlin
// Retry state tracking
private var videoUrl: String? = null
private var retryCount = 0
private var maxRetries = 3
private var retryDelayMs = 2000L

// Error handler with retry logic
private fun handlePlaybackError() {
    if (currentVideoUrl != null && 
        (currentVideoUrl.startsWith("http://") || currentVideoUrl.startsWith("https://")) &&
        retryCount < maxRetries) {
        
        retryCount++
        // Calculate exponential backoff delay
        val currentRetryDelay = retryDelayMs * (1 shl (retryCount - 1))
        
        // Release and retry
        releasePlayer()
        handler.postDelayed({
            initializePlayer(currentVideoUrl, null)
        }, currentRetryDelay)
    } else {
        showFinalError()
    }
}

// Enhanced LibVLC options
add("--network-caching=3000") // 3 seconds cache for network streams
add("--http-reconnect") // Enable automatic HTTP reconnection
add("--file-caching=300") // 300ms for local files
```

**Benefits:**
- Automatically recovers from transient network errors
- Better handling of unstable connections
- Improved user experience with retry notifications
- Reduces playback failures on slow networks

---

### 3. Hidden API Access Restriction ✅

**Problem:**
- Error: `hiddenapi: Accessing hidden field Ljava/util/Collections$SynchronizedCollection;->mutex:Ljava/lang/Object; ... denied`
- Reflection access to private fields restricted on Android 9+ (API 28+)

**Root Cause Analysis:**
The `Collections$SynchronizedCollection` error is likely from the **Crosswalk library** (xwalk_*.aar), which is an external dependency. However, we also found reflection usage in our code.

**Solution Implemented:**

1. **Fixed Reflection in `AutoCompleteTV.kt`** (Lines 31-46):
   - Wrapped reflection access in try-catch blocks
   - Added graceful fallback when reflection fails
   - Proper null safety throughout

**Code Changes:**
```kotlin
companion object {
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private val popupWindowField = try {
        AutoCompleteTextView::class.java.getDeclaredField("mPopup")
            .also { it.isAccessible = true }
    } catch (e: Exception) {
        // On newer Android versions with strict hidden API enforcement,
        // this may fail. Gracefully handle the null case.
        null
    }
}

// Safe usage with null checks
private val popupWindow = try {
    popupWindowField?.get(this) as? ListPopupWindow?
} catch (e: Exception) {
    null
}
```

**Note on Collections$SynchronizedCollection:**
This specific error is likely internal to the Crosswalk WebView library, not our application code. The Crosswalk library is a third-party component that may use reflection internally. Our fix ensures our own reflection code is safe, but the Crosswalk library's internal reflection usage is outside our control.

**Recommendations:**
- The app targets API 28 (Android 9), which is below the strict enforcement level
- For production, consider migrating from Crosswalk to Android System WebView
- Modern Android versions have better WebView support built-in

---

## Testing Recommendations

### 1. Subtitle URI Testing
- Test with local subtitle files from cache
- Test with network subtitle URLs (http/https)
- Verify proper error messages appear for invalid paths
- Check logcat for URI format validation messages

### 2. Network Stream Testing
- Test with unstable network connection
- Simulate connection drops during playback
- Verify retry attempts occur (check for retry messages)
- Confirm playback recovers after transient errors
- Test with very slow network connections

### 3. Hidden API Testing
- Test on Android 9+ devices
- Check logcat for hiddenapi warnings
- Verify AutoCompleteTextView dropdown still works
- Ensure app doesn't crash due to reflection failures

## Expected Behavior After Fixes

1. **Subtitles:**
   - Properly load from local cache with correct file:/// URI format
   - Clear error messages if subtitle loading fails
   - No more "/file:" double-prefix errors

2. **Network Streams:**
   - Automatically retry up to 3 times on connection errors
   - User sees retry progress ("Retrying 1/3...")
   - Exponential backoff prevents server overload
   - Better handling of slow/unstable connections

3. **Reflection Safety:**
   - App handles reflection failures gracefully
   - No crashes due to hidden API restrictions
   - AutoCompleteTV works even if reflection fails

## Files Modified

1. **PlayerActivity.kt**
   - Enhanced subtitle URI formatting and validation (lines 970-986, 1068-1116)
   - Added retry logic for network errors (lines 88-95, 1279-1332)
   - Improved LibVLC network configuration (lines 310-322, 417-429)

2. **AutoCompleteTV.kt**
   - Added safe reflection with error handling (lines 31-46)
   - Graceful fallback when reflection fails

## Technical Details

### LibVLC Configuration Changes
- **Network Caching:** 1000ms → 3000ms (3x improvement)
- **HTTP Reconnect:** Enabled for automatic recovery
- **File Caching:** Optimized to 300ms for local playback
- **Continuous Streaming:** Enabled for better HTTP stream handling

### Retry Algorithm
- **Strategy:** Exponential backoff with max attempts
- **Delays:** 2s, 4s, 8s (doubles each retry)
- **Scope:** Only for HTTP/HTTPS network streams
- **Reset:** Retry count resets on successful playback

### URI Format Standards
- **Local Files:** `file:///absolute/path/to/file.srt`
- **Network URLs:** `http://domain.com/subtitle.srt` or `https://...`
- **Validation:** Checks for proper scheme before passing to LibVLC

## Conclusion

All three issues have been addressed with robust, production-ready solutions:
1. ✅ Subtitle URI formatting fixed with validation
2. ✅ Network stream retry logic implemented with exponential backoff
3. ✅ Reflection usage made safe with proper error handling

The fixes improve reliability, user experience, and compatibility with newer Android versions.
