# Quick Reference: Subtitle Loading Fix

## What Was Fixed

### 1. Race Condition (Double Execution)
**Before:** Function called twice in 1 second → conflicts
**After:** Debounce prevents calls within 2 seconds → no conflicts

### 2. Track Not Appearing
**Before:** `addSlave()` returns true but track count = 0
**After:** Explicit track selection after registration delay

### 3. Code Duplication
**Before:** ~140 lines of duplicate subtitle loading code
**After:** One centralized `addAndSelectSubtitle()` function

## How to Test

### Test the Debounce
1. Play any video
2. Check logs for: `searchAndLoadExternalSubtitles called`
3. Should appear only **once**, not twice
4. If called again too soon: `Debounced call ignored`

### Test Subtitle Loading
1. Play a video that requires external subtitles
2. Wait for auto-search to complete
3. Open track selection dialog (gear icon)
4. Verify subtitle appears in list
5. Verify subtitle is auto-selected (has checkmark)
6. Check logs show:
   ```
   [INFO] Subtitle downloaded successfully: /path/to/subtitle.srt
   [INFO] VLC addSlave() Result: true
   [INFO] Subtitle slave added successfully to LibVLC
   [INFO] Auto-selected subtitle track (attempt 1): Track Name
   ```

### Test Log Export
1. Long-press subtitle settings button
2. Select "Export Logs"
3. Check: `/storage/emulated/0/Download/subtitle_debug_YYYY-MM-DD_HH-mm-ss.log`
4. File should contain detailed subtitle loading logs

## Log Analysis

### Success Pattern
```
[13:47:33.485] [INFO] searchAndLoadExternalSubtitles called for: video.mkv
[13:47:34.336] [INFO] VLC addSlave() Result: true
[13:47:34.635] [INFO] ESAdded event detected
[13:47:34.635] [DEBUG] Current tracks - Audio: 1, Video: 2, Subtitle: 1  ✅ Count increased!
[13:47:36.135] [INFO] Auto-selected subtitle track (attempt 1): English
```

### Debounce Pattern (Expected)
```
[13:47:33.485] [INFO] searchAndLoadExternalSubtitles called for: video.mkv
[13:47:34.640] [INFO] searchAndLoadExternalSubtitles: Debounced call ignored  ✅ Prevented conflict!
```

### Failure Pattern (Should Not Happen)
```
[13:47:33.485] [INFO] searchAndLoadExternalSubtitles called for: video.mkv
[13:47:34.336] [INFO] VLC addSlave() Result: true
[13:47:34.635] [INFO] ESAdded event detected
[13:47:34.635] [DEBUG] Current tracks - Audio: 1, Video: 2, Subtitle: 0  ❌ Still 0!
[13:47:38.839] [WARNING] Max retries reached - subtitle track not detected
```

## Key Functions

### `addAndSelectSubtitle(path: String): Boolean`
- Validates file exists
- Converts path to proper URI (`file:///`)
- Calls `addSlave(0, uri, true)`
- Waits 1.5s for registration
- Explicitly selects track with `setSpuTrack()`
- Returns success/failure

### Debounce Logic
```kotlin
private var lastSubtitleSearchTimestamp: Long = 0
private val subtitleSearchDebounceMs = 2000L

// In searchAndLoadExternalSubtitles():
val currentTime = System.currentTimeMillis()
if (currentTime - lastSubtitleSearchTimestamp < subtitleSearchDebounceMs) {
    return // Ignore call
}
lastSubtitleSearchTimestamp = currentTime
```

## Important Notes

1. **Track Selection:** VLC's `addSlave(type, uri, select=true)` doesn't always auto-select reliably, so we explicitly call `mediaPlayer?.spuTrack = trackId`

2. **ESAdded Event:** Fires for ANY track type (audio/video/subtitle). We determine track type by checking counts, not the event object.

3. **Timing:** LibVLC needs ~1.5 seconds to register a new subtitle track. The code retries up to 3 times with 1-second delays.

4. **URI Format:** Local files MUST use `file:///` format. We use `Uri.fromFile()` to ensure correct formatting.

## Troubleshooting

### Subtitle still not loading?
1. Check storage permissions
2. Verify file path is correct: `/data/user/0/...` or `/storage/emulated/0/...`
3. Check file is readable (not corrupted)
4. Look for permission errors in logs

### Debounce not working?
1. Check logs for timestamp
2. Verify calls are < 2 seconds apart
3. If > 2 seconds apart, debounce won't trigger (expected)

### Track count stays at 0?
1. Check VLC can read the file URI
2. Verify subtitle format is supported (.srt, .ass, .vtt)
3. Check for VLC-specific errors in Android logcat

## Files Changed

- `PlayerActivity.kt` - Main subtitle loading logic
- `SubtitleDebugHelper.kt` - Log save location

## LAMPA Icon Auto-Hide

**Status:** Not implemented - unclear what "auto hide lampa icon" refers to.

**Possibilities:**
1. Launcher icon hiding - needs clarification
2. Player controls auto-hide - already implemented (`CONTROLS_HIDE_DELAY = 3000ms`)
3. App icon in recent apps - Android system behavior

**Please clarify:** What specific icon should not auto-hide?
