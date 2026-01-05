# Quick Reference: Using reloadVideoWithSubtitle()

## Summary
The `reloadVideoWithSubtitle(subtitlePath: String)` function provides a reliable way to load external subtitles by restarting the video with the subtitle embedded in VLC media options.

## Function Signature
```kotlin
fun reloadVideoWithSubtitle(subtitlePath: String): Boolean
```

## Usage Examples

### Example 1: Load Downloaded Subtitle
```kotlin
val subtitlePath = "/storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle.srt"
val success = reloadVideoWithSubtitle(subtitlePath)
if (!success) {
    App.toast(R.string.subtitle_load_failed, true)
}
```

### Example 2: With file:// Prefix (Automatically Handled)
```kotlin
val subtitlePath = "file:///storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle.srt"
val success = reloadVideoWithSubtitle(subtitlePath) // file:// prefix is stripped internally
```

### Example 3: In a Coroutine
```kotlin
coroutineScope.launch {
    val subtitlePath = downloadSubtitle(videoUrl)
    if (subtitlePath != null) {
        runOnUiThread {
            reloadVideoWithSubtitle(subtitlePath)
        }
    }
}
```

## What It Does

1. **Saves Current Position**: Captures `mediaPlayer.time`
2. **Stops Playback**: Calls `mediaPlayer?.stop()`
3. **Creates New Media**: With `:sub-file=$path` option
4. **Restarts Playback**: Calls `mediaPlayer?.play()`
5. **Restores Position**: Automatically when `Playing` event fires

## Important Notes

### ✅ Do's
- Use for **local subtitle files only**
- Provide **absolute file paths** (e.g., `/storage/emulated/0/...`)
- Call from **UI thread** or wrap in `runOnUiThread {}`
- Ensure subtitle file **exists** before calling
- Use for **SRT, VTT, ASS/SSA** formats

### ❌ Don'ts
- Don't use for HTTP subtitle URLs (use `addAndSelectSubtitle` instead)
- Don't provide file:// prefix (it's stripped automatically but avoid it)
- Don't call before video starts playing
- Don't call multiple times rapidly (will cause restart loops)

## Return Values

| Return | Meaning |
|--------|---------|
| `true` | Reload initiated successfully |
| `false` | Failed (file not found, no video URL, etc.) |

## Expected Behavior

### User Experience
1. Video briefly stops
2. Loading spinner may appear momentarily
3. Video restarts at same position
4. Subtitle appears immediately

### Duration
- Typical reload time: 500-2000ms depending on:
  - Network speed (for streaming videos)
  - Device performance
  - Video codec complexity

## Logging

The function logs the following events:

```
[PlayerActivity] Media Option Restart - Reloading with subtitle: /path/to/subtitle.srt
[PlayerActivity] File validated, size: 45678 bytes
[PlayerActivity] Position saved: 123456ms, preparing media restart
[PlayerActivity] VLC option set: :sub-file=/path/to/subtitle.srt
[PlayerActivity] Media reloaded, waiting for Playing event to restore position
[PlayerActivity] Playback position restored: 123456ms
[PlayerActivity] Track list refreshed after media reload
```

## Error Handling

### Common Errors and Solutions

**Error: File not found**
```
[PlayerActivity] Subtitle file not found: /path/to/subtitle.srt
```
**Solution**: Verify the file was downloaded successfully and path is correct

**Error: No video URL stored**
```
[PlayerActivity] Cannot restart - no video URL stored
```
**Solution**: Ensure `videoUrl` class variable is set before calling

**Error: LibVLC not initialized**
```
[PlayerActivity] LibVLC instance is null
```
**Solution**: Wait for player to fully initialize before calling

## Migration from addSlave()

### Before (Using addSlave)
```kotlin
val subtitleUri = Uri.fromFile(File(subtitlePath)).toString()
val added = mediaPlayer?.addSlave(0, subtitleUri, true)
if (added == true) {
    // Wait and hope subtitle appears...
    handler.postDelayed({ 
        checkSubtitleTracks() 
    }, 5000)
}
```

### After (Using reloadVideoWithSubtitle)
```kotlin
val success = reloadVideoWithSubtitle(subtitlePath)
if (success) {
    // Position restore happens automatically via event
    // No need for manual delays or track checking
}
```

## Performance Tips

1. **Cache Media Objects**: Don't create/destroy media repeatedly
2. **Debounce Calls**: Prevent multiple rapid reloads
3. **Pre-validate Files**: Check file exists before calling
4. **Use Appropriate Timing**: Wait for playback to stabilize

## Troubleshooting Checklist

- [ ] Subtitle file exists and is readable
- [ ] File path is absolute (starts with `/`)
- [ ] Video is currently playing
- [ ] LibVLC is initialized
- [ ] Video URL is stored in `videoUrl` variable
- [ ] Function called from UI thread
- [ ] No other reload in progress

## Integration with Existing Code

### Works With
- ✅ `searchAndLoadExternalSubtitles()` - Uses it automatically
- ✅ `loadSubtitleFromUrl()` - Uses it for local files
- ✅ `SubtitleDownloader` - Compatible with all download providers
- ✅ Event-driven architecture - Integrates with `Playing` event

### Replaces
- ❌ `addAndSelectSubtitle()` for local files (still used for HTTP URLs)
- ❌ `forceLoadSubtitle()` for most cases (similar implementation but with event-driven timing)

## Testing

### Manual Testing Steps
1. Play a video for 30 seconds
2. Call `reloadVideoWithSubtitle(validSubtitlePath)`
3. Verify:
   - Video restarts briefly
   - Playback resumes at 30 seconds
   - Subtitle appears correctly
   - No errors in logcat

### Expected Logs
```bash
adb logcat -s PlayerActivity:D SubtitleDebugHelper:D | grep -E "reloadVideoWithSubtitle|Position|Media Option"
```

## Complete Example

```kotlin
private fun loadExternalSubtitle(subtitlePath: String) {
    // Validate file
    val file = File(subtitlePath)
    if (!file.exists()) {
        Log.e(TAG, "Subtitle file not found: $subtitlePath")
        return
    }
    
    // Log attempt
    Log.d(TAG, "Loading subtitle: ${file.name} (${file.length()} bytes)")
    
    // Call reload function
    runOnUiThread {
        val success = reloadVideoWithSubtitle(subtitlePath)
        
        if (success) {
            Log.d(TAG, "Subtitle reload initiated successfully")
            // Optional: Show success message
            // App.toast("Subtitle loaded", false)
        } else {
            Log.e(TAG, "Subtitle reload failed")
            App.toast(R.string.subtitle_load_failed, true)
        }
    }
}
```

## Further Reading

See `MEDIA_OPTION_RESTART_IMPLEMENTATION.md` for:
- Detailed implementation guide
- Architecture decisions
- Performance considerations
- Future enhancements
