package br.com.redesurftank.havalshisuku.ui.components

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import br.com.redesurftank.havalshisuku.managers.*
import br.com.redesurftank.havalshisuku.models.*
import br.com.redesurftank.havalshisuku.ui.theme.Michroma
import br.com.redesurftank.havalshisuku.utils.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.*

private val commonTextStyle =
        TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = Michroma,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
        )

private val labelStyle =
        TextStyle(
                color = Color.LightGray,
                fontSize = 10.sp,
                fontFamily = Michroma,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
        )

private fun String?.toComposeColor(): Color {
    if (this == null || !this.startsWith("#")) return Color.White
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: Exception) {
        Color.White
    }
}

@Composable
fun BottomBarContent() {
    val serviceManager = ServiceManager.getInstance()
    val scope = rememberCoroutineScope()

    // States for AC, Volume, etc.
    var driverTemp by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue()) ?: "--"
        )
    }
    var passTemp by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue()) ?: "--"
        )
    }
    var volume by remember {
        mutableIntStateOf(
                serviceManager
                        .getData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue())
                        ?.toIntOrNull()
                        ?: 0
        )
    }
    var powerModel by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue())
                        ?: "0"
        )
    }
    var energyRecovery by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue())
                        ?: "0"
        )
    }
    var driveMode by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue()) ?: "0"
        )
    }
    var steeringMode by remember {
        mutableStateOf(
                serviceManager.getData(
                        CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue()
                )
                        ?: "0"
        )
    }
    var fanSpeed by remember {
        mutableIntStateOf(
                serviceManager.getData(CarConstants.CAR_HVAC_FAN_SPEED.getValue())?.toIntOrNull()
                        ?: 1
        )
    }
    var hvacPower by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) ?: "1") }
    var acSync by remember {
        mutableStateOf(serviceManager.getData(CarConstants.CAR_HVAC_SYNC_ENABLE.getValue()) ?: "0")
    }
    var acAuto by remember {
        mutableStateOf(serviceManager.getData(CarConstants.CAR_HVAC_AUTO_ENABLE.getValue()) ?: "0")
    }

    // Update states when data changes
    DisposableEffect(Unit) {
        val listener =
                object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                    override fun onDataChanged(key: String, value: String?) {
                        if (value == null) return
                        when (key) {
                            CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue() ->
                                    driverTemp = value
                            CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue() -> passTemp = value
                            CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue() ->
                                    volume = value.toIntOrNull() ?: volume
                            CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue() ->
                                    powerModel = value
                            CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue() ->
                                    energyRecovery = value
                            CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue() ->
                                    driveMode = value
                            CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue() ->
                                    steeringMode = value
                            CarConstants.CAR_HVAC_FAN_SPEED.getValue() ->
                                    fanSpeed = value.toIntOrNull() ?: fanSpeed
                            CarConstants.CAR_HVAC_POWER_MODE.getValue() -> hvacPower = value
                            CarConstants.CAR_HVAC_SYNC_ENABLE.getValue() -> acSync = value
                            CarConstants.CAR_HVAC_AUTO_ENABLE.getValue() -> acAuto = value
                        }
                    }
                }
        serviceManager.addDataChangedListener(listener)
        onDispose { serviceManager.removeDataChangedListener(listener) }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize().pointerInput(
                                    BottomBarState.isVisible,
                                    BottomBarState.autoHideEnabled
                            ) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            // Down swipe to hide
                            if (BottomBarState.isVisible && dragAmount > 10f) {
                                BottomBarState.isVisible = false
                            }
                            // Up swipe to show - only if near the bottom when hidden
                            else if (!BottomBarState.isVisible && dragAmount < -5f) {
                                BottomBarState.isVisible = true
                            }
                        }
                    },
            contentAlignment = Alignment.BottomCenter
    ) {
        if (BottomBarState.isVisible) {
            Surface(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
                    tonalElevation = 0.dp
            ) {
                Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {

                    // 1. App Switcher (11%)
                    Box(modifier = Modifier.weight(0.11f)) { AppSwitcherSection() }

                    val isACEnabled = hvacPower == "1"

                    // 2. AC Driver (14%)
                    Box(modifier = Modifier.weight(0.14f), contentAlignment = Alignment.Center) {
                        TempControlSection("Motorista", driverTemp, isACEnabled) { delta ->
                            val newTemp = (driverTemp.toFloatOrNull() ?: 22.0f) + delta
                            serviceManager.updateData(
                                    CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(),
                                    String.format(java.util.Locale.US, "%.1f", newTemp)
                            )
                        }
                    }

                    // 3. Controls Group (Back, Settings) (14%)
                    Box(modifier = Modifier.weight(0.14f), contentAlignment = Alignment.Center) {
                        ControlsSection(scope)
                    }

                    // 4. AC Fan Speed (14%)
                    Box(modifier = Modifier.weight(0.14f), contentAlignment = Alignment.Center) {
                        FanControlSection(fanSpeed, true) { delta ->
                            val newSpeed = (fanSpeed + delta).coerceIn(0, 7)
                            serviceManager.updateData(
                                    CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
                                    newSpeed.toString()
                            )

                            // If ventilation is 0, disable AC. If it goes from 0 to 1, enable AC.
                            if (newSpeed == 0 && hvacPower == "1") {
                                serviceManager.updateData(
                                        CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                        "0"
                                )
                            } else if (newSpeed > 0 && hvacPower == "0") {
                                serviceManager.updateData(
                                        CarConstants.CAR_HVAC_POWER_MODE.getValue(),
                                        "1"
                                )
                            }
                        }
                    }

                    // 5. AC Sync/Auto (14%)
                    Box(modifier = Modifier.weight(0.14f), contentAlignment = Alignment.Center) {
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            ACControlButton(
                                    icon = Icons.Default.Sync,
                                    label = "Sync",
                                    isActive = acSync == "1",
                                    isEnabled = isACEnabled
                            ) {
                                val next = if (acSync == "1") "0" else "1"
                                serviceManager.updateData(
                                        CarConstants.CAR_HVAC_SYNC_ENABLE.getValue(),
                                        next
                                )
                            }
                            ACControlButton(
                                    icon = Icons.Default.AutoMode,
                                    label = "Auto",
                                    isActive = acAuto == "1",
                                    isEnabled = isACEnabled
                            ) {
                                val next = if (acAuto == "1") "0" else "1"
                                serviceManager.updateData(
                                        CarConstants.CAR_HVAC_AUTO_ENABLE.getValue(),
                                        next
                                )
                            }
                        }
                    }

                    // 6. Volume (14%)
                    Box(modifier = Modifier.weight(0.14f), contentAlignment = Alignment.Center) {
                        VolumeControlSection(label = "Volume", volume) { delta ->
                            val newVol = (volume + delta).coerceIn(0, 30)
                            serviceManager.updateData(
                                    CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(),
                                    newVol.toString()
                            )
                        }
                    }

                    // 7. AC Passenger (14%)
                    Box(modifier = Modifier.weight(0.14f), contentAlignment = Alignment.Center) {
                        TempControlSection("Passageiro", passTemp, isACEnabled) { delta ->
                            val newTemp = (passTemp.toFloatOrNull() ?: 22.0f) + delta
                            serviceManager.updateData(
                                    CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(),
                                    String.format(java.util.Locale.US, "%.1f", newTemp)
                            )
                        }
                    }

                    // 8. Override Section (5%)
                    Box(modifier = Modifier.weight(0.05f), contentAlignment = Alignment.Center) {
                        NavIcon(Icons.Default.Height) {
                            BottomBarState.isOverrideMenuExpanded =
                                    !BottomBarState.isOverrideMenuExpanded
                            if (BottomBarState.isOverrideMenuExpanded) {
                                BottomBarState.isMenuExpanded = false
                                BottomBarState.isSettingsMenuExpanded = false
                            }
                        }
                    }
                }
            }
        } else {
            // Trigger zone - larger area at the bottom when hidden
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(60.dp) // Match window height for maximum trigger area
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Transparent)
                                    .clickable(
                                            interactionSource =
                                                    remember {
                                                        androidx.compose.foundation.interaction
                                                                .MutableInteractionSource()
                                                    },
                                            indication = null
                                    ) { BottomBarState.isVisible = true }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun getSubstituteIconVector(substituteIcon: String?): ImageVector? {
    return when (substituteIcon) {
        "nav" -> Icons.Default.Place
        "music" -> Icons.Default.PlayArrow
        "video" -> Icons.Default.Movie
        "settings" -> Icons.Default.Tune
        "haval" -> Icons.Default.DirectionsCar
        "game" -> Icons.Default.SportsEsports
        "tv" -> Icons.Default.Tv
        "phone" -> Icons.Default.Phone
        "chat" -> Icons.Default.Chat
        "map_alt" -> Icons.Default.Map
        else -> null
    }
}

