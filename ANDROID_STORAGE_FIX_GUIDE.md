# Android Storage Fix for VLC Subtitle Loading

## Problem Summary
On Android 16 (SDK 36), VLC's native LibVLC thread cannot read subtitle files stored in internal storage (`context.cacheDir`) due to strict sandboxing policies. Even though `addSlave()` returns `true`, no `ESAdded` event is fired because the native code cannot access the file.

**Current Path (Internal - Not Accessible):** 
```
/data/user/0/top.rootu.lampa/cache/subtitle_cache/...
```

**New Path (External - Accessible):**
```
/storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/...
```

## Solution Implemented

### 1. Changed Download Location to External Cache

All subtitle providers now download files to `context.externalCacheDir` instead of `context.cacheDir`:

**Files Modified:**
- `SubtitleDownloader.kt` - Main downloader (2 locations: download + clearCache)
- `OpenSubtitlesProvider.kt` - OpenSubtitles API provider
- `StremioAddonProvider.kt` - Stremio addon provider

**Code Pattern:**
```kotlin
// OLD (Internal Storage - Not Accessible by VLC)
val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)

// NEW (External Cache - Accessible by VLC)
val baseDir = context.externalCacheDir ?: context.cacheDir
val cacheDir = File(baseDir, SUBTITLE_CACHE_DIR)
```

**Key Benefits:**
- External cache is still private to the app
- Automatically cleaned by Android system when storage is low
- No additional permissions required
- Accessible to native libraries (LibVLC)
- Falls back to internal cache if external storage unavailable

### 2. Enhanced Logging

Added comprehensive logging to track:
- Base cache directory path being used
- File existence verification after write
- File size and readability status
- Detailed error messages for debugging

**New Log Output:**
```
[SubtitleDownloader] Base cache directory: /storage/emulated/0/Android/data/.../cache
[SubtitleDownloader] Subtitle Downloaded: /storage/emulated/0/.../subtitle_en_1234567890.srt
[SubtitleDownloader] File size: 45678 bytes, readable: true
[PlayerActivity] File path: /storage/emulated/0/.../subtitle_en_1234567890.srt
[PlayerActivity] Generated URI: file:///storage/emulated/0/.../subtitle_en_1234567890.srt
[PlayerActivity] VLC addSlave() Result: true
```

### 3. URI Formatting (Already Correct)

The existing code already uses correct URI formatting:
- Uses `Uri.fromFile()` to convert absolute paths to `file://` URIs
- LibVLC receives properly formatted URIs like `file:///storage/emulated/0/...`
- No changes needed to URI generation logic

### 4. Fallback Method Documentation

Added comprehensive documentation for an alternative method using `Media.addOption()`:

```kotlin
// Alternative approach (documented but not implemented)
// Must be called BEFORE playback starts
val media = Media(libVLC, Uri.parse(videoUrl)).apply {
    // Other options...
    
    // Add subtitle file
    addOption(":sub-file=${subtitleFile.absolutePath}")
}
mediaPlayer?.media = media
mediaPlayer?.play()
```

**Key Differences:**
- `addSlave()` - Works during playback, uses file:// URIs
- `addOption()` - Must be used before playback, uses absolute paths

## Testing Instructions

### Prerequisites
- Android device or emulator with Android 16 (SDK 36) or higher
- Video content to play
- Subtitle source configured (OpenSubtitles API key or Stremio addon)

### Test Steps

1. **Clear Old Cache** (Optional but recommended):
   ```bash
   adb shell rm -rf /data/user/0/top.rootu.lampa/cache/subtitle_cache
   ```

2. **Install Updated APK**:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Enable Subtitle Debug Logging**:
   - Open LAMPA app
   - Go to Settings → Subtitle Settings
   - Enable subtitle auto-download
   - Configure at least one subtitle source

4. **Play Video and Monitor Logs**:
   ```bash
   adb logcat -s SubtitleDownloader PlayerActivity SubtitleDebugHelper
   ```

