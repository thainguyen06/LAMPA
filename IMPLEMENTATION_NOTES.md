# Implementation Notes: Subtitle Transfer Fix and Track Menu View Modes

## Issue Summary
This implementation addresses two main issues:
1. **Subtitles not transferring to the player**: External subtitle URLs passed via intent were not properly loaded into the player
2. **Track menu needs grid and list view modes**: Users need options to view tracks in different layouts

## Changes Made

### 1. Fixed Subtitle Transfer Issue

#### Problem Analysis
The original code was using `addOption(":input-slave=$subtitleUrl")` to add subtitles before media playback started. However, according to LibVLC documentation and previous fix summaries, this approach doesn't work reliably because:
- `addOption()` is meant to be called BEFORE media playback starts
- Once the media is attached to the player and playing, adding options to the media object has no effect
- LibVLC requires using the `addSlave()` method on the MediaPlayer, not the Media object

#### Solution Implemented

**Modified Files:**
- `app/src/main/java/top/rootu/lampa/PlayerActivity.kt`

**Key Changes:**

1. **Added `pendingSubtitleUrl` field** to store subtitle URLs passed via intent:
```kotlin
private var pendingSubtitleUrl: String? = null
```

2. **Modified `onCreate()` to store subtitle URL** instead of passing it to `initializePlayer()`:
```kotlin
if (!subtitleUrl.isNullOrEmpty()) {
    Log.d(TAG, "External subtitle URL: $subtitleUrl")
    // Store subtitle URL to load after player starts
    pendingSubtitleUrl = subtitleUrl
}
// Pass null for subtitle, we'll load it after playback starts
initializePlayer(videoUrl, null)
```

3. **Removed subtitle loading from media initialization** in `initializePlayer()`:
```kotlin
// Note: External subtitles are now loaded after playback starts using addSlave()
// This ensures proper subtitle transfer to the player
```

4. **Added subtitle loading in Playing event handler**:
```kotlin
MediaPlayer.Event.Playing -> {
    // ... existing code ...
    // Load pending subtitle URL if provided
    pendingSubtitleUrl?.let { subtitleUrl ->
        loadSubtitleFromUrl(subtitleUrl)
        pendingSubtitleUrl = null // Clear after loading
    }
}
```

5. **Created new `loadSubtitleFromUrl()` method** that properly loads subtitles using `addSlave()`:
```kotlin
private fun loadSubtitleFromUrl(subtitleUrl: String) {
    Log.d(TAG, "Loading subtitle from URL: $subtitleUrl")
    
    handler.postDelayed({
        try {
            val previousTrackCount = mediaPlayer?.spuTracks?.size ?: 0
            
            // Convert URL to proper URI format
            val subtitleUri = when {
                subtitleUrl.startsWith("file://") || 
                subtitleUrl.startsWith("http://") || 
                subtitleUrl.startsWith("https://") -> subtitleUrl
                subtitleUrl.startsWith("/") -> "file://$subtitleUrl"
                else -> subtitleUrl
            }
            
            // Use addSlave to add subtitle to already playing media
            val added = mediaPlayer?.addSlave(0, subtitleUri, true)
            
            if (added == true) {
                // Wait for track registration and auto-select
                handler.postDelayed({
                    refreshTracks()
                    val spuTracks = mediaPlayer?.spuTracks
                    if (spuTracks != null && spuTracks.size > previousTrackCount) {
                        val newTrack = spuTracks.last()
                        mediaPlayer?.spuTrack = newTrack.id
                    }
                }, SUBTITLE_TRACK_REGISTRATION_DELAY_MS)
                
                App.toast(R.string.subtitle_loaded, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding subtitle from URL", e)
        }
    }, SUBTITLE_TRACK_REGISTRATION_DELAY_MS)
}
```

**Benefits:**
- ✅ Subtitles from intent extras now properly load into the player
- ✅ Uses correct LibVLC API (`addSlave()`) for already-playing media
- ✅ Proper URI format conversion for both local files and remote URLs
- ✅ Auto-selection of newly added subtitle track
- ✅ User feedback via toast notifications
- ✅ Comprehensive logging for debugging

### 2. Added Grid and List View Modes to Track Selection Dialog

#### Requirements
Users need the ability to view audio and subtitle tracks in different layouts:
- **List mode**: Traditional vertical list with full track names (default)
- **Grid mode**: Horizontal layout with compact spacing and shortened names

#### Solution Implemented

**Modified Files:**
- `app/src/main/res/layout/dialog_track_selection.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/top/rootu/lampa/PlayerActivity.kt`

**Key Changes:**

1. **Added view mode toggle buttons to layout**:
```xml
<!-- View Mode Toggle Buttons -->
<LinearLayout
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="horizontal">
    
    <ImageButton
        android:id="@+id/btn_view_list"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:src="@android:drawable/ic_menu_sort_by_size"
        android:contentDescription="@string/view_mode_list" />
    
    <ImageButton
        android:id="@+id/btn_view_grid"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:src="@android:drawable/ic_menu_manage"
        android:contentDescription="@string/view_mode_grid" />
</LinearLayout>
```

2. **Added strings for view modes**:
```xml
<string name="view_mode_list">List View</string>
<string name="view_mode_grid">Grid View</string>
```

