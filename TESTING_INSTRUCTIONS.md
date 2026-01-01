# Testing Guide for Subtitle Track Display and Player Preference Fixes

## Overview

This document provides step-by-step instructions to test the two fixes implemented in this PR:

1. External subtitles now appear in track selection dialog
2. Player preference setting is now respected for HTTP/HTTPS streams

---

## Prerequisites

### For Testing External Subtitles (Fix #1)

1. **OpenSubtitles API Credentials**
   - You need an API key from OpenSubtitles.com
   - Go to https://www.opensubtitles.com/
   - Sign up for a free account
   - Navigate to your profile settings to get your API key

2. **Configure in App**
   - Open the LAMPA app
   - Go to Settings
   - Enter your OpenSubtitles API key
   - Save settings

3. **Test Video**
   - Use a popular movie or TV show for best subtitle availability
   - Ensure the video is in English or your preferred language
   - HTTP/HTTPS stream URL works best

### For Testing Player Preference (Fix #2)

1. **Multiple Players**
   - Install at least one external player (VLC, MX Player, etc.)
   - The app should detect available video players on your device

2. **Settings Access**
   - Know how to access Settings in the LAMPA app
   - Understand the "Always use Internal Player (LibVLC)" toggle

---

## Test Plan

### Test 1: External Subtitles in Track Selection Dialog

**Objective:** Verify that external subtitles loaded via OpenSubtitles API appear in the track selection dialog and are auto-selected.

#### Steps:

1. **Enable ADB Logging (Optional but recommended)**
   ```bash
   adb logcat -s PlayerActivity:D OpenSubtitlesProvider:D SubtitleDownloader:D
   ```

2. **Start Video Playback**
   - Open the LAMPA app
   - Navigate to a video (popular movie/show recommended)
   - Start playback
   - Wait a few seconds for the app to search for subtitles

3. **Look for Toast Notification**
   - You should see a toast message: "External subtitle loaded"
   - If you see this, the subtitle download was successful

4. **Open Track Selection Dialog**
   - Tap anywhere on the screen to show player controls
   - Find and tap the "Track Selection" button (usually shows tracks icon)
   - The dialog should open showing Audio and Subtitle sections

5. **Check Subtitle Section**
   - Expand the Subtitle tracks section
   - Look for the subtitle entry (e.g., "English [subtitle.srt]")

#### Expected Results (Success):

✅ **PASS Criteria:**
- Toast notification "External subtitle loaded" appears
- Track selection dialog shows subtitle track with a name (e.g., "English [subtitle.srt]")
- The subtitle track is already selected (radio button checked)
- Subtitles are visible on the video

❌ **FAIL Criteria (Old Bug):**
- Track selection dialog shows only "Disabled" option
- No subtitle track is listed
- Subtitle is not auto-selected

#### Logs to Check (if using ADB):

```
PlayerActivity: External subtitle downloaded: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
PlayerActivity: Adding subtitle URI: file:///data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1234567890.srt
PlayerActivity: Subtitle slave added successfully
PlayerActivity: Auto-selected new subtitle track: English [subtitle.srt]
```

#### Troubleshooting:

**If subtitle doesn't load:**
- Check that API key is correctly configured
- Check internet connection
- Try a different, more popular video
- Check logs for error messages

**If subtitle loads but doesn't appear in dialog:**
- This was the OLD BUG - should be fixed now
- Check that you're testing the new build

---

### Test 2A: Player Preference - Internal Player Enabled

**Objective:** Verify that when "Always use Internal Player" is enabled, HTTP/HTTPS streams open directly in the internal player.

#### Steps:

1. **Configure Setting**
   - Open the LAMPA app
   - Go to Settings
   - Find "Always use Internal Player (LibVLC)" toggle
   - **Enable** the toggle (turn it ON)
   - Tap "Save"

2. **Play a Video**
   - Navigate to any video with HTTP/HTTPS stream
   - Start playback

#### Expected Results (Success):

✅ **PASS Criteria:**
- Video opens immediately in the internal LibVLC player
- NO player selection dialog appears
- Playback starts automatically

❌ **FAIL Criteria:**
- Player selection dialog appears (should not with this setting)
- External player is used instead

#### Logs to Check (if using ADB):

```
AndroidJS: HTTP/HTTPS stream detected, launching internal player (user preference)
PlayerActivity: Launched PlayerActivity for: <url> with subtitle: <subtitle>
```

---

### Test 2B: Player Preference - Internal Player Disabled

**Objective:** Verify that when "Always use Internal Player" is disabled, HTTP/HTTPS streams show player selection dialog or use external player.

#### Steps:

1. **Configure Setting**
   - Open the LAMPA app
   - Go to Settings
   - Find "Always use Internal Player (LibVLC)" toggle
   - **Disable** the toggle (turn it OFF)
   - Tap "Save"

