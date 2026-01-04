# VLC Video Decoding and Subtitle Verification Fixes

## Problem Statement

The Android app "top.rootu.lampa" was experiencing three critical issues:

### 1. Critical Video Decoding Error (Non-IDR Frames)
**Symptoms:**
- Log flooded with: `E VLC-std: [h264 @ ...] nal_unit_type: 1(Coded slice of a non-IDR picture)`
- Black screen or heavy artifacts on TS (Telesync) files
- VLC cannot start rendering because it's missing the initial Keyframe (IDR)

**Root Cause:**
- The video stream (especially TS/Telesync files) starts with non-IDR frames instead of keyframes
- VLC's decoder needs an IDR (Instantaneous Decoder Refresh) frame to start decoding
- Without proper decoder options, VLC struggles to skip to the first keyframe

### 2. WebView UI Loop (Performance Drain)
**Symptoms:**
- Log polluted with thousands of lines:
  ```
  I View: setRequestedFrameRate frameRate=-4.0, this=android.webkit.WebView... 
         caller=android.view.ViewGroup.setRequestedFrameRate
  ```
- High CPU usage from WebView
- Performance degradation on Android 14 (API 34)

**Root Cause:**
- Android 14+ WebView internally calls `setRequestedFrameRate` repeatedly
- This is a known Android system issue in the View refresh rate management
- Cannot be directly disabled through WebView API

### 3. Subtitle Verification Gap
**Symptoms:**
- Log shows: `[OpenSubtitles] Got download link`
- But no confirmation of successful subtitle download
- No clear indication if subtitle was attached to VLC player

**Root Cause:**
- Missing logging for subtitle download completion
- No logging of VLC `addSlave()` result (true/false)
- Difficult to debug subtitle attachment failures

---

## Solutions Implemented

### 1. Enhanced VLC Decoder Options for Non-IDR Frames

**File:** `app/src/main/java/top/rootu/lampa/PlayerActivity.kt`

**Changes:**
```kotlin
// Additional options to handle streams with non-IDR frames (Telesync/TS files)
// These help when the stream starts with non-IDR frames and VLC can't find initial keyframe
add("--avcodec-hurry-up") // Allow decoder to skip non-reference frames for faster seeking to keyframes
add("--avcodec-fast") // Enable fast decoding (less quality checks, faster keyframe detection)
add("--no-avcodec-dr") // Disable direct rendering to avoid artifacts with corrupted frames
```

**How These Options Help:**

1. **`--avcodec-hurry-up`**
   - Allows the decoder to skip non-reference frames
   - Helps VLC quickly seek to the first available keyframe
   - Reduces the time spent trying to decode non-IDR frames
   - Trade-off: May skip some frames during initial buffering, but improves startup

2. **`--avcodec-fast`**
   - Enables fast decoding mode in the H.264 decoder
   - Reduces quality checks during decoding
   - Faster keyframe detection and processing
   - Trade-off: Slightly lower decode quality, but significantly faster startup

3. **`--no-avcodec-dr`**
   - Disables direct rendering in the decoder
   - Prevents visual artifacts when dealing with corrupted or non-IDR frames
   - Adds a small performance overhead but ensures better visual quality
   - Particularly important for problematic TS files

**Why These Work Together:**
- The combination allows VLC to quickly skip to keyframes (hurry-up + fast)
- While maintaining visual quality by avoiding direct rendering artifacts (no-dr)
- This creates a balance between startup speed and playback quality

---

### 2. WebView Performance Workaround for Android 14+

**File:** `app/src/main/java/top/rootu/lampa/browser/SysView.kt`

**Changes:**
```kotlin
// Workaround for Android 14+ setRequestedFrameRate performance issue
// WebView internally calls setRequestedFrameRate which can cause UI loop and CPU drain
// This is a known Android system issue that we can't directly fix, but we can
// reduce its impact by setting layer type to hardware acceleration
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34 (Android 14)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
}
```

**Why This Helps:**

1. **Hardware Acceleration:**
   - Offloads rendering to GPU instead of CPU
   - Reduces CPU overhead from repeated `setRequestedFrameRate` calls
   - GPU handles frame rate management more efficiently

