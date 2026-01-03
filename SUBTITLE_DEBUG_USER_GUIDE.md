# Subtitle Debug Feature - User Guide

## Quick Start: How to Retrieve Subtitle Logs

### Method 1: Export Logs to File (Recommended)

1. **Open the video player** and play a video where subtitles are not loading

2. **Long-press the subtitle settings button** (gear icon) in the player controls:
   ```
   Player Controls → Subtitle Settings Button (Long Press)
   ```

3. **Debug menu will appear** with three options

4. **Tap "Export Logs to File"**

5. **Note the file path** shown in the toast message:
   ```
   Subtitle logs exported to: /data/data/top.rootu.lampa/cache/subtitle_debug_2026-01-03_10-15-30.log
   ```

6. **Retrieve the file** using:
   - ADB: `adb pull /data/data/top.rootu.lampa/cache/subtitle_debug_*.log`
   - File manager app with root access
   - Backup directory if available

7. **Share the log file** with support or developers for analysis

---

### Method 2: Trigger Diagnostic Crash (As Requested)

This method intentionally crashes the app to capture logs via the crash handler, which was specifically requested in the issue.

1. **Open the video player** and play a video where subtitles are not loading

2. **Long-press the subtitle settings button** (gear icon) in the player controls

3. **Debug menu will appear** with three options

4. **Tap "Trigger Diagnostic Crash"** (red button)

5. **App will crash** and show the crash activity screen:
   ```
   ╔══════════════════════════════════════╗
   ║  App Crashed                         ║
   ║  [Restart App]  [Show Error Logs]   ║
   ╚══════════════════════════════════════╝
   ```

6. **Tap "Show Error Logs"** button

7. **View the crash log** which includes:
   - Device information
   - App version
   - Main crash info
   - **Complete subtitle debug logs** embedded in crash report
   - Stack traces

8. **Copy or Save** the crash log:
   - Tap "Copy Error Logs" to copy to clipboard
   - Tap "Save Error Logs" to save to file
   - File saved to: `/storage/emulated/0/Download/LAMPA/YYYY-MM-DD HH-mm.crashlog.txt`

9. **Share the crash log** with support or developers

---

## Debug Menu Options Explained

### 🔍 Export Logs to File
- **What it does**: Saves all subtitle loading logs to a timestamped file
- **File location**: Cache directory (and Backup directory if available)
- **When to use**: When you want to share logs without crashing the app
- **Best for**: Remote debugging, email support, GitHub issues

### 💥 Trigger Diagnostic Crash
- **What it does**: Intentionally crashes the app with logs embedded in crash report
- **Crash handler**: Uses existing LAMPA crash activity
- **When to use**: When you want logs via crash reporting mechanism
- **Best for**: Crash reporting tools, Sentry, Crashlytics integration

### 🗑️ Clear Logs
- **What it does**: Clears the log buffer
- **When to use**: Before testing a specific scenario to get clean logs
- **Best for**: Isolating a specific subtitle loading attempt

---

## UI Flow

```
Video Player Screen
       │
       │ [Long Press Subtitle Settings Button]
       │
       ▼
┌─────────────────────────────┐
│   Subtitle Debug Menu       │
│                             │
│  [Export Logs to File]      │  ← Save to file
│  [Trigger Diagnostic Crash] │  ← Crash with logs
│  [Clear Logs]               │  ← Clear buffer
│  [Close]                    │  ← Cancel
└─────────────────────────────┘
       │
       │ [If Export Logs]
       │
       ▼
Toast: "Subtitle logs exported to: /path/to/file"


       │
       │ [If Trigger Crash]
       │
       ▼
┌─────────────────────────────┐
│   App Crashed               │
│                             │
│   [Restart App]             │
│   [Show Error Logs]         │  ← View logs
└─────────────────────────────┘
       │
       │ [Show Error Logs]
       │
       ▼
┌─────────────────────────────┐
│   Error Log Sheet           │
│                             │
│   [Complete crash log with  │
│    subtitle debug info]     │
│                             │
│   [Copy Logs] [Save Logs]   │
│   [Close]                   │
└─────────────────────────────┘
```

---

## What Information is Captured

The logs include detailed information about:

### 1. Configuration
- Whether credentials are configured
- Preferred subtitle language
- Video filename extraction

### 2. Provider Attempts
- Which providers are enabled/disabled
- Order of provider attempts
- Why providers are skipped

### 3. API Calls
- Full API URLs
- HTTP request headers
- HTTP response codes
- Response body sizes
- Authentication status

### 4. Search Results
- Number of results found
- Result details (file IDs, names, languages)
- Why results were filtered

### 5. Downloads
- Download URLs
- File sizes
- Download success/failure
- File paths

### 6. Player Integration
- LibVLC addSlave() success/failure
- Track registration status
- Auto-selection results

### 7. Errors
- Exception messages
- Full stack traces
- HTTP error responses

---

## Example Log Output

Here's what you'll see in the exported logs:

