package br.com.redesurftank.havalshisuku.projectors

import kotlinx.coroutines.*

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import kotlin.collections.get
import kotlin.math.roundToInt

class InstrumentProjector2(outerContext: Context, display: Display) :
        BaseProjector(outerContext, display) {
    
    private val TAG = "InstrumentProjector2"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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
    private var isAnyAppOnDisplay3 = false
    private var currentCard = 0


    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in
                listOf(
                    SharedPreferencesKeys
                        .ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION
                        .key,
                    SharedPreferencesKeys.ENABLE_INSTRUMENT_PROJECTOR.key,
                    SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key,
                    SharedPreferencesKeys.VIRTUAL_CLUSTER_DISPLAY_ID.key
                )
            ) {
                ensureUi {
                    root.isVisible =
                        shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
                    updateVirtualClusterVisibility()
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
        isAnyAppOnDisplay3 = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.isAnyAppOnDisplay(3)
        updateVirtualClusterVisibility()
        setupDataListeners()

        root.isVisible = shouldShowProjector() && ServiceManager.getInstance().isMainScreenOn
    }
    
    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clockRunnable)
        preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        scope.cancel()
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
                    CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.value -> {
                        evaluateJsIfReady(webView, "control('batteryPercent', $value)")
                    }
                    CarConstants.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER.value -> {
                        evaluateJsIfReady(webView,"control('fuelRange', $value)")
                    }
                    CarConstants.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER.value -> {
                        evaluateJsIfReady(webView,"control('batteryRange', $value)")
                    }

                    CarConstants.CAR_BASIC_GEAR_STATUS.value -> {
                        val gear = getGearLabel(value.toString());
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
                        currentCard = args[0] as Int;
                        evaluateJsIfReady(webView, "control('cardId', $currentCard)")
                        resizeActiveApp(currentCard)
                        updateVirtualClusterVisibility()
                        
                        if (currentCard == 1 || currentCard == 3) {
                            MainUiManager.getInstance().handleCardChange(currentCard)
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
                    ServiceManagerEventType.DISPLAY_SCREEN_SELECTION -> {
                        val payload = args[0] as String
                        evaluateJsIfReady(webView, payload)
                    }
                    ServiceManagerEventType.DISPLAY_3_APP_STATE_CHANGED -> {
                        isAnyAppOnDisplay3 = args[0] as Boolean
                        Log.w(TAG, "Display 3 app state changed in projector: $isAnyAppOnDisplay3")
                        updateVirtualClusterVisibility()
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
                addJavascriptInterface(WebInterface(), "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.let {
                            Log.d(TAG, "WebView finished loading")
                            
                            // Auto-launch default app if configured
                            val defaultPackage = preferences.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "") ?: ""
                            if (defaultPackage.isNotEmpty()) {
                                val config = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs().find { it.packageName == defaultPackage }
                                if (config != null) {
                                    Log.d(TAG, "Auto-launching default app: $defaultPackage")
                                    scope.launch {
                                        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.launchApp(config)
                                    }
                                }
                            }
                            
                            // Apply pending JS or updates
                            updateValuesWebView()
                            webViewsLoaded[it] = true
                            pendingJsQueues[it]?.let { list ->
                                for (js in list) {
                                    it.evaluateJavascript(js, null)
                                }
                            }
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
        evaluateJsIfReady(webView, "control('aion', ${sm.getData(CarConstants.CAR_HVAC_ANION_ENABLE.value)})")
        evaluateJsIfReady(webView, "control('inside_temp', ${sm.getData(CarConstants.CAR_BASIC_INSIDE_TEMP.value).toFloat().roundToInt()})")
        evaluateJsIfReady(webView, "control('outside_temp', ${sm.getData(CarConstants.CAR_BASIC_OUTSIDE_TEMP.value).toFloat().roundToInt()})")
        evaluateJsIfReady(webView, "control('onepedal', ${sm.getData(CarConstants.CAR_CONFIGURE_PEDAL_CONTROL_ENABLE.value) == "1"})")

        evaluateJsIfReady(webView, "control('fuelPercent', ${sm.getData(CarConstants.CAR_BASIC_REMAIN_FUEL_PERCENTAGE.value)})")
        evaluateJsIfReady(webView, "control('batteryPercent', ${sm.getData(CarConstants.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.value)})")
        evaluateJsIfReady(webView, "control('fuelRange', ${sm.getData(CarConstants.CAR_EV_INFO_FUEL_MODE_REMAIN_ODOMETER.value)})")
        evaluateJsIfReady(webView, "control('batteryRange', ${sm.getData(CarConstants.CAR_EV_INFO_ELECTRIC_MODE_REMAIN_ODOMETER.value)})")
        evaluateJsIfReady(webView, "control('gearState', '${getGearLabel(sm.getData(CarConstants.CAR_BASIC_GEAR_STATUS.value))}')")

        // Speed and Engine
        val speedValue = sm.getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.value)
        val speedStr = speedValue.toString().split(".")[0]
        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.CAR_SPEED}', $speedStr)")
        evaluateJsIfReady(webView, "control('engineRPM', ${sm.getData(CarConstants.CAR_BASIC_ENGINE_SPEED.value)})")

        // Modes and Settings
        val evMode = sm.getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.value)
        evaluateJsIfReady(webView, "control('evMode', ${MainMenu.EvModeOptions.getLabel(evMode)})")

        val drivingMode = sm.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.value)
        val drivingModeLabel = MainMenu.DrivingModeOptions.getLabel(drivingMode)
        evaluateJsIfReady(webView, "control('drivingMode', $drivingModeLabel)")
        evaluateJsIfReady(webView, "control('evModeLabel', $drivingModeLabel)")

        val steerMode = sm.getData(CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.value)
        evaluateJsIfReady(webView, "control('steerMode', ${MainMenu.SteerModeOptions.getLabel(steerMode)})")

        val espStatus = sm.getData(CarConstants.CAR_DRIVE_SETTING_ESP_ENABLE.value)
        evaluateJsIfReady(webView, "control('espStatus', ${MainMenu.EspOptions.getLabel(espStatus)})")

        val regenLevel = sm.getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.value)
        evaluateJsIfReady(webView, "control('regenMode', ${RegenScreen.RegenOptions.getLabel(regenLevel)})")

        // Power and Regen Graph
        val outputPower = sm.getData(CarConstants.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.value)
        val regenValue = kotlin.math.max(0.0f, -1 * outputPower.toFloat())
        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.EV_POWER_FACTOR}', $outputPower)")
        evaluateJsIfReady(webView, "control('${RegenScreen.RegenOptions.REGEN_GRAPH_STATE_NAME}', $regenValue)")

        // Battery KW Calculation
        batteryVoltage = sm.getData(CarConstants.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.value).toFloatOrNull() ?: 0f
        batteryCurrent = sm.getData(CarConstants.CAR_EV_INFO_CUR_CHARGE_CURRENT.value).toFloatOrNull() ?: 0f
        val kw = batteryVoltage * batteryCurrent / 1000f
        evaluateJsIfReady(webView, "control('${GraphicsScreen.GraphOptions.EV_POWER_KW}', $kw)")
    }

    private fun updateVirtualClusterVisibility() {
        val clusterEnabled = preferences.getBoolean(SharedPreferencesKeys.ENABLE_VIRTUAL_CLUSTER.key, true)
        if (!clusterEnabled) {
            evaluateJsIfReady(webView, "control('mask', 0)")
            return
        } else {
            if (isAnyAppOnDisplay3) {
                evaluateJsIfReady(webView, "control('mask', 1)")
            } else {
                evaluateJsIfReady(webView, "control('mask', 2)")
            }
        }

        //val displayMode = preferences.getString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key, "Normal")

/*        val maskState = when (displayMode) {
            "Clean" -> 0
            "Normal" -> 1
            "Reduzido" -> 2
            else -> if (isAnyAppOnDisplay3) 1 else 2
        }

        if (this.maskState != maskState) {
            this.maskState = maskState
            evaluateJsIfReady(webView, "control('mask', $maskState)")
        }

        if (this.displayMode != displayMode) {
            this.displayMode = displayMode
            evaluateJsIfReady(webView, "control('display', '$displayMode')")
        }*/
    }

    private inner class WebInterface {
        @android.webkit.JavascriptInterface
        fun saveSetting(key: String, value: String) {
            val prefKey = when (key) {
                "currentClusterDisplay" -> SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.key
                "currentClusterTemplate" -> SharedPreferencesKeys.CURRENT_CLUSTER_TEMPLATE.key
                else -> null
            }

            if (prefKey != null) {
                preferences.edit().putString(prefKey, value).apply()
                // Update visibility if display mode changed
//                if (key == "currentClusterDisplay") {
//                    ensureUi { updateVirtualClusterVisibility() }
//                }
            }
        }
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

    private fun resizeActiveApp(cardId: Int) {
        val defaultPackage = preferences.getString(SharedPreferencesKeys.DEFAULT_DISPLAY_APP_PACKAGE.key, "") ?: ""
        if (defaultPackage.isEmpty()) return
        
        val config = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs().find { it.packageName == defaultPackage } ?: return
        
        scope.launch {
            val res = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getDisplayResolution(3)
            val fullWidth = res.first
            

            val newX = if (cardId == 0) {
                kotlin.math.max(config.x, (fullWidth * 0.3f).toInt())
            } else {
                config.x
            }
            val newWidth = if (cardId == 0 || cardId == 1) {
                kotlin.math.min(config.width, (fullWidth * 0.4f).toInt())
            } else {
                config.width
            }

            val newConfig = config.copy(
                width = newWidth,
                x = newX
            )
            
            Log.d(TAG, "Resizing app $defaultPackage for cardId $cardId: width=$newWidth x=$newX")
            br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.resizeApp(newConfig)
        }
    }

    fun getGearLabel(gear: String?): String {
        val gearLabel = when(gear.toString().toIntOrNull()) {
            2 -> "D"
            3 -> "P"
            4 -> "R"
            else -> "N"
        }
        return gearLabel;
    }

}