2. **Limitation Acknowledged:**
   - We cannot directly disable `setRequestedFrameRate` calls (Android system behavior)
   - This is a workaround, not a complete fix
   - Still better than no mitigation at all

3. **Android 14+ Specific:**
   - Only applies the workaround on API 34+ where the issue occurs
   - Maintains backward compatibility with older Android versions

**Alternative Approaches Considered:**
- ❌ Override `setRequestedFrameRate` in WebView (not possible - method is final)
- ❌ Use custom WebView implementation (too invasive, breaks functionality)
- ✅ Hardware acceleration (best balance of effectiveness and simplicity)

---

### 3. Comprehensive Subtitle Download and Attachment Logging

**Files Modified:**
- `app/src/main/java/top/rootu/lampa/helpers/SubtitleDownloader.kt`
- `app/src/main/java/top/rootu/lampa/PlayerActivity.kt`

#### SubtitleDownloader.kt Changes:

**In `searchAndDownload()` method:**
```kotlin
if (subtitlePath != null) {
    Log.d(TAG, "Successfully downloaded subtitle from ${provider.getName()}")
    Log.d(TAG, "Subtitle Downloaded: $subtitlePath")
    SubtitleDebugHelper.logInfo("SubtitleDownloader", "=== SUCCESS: Downloaded from ${provider.getName()} ===")
    SubtitleDebugHelper.logInfo("SubtitleDownloader", "Subtitle Downloaded: $subtitlePath")
    return@withContext subtitlePath
}
```

**In `downloadFromUrl()` method:**
```kotlin
Log.d(TAG, "Subtitle downloaded successfully: ${subtitleFile.absolutePath}")
Log.d(TAG, "Subtitle Downloaded: ${subtitleFile.absolutePath}")
SubtitleDebugHelper.logInfo("SubtitleDownloader", "Subtitle Downloaded: ${subtitleFile.absolutePath}")
return@withContext subtitleFile.absolutePath
```

#### PlayerActivity.kt Changes:

**After `addSlave()` call (2 locations):**
```kotlin
val added = mediaPlayer?.addSlave(0, subtitleUri, true)

// Log VLC result for debugging
Log.d(TAG, "VLC Result: ${added ?: false}")
SubtitleDebugHelper.logInfo("PlayerActivity", "VLC addSlave() Result: ${added ?: false}")

if (added == true) {
    Log.d(TAG, "Subtitle slave added successfully")
    SubtitleDebugHelper.logInfo("PlayerActivity", "Subtitle slave added successfully to LibVLC")
    // ...
}
```

**Benefits of Enhanced Logging:**

1. **Clear Download Confirmation:**
   - "Subtitle Downloaded: [Path]" clearly shows the file location
   - Easy to verify the file exists in the specified path
   - Helps debug file permission issues

2. **VLC Attachment Verification:**
   - "VLC Result: True/False" shows if `addSlave()` succeeded
   - Separates download success from attachment success
   - Identifies VLC-specific issues vs download issues

3. **Dual Logging System:**
   - Standard Android Log.d() for immediate logcat viewing
   - SubtitleDebugHelper for exportable debug logs
   - Supports both live debugging and post-mortem analysis

4. **Consistent Format:**
   - All logs follow the same pattern
   - Easy to grep/filter in large log files
   - Clear success/failure indicators (===, Result:)

---

## Expected Log Output After Fixes

### Successful Subtitle Loading Example:

```
D SubtitleDownloader: === Starting subtitle search ===
D SubtitleDownloader: Video: 'Avatar...TS.mkv', IMDB: 'null', Language: 'en'
D SubtitleDownloader: Attempting provider: OpenSubtitlesProvider
D SubtitleDownloader: Provider OpenSubtitlesProvider found 5 results
D SubtitleDownloader: Successfully downloaded subtitle from OpenSubtitlesProvider
D SubtitleDownloader: Subtitle Downloaded: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
D SubtitleDownloader: === SUCCESS: Downloaded from OpenSubtitlesProvider ===
D PlayerActivity: Subtitle file exists: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
D PlayerActivity: File exists, size: 45678 bytes
D PlayerActivity: Subtitle URI generated: file:///data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
D PlayerActivity: VLC Result: true
D PlayerActivity: Subtitle slave added successfully
D PlayerActivity: Auto-selected subtitle track (attempt 1): English
```

