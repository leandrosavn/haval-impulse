package br.com.redesurftank.havalshisuku.managers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.models.TargetDisplay
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FloatingButtonManager {

    private const val TAG = "FloatingButton"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isPanelShowing = false
    private var initialized = false

    private val context: Context get() = App.getDeviceProtectedContext()
    private val dp by lazy { context.resources.displayMetrics.density }
    private fun dp(v: Int) = (v * dp).toInt()

    fun initialize() {
        mainHandler.post {
            grantOverlayPermission()
            if (!initialized) {
                initialized = true
            }
            val configs = DisplayAppLauncher.getAllConfigs()
            if (configs.isNotEmpty()) {
                if (bubbleView == null) showBubble()
            } else {
                removeBubble()
                removePanel()
            }
        }
    }

    fun refresh() {
        mainHandler.post {
            val configs = DisplayAppLauncher.getAllConfigs()
            if (configs.isEmpty()) {
                removeBubble()
                removePanel()
            } else {
                if (bubbleView == null) showBubble()
                if (isPanelShowing) showPanel()
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            removeBubble()
            removePanel()
            initialized = false
        }
    }

    private fun getWM(): WindowManager {
        if (windowManager == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        return windowManager!!
    }

    // --- Bubble (large FAB for car touch) ---

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (bubbleView != null) return

        val size = dp(56)
        val view = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDD4A9EFF.toInt())
            }
            elevation = dp(6).toFloat()
            addView(TextView(context).apply {
                text = "⇄"
                textSize = 26f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(100)
        }

        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try { getWM().updateViewLayout(view, params) } catch (_: Exception) {}
                    // Move panel along with bubble
                    if (isPanelShowing) updatePanelPosition()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        try {
            getWM().addView(view, params)
            bubbleView = view
            bubbleParams = params
        } catch (e: Exception) {
            Log.e(TAG, "Cannot add bubble", e)
        }
    }

    private fun removeBubble() {
        bubbleView?.let {
            try { getWM().removeView(it) } catch (_: Exception) {}
            bubbleView = null
            bubbleParams = null
        }
    }

    // --- Panel (expanded app list, sized for car touch) ---

    private fun togglePanel() {
        if (isPanelShowing) removePanel() else showPanel()
    }

    private fun updatePanelPosition() {
        val bp = bubbleParams ?: return
        val pp = panelView?.layoutParams as? WindowManager.LayoutParams ?: return
        pp.x = bp.x + dp(60)
        pp.y = bp.y
        try { getWM().updateViewLayout(panelView, pp) } catch (_: Exception) {}
    }

    @SuppressLint("SetTextI18n")
    private fun showPanel() {
        removePanel()
        val configs = DisplayAppLauncher.getAllConfigs()
        if (configs.isEmpty()) return

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(0xF5131519.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF1D2430.toInt())
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(8).toFloat()
        }

        val pm = context.packageManager
        for ((_, config) in configs) {
            val appName = try {
                pm.getApplicationInfo(config.packageName, 0).let { pm.getApplicationLabel(it).toString() }
            } catch (_: Exception) { config.packageName.substringAfterLast('.') }

            val appIcon = try { pm.getApplicationIcon(config.packageName) } catch (_: Exception) { null }
            val isOnSecondary = isAppOnDisplay(config.packageName, config.displayId)

            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(if (isOnSecondary) 0xFF1A2A1A.toInt() else 0xFF1D2430.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                minimumWidth = dp(100)
                setOnClickListener {
                    scope.launch {
                        if (isOnSecondary) {
                            DisplayAppLauncher.launchOnMainDisplay(config)
                        } else {
                            DisplayAppLauncher.sendToDisplay(config)
                        }
                    }
                    removePanel()
                }
            }

            if (appIcon != null) {
                item.addView(ImageView(context).apply {
                    setImageDrawable(appIcon)
                }, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(6)
                })
            }

            item.addView(TextView(context).apply {
                text = appName
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
            })

            item.addView(TextView(context).apply {
                text = if (isOnSecondary) "← Trazer" else "Enviar →"
                setTextColor(if (isOnSecondary) 0xFF4AFF8E.toInt() else 0xFF4A9EFF.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })

            panel.addView(item, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }

        val bp = bubbleParams ?: return
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bp.x + dp(60)
            y = bp.y
        }

        try {
            getWM().addView(panel, params)
            panelView = panel
            isPanelShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot show panel", e)
        }
    }

    private fun removePanel() {
        panelView?.let {
            try { getWM().removeView(it) } catch (_: Exception) {}
            panelView = null
        }
        isPanelShowing = false
    }

    // --- Helpers ---

    private fun isAppOnDisplay(packageName: String, displayId: Int): Boolean {
        return try {
            val stackList = ShizukuUtils.runCommandAndGetOutput(
                arrayOf("sh", "-c", "am stack list 2>&1")
            )
            var currentDisplayId: Int? = null
            for (line in stackList.lines()) {
                val m = Regex("""displayId=(\d+)""").find(line)
                if (m != null) currentDisplayId = m.groupValues[1].toIntOrNull()
                if (currentDisplayId == displayId &&
                    Regex("""taskId=\d+:\s*\Q$packageName\E/""").containsMatchIn(line)) return true
            }
            false
        } catch (_: Exception) { false }
    }

    private fun grantOverlayPermission() {
        try {
            ShizukuUtils.runCommandAndGetOutput(
                arrayOf("sh", "-c", "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow 2>&1")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to grant overlay permission", e)
        }
    }
}
