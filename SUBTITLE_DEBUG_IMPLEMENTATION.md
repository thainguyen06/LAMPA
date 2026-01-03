# Subtitle Debugging Implementation Summary

## Problem Statement
User reported: "Subtitles are still not loading from either Stremio or OpenSubtitle. Please crash the device to retrieve the logs."

## Solution Implemented

### 1. SubtitleDebugHelper Class
Created a comprehensive logging system (`SubtitleDebugHelper.kt`) that:
- **Tracks all subtitle loading attempts** with timestamps
- **Records debug, info, warning, and error messages** from all subtitle providers
- **Stores stack traces** for exceptions
- **Maintains a rolling buffer** of the last 200 log entries
- **Exports logs to files** for sharing and debugging
- **Triggers diagnostic crashes** with full subtitle logs embedded

### 2. Enhanced Logging in Subtitle Providers

#### OpenSubtitlesProvider
Added detailed logging for:
- Provider enable/disable status
- Authentication attempts (API key or username/password)
- API URLs being called
- HTTP response codes
- Response body sizes
- Number of results found
- Download progress and file sizes
- All exceptions with stack traces

#### StremioAddonProvider
Added detailed logging for:
- Addon URL configuration
- Manifest verification
- Subtitle endpoint construction
- API responses
- Result parsing
- Download progress and file sizes
- All exceptions with stack traces

#### SubtitleDownloader
Added orchestration logging for:
- Overall search initiation
- Provider iteration
- Success/failure per provider
- Final outcome (success or no subtitles found)

#### PlayerActivity
Added integration logging for:
- Credential check status
- Video filename extraction
- Subtitle download completion
- LibVLC addSlave() results
- Track selection status

### 3. User Interface - Subtitle Debug Menu

Added a **long-press handler** on the subtitle settings button in the player that opens a debug menu with three options:

1. **Export Logs to File**
   - Exports all subtitle debug logs to a timestamped file
   - Saves to both cache directory and Backup directory
   - Shows toast with file location
   - File format: `subtitle_debug_YYYY-MM-DD_HH-mm-ss.log`

2. **Trigger Diagnostic Crash**
   - Intentionally crashes the app with all subtitle logs embedded
   - Triggers the existing crash handler (CrashActivity)
   - Logs can be viewed, copied, or saved from crash screen
   - Useful for capturing logs via crash reporting tools

3. **Clear Logs**
   - Clears the log buffer to start fresh
   - Useful before testing a specific scenario

## How to Use

### For End Users Troubleshooting Subtitle Issues:

1. **Start video playback** where subtitles are not loading
2. **Long-press the subtitle settings button** (gear icon) in the player
3. **Select one of the debug options:**
   
   **Option A - Export Logs (Recommended):**
   - Tap "Export Logs to File"
   - Note the file path shown in the toast message
   - Share the log file with support or developers
   
   **Option B - Trigger Crash (As Requested):**
   - Tap "Trigger Diagnostic Crash"
   - App will crash and show crash screen
   - Tap "Show Error Logs" button
   - Copy or save the crash log which includes subtitle diagnostics
   - Share the crash log with support or developers

### Reading the Logs

The log file contains:
```
===============================================
LAMPA SUBTITLE DEBUG LOG
Generated: 2026-01-03 10:15:00
===============================================

[10:15:01.234] [INFO] [PlayerActivity] searchAndLoadExternalSubtitles called for: https://...
[10:15:01.235] [INFO] [PlayerActivity] Credentials found, proceeding with search
[10:15:01.236] [DEBUG] [PlayerActivity] Preferred subtitle language: en
[10:15:01.237] [DEBUG] [PlayerActivity] Video filename: movie.mkv
[10:15:01.238] [INFO] [SubtitleDownloader] === Starting subtitle search ===
[10:15:01.239] [INFO] [SubtitleDownloader] Attempting provider: OpenSubtitles
[10:15:01.240] [INFO] [OpenSubtitles] Starting search: query='movie.mkv', imdbId='null', lang='en'
[10:15:01.241] [DEBUG] [OpenSubtitles] Provider is enabled, attempting authentication
[10:15:01.500] [DEBUG] [OpenSubtitles] Authentication successful
[10:15:01.501] [INFO] [OpenSubtitles] API URL: https://api.opensubtitles.com/api/v1/subtitles?query=movie.mkv&languages=en
[10:15:01.502] [DEBUG] [OpenSubtitles] Sending HTTP request...
[10:15:02.100] [INFO] [OpenSubtitles] HTTP response code: 200
[10:15:02.101] [DEBUG] [OpenSubtitles] Response body length: 15234 bytes
[10:15:02.102] [INFO] [OpenSubtitles] Found 5 subtitle entries in response
[10:15:02.103] [DEBUG] [OpenSubtitles] Added result: fileId=12345, release='Movie.2023.1080p'
...
```

