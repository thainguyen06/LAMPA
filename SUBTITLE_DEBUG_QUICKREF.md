# Subtitle Debug - Quick Reference

## 🚀 Quick Access
**Long-press the subtitle settings button (gear icon) in video player**

## 📋 Three Options

### 1. 📁 Export Logs to File
**When to use:** Share logs without crashing
```
Action: Saves logs to timestamped file
Location: /data/data/top.rootu.lampa/cache/subtitle_debug_*.log
Retrieve: adb pull /data/data/top.rootu.lampa/cache/subtitle_debug_*.log
```

### 2. 💥 Trigger Diagnostic Crash
**When to use:** Capture logs via crash handler (as requested in issue)
```
Action: Intentionally crashes app with logs embedded
Result: Crash screen with "Show Error Logs" button
Logs include: Complete subtitle diagnostics + device info + stack traces
Save to: /storage/emulated/0/Download/LAMPA/*.crashlog.txt
```

### 3. 🗑️ Clear Logs
**When to use:** Clear buffer before testing specific scenario
```
Action: Clears log buffer
Result: Fresh logs for next subtitle loading attempt
```

## 📊 What Gets Logged

| Category | Details |
|----------|---------|
| Config | Credentials status, language preferences |
| Providers | Which providers tried, enable/disable status |
| API Calls | URLs, headers, response codes, body sizes |
| Authentication | API key or username/password validation |
| Search Results | Number found, file IDs, names, languages |
| Downloads | URLs, file sizes, success/failure, paths |
| Player | LibVLC addSlave() results, track selection |
| Errors | Exception messages, stack traces, HTTP errors |

## 🔍 Common Error Patterns

| Log Message | Meaning | Solution |
|-------------|---------|----------|
| `No subtitle source credentials configured` | No API key or addon URL set | Configure in settings |
| `Failed to get authentication token` | API key or credentials invalid | Check API key or username/password |
| `HTTP response code: 401` | Unauthorized | API key expired or invalid |
| `HTTP response code: 403` | Forbidden | Rate limit or permissions issue |
| `HTTP response code: 404` | Not found | Subtitle doesn't exist for this video |
| `Found 0 results` | No subtitles available | Normal for obscure content |
| `addSlave() returned false` | LibVLC failed to load | Check file format or path |
| `Provider X is disabled` | No credentials for this provider | Configure provider settings |

## 📱 ADB Commands

```bash
# Pull latest subtitle debug log
adb pull /data/data/top.rootu.lampa/cache/subtitle_debug_*.log .

# View log in terminal
adb shell cat /data/data/top.rootu.lampa/cache/subtitle_debug_*.log

# Clear all subtitle debug logs
adb shell rm /data/data/top.rootu.lampa/cache/subtitle_debug_*.log

# Pull crash logs
adb pull /storage/emulated/0/Download/LAMPA/*.crashlog.txt .
```

## 🎯 Recommended Workflow

### For Troubleshooting:
1. **Clear logs** (to start fresh)
2. **Play video** where subtitles don't load
3. **Wait for subtitle search** to complete (~ 5-10 seconds)
4. **Export logs** to file
5. **Review logs** to identify the issue
6. **Share with support** if needed

### For Bug Reporting:
1. **Reproduce the issue** with logging active
2. **Either:**
   - Export logs to file (Method 1), OR
   - Trigger diagnostic crash (Method 2)
3. **Include in bug report:**
   - Log file or crash log
   - Device model and Android version
   - App version
   - Subtitle source (Stremio addon URL or OpenSubtitles API key status)
   - Brief description of issue

## 🔒 Privacy

✅ **Safe to share:**
- API URLs
- HTTP status codes
- File sizes and paths (except video filenames)
- IMDB IDs
- Error messages

⚠️ **Review before sharing:**
- Video filenames (may contain personal info)

❌ **NOT logged (protected):**
- API keys (only "configured" status logged)
- Passwords (only "configured" status logged)

## 📞 Support

When reporting issues, include:
1. Log file or crash log
2. Device info (model, Android version, app version)
3. Which subtitle source you're using
4. Steps to reproduce

---

**Need more details?** See:
- SUBTITLE_DEBUG_USER_GUIDE.md - Detailed user guide with examples
- SUBTITLE_DEBUG_IMPLEMENTATION.md - Technical implementation details
