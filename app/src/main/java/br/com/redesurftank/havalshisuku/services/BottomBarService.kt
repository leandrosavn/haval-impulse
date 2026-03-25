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

class BottomBarService : LifecycleService() {

    private var mWindowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var menuComposeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuWindowAdded = false

    private var monitoringJob: Job? = null
    private var lastPackage: String? = null

    data class BarSettings(val overscan: Int, val yOffset: Int)

    // Hardcoded overrides for density-aware apps that auto-scale overscan
    // These values are in DP and will be scaled by density
    private val APP_OVERRIDES =
            mapOf(
                    "com.google.android.youtube" to BarSettings(0, 0),
                    "com.google.android.apps.maps" to BarSettings(0, 30),
                    "com.google.android.apps.youtube.music" to BarSettings(0, 0),
                    "com.google.android.apps.messaging" to BarSettings(60, 0),
                    "deezer.android.app" to BarSettings(30, -90),
            )

    private val BOTTOM_BAR_BASE_HEIGHT_DP = 60f
    private val REFERENCE_OVERSCAN = 60
    private val BASE_OFFSET_Y = -60 // Starting point for y at 60 overscan

    override fun onCreate() {
        super.onCreate()
        BottomBarState.isVisible = true
        showBottomBar()
        observeMenuState()
        observeVisibility()
        startDynamicOverscanMonitoring()
    }

    private var currentAppSettings: BarSettings? = null

    private fun startDynamicOverscanMonitoring() {
        monitoringJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    val density = resources.displayMetrics.density

                    while (isActive) {
                        try {
                            val currentPackage = getTopPackageName()
                            if (currentPackage != null && currentPackage != lastPackage) {
                                lastPackage = currentPackage

                                // Default overscan is 60 * density (e.g. 240 for 4x)
                                val prefs =
                                        br.com.redesurftank.App.getDeviceProtectedContext()
                                                .getSharedPreferences(
                                                        "haval_prefs",
                                                        Context.MODE_PRIVATE
                                                )
                                val storedDefault =
                                        prefs.getInt(
                                                SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN
                                                        .key,
                                                60
                                        )

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
        // Return override if exists, otherwise return default settings with yOffset = 0
        return APP_OVERRIDES[packageName] ?: BarSettings(overscan = defaultOverscan, yOffset = 0)
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
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
            return
        }

        val overscanValueRaw = settings.overscan
        val overscanValuePx = (overscanValueRaw * density).toInt()
        val yOffsetPx = (settings.yOffset * density).toInt()

        Log.d(
                "BottomBarService",
                "Applying app settings: overscan=$overscanValueRaw, yOffset=${settings.yOffset} for app: $lastPackage"
        )
        ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscanValuePx"))

        // Apply custom yOffset relative to the logical bottom (where y=0 is the edge)
        lp.y = yOffsetPx

        try {
            wm.updateViewLayout(cv, lp)
        } catch (e: Exception) {
            Log.e("BottomBarService", "Error updating window layout during app settings change", e)
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

        if (visible) {
            val settings = currentAppSettings ?: run {
                val prefs = br.com.redesurftank.App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                val storedDefault = prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 60)
                BarSettings(overscan = storedDefault, yOffset = 0)
            }
            
            val overscanValuePx = (settings.overscan * density).toInt()
            val yOffsetPx = (settings.yOffset * density).toInt()

            lp.height = (BOTTOM_BAR_BASE_HEIGHT_DP * density).toInt()
            lp.y = yOffsetPx
            
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscanValuePx"))
        } else {
            lp.height = (20 * density).toInt()
            lp.y = -60
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
        }

        try {
            wm.updateViewLayout(cv, lp)
        } catch (e: Exception) {
            Log.e("BottomBarService", "Error updating window layout", e)
        }
    }

    private fun observeMenuState() {
        lifecycleScope.launch {
            snapshotFlow { BottomBarState.isMenuExpanded || BottomBarState.isSettingsMenuExpanded }
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
                                menuHeight,
                                layoutType,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
                    val storedDefault = prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 60)
                    BarSettings(overscan = storedDefault, yOffset = 0)
                }
                
                val overscanValuePx = (settings.overscan * density).toInt()
                val yOffsetPx = (settings.yOffset * density).toInt()

                val lp = params
                if (lp != null) {
                    lp.y = yOffsetPx
                }

                ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "size", "reset"))
                ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("wm", "overscan", "0,0,0,$overscanValuePx")
                )
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
