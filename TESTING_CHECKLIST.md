# Testing Checklist for Subtitle Debug Implementation

## Pre-Testing Setup

### 1. Build and Install
- [ ] Build the APK successfully
- [ ] Install on test device
- [ ] Verify app launches without crashes

### 2. Configure Subtitle Sources
**Option A: OpenSubtitles**
- [ ] Go to Settings
- [ ] Enter valid OpenSubtitles API key
- [ ] Set preferred subtitle language
- [ ] Save settings

**Option B: Stremio Addon**
- [ ] Go to Settings
- [ ] Enter valid Stremio addon URL (e.g., https://opensubtitles-v3.strem.io)
- [ ] Set preferred subtitle language
- [ ] Save settings

## Core Functionality Tests

### Test 1: Debug Menu Access
- [ ] Open video player
- [ ] Locate subtitle settings button (gear icon)
- [ ] **Long-press** the subtitle settings button
- [ ] Verify debug menu appears
- [ ] Verify three options visible:
  - [ ] "Export Logs to File"
  - [ ] "Trigger Diagnostic Crash"
  - [ ] "Clear Logs"
- [ ] Verify "Close" button visible
- [ ] Tap "Close" and verify menu dismisses

### Test 2: Export Logs to File (No Credentials Configured)
- [ ] Clear subtitle credentials in settings
- [ ] Play a video
- [ ] Long-press subtitle settings button
- [ ] Tap "Export Logs to File"
- [ ] Verify toast shows file path
- [ ] Connect device via ADB
- [ ] Run: `adb pull /data/data/top.rootu.lampa/cache/subtitle_debug_*.log`
- [ ] Verify log file exists
- [ ] Open log file
- [ ] Verify log contains:
  - [ ] "No subtitle source credentials configured" warning
  - [ ] Timestamp in format "YYYY-MM-DD HH:MM:SS"
  - [ ] "LAMPA SUBTITLE DEBUG LOG" header

### Test 3: Export Logs to File (With Credentials)
- [ ] Configure valid OpenSubtitles API key OR Stremio addon URL
- [ ] Play a video
- [ ] Wait 5-10 seconds for subtitle search
- [ ] Long-press subtitle settings button
- [ ] Tap "Export Logs to File"
- [ ] Verify toast shows file path
- [ ] Pull log file via ADB
- [ ] Verify log contains:
  - [ ] "Starting subtitle search" message
  - [ ] "Attempting provider" messages
  - [ ] API URLs being called
  - [ ] HTTP response codes
  - [ ] Search results or "no results" message
  - [ ] Either download success or failure message

### Test 4: Trigger Diagnostic Crash
- [ ] Play a video (with or without credentials)
- [ ] Long-press subtitle settings button
- [ ] Tap "Trigger Diagnostic Crash"
- [ ] Verify debug menu closes
- [ ] Verify app crashes after ~500ms
- [ ] Verify crash activity appears
- [ ] Verify "Restart App" button visible
- [ ] Verify "Show Error Logs" button visible
- [ ] Tap "Show Error Logs"
- [ ] Verify bottom sheet opens with crash log
- [ ] Verify crash log includes:
  - [ ] Device model and Android version
  - [ ] App version
  - [ ] Main crash info mentioning "SubtitleDiagnosticException"
  - [ ] Subtitle debug logs embedded in crash report
  - [ ] "LAMPA SUBTITLE DEBUG LOG" section visible
- [ ] Tap "Copy Error Logs"
- [ ] Verify toast: "Copied to clipboard"
- [ ] Paste clipboard to verify logs copied
- [ ] Close bottom sheet
- [ ] Tap "Show Error Logs" again
- [ ] Tap "Save Error Logs"
- [ ] Verify toast shows save location
- [ ] Check file saved to: `/storage/emulated/0/Download/LAMPA/*.crashlog.txt`

### Test 5: Clear Logs
- [ ] Play a video and generate some logs
- [ ] Long-press subtitle settings button
- [ ] Tap "Clear Logs"
- [ ] Verify toast: "Subtitle debug logs cleared"
- [ ] Long-press subtitle settings button again
- [ ] Tap "Export Logs to File"
- [ ] Pull log file
- [ ] Verify log shows "No subtitle loading attempts recorded" OR only shows recent attempts after clear

## Provider-Specific Tests

### Test 6: OpenSubtitles Provider Logging
**With Valid API Key:**
- [ ] Configure valid OpenSubtitles API key
- [ ] Play a popular movie (e.g., with known IMDB ID)
- [ ] Wait for subtitle search
- [ ] Export logs
- [ ] Verify logs contain:
  - [ ] "Provider is enabled, attempting authentication"
  - [ ] "Authentication successful"
  - [ ] API URL with query parameters
  - [ ] HTTP response code 200
  - [ ] "Found X subtitle entries"
  - [ ] Download URL
  - [ ] File size in bytes
  - [ ] "Download successful" with path

**With Invalid API Key:**
- [ ] Configure invalid/expired API key
- [ ] Play a video
- [ ] Wait for subtitle search
- [ ] Export logs
- [ ] Verify logs contain:
  - [ ] "Failed to get authentication token" OR
  - [ ] "HTTP response code: 401" or "403"
  - [ ] Error message from API

**With No API Key (Username/Password):**
- [ ] Configure valid username and password (if available)
- [ ] Play a video
- [ ] Export logs
- [ ] Verify logs contain:
  - [ ] "Using cached JWT token" OR "Authenticating with username/password"
  - [ ] "JWT authentication successful"

### Test 7: Stremio Addon Provider Logging
**With Valid Addon URL:**
- [ ] Configure valid Stremio addon URL
- [ ] Play a video with known IMDB ID
- [ ] Wait for subtitle search
- [ ] Export logs
- [ ] Verify logs contain:
  - [ ] Addon URL in "Starting search" message
  - [ ] "Addon manifest verified, supports subtitles"
  - [ ] Subtitle endpoint URL
  - [ ] HTTP response code
  - [ ] Search results or "no results"

**With Invalid Addon URL:**
- [ ] Configure invalid addon URL (e.g., http://invalid.example.com)
- [ ] Play a video
- [ ] Wait for subtitle search
- [ ] Export logs
- [ ] Verify logs contain:
  - [ ] Error message about addon not supporting subtitles OR
  - [ ] HTTP error code (404, 500, etc.)

### Test 8: Multiple Provider Fallback
- [ ] Configure both OpenSubtitles AND Stremio addon
- [ ] Play an obscure video with no subtitles
- [ ] Wait for subtitle search
- [ ] Export logs
- [ ] Verify logs show:
  - [ ] First provider attempted (Stremio addons prioritized)
  - [ ] "No results" from first provider
  - [ ] Second provider attempted (OpenSubtitles)
  - [ ] "No results" or results from second provider
  - [ ] Final outcome: "SUCCESS" or "FAILED: No subtitles found from any provider"

## Edge Case Tests

### Test 9: No Network Connection
- [ ] Disable Wi-Fi and mobile data
- [ ] Configure OpenSubtitles API key
- [ ] Play a video
- [ ] Wait for subtitle search
- [ ] Export logs
- [ ] Verify logs contain network error messages

### Test 10: Long Press During Playback
- [ ] Play a video
- [ ] Wait until video is actively playing (not buffering)
- [ ] Long-press subtitle settings button during playback
- [ ] Verify debug menu appears without pausing video
- [ ] Tap "Export Logs to File"
- [ ] Verify log export works while video continues

### Test 11: Rapid Debug Menu Access
- [ ] Long-press subtitle settings button
- [ ] Immediately tap "Export Logs to File"
- [ ] Repeat 3 times quickly
- [ ] Verify app doesn't crash
- [ ] Verify each export creates a new timestamped file

### Test 12: Large Log Buffer
- [ ] Play 10 different videos in sequence
- [ ] Wait for subtitle search on each
- [ ] Export logs
- [ ] Verify log file is reasonable size (not MB)
- [ ] Verify only last ~200 entries are present (buffer limit)

## Regression Tests

### Test 13: Normal Subtitle Loading Still Works
- [ ] Configure valid OpenSubtitles API key
- [ ] Play a popular movie
- [ ] Verify subtitle automatically loads (if available)
- [ ] Verify subtitle appears on video
- [ ] Verify no crashes or errors

### Test 14: Manual Subtitle Selection Still Works
- [ ] Play a video with embedded subtitles
- [ ] Tap subtitle settings button (short press)
- [ ] Verify subtitle settings dialog appears (not debug menu)
- [ ] Change subtitle settings
- [ ] Verify changes apply

### Test 15: Track Selection Still Works
- [ ] Play a video with multiple subtitle tracks
- [ ] Tap track selection button
- [ ] Verify track selection dialog appears
- [ ] Select a different subtitle track
- [ ] Verify track changes

## Performance Tests

### Test 16: Logging Performance Impact
- [ ] Play a video and note startup time
- [ ] Enable subtitle search with logging
- [ ] Play same video again
- [ ] Verify no noticeable performance degradation
- [ ] Verify playback is smooth

### Test 17: Memory Usage
- [ ] Monitor app memory usage before test
- [ ] Generate lots of logs (play multiple videos)
- [ ] Monitor app memory usage after
- [ ] Verify memory increase is reasonable (<5MB)

## Documentation Verification

### Test 18: Documentation Accuracy
- [ ] Follow steps in SUBTITLE_DEBUG_USER_GUIDE.md
- [ ] Verify all steps work as documented
- [ ] Verify file paths match documentation
- [ ] Verify screenshot descriptions match actual UI

### Test 19: Quick Reference Accuracy
- [ ] Follow steps in SUBTITLE_DEBUG_QUICKREF.md
- [ ] Verify ADB commands work
- [ ] Verify error patterns match actual errors in logs

## Final Checks

### Test 20: Clean State
- [ ] Uninstall app
- [ ] Reinstall app
- [ ] Open video player
- [ ] Long-press subtitle settings button
- [ ] Verify debug menu works on fresh install

### Test 21: Different Android Versions (if available)
- [ ] Test on Android 7.0 (API 24)
- [ ] Test on Android 10.0 (API 29)
- [ ] Test on Android 13.0 (API 33) or latest
- [ ] Verify debug menu works on all versions

## Test Results Summary

### Pass Criteria
- [ ] All core functionality tests pass
- [ ] At least one provider-specific test passes for each provider
- [ ] No crashes during normal usage
- [ ] No regression in existing features
- [ ] Logs contain useful diagnostic information
- [ ] Documentation matches implementation

### Known Issues
(Document any issues found during testing)

---

## Notes for Testers

1. **Long-press duration**: Hold for ~1 second, should feel like a typical long-press gesture
2. **Log file retrieval**: Requires ADB or root file manager
3. **Crash logs**: Save to Downloads folder, easier to access than cache
4. **Provider priority**: Stremio addons are tried first, then OpenSubtitles
5. **Subtitle search**: Happens automatically 2 seconds after video starts (if no subtitle URL provided)

## Success Criteria

✅ Users can easily access debug logs
✅ Users can export logs to file
✅ Users can trigger diagnostic crash with logs
✅ Logs contain comprehensive diagnostic information
✅ No impact on normal app functionality
✅ Documentation is accurate and helpful