@Composable
fun AppSwitcherSection() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configs = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()

    // Initialize if empty
    if (br.com.redesurftank.havalshisuku.models.BottomBarState.selectedPackage.isEmpty()) {
        br.com.redesurftank.havalshisuku.models.BottomBarState.selectedPackage =
                configs.firstOrNull()?.packageName ?: ""
    }

    val selectedPackage = br.com.redesurftank.havalshisuku.models.BottomBarState.selectedPackage
    val showMenu = br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded
    
    val selectedConfig = configs.find { it.packageName == selectedPackage }
    val substituteIconVector = getSubstituteIconVector(selectedConfig?.substituteIcon)

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
                onClick = {
                    configs.find { it.packageName == selectedPackage }?.let {
                        scope.launch {
                            br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                    .sendToDisplay(it)
                        }
                    }
                },
                modifier = Modifier.width(70.dp).fillMaxHeight()
        ) {
            Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Cluster",
                    tint = Color.White
            )
        }
        Box(
                modifier =
                        Modifier.size(55.dp)
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .pointerInput(showMenu, selectedPackage) {
                                    detectTapGestures(
                                        onTap = {
                                            br.com.redesurftank.havalshisuku.models.BottomBarState
                                                    .isMenuExpanded = !showMenu
                                        },
                                        onDoubleTap = {
                                            if (selectedPackage.isNotEmpty()) {
                                                br.com.redesurftank.havalshisuku.models.BottomBarState
                                                        .isMenuExpanded = false
                                                scope.launch {
                                                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                                            .launchAnyApp(context, selectedPackage)
                                                }
                                            }
                                        }
                                    )
                                },
                contentAlignment = Alignment.Center
        ) {
            if (substituteIconVector != null) {
                val iconTint = selectedConfig?.iconColor.toComposeColor()
                Icon(
                    substituteIconVector,
                    contentDescription = "App Icon",
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            } else if (selectedPackage.isNotEmpty()) {
                AsyncImage(
                        model =
                                ImageRequest.Builder(context)
                                        .data(
                                                try {
                                                    context.packageManager.getApplicationIcon(
                                                            selectedPackage
                                                    )
                                                } catch (e: Exception) {
                                                    null
                                                }
                                        )
                                        .build(),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                        Icons.Default.Layers,
                        contentDescription = "Apps",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                )
            }
        }

        IconButton(
                onClick = {
                    if (selectedPackage.isNotEmpty()) {
                        scope.launch {
                            br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                    .launchAnyApp(context, selectedPackage)
                        }
                    }
                },
                modifier = Modifier.width(70.dp).fillMaxHeight()
        ) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "MMI", tint = Color.White) }
    }
}

