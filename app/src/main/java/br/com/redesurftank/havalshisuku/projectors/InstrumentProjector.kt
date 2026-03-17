package br.com.redesurftank.havalshisuku.projectors

import android.animation.ObjectAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.listeners.IDataChanged
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

class InstrumentProjector(outerContext: Context, display: Display) : BaseProjector(outerContext, display), IDataChanged {
    private val preferences: SharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    private val serviceManager: ServiceManager = ServiceManager.getInstance()

    private var currentKm: Int by Delegates.observable(serviceManager.totalOdometer) { _, _, _ ->
        ensureUi { updateView() }
    }

    private var maintenanceTextView: TextView? = null
    private var blinkAnimator: ObjectAnimator? = null
    private lateinit var rootLayout: RelativeLayout

    private val maintenanceParams = RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        addRule(RelativeLayout.CENTER_HORIZONTAL)
        bottomMargin = 15
    }

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            ensureUi { updateView() }
            handler.postDelayed(this, 60000)
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf(
                SharedPreferencesKeys.ENABLE_INSTRUMENT_EV_BATTERY_PERCENTAGE.key,
                SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key,
                SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key,
                SharedPreferencesKeys.ENABLE_INSTRUMENT_REVISION_WARNING.key
            )
        ) {
            ensureUi { updateView() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        rootLayout = RelativeLayout(context)
        rootLayout.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        setContentView(rootLayout)

        serviceManager.addDataChangedListener(this)
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        handler.post(timeUpdateRunnable)

        ensureUi { updateView() }
        rootLayout.isVisible = ServiceManager.getInstance().isMainScreenOn
    }

    private fun updateView() {
        val enableWarning = preferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_REVISION_WARNING.key, false)
        if (!enableWarning) {
            maintenanceTextView?.let {
                blinkAnimator?.cancel()
                rootLayout.removeView(it)
                maintenanceTextView = null
                blinkAnimator = null
            }
            return
        }

        val nextKm = preferences.getInt(SharedPreferencesKeys.INSTRUMENT_REVISION_KM.key, 12000)
        val remainingKm = nextKm - currentKm

        val nextDateMillis = preferences.getLong(SharedPreferencesKeys.INSTRUMENT_REVISION_NEXT_DATE.key, 0L)
        var text = "Próxima Manutenção em: $remainingKm Km"
        var shouldBlink = remainingKm < 1000

        if (nextDateMillis > 0) {
            val remainingMillis = nextDateMillis - System.currentTimeMillis()
            if (remainingMillis > 0) {
                val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMillis) + 1
                text += " ou $remainingDays dias"
                if (remainingDays <= 30) shouldBlink = true
            } else {
                text += " ou atrasada"
                shouldBlink = true
            }
        }

        if (maintenanceTextView == null) {
            maintenanceTextView = TextView(context).apply {
                textSize = 20f
                gravity = Gravity.CENTER
            }
            rootLayout.addView(maintenanceTextView, maintenanceParams)
        }

        maintenanceTextView!!.text = text

        if (shouldBlink) {
            maintenanceTextView!!.setTextColor(Color.RED)
            if (blinkAnimator == null) {
                blinkAnimator = ObjectAnimator.ofFloat(maintenanceTextView, View.ALPHA, 1f, 0f).apply {
                    duration = 1500
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
            }
        } else {
            maintenanceTextView!!.setTextColor(Color.WHITE)
            blinkAnimator?.cancel()
            blinkAnimator = null
            maintenanceTextView!!.alpha = 1f
        }
    }

    override fun onDataChanged(key: String, value: String) {
        if (key == CarConstants.CAR_BASIC_TOTAL_ODOMETER.value) {
            currentKm = value.toInt()
        }
    }

    override fun cancel() {
        handler.removeCallbacks(timeUpdateRunnable)
        preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        serviceManager.removeDataChangedListener(this)
        super.cancel()
    }

    override fun carMainScreenOff() {
        ensureUi {
            rootLayout.isVisible = false
        }
    }

    override fun carMainScreenOn() {
        ensureUi {
            rootLayout.isVisible = true
        }
    }
}