### Common Scenarios Covered

**Scenario 1: No credentials configured**
```
[WARNING] [PlayerActivity] No subtitle source credentials configured
```
**Solution:** Configure API key or Stremio addon URL in settings

**Scenario 2: Authentication failed**
```
[ERROR] [OpenSubtitles] Failed to get authentication token - check API key or username/password
```
**Solution:** Check that API key or credentials are valid

**Scenario 3: API returned error**
```
[ERROR] [OpenSubtitles] Search failed: HTTP 401 - Unauthorized
```
**Solution:** Check API key validity or account status

**Scenario 4: No subtitles found**
```
[INFO] [OpenSubtitles] Found 0 subtitle entries in response
[WARNING] [SubtitleDownloader] === FAILED: No subtitles found from any provider ===
```
**Expected:** This is normal for obscure content

**Scenario 5: Download failed**
```
[ERROR] [OpenSubtitles] File download failed: HTTP 403
```
**Solution:** Check network connection or API rate limits

**Scenario 6: LibVLC failed to load subtitle**
```
[ERROR] [PlayerActivity] addSlave() returned false
```
**Solution:** Check file path, permissions, or subtitle format compatibility

## Technical Details

### Log Entry Structure
```kotlin
data class LogEntry(
    val timestamp: Long,          // When the event occurred
    val level: LogLevel,          // DEBUG, INFO, WARNING, ERROR
    val provider: String,         // Which component logged this
    val message: String,          // The log message
    val stackTrace: String?       // Exception stack trace if applicable
)
```

### Log Buffer Management
- Maximum 200 entries kept in memory
- Older entries automatically removed (FIFO)
- Logs cleared on app restart
- Manual clear available via debug menu

### File Export
- Exports to: `/data/data/top.rootu.lampa/cache/subtitle_debug_YYYY-MM-DD_HH-mm-ss.log`
- Also attempts to save to Backup directory if available
- Returns file path on success, null on failure

### Crash Integration
- Uses existing CrashActivity infrastructure
- Subtitle logs embedded in crash report
- Full device info and stack traces included
- Crash handler already configured in App.kt

## Files Modified

1. **SubtitleDebugHelper.kt** (NEW)
   - Core logging functionality
   - Log collection and formatting
   - Export and crash trigger methods

2. **OpenSubtitlesProvider.kt**
   - Added SubtitleDebugHelper calls throughout
   - Enhanced logging in search() method
   - Enhanced logging in download() method

3. **StremioAddonProvider.kt**
   - Added SubtitleDebugHelper calls throughout
   - Enhanced logging in search() method
   - Enhanced logging in download() method

4. **SubtitleDownloader.kt**
   - Added orchestration logging
   - Logs provider iteration and results

5. **PlayerActivity.kt**
   - Added SubtitleDebugHelper import
   - Enhanced logging in searchAndLoadExternalSubtitles()
   - Added long-press handler on subtitle settings button
   - Added showSubtitleDebugMenu() method

6. **dialog_subtitle_debug.xml** (NEW)
   - Debug menu layout
   - Three action buttons (Export, Crash, Clear)
   - Close button

7. **strings.xml**
   - Added subtitle debug strings
   - Added close string

## Benefits

1. **No Code Changes Needed for Testing**
   - Users can enable detailed logging without rebuilding
   - Logs captured automatically during normal usage

2. **Multiple Export Options**
   - Export to file for sharing
   - Trigger crash for crash reporting tools
   - Both include full diagnostic information

3. **Easy Access**
   - Long-press gesture on existing button
   - No hidden developer menus
   - Intuitive button labels

4. **Comprehensive Information**
   - Full API request/response logging
   - HTTP status codes
   - File sizes and paths
   - Exception stack traces
   - Timing information

5. **Production Ready**
   - Minimal performance impact (only last 200 entries)
   - No sensitive data logged (passwords/keys shown as configured, not values)
   - Works with existing crash handler
   - No external dependencies

## Future Enhancements

1. Add log filtering by provider or level
2. Add log sharing via Android share intent
3. Add automatic log export on repeated failures
4. Add visual indicator when logs are being captured
5. Add log upload to support server
6. Add performance metrics (API latency, download speed)
7. Add subtitle format validation logging

## Conclusion

This implementation provides a comprehensive debugging solution for subtitle loading issues. Users can now easily capture detailed logs to diagnose why subtitles aren't loading, whether from Stremio or OpenSubtitles. The ability to trigger a diagnostic crash (as specifically requested) provides a failsafe way to capture logs via the existing crash reporting infrastructure.
