# Android Storage Fix - Quick Reference

## 🎯 What Was Fixed

**Problem:** VLC's native LibVLC cannot read subtitles from Android's internal storage on Android 16+ (SDK 36+)
**Solution:** Changed download location to external cache directory which is accessible to native libraries

## 📝 Files Changed

### 1. SubtitleDownloader.kt
- **Line 160**: `context.externalCacheDir ?: context.cacheDir`
- **Line 221**: `context.externalCacheDir ?: context.cacheDir`
- Added logging and file verification

### 2. OpenSubtitlesProvider.kt
- **Line 341**: `context.externalCacheDir ?: context.cacheDir`

### 3. StremioAddonProvider.kt
- **Line 272**: `context.externalCacheDir ?: context.cacheDir`

### 4. PlayerActivity.kt
- Added comprehensive fallback documentation (lines 1382-1431)

## 🔍 Key Changes

### Before:
```kotlin
val cacheDir = File(context.cacheDir, SUBTITLE_CACHE_DIR)
// Path: /data/user/0/top.rootu.lampa/cache/subtitle_cache/
```

### After:
```kotlin
val cacheDir = File(context.externalCacheDir ?: context.cacheDir, SUBTITLE_CACHE_DIR)
// Path: /storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/
```

## ✅ Expected Behavior

### Log Output After Fix:
```
[SubtitleDownloader] Base cache directory: /storage/emulated/0/Android/data/top.rootu.lampa/cache
[SubtitleDownloader] Subtitle Downloaded: /storage/emulated/0/.../subtitle_en_1234567890.srt
[SubtitleDownloader] File size: 45678 bytes, readable: true
[PlayerActivity] VLC addSlave() Result: true
[PlayerActivity] Auto-selected subtitle track (attempt 1): English [en]
```

### User Experience:
1. Video plays
2. Subtitle search triggers automatically (if configured)
3. Subtitle downloads to external cache
4. VLC successfully loads and displays subtitle
5. No "Track detection failed" messages

## 🚀 Testing Commands

```bash
# Clear old cache
adb shell rm -rf /data/user/0/top.rootu.lampa/cache/subtitle_cache

# Monitor logs
adb logcat -s SubtitleDownloader PlayerActivity SubtitleDebugHelper

# Check file location
adb shell ls -la /storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/
```

## 📊 Success Indicators

✅ Subtitle file created in `/storage/emulated/0/...`  
✅ File size > 0 bytes  
✅ File readable: true  
✅ `addSlave()` returns true  
✅ Subtitle track auto-selected  
✅ Subtitles display during playback  

## 🛡️ Safety Features

- **Fallback**: Uses internal cache if external not available
- **Auto-cleanup**: Android clears external cache when storage low
- **No permissions**: Works without WRITE_EXTERNAL_STORAGE
- **Private**: Files only accessible by this app

## 🔧 Alternative Method (If Needed)

If `addSlave()` still fails, use `Media.addOption()` before playback:

```kotlin
val media = Media(libVLC, Uri.parse(videoUrl)).apply {
    // Add subtitle before playback
    addOption(":sub-file=/storage/emulated/0/.../subtitle.srt")
}
```

See `PlayerActivity.kt` lines 1382-1431 for full documentation.

## 📚 References

- **Full Guide**: `ANDROID_STORAGE_FIX_GUIDE.md`
- **Android Docs**: [Data and File Storage Overview](https://developer.android.com/training/data-storage)
- **LibVLC**: [Android Compilation Guide](https://wiki.videolan.org/AndroidCompile/)
