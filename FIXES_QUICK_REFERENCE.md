# Quick Reference: VLC and Subtitle Fixes

## What Was Fixed

### 1. VLC Non-IDR Frame Handling
**Problem:** Black screen/artifacts on TS files due to missing keyframes
**Solution:** Added 3 decoder options to help VLC skip to keyframes faster
```
--avcodec-hurry-up
--avcodec-fast  
--no-avcodec-dr
```

### 2. WebView Performance (Android 14+)
**Problem:** CPU drain from setRequestedFrameRate loop
**Solution:** Hardware acceleration to reduce CPU overhead
```kotlin
setLayerType(View.LAYER_TYPE_HARDWARE, null)
```

### 3. Subtitle Logging
**Problem:** No confirmation of subtitle download/attachment
**Solution:** Added clear logging throughout pipeline
```
"Subtitle Downloaded: [Path]"
"VLC Result: True/False"
```

## Quick Testing

### Test TS File Playback
```bash
adb logcat | grep -i "nal_unit_type"
# Should see fewer errors
```

### Test WebView Performance
```bash
adb logcat | grep "setRequestedFrameRate" | wc -l
# Count should be lower
```

### Test Subtitle Logging
```bash
adb logcat | grep "Subtitle Downloaded\|VLC Result"
# Should see clear download and attachment logs
```

## Expected Log Output

**Successful subtitle:**
```
D SubtitleDownloader: Subtitle Downloaded: /path/to/subtitle.srt
D PlayerActivity: VLC Result: true
D PlayerActivity: Subtitle slave added successfully
```

**Failed subtitle:**
```
D SubtitleDownloader: === FAILED: No subtitles found ===
```

**VLC attachment failure:**
```
D SubtitleDownloader: Subtitle Downloaded: /path/to/subtitle.srt
D PlayerActivity: VLC Result: false
E PlayerActivity: Failed to add subtitle slave
```

## Files Modified
- `PlayerActivity.kt` - VLC options + logging
- `SubtitleDownloader.kt` - Download logging
- `SysView.kt` - WebView performance fix

## Documentation
See `VLC_AND_SUBTITLE_FIXES_FINAL.md` for complete details
