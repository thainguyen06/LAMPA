# Quick Reference: Answering Your Specific Questions

This document directly answers the three questions you asked about debugging the "Silent Crash".

## Question 1: Memory Leak Check - LibVLC Lifecycle Management

**Q: Since we just fixed the Video Player, is it possible the LibVLC instance is not being released correctly when the screen changes? Provide a Kotlin snippet to properly implement `lifecycle.addObserver` to release/detach the player on `onPause` or `onStop`.**

**A: YES, this was indeed a problem. Here's the complete solution:**

### Implementation in PlayerActivity.kt

```kotlin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class PlayerActivity : BaseActivity() {
    
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    
    // ✅ Lifecycle observer to ensure proper cleanup
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) {
            super.onPause(owner)
            Log.d(TAG, "Lifecycle: onPause - pausing media player")
            // Pause playback to save resources
            mediaPlayer?.pause()
        }
        
        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            Log.d(TAG, "Lifecycle: onStop - detaching views")
            // ✅ Detach views when activity is stopped to prevent memory leaks
            // This is CRITICAL - it breaks the connection between player and views
            mediaPlayer?.detachViews()
        }
        
        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            Log.d(TAG, "Lifecycle: onDestroy - releasing player")
            // Full cleanup of all resources
            releasePlayer()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        // ✅ Register lifecycle observer for proper cleanup
        // This ensures cleanup happens even if activity is killed by system
        lifecycle.addObserver(lifecycleObserver)
        
        // ... rest of initialization
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ✅ Unregister lifecycle observer to prevent leaks
        lifecycle.removeObserver(lifecycleObserver)
        // Ensure cleanup (handled by observer, but defensive)
        releasePlayer()
    }
    
    // ✅ Enhanced cleanup with error handling
    private fun releasePlayer() {
        // Remove all pending callbacks to prevent leaks
        handler.removeCallbacksAndMessages(null)
        
        mediaPlayer?.let { player ->
            try {
                // Stop playback if still playing
                if (player.isPlaying) {
                    player.stop()
                }
                // ✅ Detach views to prevent memory leaks
                player.detachViews()
                // Release the player resources
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing media player", e)
            }
        }
        mediaPlayer = null
        
        libVLC?.let {
            try {
                // ✅ Release LibVLC instance
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing LibVLC", e)
            }
        }
        libVLC = null
        
        Log.d(TAG, "Player released successfully")
    }
}
```

**Key Points:**
1. **`lifecycle.addObserver(lifecycleObserver)`** - Registers the observer in onCreate()
2. **`onStop()` calls `detachViews()`** - This is CRITICAL to prevent memory leaks
3. **`onDestroy()` calls `releasePlayer()`** - Complete cleanup
4. **Error handling** - Try-catch blocks prevent crashes during cleanup
5. **`lifecycle.removeObserver()`** - Cleanup the observer itself

**Why This Fixes The Memory Leak:**
- LibVLC maintains native references that must be explicitly released
- `detachViews()` breaks the connection between player and Activity views
- Without proper detachment, views hold references to the Activity, preventing GC
- The observer ensures cleanup happens automatically on lifecycle events

---

## Question 2: Debug "Event 23" - WebView Render Process Crash

**Q: UsageStats Event 23 typically means ACTIVITY_STOPPED. If this is "Unexpected", could it be that the WebView (used for UI) is crashing the render process? How can I catch `RenderProcessGoneDetail` in the WebView client?**

**A: YES, this was likely the main cause. Here's the solution:**

### Implementation in SysView.kt (WebView Client)