2. **Play a Video**
   - Navigate to any video with HTTP/HTTPS stream
   - Start playback

#### Expected Results (Success):

✅ **PASS Criteria:**
- Player selection dialog appears
- Dialog shows available players (LibVLC, VLC, MX Player, etc.)
- User can choose which player to use
- Selected player opens with the video

❌ **FAIL Criteria (Old Bug):**
- Internal player opens automatically without dialog
- No choice of player is given
- Setting has no effect

#### Logs to Check (if using ADB):

```
AndroidJS: HTTP/HTTPS stream detected, using player selection based on preference
MainActivity: Showing player selection dialog
```

---

### Test 2C: Player Preference - Toggle Between Settings

**Objective:** Verify that the setting change is immediately effective without app restart.

#### Steps:

1. **Enable Internal Player**
   - Go to Settings
   - Enable "Always use Internal Player (LibVLC)"
   - Save
   - Play a video → Should use internal player

2. **Disable Internal Player**
   - Go to Settings
   - Disable "Always use Internal Player (LibVLC)"
   - Save
   - Play a video → Should show player selection

3. **Repeat**
   - Toggle the setting a few times
   - Each time, verify the behavior matches the setting

#### Expected Results (Success):

✅ **PASS Criteria:**
- Setting change takes effect immediately
- No app restart required
- Behavior consistently matches the setting

---

## Test Matrix

| Test Case | Setting | Expected Behavior | Status |
|-----------|---------|-------------------|--------|
| 1. External Subtitle Loading | API key configured | Subtitle appears in track list | ☐ |
| 2A. Internal Player ON | Always use Internal Player = ON | Opens in internal player | ☐ |
| 2B. Internal Player OFF | Always use Internal Player = OFF | Shows player selection | ☐ |
| 2C. Toggle Setting | Toggle ON/OFF multiple times | Behavior matches setting | ☐ |

---

## Regression Tests

### Existing Functionality to Verify

1. **Explicit Subtitle URL**
   - Play a video with explicit subtitle URL in the intent
   - Verify subtitle still loads correctly
   - Verify it appears in track selection

2. **Audio Track Selection**
   - Open track selection dialog
   - Verify audio tracks are listed
   - Switch between audio tracks
   - Verify audio changes correctly

3. **Subtitle Settings**
   - Open subtitle settings (font size, color, background)
   - Change settings
   - Verify changes apply to subtitles

4. **Magnet Links**
   - Try to play a magnet link
   - Verify it still launches external torrent handler
   - Verify internal player is not used for magnets

5. **Other Stream Types**
   - Test with non-HTTP URLs if available
   - Verify appropriate player is used

---

## Known Limitations

1. **Subtitle Availability**
   - Not all videos have subtitles in OpenSubtitles database
   - Test with popular content for best results

2. **Track Registration Delay**
   - There's a 500ms delay for track registration
   - On very slow devices, tracks might take longer to appear

3. **Network Dependency**
   - External subtitle loading requires internet connection
   - API rate limits may apply with free accounts

---

## Success Criteria

**Overall Test Pass Requirements:**

✅ All test cases in the test matrix pass  
✅ No regression in existing functionality  
✅ No crashes or errors during testing  
✅ Logs show correct behavior (if monitoring)  
✅ User experience is smooth and intuitive  

---

## Reporting Issues

If you encounter any issues during testing, please report:

1. **Which test case failed**
2. **Expected vs actual behavior**
3. **Steps to reproduce**
4. **ADB logs (if available)**
5. **Device model and Android version**
6. **App version/build**

---

## Quick Verification Commands

### Check Current Settings

```bash
# Check player preference
adb shell "run-as top.rootu.lampa cat /data/data/top.rootu.lampa/shared_prefs/top.rootu.lampa_preferences.xml | grep player"

# Check subtitle credentials
adb shell "run-as top.rootu.lampa cat /data/data/top.rootu.lampa/shared_prefs/SubtitlePreferences.xml"
```

### Monitor Logs During Testing

```bash
# Complete log
adb logcat -s PlayerActivity:D AndroidJS:D OpenSubtitlesProvider:D SubtitleDownloader:D MainActivity:D

# Just subtitle-related
adb logcat -s PlayerActivity:D OpenSubtitlesProvider:D

# Just player selection
adb logcat -s AndroidJS:D MainActivity:D
```

---

## Notes

- All tests should be performed on the new build with the fixes applied
- Clean app data before testing if you encounter unexpected behavior
- Test on multiple devices/Android versions if possible
- Document any edge cases or unexpected behavior

---

**Last Updated:** January 1, 2026  
**Test Version:** After PR #[number] - Subtitle Track Display and Player Preference Fixes
