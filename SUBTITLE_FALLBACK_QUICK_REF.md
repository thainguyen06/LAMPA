# Subtitle Fallback - Quick Reference

## Problem
`addSlave()` returns `true` but no subtitle appears (track count = 0)

## Solution: Multi-Strategy Fallback System

### Automatic Flow (No Code Changes Needed)

```
User loads subtitle
    ↓
Try addSlave() with file:// URI (Standard)
    ↓ [FAIL]
Try addSlave() with raw path (Strategy B)
    ↓ [FAIL]
Try forceLoadSubtitle() - Media restart (Strategy A)
    ↓
Success or show error
```

### When to Manually Call forceLoadSubtitle()

Only if you need to bypass automatic flow:

```kotlin
// Direct call for testing/debugging
val success = forceLoadSubtitle("/storage/emulated/0/Android/data/.../subtitle.srt")
if (success) {
    Log.d(TAG, "Subtitle loaded via Strategy A")
}
```

## Strategy Overview

| Strategy | Method | When Used | Success Rate | Side Effect |
|----------|--------|-----------|--------------|-------------|
| Standard | `addSlave(file:// URI)` | First attempt | Medium | None |
| B | `addSlave(raw path)` | Auto fallback | Medium-High | None |
| A | `forceLoadSubtitle()` | Auto fallback | Very High | ~1s pause |

## Path Format Cheatsheet

```kotlin
// Strategy Standard & B (addSlave)
"file:///storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle.srt"
"/storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle.srt"

// Strategy A (forceLoadSubtitle)
"/storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle.srt"
```

## Log Output Examples

### Success with Strategy B
```
[PlayerActivity] VLC addSlave(file://...) with URI Result: false
[PlayerActivity] STRATEGY B - Trying raw path: /storage/.../subtitle.srt
[PlayerActivity] STRATEGY B Result: true
[PlayerActivity] Subtitle added successfully via: Raw Path
```

### Success with Strategy A
```
[PlayerActivity] All addSlave strategies failed
[PlayerActivity] STRATEGY A - Force loading subtitle via Media option
[PlayerActivity] File validated, size: 45123 bytes
[PlayerActivity] Saving position: 125340ms, restarting media
[PlayerActivity] Playback restarted with subtitle embedded
[PlayerActivity] Restored playback position: 125340ms
```

## Debugging

### Check if strategies are being tried
```bash
adb logcat -s PlayerActivity:D | grep STRATEGY
```

### Check file access
```bash
adb shell ls -la /storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/
```

### Pull subtitle file for inspection
```bash
adb pull /storage/emulated/0/Android/data/top.rootu.lampa/cache/subtitle_cache/movie.srt
```

## Common Issues

### ❌ "File not found for Strategy A"
**Cause:** Subtitle in internal cache, not external
**Fix:** Already handled - SubtitleDownloader saves to externalCacheDir

### ❌ All strategies fail
**Cause:** Corrupted subtitle file or unsupported encoding
**Fix:** Check file manually, re-download

### ❌ Playback interruption with Strategy A
**Cause:** Expected behavior (media restart required)
**Fix:** None needed - position is restored automatically

## Testing Commands

### Test subtitle loading flow
```bash
# Start app with subtitle URL
adb shell am start -n top.rootu.lampa/.PlayerActivity \
  -e video_url "http://..." \
  -e subtitle_url "http://...subtitle.srt"

# Watch logs
adb logcat -s PlayerActivity:D SubtitleDebugHelper:D | grep -E "STRATEGY|subtitle|track"
```

## Performance Metrics

| Strategy | Avg Time | User Impact |
|----------|----------|-------------|
| Standard | ~100ms | None |
| B | ~100ms | None |
| A | ~1-2s | Brief pause |

## Code Reference

### Main Functions
- `addAndSelectSubtitle(path)` - Tries Standard + Strategy B
- `forceLoadSubtitle(path)` - Strategy A (media restart)
- `loadSubtitleFromUrl(url)` - Orchestrates all strategies

### Location in Code
`app/src/main/java/top/rootu/lampa/PlayerActivity.kt`

**Lines of Interest:**
- `forceLoadSubtitle()`: ~1448
- `addAndSelectSubtitle()`: ~1298
- Strategy B implementation: ~1382
- Fallback integration: ~1077, ~1055

## When to Use Each Strategy Manually

### Use Standard Flow (Recommended)
```kotlin
// Let the system try all strategies automatically
addAndSelectSubtitle(subtitlePath)
```

### Force Strategy A Only
```kotlin
// Skip addSlave attempts, go straight to media restart
forceLoadSubtitle(subtitlePath)
```

**When to force Strategy A:**
- You've confirmed addSlave always fails on target device
- Subtitle must work without any possibility of failure
- Testing Strategy A specifically

## FAQs

**Q: Will Strategy A always work?**  
A: Yes, unless the file itself is corrupted or VLC cannot parse it.

**Q: Can I disable Strategy A to avoid playback interruption?**  
A: No, but it only triggers after other strategies fail. If Standard/B work, A never runs.

**Q: Does Strategy A work with HTTP URLs?**  
A: No, only local files. HTTP subtitles must use addSlave().

**Q: How long is the playback interruption?**  
A: Typically 1-2 seconds. Position is restored automatically.

**Q: Can I see which strategy succeeded?**  
A: Yes, check logs for "Subtitle added successfully via: <strategy>"
