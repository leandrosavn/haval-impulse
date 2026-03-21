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

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var menuComposeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuWindowAdded = false

    private var monitoringJob: Job? = null
    private var lastPackage: String? = null

    // Hardcoded overrides for density-aware apps that auto-scale overscan
    private val OVERSCAN_OVERRIDES =
            mapOf(
                    "com.google.android.youtube" to 30,
                    "com.google.android.apps.maps" to 60,
                    "com.google.android.apps.youtube.music" to 60,
                    "com.google.android.apps.messaging" to 60
            )

    override fun onCreate() {
        super.onCreate()
        BottomBarState.isVisible = true
        showBottomBar()
        observeMenuState()
        observeVisibility()
        startDynamicOverscanMonitoring()
    }

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
                                val defaultOverscan = (storedDefault * density).toInt()

                                val overscanToApply =
                                        getOverscanForPackage(currentPackage, defaultOverscan)
                                applyOverscan(overscanToApply)
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

    private fun getOverscanForPackage(packageName: String, defaultOverscan: Int): Int {
        // Return 60 for whitelisted apps, otherwise the density-scaled default
        return OVERSCAN_OVERRIDES[packageName] ?: defaultOverscan
    }

    private fun applyOverscan(value: Int) {
        if (!BottomBarState.isVisible) {
            Log.d(
                    "BottomBarService",
                    "Bottom bar hidden, ignoring dynamic overscan request: $value"
            )
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
            return
        }
        Log.d("BottomBarService", "Applying dynamic overscan: $value for app: $lastPackage")
        ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$value"))
    }

    private fun observeVisibility() {
        lifecycleScope.launch {
            snapshotFlow { BottomBarState.isVisible }.collectLatest { visible ->
                updateBarVisibility(visible)
            }
        }
    }

    private fun updateBarVisibility(visible: Boolean) {
        val wm = windowManager ?: return
        val cv = composeView ?: return
        val lp = params ?: return

        val density = resources.displayMetrics.density

        if (visible) {
            val prefs =
                    br.com.redesurftank.App.getDeviceProtectedContext()
                            .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
            val storedDefault =
                    prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 60)
            val overscanValue = (storedDefault * density).toInt()
            lp.height = (60 * density).toInt()
            lp.y = -60
            ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$overscanValue"))
        } else {
            lp.height = (20 * density).toInt()
            lp.y = 0
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
        val wm = windowManager ?: return
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
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        val barHeight = (60 * density).toInt()
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
                            y = -60
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
                windowManager?.addView(composeView, params)
                val prefs =
                        br.com.redesurftank.App.getDeviceProtectedContext()
                                .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
                val storedDefault =
                        prefs.getInt(SharedPreferencesKeys.PERSISTENT_BOTTOM_BAR_OVERSCAN.key, 60)
                val overscanValue = (storedDefault * density).toInt()

                ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "size", "reset"))
                ShizukuUtils.runCommandAndGetOutput(
                        arrayOf("wm", "overscan", "0,0,0,$overscanValue")
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
            composeView?.let { windowManager?.removeView(it) }
            if (isMenuWindowAdded) {
                menuComposeView?.let { windowManager?.removeView(it) }
            }
        } catch (e: Exception) {}
    }
}
