# External Subtitle Loading - Testing Guide

## Quick Start

This guide helps you test the external subtitle loading fix.

## What Was Fixed

The app now properly loads subtitles from OpenSubtitles when playing videos. Previously:
- ❌ Notification appeared but no subtitles displayed
- ❌ API logs showed nothing

Now:
- ✅ Subtitles are properly added to the playing video
- ✅ Comprehensive logging shows all API activity
- ✅ Subtitles auto-select and display correctly

## Prerequisites

### 1. Get OpenSubtitles Credentials

**Option A: API Key (Recommended)**
1. Go to https://www.opensubtitles.com/
2. Create an account or log in
3. Go to your account settings
4. Generate an API key
5. Copy the API key

**Option B: Username & Password**
1. Go to https://www.opensubtitles.com/
2. Create an account or log in
3. Note your username and password

### 2. Configure in App

1. Open the LAMPA app
2. Go to Settings
3. Find "Subtitle Source Settings"
4. Enter your API key (or username/password)
5. Select preferred subtitle language (e.g., English)
6. Save settings

## Testing Steps

### Step 1: Enable Logging

Connect your device and enable adb logging to see what's happening:

```bash
adb logcat -s PlayerActivity:D OpenSubtitlesProvider:D SubtitleDownloader:D
```

### Step 2: Play a Video

1. Open a video in LAMPA (without explicit subtitle URL)
2. Wait for video to start playing
3. Watch the logs

### Step 3: Check Logs

You should see logs like this:

```
PlayerActivity: searchAndLoadExternalSubtitles called for: http://...
PlayerActivity: Subtitle credentials found, proceeding with search
PlayerActivity: Preferred subtitle language: en
PlayerActivity: Extracted video filename: movie.mp4
PlayerActivity: Starting external subtitle search...
SubtitleDownloader: Searching subtitles for: movie.mp4
OpenSubtitlesProvider: Calling OpenSubtitles API: https://api.opensubtitles.com/api/v1/subtitles?query=...
OpenSubtitlesProvider: Making search request to OpenSubtitles...
OpenSubtitlesProvider: OpenSubtitles API response code: 200
OpenSubtitlesProvider: Found 3 results
OpenSubtitlesProvider: Downloading subtitle: Movie.Name.2020.720p.srt
OpenSubtitlesProvider: Subtitle downloaded successfully: /data/.../subtitle_en_1234567890.srt
PlayerActivity: External subtitle downloaded: /data/.../subtitle_en_1234567890.srt
PlayerActivity: Adding subtitle URI: file:///data/.../subtitle_en_1234567890.srt
PlayerActivity: Subtitle slave added successfully
PlayerActivity: Auto-selected new subtitle track: Movie.Name.2020.720p.srt
```

### Step 4: Verify on Screen

1. ✅ Check that subtitles appear on the video
2. ✅ Check that you see a toast: "External subtitle loaded"
3. ✅ Open track selection dialog - the subtitle should appear in the list

## Troubleshooting

### Issue: "No subtitle source credentials configured"

**Solution:** Configure API key or username/password in app settings.

### Issue: "OpenSubtitles API response code: 401"

**Solution:** Your API key or credentials are invalid. Check them in settings.

### Issue: "OpenSubtitles API response code: 403"

**Solution:** Your API key doesn't have permission, or you've hit rate limits.

### Issue: "Found 0 results"

**Solution:** This is normal - no subtitles exist for this video. Try a popular movie/show.

### Issue: "Failed to add subtitle slave"

**Possible causes:**
- File path is invalid
- File doesn't exist
- Permission issues

**Solution:** Check the logs for the file path and verify it exists.

### Issue: "New subtitle track not detected in track list"

**Possible causes:**
- LibVLC didn't register the track yet (rare)
- Subtitle format is incompatible

**Solution:** 
1. Try opening track selection dialog manually - the subtitle might still be there
2. Check if the .srt file is valid

## Expected Behavior Summary

| Step | Expected Result |
|------|----------------|
| Video starts | "searchAndLoadExternalSubtitles called" in logs |
| Credentials check | "Subtitle credentials found" in logs |
| API call | "OpenSubtitles API response code: 200" in logs |
| Results found | "Found X results" in logs |
| Download | "Subtitle downloaded successfully" in logs |
| Add to player | "Subtitle slave added successfully" in logs |
| Auto-select | "Auto-selected new subtitle track" in logs |
| On screen | Subtitles visible on video |
| Toast | "External subtitle loaded" notification |
| Track list | Subtitle appears in track selection dialog |

## Success Criteria

✅ All logs appear as expected
✅ Subtitles display on video
✅ Toast notification appears
✅ Subtitle track in selection dialog
✅ No errors in logs

## Notes

- First subtitle search may take a few seconds
- Subtitles are cached in `/data/user/0/top.rootu.lampa/cache/subtitle_cache/`
- The app tries all enabled subtitle providers in order (OpenSubtitles, SubSource, SubDL, SubHero)
- Only providers with credentials configured are tried

## Technical Details

For developers who want to understand the implementation:

### How it works:
1. When media is parsed, `searchAndLoadExternalSubtitles()` is called
2. Credentials are checked
3. Video filename is extracted from URL
4. Subtitle providers are queried in order
5. First matching subtitle is downloaded to cache
6. File path is converted to `file://` URI
7. `MediaPlayer.addSlave(0, uri, true)` is called
8. After 500ms, tracks are refreshed
9. New track is identified by comparing track count
10. New track is auto-selected

### Key API:
```kotlin
mediaPlayer.addSlave(
    type = 0,        // 0 = subtitle, 1 = audio
    uri = "file://...",  // File URI
    select = true    // Auto-select
): Boolean
```

### Files changed:
- `PlayerActivity.kt` - Main subtitle loading logic
- `OpenSubtitlesProvider.kt` - API logging
- `strings.xml` - Error message resource
- `SUBTITLE_FIX_SUMMARY.md` - Detailed implementation doc

## Support

If you encounter issues:
1. Check the logs first
2. Verify your credentials are correct
3. Try a different video (popular movies work best)
4. Check your internet connection
5. Review `SUBTITLE_FIX_SUMMARY.md` for more details

## Summary

The fix ensures that:
1. OpenSubtitles API is actually called (logs visible)
2. Subtitles are properly downloaded
3. Subtitles are correctly added to playing video using `addSlave()` API
4. Subtitles auto-select and display
5. All steps are logged for debugging

Happy testing! 🎬
