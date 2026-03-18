package br.com.redesurftank.havalshisuku.projectors

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import br.com.redesurftank.App
import br.com.redesurftank.havalshisuku.R
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.models.MainUiManager
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys
import br.com.redesurftank.havalshisuku.models.SteeringWheelAcControlType
import br.com.redesurftank.havalshisuku.models.screens.GraphicsScreen
import br.com.redesurftank.havalshisuku.models.screens.MainMenu
import br.com.redesurftank.havalshisuku.models.screens.RegenScreen
import br.com.redesurftank.havalshisuku.models.screens.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class InstrumentProjector2(outerContext: Context, display: Display) :
        BaseProjector(outerContext, display) {
    
    private val clockRunnable = object : Runnable {
        override fun run() {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            evaluateJsIfReady(webView, "control('clockTime', '$time')")
            handler.postDelayed(this, 30000) // Update every 30s
        }
    }
    private val preferences: SharedPreferences =
            App.getDeviceProtectedContext()
                    .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    private var webView: WebView? = null
    private val webViewsLoaded = mutableMapOf<WebView, Boolean>()
    private val pendingJsQueues = mutableMapOf<WebView, MutableList<String>>()
    private lateinit var root: FrameLayout

    // Cached EV power values for kW calculation
    private var batteryVoltage = 0f
    private var batteryCurrent = 0f
    
    private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in
                                listOf(
                                    SharedPreferencesKeys
                                                .ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION
                                                .key,
                                    SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key,
                                    SharedPreferencesKeys.ENABLE_INSTRUMENT_MASK.key,
                                    SharedPreferencesKeys.INSTRUMENT_MASK_DISPLAY_ID.key
                                )
                ) {
                    ensureUi {
                        root.isVisible =
                                shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
                        updateMaskVisibility()
                    }
                }
            }

    private fun shouldShowProjector(): Boolean {
        return preferences.getBoolean(
                SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key,
                false
        ) &&
                preferences.getBoolean(
                        SharedPreferencesKeys.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.key,
                        false
                )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler.post(clockRunnable)
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        WebView.setWebContentsDebuggingEnabled(true)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        setContentView(root)
        setupControlView(root)
        updateMaskVisibility()
        setupDataListeners()

        root.isVisible = shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
    }
    
    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clockRunnable)
        preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun setupDataListeners() {
        ServiceManager.getInstance().addDataChangedListener { key, value ->
            ensureUi {
                when (key) {
                    CarConstants.CAR_BASIC_VEHICLE_SPEED.value -> {
                        val speedStr = value.toString().split(".")[0]
                        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.CAR_SPEED}', $speedStr)")
                    }
                    CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.value -> {
                        evaluateJsIfReady(webView, "control('fuelPercent', $value)")
                    }
                    CarConstants.CAR_EV_INFO_BATTERY_CHARGE_PERCENTAGE.value -> {
                        evaluateJsIfReady(webView, "control('batteryPercent', $value)")
                    }
                    CarConstants.CAR_BASIC_GEAR_STATUS.value -> {
                        val gear = when(value.toString().toIntOrNull()) {
                            1 -> "P"
                            2 -> "R"
                            3 -> "N"
                            4 -> "D"
                            else -> "P"
                        }
                        evaluateJsIfReady(webView, "control('gearState', '$gear')")
                    }
                    CarConstants.CAR_HVAC_FAN_SPEED.value -> evaluateJsIfReady(webView, "control('fan', $value)")
                    CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value -> evaluateJsIfReady(webView, "control('temp', $value)")
                    CarConstants.CAR_HVAC_POWER_MODE.value -> evaluateJsIfReady(webView, "control('power', $value)")
                    CarConstants.CAR_HVAC_CYCLE_MODE.value -> evaluateJsIfReady(webView, "control('recycle', $value)")
                    CarConstants.CAR_HVAC_AUTO_ENABLE.value -> evaluateJsIfReady(webView, "control('auto', $value)")
                    CarConstants.CAR_HVAC_ANION_ENABLE.value -> evaluateJsIfReady(webView, "control('aion', $value)")
                    CarConstants.CAR_BASIC_OUTSIDE_TEMP.value -> {
                        evaluateJsIfReady(webView, "control('outside_temp', ${value.toFloat().roundToInt()})")
                    }
                    CarConstants.CAR_BASIC_INSIDE_TEMP.value -> {
                        evaluateJsIfReady(webView, "control('inside_temp', ${value.toFloat().roundToInt()})")
                    }
                    CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value -> {
                        evaluateJsIfReady(webView, "control('evMode', ${MainMenu.EvModeOptions.getLabel(value)})")
                    }
                    CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value -> {
                        val label = MainMenu.DrivingModeOptions.getLabel(value)
                        evaluateJsIfReady(webView, "control('drivingMode', $label)")
                        evaluateJsIfReady(webView, "control('evModeLabel', $label)")
                    }
                    CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value -> {
                        evaluateJsIfReady(webView, "control('steerMode', ${MainMenu.SteerModeOptions.getLabel(value)})")
                    }
                    CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value -> {
                        evaluateJsIfReady(webView, "control('espStatus', ${MainMenu.EspOptions.getLabel(value)})")
                    }
                    CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.value -> {
                        evaluateJsIfReady(webView, "control('onepedal', ${value == "1"})")
                    }
                    CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value -> {
                        evaluateJsIfReady(webView, "control('regenMode', ${RegenScreen.RegenOptions.getLabel(value)})")
                    }
                    CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value -> {
                        val regenValue = kotlin.math.max(0.0f, -1 * (value).toFloat())
                        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.EV_POWER_FACTOR}',$value)")
                        evaluateJsIfReady(webView, "control('${RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME}', $regenValue)")
                    }
                    CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value -> {
                        batteryVoltage = value.toFloatOrNull() ?: 0f
                        val kw = batteryVoltage * batteryCurrent / 1000f
                        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.EV_POWER_KW}', $kw)")
                    }
                    CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value -> {
                        batteryCurrent = value.toFloatOrNull() ?: 0f
                        val kw = batteryVoltage * batteryCurrent / 1000f
                        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.EV_POWER_KW}', $kw)")
                    }
                    CarConstants.CAR_BASIC_ENGINE_SPEED.value -> {
                        evaluateJsIfReady(webView, "control('engineRPM',$value)")
                    }
                }
            }
        }

        ServiceManager.getInstance().addServiceManagerEventListener { event, args ->
            ensureUi {
                when (event) {
                    ServiceManagerEventType.CLUSTER_CARD_CHANGED -> {
                        val card = args[0] as Int
                        // In the new full-screen template, card transitions might affect visibility of secondary elements
                        if (card == 1 || card == 3) {
                            MainUiManager.getInstance().handleCardChange(card)
                            updateValuesWebView()
                        }
                    }
                    ServiceManagerEventType.STEERING_WHEEL_AC_CONTROL -> {
                        when (args[0] as SteeringWheelAcControlType) {
                            SteeringWheelAcControlType.FAN_SPEED -> evaluateJsIfReady(webView, "focus('fan')")
                            SteeringWheelAcControlType.TEMPERATURE -> evaluateJsIfReady(webView, "focus('temp')")
                            SteeringWheelAcControlType.POWER -> evaluateJsIfReady(webView, "focus('power')")
                        }
                    }
                    ServiceManagerEventType.MENU_ITEM_NAVIGATION -> {
                        val item = args[0] as String
                        evaluateJsIfReady(webView, "focus('$item')")
                    }
                    ServiceManagerEventType.UPDATE_SCREEN -> {
                        val screen = args[0] as Screen
                        evaluateJsIfReady(webView, "showScreen('${screen.jsName}')")
                    }
                    ServiceManagerEventType.GRAPH_SCREEN_NAVIGATION -> {
                        val screen = args[0] as String
                        evaluateJsIfReady(webView, "control('currentGraph','$screen')")
                    }
                    ServiceManagerEventType.MAX_AUTO_AC_STATUS_CHANGED -> {
                        val status = args[0] as Int
                        evaluateJsIfReady(webView, "control('maxauto', $status)")
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupControlView(parent: FrameLayout) {
        if (webView == null) {
            webView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let {
                            webViewsLoaded[it] = true
                            updateValuesWebView()
                            pendingJsQueues[it]?.forEach { js -> it.evaluateJavascript(js, null) }
                            pendingJsQueues.remove(it)
                        }
                    }
                }
                loadDataWithBaseURL(null, readRawHtml(context), "text/html", "UTF-8", null)
            }
            parent.addView(webView)
        }
    }

    private fun updateValuesWebView() {
        // Send initial state to JS
        val sm = ServiceManager.getInstance()
        evaluateJsIfReady(webView, "control('temp', ${sm.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.value)})")
        evaluateJsIfReady(webView, "control('fan', ${sm.getData(CarConstants.CAR_HVAC_FAN_SPEED.value)})")
        evaluateJsIfReady(webView, "control('power', ${sm.getData(CarConstants.CAR_HVAC_POWER_MODE.value)})")
        evaluateJsIfReady(webView, "control('recycle', ${sm.getData(CarConstants.CAR_HVAC_CYCLE_MODE.value)})")
        evaluateJsIfReady(webView, "control('auto', ${sm.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.value)})")
        evaluateJsIfReady(webView, "control('outside_temp', ${sm.getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.value).toFloat().roundToInt()})")
        evaluateJsIfReady(webView, "control('onepedal', ${sm.getData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.value) == "1"})")
        
        val isMaskEnabled = preferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_MASK.key, true)
        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.MASK_VISIBLE}', $isMaskEnabled)")
    }

    private fun updateMaskVisibility() {
        val maskEnabled = preferences.getBoolean(SharedPreferencesKeys.ENABLE_INSTRUMENT_MASK.key, true)
        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.MASK_VISIBLE}', $maskEnabled)")
    }

    private fun evaluateJsIfReady(webView: WebView?, js: String) {
        if (webView == null) return
        if (webViewsLoaded.getOrDefault(webView, false)) {
            webView.evaluateJavascript(js, null)
        } else {
            pendingJsQueues.getOrPut(webView) { mutableListOf() }.add(js)
        }
    }

    fun readRawHtml(context: Context): String {
        return context.resources.openRawResource(R.raw.app).bufferedReader().use { it.readText() }
    }

    override fun carMainScreenOff() {
        ensureUi { root.visibility = View.INVISIBLE }
    }

    override fun carMainScreenOn() {
        ensureUi { root.visibility = View.VISIBLE }
    }
}