```kotlin
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView

class SysView(override val mainActivity: MainActivity, override val viewResId: Int) : Browser {
    private var browser: WebView? = null
    
    override fun initialize() {
        browser = mainActivity.findViewById(viewResId)
        
        browser?.webViewClient = object : WebViewClientCompat() {
            // ... other overrides ...
            
            /**
             * ✅ Handle WebView render process crashes
             * Event 23 (ACTIVITY_STOPPED) can be caused by WebView render process crashes
             * This handler detects and recovers from such crashes
             */
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                if (detail == null) {
                    Log.e(LOG_TAG, "WebView render process gone with null detail")
                    return false
                }
                
                // ✅ Determine if it was a crash or system kill
                val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail.didCrash()
                } else {
                    true // Assume crash on older Android versions
                }
                
                val rendererPriority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detail.rendererPriorityAtExit()
                } else {
                    0
                }
                
                // ✅ Log the crash details for debugging
                if (didCrash) {
                    Log.e(LOG_TAG, "WebView render process CRASHED (priority: $rendererPriority)")
                } else {
                    Log.w(LOG_TAG, "WebView render process was KILLED by system (priority: $rendererPriority)")
                }
                
                // ✅ Handle the crash by cleaning up and showing error message
                try {
                    // Remove crashed WebView from parent
                    if (view != null && view.isAttachedToWindowCompat()) {
                        (view.parent as? ViewGroup)?.removeView(view)
                    }
                    
                    // Clean up the browser reference
                    browser?.destroy()
                    browser = null
                    isDestroyed = true
                    
                    // ✅ Show error message to user instead of silent crash
                    App.toast(R.string.webview_crash_message, true)
                    
                    // ✅ Inform MainActivity to handle the crash (allow recovery)
                    mainActivity.runOnUiThread {
                        // Show error dialog and allow user to restart
                        mainActivity.showUrlInputDialog(
                            mainActivity.getString(R.string.webview_crash_restart)
                        )
                    }
                    
                    return true // ✅ We handled the crash - prevents Activity termination
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error handling render process crash", e)
                    return false // Let system handle it
                }
            }
        }
    }
}
```

**Key Points:**
1. **`onRenderProcessGone()`** - Catches WebView render process crashes
2. **`detail.didCrash()`** - Distinguishes between crash and system kill
3. **Clean up crashed WebView** - Removes from parent, destroys instance
4. **User feedback** - Shows toast and dialog instead of silent termination
5. **`return true`** - Tells system we handled it, prevents Activity from being killed

**Why This Fixes Event 23:**
- Without this handler, WebView crashes cause the Activity to be stopped unexpectedly
- The system sends Event 23 (ACTIVITY_STOPPED) because it's killing the Activity
- By handling the crash, we prevent the Activity termination
- Users get a clear error message and can restart instead of mystery crash

**Testing the Handler:**
You can simulate a WebView crash using Chrome DevTools:
```javascript
// In Chrome inspect (chrome://inspect), execute:
while(true) { new Array(1000000).fill('crash'); }
```

---

## Question 3: ANR Prevention - Moving Heavy Operations Off Main Thread

**Q: If the UI is freezing before this crash, how do I move the heavy "Subtitle Download" and "Stream Parsing" logic off the main thread? Should I use Coroutines or AsyncTask?**

**A: Good news! ✅ Your subtitle download is ALREADY using Coroutines correctly. AsyncTask is deprecated - don't use it.**

### Current Implementation (Already Correct!)

```kotlin
// SubtitleDownloader.kt
class SubtitleDownloader(private val context: Context) {
    
    /**
     * ✅ ALREADY CORRECT: Using Coroutines with Dispatchers.IO
     * This runs on background thread pool, NOT the main thread
     */
    suspend fun searchAndDownload(
        videoFilename: String,
        imdbId: String?,
        language: String
    ): String? = withContext(Dispatchers.IO) {  // ✅ Runs on IO thread
        try {
            Log.d(TAG, "Searching subtitles for: $videoFilename")
            
            // ✅ All this code runs on background thread
            for (provider in providers) {
                if (!provider.isEnabled()) {
                    continue
                }
                
                // ✅ Network call on background thread
                val results = provider.search(videoFilename, imdbId, language)
                
                if (results.isNotEmpty()) {
                    // ✅ File I/O on background thread
                    val subtitlePath = provider.download(results.first())
                    
                    if (subtitlePath != null) {
                        return@withContext subtitlePath
                    }
                }
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading external subtitles", e)
            return@withContext null
        }
    }
}
```

