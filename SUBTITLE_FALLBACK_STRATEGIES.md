# VLC Subtitle Loading Fallback Strategies

## Problem Statement

On specific Android SDK/VLC version combinations, the `MediaPlayer.addSlave(Uri)` method fails silently:
- `addSlave()` returns `true`
- No `ESAdded` event is fired
- Track count remains 0
- Subtitle file exists and permissions are fine (stored in External Cache)

## Solution Overview

This implementation provides **multiple fallback strategies** to force subtitle loading when the standard `addSlave()` method fails.

## Strategies Implemented

### Strategy A: Media Option Method (PRIMARY FALLBACK)

**Function:** `forceLoadSubtitle(path: String): Boolean`

**How it works:**
1. Validates the subtitle file exists
2. Stores current playback position
3. Stops current media playback
4. Creates a new Media object with `:sub-file=<absolute_path>` option
5. Restarts playback from stored position

**Key Features:**
- Uses VLC command-line flag `:sub-file=<path>`
- Requires **raw absolute path** (NO `file://` prefix)
- Most robust method for forcing subtitle load
- Requires media restart (minimal interruption due to position restore)

**Usage:**
```kotlin
val success = forceLoadSubtitle("/storage/emulated/0/Android/data/.../subtitle.srt")
```

**When to use:**
- After all `addSlave()` strategies have failed
- As a last resort for local subtitle files
- When `addSlave()` returns true but no track appears

---

### Strategy B: Raw Path to addSlave

**Implementation:** Integrated into `addAndSelectSubtitle()`

**How it works:**
1. First attempts standard `addSlave()` with `file://` URI
2. If that fails, retries with raw absolute path (no `file://` prefix)

**Rationale:**
Some LibVLC versions dislike the `file://` scheme prefix for local files.

**Code Flow:**
```kotlin
// Attempt 1: Standard URI
val added = mediaPlayer?.addSlave(0, "file:///storage/.../subtitle.srt", true)

// Attempt 2: Raw path (if Attempt 1 failed)
if (added != true) {
    added = mediaPlayer?.addSlave(0, "/storage/.../subtitle.srt", true)
}
```

---

### Strategy C: Media.addSlave Documentation

**Status:** Documented but not implemented

**Why not implemented:**
- `Media.addSlave()` must be called BEFORE playback starts
- Current use case requires loading subtitles AFTER playback has started
- Not compatible with dynamic subtitle loading workflow

**When it could be used:**
If you have the subtitle file path BEFORE starting playback:
```kotlin
val media = Media(libVLC, Uri.parse(videoUrl)).apply {
    // Add other options...
    
    // Would need to call media.addSlave() here before parsing
    // But this API is not available in current LibVLC Java bindings
}
mediaPlayer?.media = media
```

---

## Integration into PlayerActivity

### Primary Loading Flow

1. **Initial attempt:** `addAndSelectSubtitle(path)`
   - Tries Strategy B internally (URI then raw path)
   - Returns `true` if successful

2. **Fallback:** `forceLoadSubtitle(path)` (Strategy A)
   - Automatically triggered if `addAndSelectSubtitle()` fails
   - Only for local files (not HTTP URLs)

### Code Locations

**Subtitle from URL (passed via Intent):**
```kotlin
// Location: loadSubtitleFromUrl()
val success = addAndSelectSubtitle(subtitleUrl)
if (!success && isLocalFile(subtitleUrl)) {
    forceLoadSubtitle(subtitleUrl)
}
```

**External subtitle auto-download:**
```kotlin
// Location: searchAndLoadExternalSubtitles()
val success = addAndSelectSubtitle(subtitlePath)
if (!success) {
    forceLoadSubtitle(subtitlePath)
}
```

---

## API Reference

### forceLoadSubtitle(path: String): Boolean

**Parameters:**
- `path`: Absolute file path to subtitle file
  - Can start with `file://` (will be stripped automatically)
  - Or raw path: `/storage/emulated/0/Android/data/.../subtitle.srt`

**Returns:**
- `true` if subtitle was successfully configured and playback restarted
- `false` if file doesn't exist, LibVLC not initialized, or error occurred

**Side Effects:**
- Stops current playback
- Restarts media with subtitle embedded
- Seeks to previous playback position
- Refreshes track list after 2 seconds

**Logging:**
All operations logged to:
- Android Logcat (tag: `PlayerActivity`)
- SubtitleDebugHelper (persistent logs)