```
===============================================
LAMPA SUBTITLE DEBUG LOG
Generated: 2026-01-03 10:15:00
===============================================

[10:15:01.234] [INFO] [PlayerActivity] searchAndLoadExternalSubtitles called for: https://example.com/video.mkv
[10:15:01.235] [INFO] [PlayerActivity] Credentials found, proceeding with search
[10:15:01.236] [DEBUG] [PlayerActivity] Preferred subtitle language: en
[10:15:01.237] [DEBUG] [PlayerActivity] Video filename: video.mkv
[10:15:01.238] [INFO] [PlayerActivity] Launching coroutine for subtitle search
[10:15:01.239] [INFO] [SubtitleDownloader] === Starting subtitle search ===
[10:15:01.240] [INFO] [SubtitleDownloader] Video: 'video.mkv', IMDB: 'null', Language: 'en'
[10:15:01.241] [INFO] [SubtitleDownloader] Attempting provider: Stremio Addon (opensubtitles-v3.strem.io)
[10:15:01.242] [INFO] [Stremio Addon (opensubtitles-v3.strem.io)] Starting search: query='video.mkv', imdbId='null', lang='en', addon='https://opensubtitles-v3.strem.io'
[10:15:01.500] [DEBUG] [Stremio Addon (opensubtitles-v3.strem.io)] Addon manifest verified, supports subtitles
[10:15:01.501] [INFO] [Stremio Addon (opensubtitles-v3.strem.io)] API URL: https://opensubtitles-v3.strem.io/subtitles/search/video.mkv.json
[10:15:02.100] [INFO] [Stremio Addon (opensubtitles-v3.strem.io)] HTTP response code: 404
[10:15:02.101] [ERROR] [Stremio Addon (opensubtitles-v3.strem.io)] Subtitle search failed: HTTP 404
[10:15:02.102] [DEBUG] [SubtitleDownloader] Provider Stremio Addon (opensubtitles-v3.strem.io) returned no results
[10:15:02.103] [INFO] [SubtitleDownloader] Attempting provider: OpenSubtitles
[10:15:02.104] [INFO] [OpenSubtitles] Starting search: query='video.mkv', imdbId='null', lang='en'
[10:15:02.105] [DEBUG] [OpenSubtitles] Provider is enabled, attempting authentication
[10:15:02.300] [DEBUG] [OpenSubtitles] Authentication successful
[10:15:02.301] [INFO] [OpenSubtitles] API URL: https://api.opensubtitles.com/api/v1/subtitles?query=video.mkv&languages=en
[10:15:03.100] [INFO] [OpenSubtitles] HTTP response code: 200
[10:15:03.101] [DEBUG] [OpenSubtitles] Response body length: 15234 bytes
[10:15:03.102] [INFO] [OpenSubtitles] Found 5 subtitle entries in response
[10:15:03.103] [DEBUG] [OpenSubtitles] Added result: fileId=12345, release='Video.2023.1080p'
[10:15:03.104] [INFO] [OpenSubtitles] Search completed with 5 results
[10:15:03.105] [INFO] [SubtitleDownloader] Provider OpenSubtitles found 5 results
[10:15:03.106] [INFO] [OpenSubtitles] Starting download: name='Video.2023.1080p', id='12345'
[10:15:03.107] [DEBUG] [OpenSubtitles] Requesting download link for file_id=12345
[10:15:03.500] [INFO] [OpenSubtitles] Download request response: HTTP 200
[10:15:03.501] [DEBUG] [OpenSubtitles] Got download link: https://api.opensubtitles.com/...
[10:15:04.200] [INFO] [OpenSubtitles] File download response: HTTP 200
[10:15:04.201] [DEBUG] [OpenSubtitles] Cache directory created: true
[10:15:04.300] [INFO] [OpenSubtitles] Download successful: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1735902904300.srt (45678 bytes)
[10:15:04.301] [INFO] [SubtitleDownloader] === SUCCESS: Downloaded from OpenSubtitles ===
[10:15:04.302] [INFO] [PlayerActivity] Subtitle downloaded successfully: /data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1735902904300.srt
[10:15:04.303] [DEBUG] [PlayerActivity] Adding subtitle to player: file:///data/user/0/top.rootu.lampa/cache/subtitle_cache/subtitle_en_1735902904300.srt
[10:15:04.304] [INFO] [PlayerActivity] Subtitle slave added successfully to LibVLC
[10:15:04.805] [INFO] [PlayerActivity] Auto-selected subtitle track: English

===============================================
END OF LOG
===============================================
```

---

## Troubleshooting Common Issues

### Issue: Debug menu doesn't appear
- **Solution**: Make sure you're **long-pressing** (not short tapping) the subtitle settings button
- The button is the gear icon in the player controls

### Issue: Can't find exported log file
- **Solution 1**: Check the toast message for the exact path
- **Solution 2**: Use ADB to pull the file: `adb pull /data/data/top.rootu.lampa/cache/subtitle_debug_*.log .`
- **Solution 3**: Use a file manager app with root access

### Issue: Crash log is too long
- **Solution**: Save the crash log to file instead of copying to clipboard
- The file will be in the Download folder under LAMPA directory

### Issue: Need to test multiple scenarios
- **Solution**: Use "Clear Logs" between tests to isolate each attempt
- This gives you clean logs for each scenario

---

## Privacy Note

The logs contain:
- ✅ API URLs (safe to share)
- ✅ HTTP status codes (safe to share)
- ✅ File sizes and paths (safe to share)
- ✅ Video filenames (may contain personal info - review before sharing)
- ✅ IMDB IDs (safe to share)
- ⚠️ Does NOT contain API keys or passwords (they are checked but not logged)

Always review logs before sharing publicly to ensure no personal information is included.

---

## Support Information

When reporting subtitle loading issues, please include:
1. **Exported log file** or **crash log**
2. **Device model** and **Android version**
3. **App version** (shown in settings or crash log)
4. **Which subtitle source** you're using (Stremio addon URL, OpenSubtitles API key, etc.)
5. **Brief description** of the issue

This information helps developers quickly identify and fix subtitle loading problems.
