# Stremio Addon Support for Subtitles - User Guide

## Overview

This guide explains how to use Stremio addons for subtitle support in LAMPA. Stremio addons provide a standardized, reliable way to access subtitles without needing to scrape websites or manage multiple API keys.

## What is a Stremio Addon?

Stremio addons follow a standard protocol that makes them easy to integrate. They provide:
- A manifest describing their capabilities
- Standard API endpoints for searching and retrieving content
- Subtitle support through a simple JSON-based API

## Benefits of Using Stremio Addons

1. **Reliability**: Addons follow a standard protocol, reducing compatibility issues
2. **No API Keys**: Most Stremio addons don't require authentication
3. **Multiple Sources**: Many subtitle addons are available
4. **Easy Configuration**: Just enter the addon URL

## How to Configure

### Method 1: Using Settings Activity

1. Open LAMPA app
2. Go to Settings (from main menu)
3. Scroll to "Subtitle Settings" section
4. Enter the Stremio addon URL in the "Stremio Addon URL" field
5. Click "Save"

### Method 2: Using In-Player Settings (if available)

1. Start playing a video
2. Click the settings/gear icon
3. Enter the Stremio addon URL
4. Save the settings

## Popular Stremio Subtitle Addons

### OpenSubtitles Addon
- **URL**: `https://opensubtitles-v3.strem.io/manifest.json`
- **Alternative**: `https://opensubtitles-v3.strem.io` (base URL format)
- **Description**: Official OpenSubtitles addon for Stremio
- **Languages**: Multiple languages supported
- **Notes**: Most popular and reliable option. Supports mp4, mkv, and other file types.

### Subscene Addon
- **URL**: `https://subscene.strem.io/manifest.json`
- **Alternative**: `https://subscene.strem.io` (base URL format)
- **Description**: Subscene subtitle database
- **Languages**: Multiple languages supported
- **Notes**: Good alternative to OpenSubtitles

### Custom Addons
You can also use any custom Stremio addon that supports the subtitle resource type. Just enter the addon's base URL.

## How It Works

1. **Configuration**: You enter the Stremio addon URL in settings
2. **Video Playback**: When you play a video, LAMPA automatically searches for subtitles
3. **Manifest Check**: LAMPA verifies the addon supports subtitles
4. **Search**: LAMPA queries the addon for subtitles matching your video
5. **Download**: If subtitles are found, they're downloaded and added to the player
6. **Display**: Subtitles appear in the track selection menu

## Priority Order

LAMPA tries subtitle sources in this order:
1. **Stremio Addon** (if configured)
2. OpenSubtitles API (if API key is configured)
3. SubSource (placeholder - not yet implemented)
4. SubDL (placeholder - not yet implemented)
5. SubHero (placeholder - not yet implemented)

## Troubleshooting

### No Subtitles Found

**Problem**: The addon is configured but no subtitles appear.

**Solutions**:
1. Check that the addon URL is correct and accessible
2. Verify the video has an IMDB ID or recognizable filename
3. Try a different addon URL
4. Check your internet connection
5. Look at the logs for error messages:
   ```bash
   adb logcat -s StremioAddonProvider:D SubtitleDownloader:D
   ```

### "Addon does not support subtitles"

**Problem**: The addon manifest doesn't include subtitle support.

**Solutions**:
1. Verify you're using a subtitle addon (not a streaming addon)
2. Check the addon URL is correct
3. Try a different addon from the list above

### Subtitles in Wrong Language

**Problem**: Subtitles are downloaded but in the wrong language.

**Solutions**:
1. Go to Settings > Subtitle Settings
2. Set your preferred subtitle language
3. The addon will filter results by this language

### Connection Timeout

**Problem**: The addon is taking too long to respond.