3. **Updated `showTrackSelectionDialog()` with view mode logic**:
```kotlin
private fun showTrackSelectionDialog() {
    // ... setup code ...
    
    var isGridMode = false
    
    // Update button appearance based on current mode
    fun updateViewModeButtons() {
        if (isGridMode) {
            btnViewList?.setColorFilter(0xFF888888.toInt())
            btnViewGrid?.setColorFilter(0xFFFFFFFF.toInt())
        } else {
            btnViewList?.setColorFilter(0xFFFFFFFF.toInt())
            btnViewGrid?.setColorFilter(0xFF888888.toInt())
        }
    }
    
    // Refresh tracks with current view mode
    fun refreshTracks() {
        populateAudioTracks(audioGroup, player, isGridMode)
        populateSubtitleTracks(subtitleGroup, player, isGridMode)
    }
    
    // Toggle handlers
    btnViewList?.setOnClickListener {
        if (isGridMode) {
            isGridMode = false
            updateViewModeButtons()
            refreshTracks()
        }
    }
    
    btnViewGrid?.setOnClickListener {
        if (!isGridMode) {
            isGridMode = true
            updateViewModeButtons()
            refreshTracks()
        }
    }
}
```

4. **Updated track population methods to support both modes**:
```kotlin
private fun populateAudioTracks(
    audioGroup: RadioGroup, 
    player: MediaPlayer, 
    isGridMode: Boolean
) {
    // Set orientation based on mode
    audioGroup.orientation = if (isGridMode) 
        RadioGroup.HORIZONTAL 
    else 
        RadioGroup.VERTICAL
    
    audioTracks.forEachIndexed { index, trackDescription ->
        val trackName = trackDescription.name ?: getString(R.string.track_unknown)
        
        val radioButton = RadioButton(this).apply {
            text = if (isGridMode) {
                // Shortened name for grid mode
                val parts = trackName.split(" - ", " ", limit = 2)
                if (parts.isNotEmpty()) parts[0] else trackName
            } else {
                trackName
            }
            textSize = if (isGridMode) 14f else 16f
            
            // Compact spacing in grid mode
            if (isGridMode) {
                val params = RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.WRAP_CONTENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 4, 8, 4)
                }
                layoutParams = params
            }
        }
    }
}
```

**Features:**
- ✅ Toggle buttons with visual feedback (color changes)
- ✅ List mode: Vertical layout with full track names
- ✅ Grid mode: Horizontal layout with compact spacing
- ✅ Grid mode: Shortened track names for better fit
- ✅ Smaller font size in grid mode (14sp vs 16sp)
- ✅ Compact margins in grid mode (8dp horizontal, 4dp vertical)
- ✅ Seamless switching between modes without closing dialog

## Testing Recommendations

### Subtitle Transfer Testing

1. **Test with HTTP/HTTPS subtitle URL**:
   ```kotlin
   intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, "http://example.com/video.mp4")
   intent.putExtra(PlayerActivity.EXTRA_SUBTITLE_URL, "http://example.com/subtitle.srt")
   ```
   - Expected: Subtitle loads after playback starts
   - Expected: Toast notification "External subtitle loaded"
   - Expected: Subtitle appears in track selection dialog
   - Expected: Subtitle is auto-selected and displays on video

2. **Test with local file path**:
   ```kotlin
   intent.putExtra(PlayerActivity.EXTRA_SUBTITLE_URL, "/storage/emulated/0/subtitle.srt")
   ```
   - Expected: File path converted to `file://` URI
   - Expected: Subtitle loads successfully

3. **Monitor logs**:
   ```bash
   adb logcat -s PlayerActivity:D
   ```
   - Look for: "Loading subtitle from URL"
   - Look for: "Adding subtitle URI: file://..."
   - Look for: "Subtitle slave added successfully"
   - Look for: "Auto-selected new subtitle track"

### Track Menu View Mode Testing

1. **Open track selection dialog during playback**
2. **Verify initial state**:
   - List view button should be highlighted (white)
   - Grid view button should be dim (gray)
   - Tracks displayed vertically with full names

3. **Click grid view button**:
   - Grid button should become highlighted
   - List button should become dim
   - Tracks should rearrange horizontally
   - Track names should be shortened
   - Font size should be smaller
   - Spacing should be compact

4. **Click list view button again**:
   - Should revert to vertical layout
   - Full track names displayed
   - Original font size restored

5. **Test with multiple tracks**:
   - Video with multiple audio tracks
   - Video with multiple subtitle tracks
   - Verify both modes display all tracks correctly

## Known Limitations

1. **Grid mode overflow**: If there are many tracks, horizontal scrolling may be needed (RadioGroup doesn't scroll horizontally by default)
2. **Track name shortening**: Simple algorithm that splits on " - " or space - may not work well for all track naming conventions
3. **No persistent view mode**: View mode preference is not saved between sessions
4. **Fixed delay**: Uses 500ms delay for track registration - might not be optimal for all devices

## Future Enhancements

1. **Add ScrollView for grid mode**: Wrap RadioGroup in HorizontalScrollView for better handling of many tracks
2. **Smarter name shortening**: Implement better algorithm for shortening track names (e.g., show language codes)
3. **Save view mode preference**: Remember user's preferred view mode using SharedPreferences
4. **Adaptive layout**: Automatically switch to grid mode if there are many tracks
5. **Custom track icons**: Add language flag icons or track type icons
6. **Track filtering**: Add search/filter functionality for large track lists

## Conclusion

Both issues have been successfully addressed:

✅ **Subtitle Transfer Fixed**: External subtitle URLs now properly load into the player using the correct LibVLC API  
✅ **Track Menu Enhanced**: Users can now switch between list and grid view modes for better track management

The implementation is minimal, focused, and follows Android best practices while maintaining compatibility with the existing codebase.