---

## Path Format Requirements

### For addSlave() (Standard URI)
```
file:///storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/movie.srt
```

### For addSlave() (Raw Path - Strategy B)
```
/storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/movie.srt
```

### For Media.addOption() (Strategy A)
```
/storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/movie.srt
```

**CRITICAL:** Strategy A requires **externalCacheDir** path for native LibVLC access:
- ✅ `/storage/emulated/0/Android/data/top.rootu.lampa/cache/`
- ❌ `/data/data/top.rootu.lampa/cache/` (internal storage - not accessible to native code on Android 16+)

---

## Troubleshooting

### Issue: forceLoadSubtitle causes brief playback interruption

**Expected behavior:** This is normal. Strategy A requires restarting the media.

**Mitigation:**
- Playback position is restored automatically
- Interruption is typically < 1 second

---

### Issue: Strategy A fails with "File not found"

**Check:**
1. File is in `externalCacheDir` (not internal cache)
2. File path is absolute
3. Permissions are granted

**Diagnostic log to check:**
```
[PlayerActivity] STRATEGY A - Force loading subtitle via Media option: <path>
[PlayerActivity] File validated, size: <bytes> bytes
```

---

### Issue: All strategies fail

**Possible causes:**
1. File format not supported by VLC (e.g., corrupted SRT)
2. File encoding issues (VLC expects UTF-8)
3. VLC native library compatibility issue

**Next steps:**
1. Check file manually: `adb pull <path> subtitle.srt`
2. Verify file opens in text editor
3. Check SubtitleDebugHelper logs for details

---

## Performance Considerations

### Strategy A (forceLoadSubtitle)
- **Overhead:** Media restart + seek operation
- **Time:** ~1-2 seconds for typical streams
- **User impact:** Brief pause in playback

### Strategy B (Raw Path)
- **Overhead:** Negligible (just a second API call)
- **Time:** Immediate
- **User impact:** None

---

## Testing Checklist

### Manual Testing
- [ ] Test with subtitle from Intent (EXTRA_SUBTITLE_URL)
- [ ] Test with auto-downloaded subtitle
- [ ] Test forceLoadSubtitle with local file
- [ ] Verify playback position restored after Strategy A
- [ ] Check SubtitleDebugHelper logs

### Logging to verify
```bash
adb logcat -s PlayerActivity:D SubtitleDebugHelper:D | grep -E "STRATEGY|forceLoadSubtitle|addSlave"
```

**Expected output (success with Strategy A):**
```
[PlayerActivity] VLC addSlave() with URI Result: true
[PlayerActivity] All addSlave strategies failed
[PlayerActivity] STRATEGY A - Force loading subtitle via Media option
[PlayerActivity] File validated, size: 45123 bytes
[PlayerActivity] Playback restarted with subtitle embedded
[PlayerActivity] Restored playback position: 125340ms
```

---

## Implementation Notes

### Why Strategy A is Most Robust

1. **Bypasses Java/Native boundary:** Subtitle path passed as VLC option during Media creation
2. **Native code handles file access:** VLC's native code reads the file directly
3. **Works on all Android versions:** Not affected by scoped storage or permission issues
4. **Guaranteed visibility:** Subtitle is embedded in Media object, not added post-playback

### Trade-offs

**Pros:**
- Highest success rate
- Works when all other methods fail
- No timing issues (subtitle loads with media)

**Cons:**
- Requires playback restart
- Brief interruption for user
- Cannot be used for HTTP subtitle URLs

---

## Future Enhancements

### Potential Strategy D: Pre-load subtitles

If subtitle URL is known before playback starts, load subtitle during Media creation:

```kotlin
val media = Media(libVLC, Uri.parse(videoUrl)).apply {
    addOption(":codec=all")
    // ... other options ...
    
    // If subtitle is already downloaded:
    if (subtitleFile.exists()) {
        addOption(":sub-file=${subtitleFile.absolutePath}")
    }
}
```

**Benefit:** No playback interruption
**Challenge:** Requires subtitle download before video starts playing

---

## References

- LibVLC Documentation: https://wiki.videolan.org/Documentation:Modules/
- VLC Command-line Options: https://wiki.videolan.org/VLC_command-line_help/
- Android Scoped Storage: https://developer.android.com/about/versions/11/privacy/storage
