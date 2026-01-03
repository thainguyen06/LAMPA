# VLC Playback Fixes - Quick Reference

## What Was Fixed

### 1. Extreme Latency/Desync (14 seconds delay)
**Problem:** `libvlc audio output: buffer too late (-14409040 μs): dropped`

**Solution:**
- Doubled network caching: 5s → 10s
- Added clock sync options:
  - `--clock-jitter=5000` (allow 5s jitter)
  - `--clock-synchro=0` (auto sync)
  - `--audio-desync=0` (auto audio sync)

### 2. Non-IDR Frame Handling
**Problem:** `nal_unit_type: 1(Coded slice of a non-IDR picture)` - Missing keyframes

**Solution:**
Added H.264 decoder options:
- `--avcodec-skiploopfilter=0` (decode all frames properly)
- `--avcodec-skip-frame=0` (don't skip frames)
- `--avcodec-skip-idct=0` (don't skip IDCT)

### 3. Subtitle Track Not Detected
**Problem:** OpenSubtitles returns 200 OK, subtitle downloaded, but track not detected

**Solution:**
- Increased initial delay: 500ms → 1500ms
- Added retry mechanism: up to 3 retries with 1s delays
- Total wait time: up to 4.5 seconds
- Added user notification on failure

## VLC Options Quick Reference

### For High Latency/Unstable Streams
```kotlin
--network-caching=10000    // 10 seconds (increase if needed)
--live-caching=10000       // 10 seconds for live streams
--clock-jitter=5000        // Allow 5 second timing variance
--clock-synchro=0          // Auto sync mode
```

### For Non-IDR Frame Streams
```kotlin
--avcodec-skiploopfilter=0 // Decode all frames
--avcodec-skip-frame=0     // Don't skip any frames
--avcodec-skip-idct=0      // Don't skip IDCT
```

### For Subtitle Loading
```kotlin
// Initial delay after addSlave()
SUBTITLE_TRACK_REGISTRATION_DELAY_MS = 1500L

// Retry mechanism
SUBTITLE_TRACK_RETRY_DELAY_MS = 1000L
SUBTITLE_TRACK_MAX_RETRIES = 3

// Total possible wait: 1.5s + (3 × 1s) = 4.5s
```

## How to Test

### Test 1: High Latency Stream
1. Play network stream with poor connection
2. ✅ Should buffer longer initially but play smoothly
3. ✅ Check logs: fewer/no "buffer too late" messages

### Test 2: Non-IDR Stream
1. Play stream that starts without keyframe
2. ✅ Video should decode and display correctly
3. ✅ Check logs: fewer NAL unit warnings

### Test 3: Subtitle Loading
1. Play video, wait for OpenSubtitles API call
2. ✅ Should see: "OpenSubtitles API response code: 200"
3. ✅ Should see: "Subtitle slave added successfully"
4. ✅ Wait up to 4.5 seconds
5. ✅ Should see: "Auto-selected subtitle track"
6. ✅ Should see toast: "Subtitle loaded"
7. ✅ Subtitle appears in player

### Test 4: Subtitle Loading Failure
1. If subtitle track not detected after 4.5 seconds
2. ✅ Should see toast: "Failed to load subtitle"

## Troubleshooting

### Still seeing latency/desync?
- Increase `--network-caching` further (try 15000 or 20000)
- Check network connection quality
- Check server response time

### Subtitle still not loading?
- Check SubtitleDebugHelper logs
- Verify file was downloaded (check cache directory)
- Verify file format is supported (SRT, VTT, SSA, etc.)
- Try manual selection from track dialog

### Non-IDR frame issues persist?
- Some streams may have encoder issues at source
- Try different video source if available
- Consider reporting to stream provider

## Files Modified

- `app/src/main/java/top/rootu/lampa/PlayerActivity.kt`
  - LibVLC initialization options
  - Media configuration
  - Subtitle track selection with retry
  - ESAdded event logging

## Documentation

See `VLC_PERFORMANCE_AND_SUBTITLE_FIXES.md` for detailed explanation of all changes.