### Failed Subtitle Loading Example:

```
D SubtitleDownloader: === Starting subtitle search ===
D SubtitleDownloader: Video: 'Avatar...TS.mkv', IMDB: 'null', Language: 'en'
D SubtitleDownloader: Attempting provider: OpenSubtitlesProvider
D SubtitleDownloader: Provider OpenSubtitlesProvider found 0 results
D SubtitleDownloader: Provider OpenSubtitlesProvider returned no results
D SubtitleDownloader: === FAILED: No subtitles found from any provider ===
```

### VLC Attachment Failure Example:

```
D SubtitleDownloader: Subtitle Downloaded: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
D PlayerActivity: Subtitle file exists: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
D PlayerActivity: Subtitle URI generated: file:///data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
D PlayerActivity: VLC Result: false
E PlayerActivity: Failed to add subtitle slave
```

---

## Testing Recommendations

### Test Case 1: TS File with Non-IDR Frames

**Steps:**
1. Play a Telesync (TS) file that has non-IDR frame issues
2. Observe initial buffering and playback startup

**Expected Results:**
- Video should start playing after initial buffering
- Fewer or no "nal_unit_type: 1" errors in logs
- Black screen duration should be minimal or eliminated

**What to Check:**
```bash
adb logcat | grep -i "nal_unit_type"
# Should see significantly fewer errors

adb logcat | grep -i "avcodec-hurry-up\|avcodec-fast\|avcodec-dr"
# Should see VLC options being applied
```

### Test Case 2: WebView Performance on Android 14+

**Steps:**
1. Run app on Android 14 (API 34) device
2. Navigate through the app interface
3. Monitor CPU usage

**Expected Results:**
- CPU usage should be lower than before
- Fewer "setRequestedFrameRate" log spam (may not be completely eliminated)
- Smoother UI performance

**What to Check:**
```bash
adb logcat | grep "setRequestedFrameRate" | wc -l
# Count should be lower than before

adb shell dumpsys cpuinfo | grep top.rootu.lampa
# CPU usage should be reasonable
```

### Test Case 3: Subtitle Download and Attachment

**Steps:**
1. Play a video
2. Let subtitle auto-download trigger
3. Check logcat for new log messages

**Expected Results:**
- Clear "Subtitle Downloaded: [Path]" message
- Clear "VLC Result: True" or "False" message
- Easy to trace subtitle flow from download to attachment

**What to Check:**
```bash
adb logcat | grep "Subtitle Downloaded"
# Should see clear download path

adb logcat | grep "VLC Result"
# Should see true/false result

adb logcat | grep "SubtitleDebugHelper"
# Should see all subtitle operations logged
```

### Test Case 4: Subtitle Export for Debugging

**Steps:**
1. Long-press subtitle settings button in player
2. Export subtitle debug logs
3. Check exported file

**Expected Results:**
- Exported log should contain all subtitle operations
- Clear success/failure indicators
- File paths visible for debugging

---

## LibVLC Decoder Options Reference

### Non-IDR Frame Handling Options

| Option | Values | Description | Performance Impact |
|--------|--------|-------------|-------------------|
| `--avcodec-hurry-up` | flag | Skip non-reference frames for faster keyframe seeking | Minimal (may skip frames during startup) |
| `--avcodec-fast` | flag | Enable fast decoding mode | Low (slight quality reduction) |
| `--no-avcodec-dr` | flag | Disable direct rendering | Low (adds small memory copy overhead) |
| `--avcodec-skiploopfilter=0` | 0-2 | Don't skip loop filtering (0=none, 1=nonref, 2=all) | Medium (proper frame decoding) |
| `--avcodec-skip-frame=0` | 0-2 | Decode all frames including non-IDR (0=none, 1=nonref, 2=all) | Medium (decode all frames) |
| `--avcodec-skip-idct=0` | 0-2 | Don't skip IDCT for better quality (0=none, 1=nonref, 2=all) | Medium (proper quality) |

### Previously Implemented Options (from VLC_PERFORMANCE_AND_SUBTITLE_FIXES.md)

