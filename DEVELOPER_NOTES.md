# Developer Notes for Haval App Tool

## Display Overscan Management

The app uses a dynamic overscan service (`BottomBarService`) to ensure the persistent bottom bar is always visible and aligned across different apps.

### 1. Hardcoded Overrides
Some apps are "density-aware" and handle overscan differently. These are hardcoded in `BottomBarService.kt` to use a fixed **60px** overscan:
- `com.google.android.youtube`
- `com.google.android.apps.maps`
- `com.google.android.apps.youtube.music`
- `com.google.android.apps.messaging`

### 2. Global Overscan Tuning (ADB)
You can adjust the "Base" overscan value (default 60) for all apps (including density-scaled ones) via ADB. 
- Higher values increase the bottom margin.
- Lower values decrease it.

**Command**:
```bash
adb shell am broadcast -a br.com.redesurftank.havalshisuku.ACTION_UPDATE_OVERSCAN --ei value 60
```

*Note: The system multiplies this base value by the screen density (e.g., 4.0x) for standard apps, but uses the raw value for the whitelisted apps above.*