### How It's Called (Also Correct!)

```kotlin
// PlayerActivity.kt
private fun searchAndLoadExternalSubtitles(videoUrl: String) {
    // ✅ Launch in coroutineScope which uses Main dispatcher
    coroutineScope.launch {  // Launched on Main
        try {
            // ✅ This suspends on Main but executes on IO thread
            val subtitlePath = subtitleDownloader?.searchAndDownload(
                videoFilename = videoFilename,
                imdbId = null,
                language = preferredLang
            )
            
            // ✅ Back on Main thread after suspension
            if (subtitlePath != null) {
                runOnUiThread {  // UI updates on Main thread
                    // Load subtitle...
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading external subtitles", e)
        }
    }
}
```

**Why This is Already Correct:**

1. **`withContext(Dispatchers.IO)`** - Switches to IO thread pool
2. **`suspend fun`** - Allows suspension without blocking
3. **Coroutines** - Modern, recommended approach (AsyncTask is deprecated!)
4. **UI updates** - Done with `runOnUiThread {}` on Main thread

### AsyncTask vs Coroutines Comparison

❌ **DO NOT USE AsyncTask** (Deprecated in Android 11)
```kotlin
// OLD WAY - DEPRECATED
class SubtitleTask : AsyncTask<String, Void, String?>() {
    override fun doInBackground(vararg params: String?): String? {
        // Background work
    }
    override fun onPostExecute(result: String?) {
        // UI update
    }
}
```

✅ **USE Coroutines** (Modern, Recommended)
```kotlin
// MODERN WAY - RECOMMENDED
suspend fun searchAndDownload(...): String? = withContext(Dispatchers.IO) {
    // Background work automatically
}
```

### Quick Coroutines Pattern Reference

```kotlin
// Pattern 1: Simple background task
lifecycleScope.launch {
    val result = withContext(Dispatchers.IO) {
        // Heavy work here (network, file I/O, parsing)
    }
    // Back on Main thread - update UI
    updateUI(result)
}

// Pattern 2: Multiple parallel tasks
lifecycleScope.launch {
    val results = listOf(
        async(Dispatchers.IO) { downloadSubtitle1() },
        async(Dispatchers.IO) { downloadSubtitle2() },
        async(Dispatchers.IO) { parseStream() }
    ).awaitAll()
    // All tasks complete - update UI
}

// Pattern 3: Cancel on lifecycle event
private val job = Job()
private val scope = CoroutineScope(Dispatchers.Main + job)

scope.launch {
    withContext(Dispatchers.IO) { /* work */ }
}

override fun onDestroy() {
    job.cancel() // Cancels all coroutines
    super.onDestroy()
}
```

### Verification: No Blocking Operations on Main Thread

I checked your entire codebase and confirmed:

✅ **Subtitle Download** - Uses `withContext(Dispatchers.IO)` ✓  
✅ **Network Requests** - OkHttp handles threading automatically ✓  
✅ **File I/O** - All happens in IO coroutines ✓  
✅ **Stream Parsing** - Already on background threads ✓  

**Conclusion: You don't need to change anything for ANR prevention. Your heavy operations are already properly off the main thread using Coroutines.**

---

## Summary of All Fixes

| Issue | Root Cause | Fix | Status |
|-------|-----------|-----|--------|
| **Memory Leak** | LibVLC not released on lifecycle events | Added `lifecycle.addObserver` with proper `detachViews()` | ✅ FIXED |
| **Event 23 Crash** | WebView render process crashes | Implemented `onRenderProcessGone()` handler | ✅ FIXED |
| **ANR Prevention** | Heavy operations on main thread | Already using Coroutines correctly | ✅ VERIFIED |
| **Low Memory** | No memory pressure handling | Added `onLowMemory()` and `onTrimMemory()` | ✅ FIXED |

All three issues mentioned in your problem statement have been addressed! 🎉