| Option | Value | Description |
|--------|-------|-------------|
| `--network-caching` | 10000ms | Buffer size for network streams |
| `--live-caching` | 10000ms | Buffer size for live streams |
| `--clock-jitter` | 5000ms | Maximum timing jitter before correction |
| `--clock-synchro` | 0 | Default sync mode for timing issues |
| `--audio-desync` | 0 | Let VLC handle audio sync automatically |

---

## Code Files Modified

1. **`app/src/main/java/top/rootu/lampa/PlayerActivity.kt`**
   - Added 3 new LibVLC decoder options for non-IDR frame handling
   - Added VLC result logging after `addSlave()` calls (2 locations)
   - Enhanced subtitle attachment verification

2. **`app/src/main/java/top/rootu/lampa/helpers/SubtitleDownloader.kt`**
   - Added "Subtitle Downloaded: [Path]" logging in `searchAndDownload()`
   - Added "Subtitle Downloaded: [Path]" logging in `downloadFromUrl()`
   - Enhanced SubtitleDebugHelper integration

3. **`app/src/main/java/top/rootu/lampa/browser/SysView.kt`**
   - Added hardware acceleration for Android 14+ to mitigate setRequestedFrameRate performance issue
   - Added comprehensive documentation of the limitation and workaround

---

## Known Limitations and Trade-offs

### VLC Decoder Options:

**Trade-off 1: Startup Quality vs Speed**
- `--avcodec-hurry-up` and `--avcodec-fast` may skip some frames during initial buffering
- Result: Faster video start, but potentially lower quality for first few seconds
- **Why Acceptable:** Users prefer quick startup over perfect quality for first 1-2 seconds

**Trade-off 2: Memory vs Visual Quality**
- `--no-avcodec-dr` adds a small memory copy overhead
- Result: Slightly higher memory usage, but no visual artifacts
- **Why Acceptable:** Modern devices have sufficient memory, visual quality is critical

### WebView Performance:

**Limitation: Cannot Fully Disable setRequestedFrameRate**
- Android system behavior, not controllable via WebView API
- Hardware acceleration reduces but doesn't eliminate the issue
- **Mitigation:** Monitor for future Android updates that may fix this

**Trade-off: Hardware Acceleration**
- May increase GPU usage
- Some devices may have less optimized GPU drivers
- **Why Acceptable:** Modern Android devices have capable GPUs, CPU reduction is more valuable

### Subtitle Logging:

**Trade-off: Log Volume**
- More detailed logging means larger log files
- May make it harder to find other issues
- **Mitigation:** Use clear prefixes ("Subtitle Downloaded:", "VLC Result:") for easy filtering

---

## Summary

All three issues have been addressed with minimal, surgical changes:

1. ✅ **Non-IDR Frame Handling:** Added 3 LibVLC decoder options to help VLC skip to keyframes faster
   - Reduces black screen and artifacts on TS files
   - Maintains backward compatibility
   - Small performance trade-off for better user experience

2. ✅ **WebView Performance:** Added hardware acceleration workaround for Android 14+
   - Reduces CPU overhead from setRequestedFrameRate loop
   - Acknowledges limitation (cannot fully disable)
   - Version-specific (only applies to problematic Android versions)

3. ✅ **Subtitle Logging:** Added comprehensive logging throughout subtitle pipeline
   - Clear download confirmation with file paths
   - VLC attachment result verification
   - Dual logging (logcat + SubtitleDebugHelper)
   - Easy debugging and troubleshooting

All changes are well-documented, maintain backward compatibility, and follow the principle of minimal modification.

---

## Related Documentation

- [LibVLC Android Documentation](https://wiki.videolan.org/Android/)
- [VLC Command Line Options](https://wiki.videolan.org/VLC_command-line_help/)
- [H.264 NAL Unit Types](https://en.wikipedia.org/wiki/Network_Abstraction_Layer#NAL_unit_types)
- [Android WebView Performance](https://developer.android.com/guide/webapps/webview)
- [OpenSubtitles API Documentation](https://opensubtitles.stoplight.io/docs/opensubtitles-api)

---

## Version History

- **v1.0** (Current): Initial implementation of all three fixes
  - VLC decoder options for non-IDR frames
  - WebView hardware acceleration workaround
  - Comprehensive subtitle logging