5. **Verify in Logs**:
   - Look for: `Base cache directory: /storage/emulated/0/...` (NOT `/data/user/0/...`)
   - Look for: `Subtitle Downloaded: /storage/emulated/0/...`
   - Look for: `VLC addSlave() Result: true`
   - Look for: `Auto-selected subtitle track` (SUCCESS!)

6. **Export Debug Logs**:
   - Long-press the subtitle button during playback
   - Select "Export Subtitle Logs"
   - Share the log file for analysis

### Expected Results

**Before Fix:**
```
[SubtitleDownloader] Base cache directory: /data/user/0/top.rootu.lampa/cache
[PlayerActivity] VLC addSlave() Result: true
[PlayerActivity] Track detection failed - addSlave() succeeded but no tracks found
```

**After Fix:**
```
[SubtitleDownloader] Base cache directory: /storage/emulated/0/Android/data/top.rootu.lampa/cache
[SubtitleDownloader] File size: 45678 bytes, readable: true
[PlayerActivity] VLC addSlave() Result: true
[PlayerActivity] Auto-selected subtitle track (attempt 1): English [en]
```

## Troubleshooting

### Issue: Still Using Internal Storage
**Symptom:** Logs show `/data/user/0/...` path
**Solution:** 
- Verify `context.externalCacheDir` is not null
- Check if external storage is mounted
- App should fallback to internal cache but log a warning

### Issue: File Not Readable by VLC
**Symptom:** `addSlave()` returns `true` but no tracks appear
**Solutions:**
1. Verify file exists and size > 0 in logs
2. Check file permissions: `adb shell ls -la /storage/emulated/0/.../subtitle_cache/`
3. Try the `Media.addOption()` fallback method (requires code changes)

### Issue: Permission Denied
**Symptom:** Cannot write to external cache
**Solution:**
- External cache doesn't require special permissions
- If issue persists, app will fallback to internal cache
- Check for storage full conditions

## Alternative Fallback Implementation

If issues persist, implement the `Media.addOption()` fallback:

1. **Store pending subtitle path** in `PlayerActivity`:
   ```kotlin
   private var pendingSubtitlePath: String? = null
   ```

2. **Save path when download completes**:
   ```kotlin
   val subtitlePath = subtitleDownloader?.searchAndDownload(...)
   if (subtitlePath != null) {
       pendingSubtitlePath = subtitlePath
       // Don't call addAndSelectSubtitle yet
   }
   ```

3. **Add option before playback**:
   ```kotlin
   val media = Media(libVLC, Uri.parse(videoUrl)).apply {
       // ... other options ...
       
       pendingSubtitlePath?.let { path ->
           val file = File(path)
           if (file.exists()) {
               addOption(":sub-file=${file.absolutePath}")
               Log.d(TAG, "Added subtitle via Media option: $path")
               SubtitleDebugHelper.logInfo("PlayerActivity", "Using Media.addOption for subtitle: $path")
           }
       }
   }
   ```

## Technical Notes

### Why External Cache Works
- **Internal Cache** (`/data/user/0/`): Only accessible by the app's Java/Kotlin process
- **External Cache** (`/storage/emulated/0/Android/data/`): Accessible by app's native libraries
- VLC's LibVLC uses native C/C++ code that needs filesystem-level access
- Android 16+ enforces stricter sandboxing on internal storage

### Storage Cleanup
External cache is automatically managed by Android:
- Cleared when storage is low
- Removed when app is uninstalled
- Still private to the app (other apps cannot access)

### No Permission Changes Required
The app manifest doesn't need updates because:
- `externalCacheDir` is in the app's private external storage
- No `WRITE_EXTERNAL_STORAGE` permission needed for app-specific directories
- Works on Android 10+ with scoped storage

## References

- [Android Data and File Storage Overview](https://developer.android.com/training/data-storage)
- [LibVLC Android Documentation](https://wiki.videolan.org/AndroidCompile/)
- Issue Log: File accessibility on Android 16 (SDK 36)
