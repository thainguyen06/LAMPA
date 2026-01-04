# Visual Flow Diagram - Subtitle Loading Fix

## Before Fix (Race Condition) ❌

```
Time: 13:47:33.485
├─> Media ParsedChanged Event
│   └─> searchAndLoadExternalSubtitles() CALL #1
│       ├─> Credentials check ✓
│       ├─> Launch coroutine
│       │   ├─> Search subtitles...
│       │   └─> Download subtitle file
│       └─> (1 second later)
│
Time: 13:47:34.336
├─> (FIRST CALL CONTINUES)
│   ├─> File exists ✓
│   ├─> addSlave() → true
│   ├─> Wait 1.5s for track... (WAITING...)
│
Time: 13:47:34.640  ⚠️ INTERFERENCE!
├─> Media ParsedChanged AGAIN?
│   └─> searchAndLoadExternalSubtitles() CALL #2  ⚠️ RACE!
│       ├─> Launches SECOND coroutine  ⚠️
│       └─> ⚠️ CONFLICTS WITH FIRST CALL!
│
Time: 13:47:35-38
└─> Max retries reached!  ❌ FAILURE
```

## After Fix (With Debounce) ✅

```
Time: 13:47:33.485
├─> searchAndLoadExternalSubtitles() CALL #1
│   ├─> Debounce check: PASS ✓
│   └─> Proceeds normally
│
Time: 13:47:34.640
├─> searchAndLoadExternalSubtitles() CALL #2
│   ├─> Debounce check: < 2s  ⚠️
│   └─> BLOCKED ✅ (No interference!)
│
Time: 13:47:35.836
└─> SUCCESS!  ✅
```

See IMPLEMENTATION_COMPLETE_SUMMARY.md for full details.