**Solutions**:
1. Check your internet connection
2. Try a different addon (the one you're using might be down)
3. Wait a moment and try again

## Technical Details

### Stremio Addon Protocol

Stremio addons follow this structure:
```
{addon_url}/manifest.json          - Addon metadata and capabilities
{addon_url}/subtitles/{type}/{id}.json  - Subtitle search endpoint
```

### Supported Subtitle Formats

The addon provider supports these subtitle formats:
- **SRT** (SubRip) - Most common
- **VTT** (WebVTT) - Web standard
- **ASS** (Advanced SubStation Alpha) - Advanced styling

### Response Format

Stremio addons return subtitles in this JSON format:
```json
{
  "subtitles": [
    {
      "id": "subtitle_id",
      "url": "https://example.com/subtitle.srt",
      "lang": "en",
      "label": "English"
    }
  ]
}
```

## Logs and Debugging

To view detailed logs about subtitle loading:

```bash
# Full subtitle-related logs
adb logcat -s PlayerActivity:D StremioAddonProvider:D SubtitleDownloader:D OpenSubtitlesProvider:D

# Just Stremio addon logs
adb logcat -s StremioAddonProvider:D
```

### Key Log Messages

**Success**:
```
StremioAddonProvider: Addon supports subtitles
StremioAddonProvider: Found N subtitle(s) from Stremio addon
StremioAddonProvider: Subtitle downloaded successfully: /path/to/subtitle.srt
PlayerActivity: Subtitle slave added successfully
```

**Failure**:
```
StremioAddonProvider: No Stremio addon URL configured
StremioAddonProvider: Addon does not support subtitles
StremioAddonProvider: Subtitle search failed: 404
```

## Example Configuration

Here's a complete example of configuring the OpenSubtitles Stremio addon:

1. **Open Settings**
2. **Scroll to Subtitle Settings**
3. **Enter in "Stremio Addon URL" field**:
   ```
   https://opensubtitles-v3.strem.io/manifest.json
   ```
   (Or use the base URL: `https://opensubtitles-v3.strem.io`)
4. **Click Save**
5. **Play a video**
6. **Wait for subtitle notification**: "External subtitle loaded"
7. **Open track selection** to verify subtitle appears

**URL Format Options:**
- Full manifest link (recommended): `https://opensubtitles-v3.strem.io/manifest.json`
- Base URL (also supported): `https://opensubtitles-v3.strem.io`
- Both formats work identically - the app automatically handles both

## Advanced Usage

### Using Multiple Addons

**New Feature**: LAMPA now supports configuring multiple Stremio addons simultaneously!

Instead of switching between addons, you can now:
1. Configure multiple addon URLs in settings
2. LAMPA will try each addon in order when searching for subtitles
3. First addon to find matching subtitles is used
4. Better subtitle coverage across different sources

For detailed information, see [MULTIPLE_ADDONS_GUIDE.md](MULTIPLE_ADDONS_GUIDE.md).

To configure multiple addons:
1. Go to Settings
2. Scroll to "Stremio Addon URLs" section
3. Enter first addon URL and click "Add Addon URL"
4. Repeat for additional addons
5. Save settings
6. All addons will be tried automatically when playing videos

### Creating Your Own Addon

If you want to create a custom Stremio addon for subtitles:

1. Follow the [Stremio Addon SDK](https://github.com/Stremio/stremio-addon-sdk) documentation
2. Implement the `subtitles` resource type
3. Deploy your addon to a public URL
4. Configure LAMPA to use your addon URL

## FAQ

**Q: Do I need an account to use Stremio addons?**
A: Most subtitle addons don't require authentication. Just enter the URL.

**Q: Can I use multiple Stremio addons?**
A: Yes! You can now configure multiple addon URLs. All will be tried when searching for subtitles. See [MULTIPLE_ADDONS_GUIDE.md](MULTIPLE_ADDONS_GUIDE.md) for details.

**Q: Do Stremio addons work offline?**
A: No, an internet connection is required to query the addon.

**Q: What if my addon URL requires authentication?**
A: Some addons might support authentication in the URL (e.g., `https://addon.com?apikey=xxx`). Check the addon's documentation.

**Q: Can I use a local Stremio addon?**
A: Yes, if you're running an addon locally, you can use `http://localhost:PORT` or your local IP address.

**Q: How do I find more Stremio addons?**
A: Visit the [Stremio Addons](https://stremio-addons.netlify.app/) catalog or search online for "Stremio subtitle addon".

## Support

If you encounter issues with Stremio addon support:

1. Check this guide for troubleshooting steps
2. Review the logs using `adb logcat`
3. Verify the addon URL is correct and accessible
4. Try a different addon to isolate the issue
5. Report issues on the LAMPA GitHub repository with log output

## Summary

Stremio addon support provides a reliable, standardized way to access subtitles in LAMPA:
- ✅ Easy configuration (just enter a URL)
- ✅ No API keys required (for most addons)
- ✅ Multiple addon options available
- ✅ Automatic subtitle search and download
- ✅ Falls back to other sources if needed

Start by configuring the OpenSubtitles addon (`https://opensubtitles-v3.strem.io/manifest.json` or `https://opensubtitles-v3.strem.io`) and enjoy automatic subtitle support!