@Composable
fun AppMenuContent() {
    val configs = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
            modifier =
                    Modifier.background(Color(0xFF13151A).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1D2430), RoundedCornerShape(12.dp))
                            .fillMaxWidth(0.3f)
                            .padding(16.dp)
    ) {
        val appList = configs.toList()
        val columns = 3
        val totalApps = appList.size
        val rows = (totalApps + columns - 1) / columns

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header with Title and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aplicativos",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                for (r in 0 until rows) {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (c in 0 until columns) {
                            val index = r * columns + c
                            if (index < totalApps) {
                                val config = appList[index]
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AppGridItem(config.packageName, config.substituteIcon, context, scope) {
                                        br.com.redesurftank.havalshisuku.models.BottomBarState
                                                .isMenuExpanded = false
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CarSettingsSection() {
    val showSettings = BottomBarState.isSettingsMenuExpanded
    Box(
            modifier =
                    Modifier.fillMaxHeight().width(40.dp).clickable {
                        BottomBarState.isSettingsMenuExpanded = !showSettings
                    },
            contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "Configurações do Carro",
                    tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                    modifier = Modifier.size(32.dp)
            )
            // Smaller gear icon overlay
            Box(
                    modifier =
                            Modifier.size(16.dp)
                                    .offset(x = 10.dp, y = 10.dp)
                                    .background(Color.Black, RoundedCornerShape(4.dp))
                                    .padding(1.dp),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                        modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsMenuContent(drive: String, ev: String, regen: String, steer: String) {
    val serviceManager = br.com.redesurftank.havalshisuku.managers.ServiceManager.getInstance()
    Box(
            modifier =
                    Modifier.background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                            .width(480.dp)
                            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Category: Drive Mode
            SettingsCategoryRow(
                    "Modo de Condução",
                    drive,
                    listOf(
                            "2" to "Eco",
                            "0" to "Normal",
                            "1" to "Sport",
                            "3" to "Neve",
                            "4" to "Areia",
                            "5" to "Lama"
                    )
            ) { newVal ->
                serviceManager.updateData(
                        CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue(),
                        newVal
                )
            }

            // Category: EV Mode
            SettingsCategoryRow(
                    "Modo EV",
                    ev,
                    listOf("0" to "HEV", "1" to "EV Prioritário", "3" to "EV")
            ) { newVal ->
                serviceManager.updateData(
                        CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue(),
                        newVal
                )
            }

            // Category: Regen
            SettingsCategoryRow(
                    "Modo de Regeneração",
                    regen,
                    listOf("2" to "Baixo", "0" to "Normal", "1" to "Alto")
            ) { newVal ->
                serviceManager.updateData(
                        CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue(),
                        newVal
                )
            }

            // Category: Steering
            SettingsCategoryRow(
                    "Modo de Direção",
                    steer,
                    listOf("2" to "Conforto", "0" to "Normal", "1" to "Esportiva")
            ) { newVal ->
                serviceManager.updateData(
                        CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue(),
                        newVal
                )
            }

            Box(
                    modifier =
                            Modifier.fillMaxWidth().height(24.dp).clickable {
                                br.com.redesurftank.havalshisuku.models.BottomBarState
                                        .isSettingsMenuExpanded = false
                            },
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Fechar",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun BottomBarMenus() {
    val serviceManager = br.com.redesurftank.havalshisuku.managers.ServiceManager.getInstance()

    var driveMode by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue()) ?: "0"
        )
    }
    var powerModel by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue())
                        ?: "0"
        )
    }
    var energyRecovery by remember {
        mutableStateOf(
                serviceManager.getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue())
                        ?: "0"
        )
    }
    var steeringMode by remember {
        mutableStateOf(
                serviceManager.getData(
                        CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue()
                )
                        ?: "0"
        )
    }

    // Update states when data changes
    DisposableEffect(Unit) {
        val listener =
                object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
                    override fun onDataChanged(key: String, value: String?) {
                        if (value == null) return
                        when (key) {
                            CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue() ->
                                    powerModel = value
                            CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue() ->
                                    energyRecovery = value
                            CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue() ->
                                    driveMode = value
                            CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue() ->
                                    steeringMode = value
                        }
                    }
                }
        serviceManager.addDataChangedListener(listener)
        onDispose { serviceManager.removeDataChangedListener(listener) }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize().clickable(
                                    interactionSource =
                                            remember {
                                                androidx.compose.foundation.interaction
                                                        .MutableInteractionSource()
                                            },
                                    indication = null
                            ) {
                        BottomBarState.isMenuExpanded = false
                        BottomBarState.isSettingsMenuExpanded = false
                        BottomBarState.isOverrideMenuExpanded = false
                    },
            contentAlignment = Alignment.BottomCenter
    ) {
        // We use a Box with fillMaxWidth to contain our menus at the bottom
        Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 60.dp),
        ) {
            // App Menu (Left side)
            if (br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded) {
                Box(
                        modifier =
                                Modifier.padding(start = 16.dp)
                                        .align(Alignment.BottomStart)
                                        .clickable(enabled = false) {}
                ) { AppMenuContent() }
            }

            // Settings/Override Menu (Aligned to Left as requested)
            if (BottomBarState.isSettingsMenuExpanded || BottomBarState.isOverrideMenuExpanded) {
                Box(
                        modifier =
                                Modifier.align(Alignment.BottomStart).clickable(enabled = false) {}
                ) {
                    if (BottomBarState.isSettingsMenuExpanded) {
                        SettingsMenuContent(driveMode, powerModel, energyRecovery, steeringMode)
                    } else if (BottomBarState.isOverrideMenuExpanded) {
                        OverrideMenuContent()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryRow(
        label: String,
        currentValue: String,
        options: List<Pair<String, String>>,
        onSelect: (String) -> Unit
) {
    Column {
        Text(text = label, style = labelStyle.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (valKey, valLabel) ->
                val isSelected = currentValue == valKey
                Surface(
                        onClick = { onSelect(valKey) },
                        modifier = Modifier.weight(1f),
                        color =
                                if (isSelected) Color(0xFF2196F3).copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        border =
                                BorderStroke(
                                        width = 1.dp,
                                        color =
                                                if (isSelected) Color(0xFF2196F3)
                                                else Color.Transparent
                                )
                ) {
                    Text(
                            text = valLabel,
                            color = if (isSelected) Color(0xFF2196F3) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = Michroma,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TempControlSection(
        label: String,
        temp: String,
        isEnabled: Boolean,
        onValueChange: (Float) -> Unit
) {
    val alpha = if (isEnabled) 1f else 0.4f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
        SmallButton(Icons.Default.Remove, isEnabled) { onValueChange(-0.5f) }
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(120.dp)
        ) {
            Text(text = label, style = labelStyle)
            val floatTemp = temp.toFloatOrNull() ?: -200f
            val isAbnormal = floatTemp >= 85f || floatTemp <= -40f || floatTemp == -1f
            val displayTemp = if (!isEnabled || isAbnormal) "--" else temp
            val tempColor = if (floatTemp > 30f) Color.Red else Color.White
            Text(
                    text =
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(color = tempColor)) { append(displayTemp) }
                                if (displayTemp != "--") append("°C")
                            },
                    style = commonTextStyle.copy(fontSize = 18.sp),
                    modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        SmallButton(Icons.Default.Add, isEnabled) { onValueChange(0.5f) }
    }
}

@Composable
fun FanControlSection(speed: Int, isEnabled: Boolean, onValueChange: (Int) -> Unit) {
    val alpha = if (isEnabled) 1f else 0.4f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
        SmallButton(Icons.Default.Remove, isEnabled) { onValueChange(-1) }
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(120.dp)
        ) {
            Text(text = "Ventilação", style = labelStyle.copy(fontSize = 10.sp))
            Text(text = speed.toString(), style = commonTextStyle.copy(fontSize = 18.sp))
        }
        SmallButton(Icons.Default.Add, isEnabled) { onValueChange(1) }
    }
}

@Composable
fun FanSpeedIcon(speed: Int) {
    Canvas(modifier = Modifier.size(24.dp).padding(2.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2f
        val innerRadius = radius * 0.3f

        // Draw 7 segments around the circle
        val segmentGap = 10f
        val totalGap = segmentGap * 7
        val sweepAngle = (360f - totalGap) / 7f

        for (i in 0 until 7) {
            val startAngle = i * (sweepAngle + segmentGap) - 90f
            val isActive = i < speed
            val color = if (isActive) Color.White else Color.Gray.copy(alpha = 0.3f)

            drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Draw a small fan hub in the center
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = innerRadius, center = center)
    }
}

@Composable
fun VolumeControlSection(label: String, volume: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallButton(Icons.Default.Remove) { onValueChange(-1) }
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
        ) {
            Text(text = label, style = labelStyle)
            Text(
                    text = volume.toString(),
                    style = commonTextStyle,
                    modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        SmallButton(Icons.Default.Add) { onValueChange(1) }
    }
}

@Composable
fun ControlsSection(scope: CoroutineScope) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    ShizukuUtils.runCommandAndGetOutput(arrayOf("input", "keyevent", "4"))
                }
            }
        ) {
            Text(
                text = "Voltar",
                style = labelStyle.copy(fontSize = 10.sp, color = Color.White)
            )
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }

        val showSettings = BottomBarState.isSettingsMenuExpanded
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                BottomBarState.isSettingsMenuExpanded = !showSettings
            }
        ) {
            Text(
                text = "Condução",
                style = labelStyle.copy(
                    fontSize = 10.sp,
                    color = if (showSettings) Color(0xFF2196F3) else Color.White
                )
            )
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(x = 6.dp, y = 6.dp)
                        .background(Color.Black, RoundedCornerShape(2.dp))
                        .padding(0.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (showSettings) Color(0xFF2196F3) else Color.White,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NavIcon(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun ACControlButton(
        icon: ImageVector,
        label: String,
        isActive: Boolean,
        isEnabled: Boolean,
        onClick: () -> Unit
) {
    val context = LocalContext.current
    val alpha = if (isEnabled) 1f else 0.4f
    val activeColor = Color(0xFF2196F3) // Vibrant blue for active state
    val contentColor = if (isActive && isEnabled) activeColor else Color.White

    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha).clickable(enabled = isEnabled) { onClick() }
    ) {
        Text(
                text = label,
                style =
                        labelStyle.copy(
                                fontSize = 10.sp,
                                color = contentColor,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                        )
        )
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmallButton(
        icon: ImageVector,
        enabled: Boolean = true,
        iconSize: androidx.compose.ui.unit.Dp = 20.dp,
        onClick: () -> Unit
) {
    Surface(
            onClick = if (enabled) onClick else ({}),
            shape = RoundedCornerShape(0.dp),
            color = Color.Black.copy(alpha = 0.95f),
            modifier = Modifier.size(70.dp, 60.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun AppGridItem(
        pkg: String,
        substituteIcon: String?,
        context: Context,
        scope: CoroutineScope,
        onClick: () -> Unit
) {
    val appLabel =
            remember(pkg) {
                try {
                    val info = context.packageManager.getApplicationInfo(pkg, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    pkg
                }
            }
    val appIcon =
            remember(pkg) {
                try {
                    context.packageManager.getApplicationIcon(pkg)
                } catch (e: Exception) {
                    null
                }
            }

    val substituteIconVector = getSubstituteIconVector(substituteIcon)

    val appConfig = remember(pkg) {
        br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAppConfig(pkg)
    }
    val iconTint = appConfig?.iconColor.toComposeColor()
    val displayName = appConfig?.customName ?: appLabel

    Column(
            modifier =
                    Modifier.width(80.dp)
                            .clickable {
                                onClick()
                                // Update shared selection state
                                br.com.redesurftank.havalshisuku.models.BottomBarState
                                        .selectedPackage = pkg
                                // Launch immediately on selection
                                scope.launch {
                                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher
                                            .launchAnyApp(context, pkg)
                                }
                            }
                            .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.size(64.dp)
                                .background(
                                        Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(12.dp)
                                ),
                contentAlignment = Alignment.Center
        ) {
            if (substituteIconVector != null) {
                Icon(
                        substituteIconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(40.dp)
                )
            } else if (appIcon != null) {
                AsyncImage(
                        model = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
                text = displayName,
                color = Color.White,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun OverrideMenuContent() {
    val context = LocalContext.current
    val pkg = BottomBarState.currentPackage
    val prefs = remember {
        br.com.redesurftank.App.getDeviceProtectedContext()
                .getSharedPreferences("haval_prefs", Context.MODE_PRIVATE)
    }

    // Load current app settings from SharedPreferences
    val overridesJson = prefs.getString(SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key, null)
    val gson = com.google.gson.Gson()
    val type =
            object : com.google.gson.reflect.TypeToken<MutableMap<String, Map<String, Int>>>() {}
                    .type
    val overrides: MutableMap<String, Map<String, Int>> =
            if (overridesJson != null) {
                try {
                    gson.fromJson(overridesJson, type)
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
            }

    val currentSettings = overrides[pkg]
    var overscan by remember(pkg) { mutableIntStateOf(currentSettings?.get("overscan") ?: 0) }
    var offset by remember(pkg) { mutableIntStateOf(currentSettings?.get("offset") ?: 0) }

    Box(
            modifier =
                    Modifier.background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                            .width(280.dp)
                            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                    text = "Ajuste Real-time: $pkg",
                    style = labelStyle.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
            )

            OverrideControlRow("Overscan (Move o app para cima)", overscan, 0..120) {
                overscan = it
            }
            OverrideControlRow("Offset (Move a barra para baixo)", offset, -100..100) {
                offset = it
            }

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                        onClick = {
                            // Apply immediately via Shizuku if service is bound or via broadcast
                            // For simplicity, we can use ShizukuUtils directly here as well
                            val density = context.resources.displayMetrics.density
                            val overscanPx = (overscan * density).toInt()
                            val yOffsetPx = (offset * density).toInt()

                            ShizukuUtils.runCommandAndGetOutput(
                                    arrayOf("wm", "overscan", "0,0,0,$overscanPx")
                            )
                            // We need a way to tell the service to update its layout params
                            // For now, let's assume the service will pick it up or we can send an
                            // internal intent
                            context.sendBroadcast(
                                    android.content.Intent(
                                                    "br.com.redesurftank.havalshisuku.UPDATE_BAR_POSITION"
                                            )
                                            .apply {
                                                putExtra("overscan", overscan)
                                                putExtra("offset", offset)
                                            }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) { Text("Aplicar", color = Color.White) }

                Button(
                        onClick = {
                            val newOverrides = overrides.toMutableMap()
                            newOverrides[pkg] = mapOf("overscan" to overscan, "offset" to offset)
                            prefs.edit()
                                    .putString(
                                            SharedPreferencesKeys.BOTTOM_BAR_OVERRIDES.key,
                                            gson.toJson(newOverrides)
                                    )
                                    .apply()
                            BottomBarState.isOverrideMenuExpanded = false
                            // Alert service to reload
                            context.sendBroadcast(
                                    android.content.Intent(
                                            "br.com.redesurftank.havalshisuku.UPDATE_BAR_POSITION"
                                    )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) { Text("Salvar", color = Color.White) }
            }
        }
    }
}

@Composable
fun OverrideControlRow(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = labelStyle)
            Text(text = value.toString(), style = commonTextStyle.copy(fontSize = 14.sp))
        }
        Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                colors =
                        SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White
                        )
        )
    }
}
