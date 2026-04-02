package br.com.redesurftank.havalshisuku.services

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.models.BottomBarState
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.ui.components.BottomBarContent
import br.com.redesurftank.havalshisuku.ui.components.BottomBarMenus
import br.com.redesurftank.havalshisuku.ui.theme.HavalShisukuTheme
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BottomBarService : LifecycleService() {

    private var mWindowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var menuComposeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuWindowAdded = false

    private var monitoringJob: Job? = null
    private var autoHideJob: Job? = null
    private var lastPackage: String? = null

    data class BarSettings(val overscan: Int, val yOffset: Int)

    // Hardcoded overrides for density-aware apps that auto-scale overscan
    // These values are in DP and will be scaled by density
    private val APP_OVERRIDES =
            mapOf(
                    "com.google.android.youtube" to BarSettings(0, 0),
                    "com.google.android.apps.maps" to BarSettings(0, 60),
                    "com.google.android.apps.youtube.music" to BarSettings(0, 0),
                    "com.google.android.apps.messaging" to BarSettings(60, 0),
                    "deezer.android.app" to BarSettings(60, 0),
            )

    private val BOTTOM_BAR_BASE_HEIGHT_DP = 60f
    private val REFERENCE_OVERSCAN = 60
    private val BASE_OFFSET_Y = -60 // Starting point for y at 60 overscan

    override fun onCreate() {
        super.onCreate()
        
        // Initialize state from SharedPreferences
        val prefs = br.com.redesurftank.App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        BottomBarState.autoHideEnabled = prefs.getBoolean(SharedPreferencesKeys.BOTTOM_BAR_AUTO_HIDE.key, false)
        
        BottomBarState.isVisible = true
        showBottomBar()
        observeMenuState()
        observeVisibility()
        observeAutoHide()
        registerUpdateReceiver()
        startDynamicOverscanMonitoring()
        
        // Initial timer start
        resetAutoHideTimer()
    }

    private fun registerUpdateReceiver() {
        val filter = android.content.IntentFilter("br.com.redesurftank.havalshisuku.UPDATE_BAR_POSITION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
    }

    private val updateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            val overscan = intent?.getIntExtra("overscan", -1) ?: -1
            val offset = intent?.getIntExtra("offset", -101) ?: -101
            
            if (overscan != -1 || offset != -101) {
                // Real-time update from Apply button
                val currentPackage = BottomBarState.currentPackage
                if (currentPackage.isNotEmpty()) {
                    val settings = BarSettings(
                        overscan = if (overscan != -1) overscan else (currentAppSettings?.overscan ?: 0),
                        yOffset = if (offset != -101) offset else (currentAppSettings?.yOffset ?: 0)
                    )
                    currentAppSettings = settings
                    applyAppSettings(settings)
                }
            } else {
                // Reload from SharedPreferences (Save button or generic refresh)
                lastPackage = null // Force reload in monitoring loop
            }
        }
    }

    private fun observeAutoHide() {
        lifecycleScope.launch {
            // Reset timer on any state change that might indicate activity
            snapshotFlow { 
                listOf(
                    BottomBarState.isVisible,
                    BottomBarState.isMenuExpanded,
                    BottomBarState.isSettingsMenuExpanded,
                    BottomBarState.isOverrideMenuExpanded
                )
            }.collectLatest { 
                resetAutoHideTimer()
            }
        }
    }

    fun resetAutoHideTimer() {
        autoHideJob?.cancel()
        if (!BottomBarState.autoHideEnabled || !BottomBarState.isVisible) return
        
        autoHideJob = lifecycleScope.launch {
            delay(30000) // 30 seconds
            if (BottomBarState.isVisible && !BottomBarState.isMenuExpanded && 
                !BottomBarState.isSettingsMenuExpanded && !BottomBarState.isOverrideMenuExpanded) {
                BottomBarState.isVisible = false
            }
        }
    }

    private var currentAppSettings: BarSettings? = null

    private fun startDynamicOverscanMonitoring() {
        monitoringJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    val density = resources.displayMetrics.density

                    while (isActive) {
                        try {
                            val currentPackage = getTopPackageName()
                            if (currentPackage != null) {
                                withContext(Dispatchers.Main) {
                                    BottomBarState.currentPackage = currentPackage
                                }
                            }
                            
                            if (currentPackage != null && currentPackage != lastPackage) {
                                lastPackage = currentPackage

                                val prefs =
                                        br.com.redesurftank.App.getDeviceProtectedContext()
                                                .getSharedPreferences(
                                                        "haval_prefs",
                                                        Context.MODE_PRIVATE
                                                )
                                // Default overscan is now 0 as requested
                                val storedDefault =
                                        prefs.getInt(
                                                SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN
                                                        .key,
                                                0
                                        )
                                
                                // Also update autoHideEnabled from prefs
                                withContext(Dispatchers.Main) {
                                    BottomBarState.autoHideEnabled = prefs.getBoolean(
                                        SharedPreferencesKeys.BOTTOM_BAR_AUTO_HIDE.key, 
                                        false
                                    )
                                }

                                val settings = getSettingsForPackage(currentPackage, storedDefault)
                                currentAppSettings = settings
                                applyAppSettings(settings)
                            }
                        } catch (e: Exception) {
                            Log.e("BottomBarService", "Error in monitoring loop", e)
                        }
                        delay(2000)
                    }
                }
    }

    private fun getTopPackageName(): String? {
        val output =
                ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("sh", "-c", "dumpsys activity activities | grep mResumedActivity")
                )
        val regex =
                Regex(
                        """mResumedActivity: ActivityRecord\{.*\s([a-zA-Z0-9._]+)/\.?[a-zA-Z0-9._]+\s.*\}"""
                )
        val match = regex.find(output)
        return match?.groupValues?.get(1)
    }

    private fun getSettingsForPackage(packageName: String, defaultOverscan: Int): BarSettings {
        val prefs = br.com.redesurftank.App.getDeviceProtectedContext()
            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
        
        val dynamicOverridesJson = prefs.getString(SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key, null)
        val dynamicOverrides: Map<String, BarSettings> = if (dynamicOverridesJson != null) {
            try {
                val type = object : TypeToken<Map<String, BarSettings>>() {}.type
                Gson().fromJson(dynamicOverridesJson, type)
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        // Priority: Dynamic Overrides -> Hardcoded Overrides -> Default
        return dynamicOverrides[packageName] 
            ?: APP_OVERRIDES[packageName] 
            ?: BarSettings(overscan = defaultOverscan, yOffset = 0)
    }

    private fun applyAppSettings(settings: BarSettings) {
        val wm = mWindowManager ?: return
        val cv = composeView ?: return
        val lp = params ?: return
        val density = this.resources.displayMetrics.density

        if (!BottomBarState.isVisible) {
            Log.d(
                "BottomBarService",
                "Bottom bar hidden, ignoring dynamic overscan request: ${settings.overscan}"
            )
            lifecycleScope.launch(Dispatchers.IO) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
            }
            return
        }

        val overscanValueRaw = settings.overscan
        val overscanValuePx = (overscanValueRaw * density).toInt()
        val yOffsetPx = (settings.yOffset * density).toInt()

        Log.d(
            "BottomBarService",
            "Applying app settings: overscan=$overscanValueRaw, yOffset=${settings.yOffset} for app: $lastPackage"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscanValuePx"))
            withContext(Dispatchers.Main) {
                // Apply custom yOffset relative to the logical bottom (where y=0 is the edge)
                lp.y = yOffsetPx
                try {
                    wm.updateViewLayout(cv, lp)
                } catch (e: Exception) {
                    Log.e("BottomBarService", "Error updating window layout during app settings change", e)
                }
            }
        }
    }

    private fun observeVisibility() {
        lifecycleScope.launch {
            snapshotFlow { BottomBarState.isVisible }.collectLatest { visible ->
                updateBarVisibility(visible)
            }
        }
    }

    private fun updateBarVisibility(visible: Boolean) {
        val wm = mWindowManager ?: return
        val cv = composeView ?: return
        val lp = params ?: return

        val density = resources.displayMetrics.density

        lifecycleScope.launch(Dispatchers.IO) {
            val overscanCmd: Array<String>
            if (visible) {
                val settings = currentAppSettings ?: run {
                    val prefs = br.com.redesurftank.App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                    val storedDefault = prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 0)
                    BarSettings(overscan = storedDefault, yOffset = 0)
                }
                
                val overscanValuePx = (settings.overscan * density).toInt()
                val yOffsetPx = (settings.yOffset * density).toInt()

                withContext(Dispatchers.Main) {
                    lp.height = (120 * density).toInt()
                    lp.y = 0
                }
                overscanCmd = arrayOf("wm", "overscan", "0,0,0,$overscanValuePx")
            } else {
                // Trigger zone - keep 40dp on screen when hidden
                withContext(Dispatchers.Main) {
                    lp.height = (120 * density).toInt()
                    lp.y = -(20 * density).toInt() 
                }
                overscanCmd = arrayOf("wm", "overscan", "0,0,0,0")
            }

            ShizukuUtils.runCommandAndGetOutput(overscanCmd)

            withContext(Dispatchers.Main) {
                try {
                    wm.updateViewLayout(cv, lp)
                } catch (e: Exception) {
                    Log.e("BottomBarService", "Error updating window layout", e)
                }
            }
        }
    }

    private fun observeMenuState() {
        lifecycleScope.launch {
            snapshotFlow { BottomBarState.isMenuExpanded || BottomBarState.isSettingsMenuExpanded || BottomBarState.isOverrideMenuExpanded }
                    .collectLatest { expanded -> updateMenuWindow(expanded) }
        }
    }

    private fun updateMenuWindow(show: Boolean) {
        val wm = mWindowManager ?: return
        val mv = menuComposeView ?: return
        val mp = menuParams ?: return

        if (show && !isMenuWindowAdded) {
            try {
                wm.addView(mv, mp)
                isMenuWindowAdded = true
            } catch (e: Exception) {
                Log.e("BottomBarService", "Error adding menu window", e)
            }
        } else if (!show && isMenuWindowAdded) {
            try {
                wm.removeView(mv)
                isMenuWindowAdded = false
            } catch (e: Exception) {
                Log.e("BottomBarService", "Error removing menu window", e)
            }
        }
    }

    private fun showBottomBar() {
        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HavalShisuku)

        composeView =
                ComposeView(themedContext)
                        .apply { setContent { HavalShisukuTheme { BottomBarContent() } } }
                        .also { it.setupForService() }

        menuComposeView =
                ComposeView(themedContext)
                        .apply { setContent { HavalShisukuTheme { BottomBarMenus() } } }
                        .also { it.setupForService() }

        val density = resources.displayMetrics.density
        val barHeight = (BOTTOM_BAR_BASE_HEIGHT_DP * density).toInt()
        val menuHeight = (500 * density).toInt()

        val layoutType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                }

        params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                barHeight,
                                layoutType,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            // On show, we derive y from the currently applied overscan value if
                            // possible,
                            // but setting it to -defaultOverscan below in show logic.
                            // For initial params, we can use 0 and it will be updated.
                            y = 0
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                layoutInDisplayCutoutMode =
                                        WindowManager.LayoutParams
                                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                        }

        menuParams =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                layoutType,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply {
                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            y = 0
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                layoutInDisplayCutoutMode =
                                        WindowManager.LayoutParams
                                                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                        }

        if (android.provider.Settings.canDrawOverlays(this)) {
            try {
                mWindowManager?.addView(composeView, params)
                val settings = currentAppSettings ?: run {
                    val prefs = br.com.redesurftank.App.getDeviceProtectedContext()
                        .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                    val storedDefault = prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 0)
                    BarSettings(overscan = storedDefault, yOffset = 0)
                }
                
                val overscanValuePx = (settings.overscan * density).toInt()
                val yOffsetPx = (settings.yOffset * density).toInt()

                val lp = params
                if (lp != null) {
                    lp.y = yOffsetPx
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "size", "reset"))
                    ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("wm", "overscan", "0,0,0,$overscanValuePx")
                    )
                }
            } catch (e: Exception) {
                Log.e("BottomBarService", "Error adding views", e)
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private fun ComposeView.setupForService() {
        this.setViewTreeLifecycleOwner(this@BottomBarService)
        val viewModelStore = ViewModelStore()
        this.setViewTreeViewModelStoreOwner(
                object : ViewModelStoreOwner {
                    override val viewModelStore: ViewModelStore = viewModelStore
                }
        )
        val savedStateRegistryOwner =
                object : SavedStateRegistryOwner {
                    private val lifecycleRegistry = this@BottomBarService.lifecycle
                    private val savedStateRegistryController =
                            SavedStateRegistryController.create(this)
                    override val lifecycle = lifecycleRegistry
                    override val savedStateRegistry =
                            savedStateRegistryController.savedStateRegistry
                    init {
                        savedStateRegistryController.performRestore(null)
                    }
                }
        this.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        unregisterReceiver(updateReceiver)
        super.onDestroy()
        ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "size", "reset"))
        ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
        try {
            composeView?.let { mWindowManager?.removeView(it) }
            if (isMenuWindowAdded) {
                menuComposeView?.let { mWindowManager?.removeView(it) }
            }
        } catch (e: Exception) {}
    }
}
