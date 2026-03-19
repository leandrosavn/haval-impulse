package br.com.redesurftank.havalshisuku.services

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.snapshotFlow
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.ui.components.BottomBarContent
import br.com.redesurftank.havalshisuku.models.BottomBarState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BottomBarService : LifecycleService() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        showBottomBar()
        observeMenuState()
    }

    private fun observeMenuState() {
        // Dynamic resizing removed to avoid "jumping" sensations.
        // The Popup in BottomBarUI.kt will handle its own window rendering.
    }

    private fun showBottomBar() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HavalShisuku)
        composeView = ComposeView(themedContext).apply {
            setContent {
                BottomBarContent()
            }
        }

        // Essential for Compose to work in a Service's WindowManager
        composeView?.setViewTreeLifecycleOwner(this)
        
        val viewModelStore = ViewModelStore()
        composeView?.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = viewModelStore
        })

        val savedStateRegistryOwner = object : SavedStateRegistryOwner {
            private val lifecycleRegistry = this@BottomBarService.lifecycle
            private val savedStateRegistryController = SavedStateRegistryController.create(this)

            override val lifecycle = lifecycleRegistry
            override val savedStateRegistry = savedStateRegistryController.savedStateRegistry

            init {
                savedStateRegistryController.performRestore(null)
            }
        }
        composeView?.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

        val barHeight = (60 * resources.displayMetrics.density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        windowParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        // Explicitly set y to 0 to ensure it's at the absolute bottom
        windowParams.y = 0
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            windowParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        this.params = windowParams

        if (android.provider.Settings.canDrawOverlays(this)) {
            try {
                windowManager?.addView(composeView, windowParams)
                // Reserve screen space if Shizuku is available
                val barHeightPx = barHeight
                br.com.redesurftank.havalshisuku.utils.ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,$barHeightPx"))
            } catch (e: Exception) {
                android.util.Log.e("BottomBarService", "Error adding Compose view", e)
                stopSelf()
            }
        } else {
            android.util.Log.e("BottomBarService", "Overlay permission not granted")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Reset screen space reservation
        br.com.redesurftank.havalshisuku.utils.ShizukuUtils.runCommandAndGetOutput(arrayOf("wm", "overscan", "0,0,0,0"))
        composeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("BottomBarService", "Error removing view", e)
            }
        }
    }
}
