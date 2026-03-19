package br.com.redesurftank.havalshisuku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import br.com.redesurftank.havalshisuku.managers.ServiceManager
import br.com.redesurftank.havalshisuku.models.CarConstants
import br.com.redesurftank.havalshisuku.utils.ShizukuUtils

@Composable
fun BottomBarContent() {
    val serviceManager = ServiceManager.getInstance()
    val scope = rememberCoroutineScope()

    // States for AC, Volume, etc.
    var driverTemp by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue()) ?: "--") }
    var passTemp by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue()) ?: "--") }
    var volume by remember { mutableIntStateOf(serviceManager.getData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue())?.toIntOrNull() ?: 0) }
    var powerModel by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue()) ?: "0") }
    var energyRecovery by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue()) ?: "0") }
    var driveMode by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue()) ?: "0") }
    var steeringMode by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue()) ?: "0") }
    var fanSpeed by remember { mutableIntStateOf(serviceManager.getData(CarConstants.CAR_HVAC_FAN_SPEED.getValue())?.toIntOrNull() ?: 1) }
    var hvacPower by remember { mutableStateOf(serviceManager.getData(CarConstants.CAR_HVAC_POWER_MODE.getValue()) ?: "1") }

    // Update states when data changes
    DisposableEffect(Unit) {
        val listener = object : br.com.redesurftank.havalshisuku.listeners.IDataChanged {
            override fun onDataChanged(key: String, value: String) {
                when (key) {
                    CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue() -> driverTemp = value
                    CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue() -> passTemp = value
                    CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue() -> volume = value.toIntOrNull() ?: volume
                    CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue() -> powerModel = value
                    CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue() -> energyRecovery = value
                    CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue() -> driveMode = value
                    CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue() -> steeringMode = value
                    CarConstants.CAR_HVAC_FAN_SPEED.getValue() -> fanSpeed = value.toIntOrNull() ?: fanSpeed
                    CarConstants.CAR_HVAC_POWER_MODE.getValue() -> hvacPower = value
                }
            }
        }
        serviceManager.addDataChangedListener(listener)
        onDispose { serviceManager.removeDataChangedListener(listener) }
    }

    val commonTextStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
    val labelStyle = TextStyle(
        color = Color.Gray,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            color = Color(0xF5131519),
            shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 1. App Switcher (Leftmost 10%)
                Box(modifier = Modifier.weight(0.10f)) {
                    AppSwitcherSection()
                }

                val isACEnabled = hvacPower == "1"

                // 2. AC Driver (15%)
                Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    TempControlSection("Motorista", driverTemp, isACEnabled, commonTextStyle, labelStyle) { delta ->
                        val newTemp = (driverTemp.toFloatOrNull() ?: 22.0f) + delta
                        serviceManager.updateData(CarConstants.CAR_HVAC_DRIVER_TEMPERATURE.getValue(), String.format(java.util.Locale.US, "%.1f", newTemp))
                    }
                }

                // 3. AC Fan Speed (15%)
                Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    FanControlSection(fanSpeed, isACEnabled, labelStyle) { delta ->
                        val newSpeed = (fanSpeed + delta).coerceIn(1, 7)
                        serviceManager.updateData(CarConstants.CAR_HVAC_FAN_SPEED.getValue(), newSpeed.toString())
                    }
                }

                // Shortcuts Area (20%)
                Box(modifier = Modifier.weight(0.2f), contentAlignment = Alignment.Center) {
                    CarSettingsSection(driveMode, powerModel, energyRecovery, steeringMode)
                }

                // 4. Volume (15%)
                Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    VolumeControlSection(label = "Volume", volume, commonTextStyle, labelStyle) { delta ->
                        val newVol = (volume + delta).coerceIn(0, 30)
                        serviceManager.updateData(CarConstants.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.getValue(), newVol.toString())
                    }
                }

                // 5. AC Passenger (15%)
                Box(modifier = Modifier.weight(0.15f), contentAlignment = Alignment.Center) {
                    TempControlSection("Passageiro", passTemp, isACEnabled, commonTextStyle, labelStyle) { delta ->
                        val newTemp = (passTemp.toFloatOrNull() ?: 22.0f) + delta
                        serviceManager.updateData(CarConstants.CAR_HVAC_PASS_TEMPERATURE.getValue(), String.format(java.util.Locale.US, "%.1f", newTemp))
                    }
                }

                // 6. Navigation (Rightmost 10%)
                Box(modifier = Modifier.weight(0.1f)) {
                    NavigationSection(scope)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppSwitcherSection() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configs = br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.getAllConfigs()
    // Select first by default if nothing selected
    var selectedPackage by remember { mutableStateOf(configs.keys.firstOrNull() ?: "") }
    val showMenu = br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { 
            configs[selectedPackage]?.let { scope.launch { br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.sendToDisplay(it) } }
        }) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Cluster", tint = Color.White)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFF333D4D), RoundedCornerShape(4.dp))
                .clickable { br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded = !showMenu },
            contentAlignment = Alignment.Center
        ) {
            if (selectedPackage.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(try { context.packageManager.getApplicationIcon(selectedPackage) } catch(e: Exception) { null })
                        .crossfade(true)
                        .build(),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(Icons.Default.Layers, contentDescription = "Apps", tint = Color.White)
            }
        }

        IconButton(onClick = { 
            if (selectedPackage.isNotEmpty()) {
                scope.launch { br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.launchAnyApp(context, selectedPackage) }
            }
        }) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "MMI", tint = Color.White)
        }
    
        if (showMenu) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = androidx.compose.ui.unit.IntOffset(12 * context.resources.displayMetrics.density.toInt(), -50 * context.resources.displayMetrics.density.toInt()),
                onDismissRequest = { br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded = false },
                properties = PopupProperties(focusable = true, clippingEnabled = false)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .width(300.dp)
                        .padding(12.dp)
                ) {
                    val appList = configs.toList()
                    val columns = 3
                    val rows = (appList.size + columns - 1) / columns

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                for (c in 0 until columns) {
                                    val index = r * columns + c
                                    if (index < appList.size) {
                                        val (pkg, config) = appList[index]
                                        AppGridItem(pkg, context, scope) {
                                            selectedPackage = pkg
                                            br.com.redesurftank.havalshisuku.models.BottomBarState.isMenuExpanded = false
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

            }
}
@Composable
fun CarSettingsSection(drive: String, ev: String, regen: String, steer: String) {
    val showSettings = br.com.redesurftank.havalshisuku.models.BottomBarState.isSettingsMenuExpanded
    val serviceManager = br.com.redesurftank.havalshisuku.managers.ServiceManager.getInstance()
    val context = br.com.redesurftank.App.getContext()


    Row(
        modifier = Modifier
            .wrapContentHeight()
            .clickable { br.com.redesurftank.havalshisuku.models.BottomBarState.isSettingsMenuExpanded = !showSettings },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val toggle = { br.com.redesurftank.havalshisuku.models.BottomBarState.isSettingsMenuExpanded = !showSettings }
        
        ShortcutItem("Condução", when(drive) { "2" -> "ECO"; "0" -> "NORM"; "1" -> "SPORT"; else -> "DRIVE" }, 
            when(drive) { "2" -> Color.Green; "1" -> Color.Red; else -> Color.White }, toggle)
            
        ShortcutItem("Regen", when(regen) { "2" -> "LOW"; "0" -> "MED"; "1" -> "HIGH"; else -> "REG" },
            Color.White, toggle)
            
        ShortcutItem("Modo EV", when(ev) { "0" -> "HEV"; "1" -> "EVP"; "3" -> "EV"; else -> "EV" }, 
            when(ev) { "1" -> Color.Green; "3" -> Color.Blue; else -> Color.White }, toggle)
            
        ShortcutItem("Direção", when(steer) { "2" -> "COMF"; "0" -> "NORM"; "1" -> "SPRT"; else -> "STR" }, 
            Color.White, toggle)
    }

    if (showSettings) {
        Popup(
            alignment = Alignment.BottomCenter,
            offset = androidx.compose.ui.unit.IntOffset(0, -50 * context.resources.displayMetrics.density.toInt()),
            onDismissRequest = { br.com.redesurftank.havalshisuku.models.BottomBarState.isSettingsMenuExpanded = false },
            properties = PopupProperties(focusable = true, clippingEnabled = false)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .width(420.dp)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Category: Drive Mode
                    SettingsCategoryRow("Modo de Condução", drive, listOf(
                        "2" to "Eco", "0" to "Normal", "1" to "Sport", "3" to "Neve", "4" to "Areia", "5" to "Lama"
                    )) { newVal -> serviceManager.updateData(CarConstants.CAR_DRIVE_SETTING_DRIVE_MODE.getValue(), newVal) }

                    // Category: EV Mode
                    SettingsCategoryRow("Modo EV", ev, listOf(
                        "0" to "HEV", "1" to "EV Prioritário", "3" to "EV"
                    )) { newVal -> serviceManager.updateData(CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue(), newVal) }

                    // Category: Regen
                    SettingsCategoryRow("Modo de Regeneração", regen, listOf(
                        "2" to "Baixo", "0" to "Normal", "1" to "Alto"
                    )) { newVal -> serviceManager.updateData(CarConstants.CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL.getValue(), newVal) }

                    // Category: Steering
                    SettingsCategoryRow("Modo de Direção", steer, listOf(
                        "2" to "Conforto", "0" to "Normal", "1" to "Esporte"
                    )) { newVal -> serviceManager.updateData(CarConstants.CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE.getValue(), newVal) }

                    Spacer(Modifier.height(8.dp))
                    IconButton(
                        onClick = { br.com.redesurftank.havalshisuku.models.BottomBarState.isSettingsMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Fechar", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryRow(label: String, currentValue: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                    color = if (isSelected) Color(0xFF2196F3).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF2196F3) else Color.Transparent
                    )
                ) {
                    Text(
                        text = valLabel,
                        color = if (isSelected) Color(0xFF2196F3) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ShortcutItem(label: String, value: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.height(30.dp).offset(y = 3.dp).clickable { onClick() }
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center
        )
        ShortcutLabel(text = value, color = color, onClick = onClick)
    }
}

@Composable
fun ShortcutLabel(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.padding(horizontal = 1.dp).width(40.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.dp).fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TempControlSection(label: String, temp: String, isEnabled: Boolean, commonTextStyle: TextStyle, labelStyle: TextStyle, onValueChange: (Float) -> Unit) {
    val alpha = if (isEnabled) 1f else 0.4f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
        SmallButton(Icons.Default.Remove, isEnabled) { onValueChange(-0.5f) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = labelStyle)
            val currentTemp = temp.toFloatOrNull() ?: 22.0f
            val tempColor = if (currentTemp > 30f) Color.Red else Color.Blue
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = tempColor)) {
                        append(temp)
                    }
                    append(" °C")
                },
                style = commonTextStyle,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        SmallButton(Icons.Default.Add, isEnabled) { onValueChange(0.5f) }
    }
}

@Composable
fun FanControlSection(speed: Int, isEnabled: Boolean, labelStyle: TextStyle, onValueChange: (Int) -> Unit) {
    val alpha = if (isEnabled) 1f else 0.4f
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
        SmallButton(Icons.Default.Remove, isEnabled) { onValueChange(-1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Ventilação", style = labelStyle)
            FanSpeedIcon(speed = speed)
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
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = innerRadius,
            center = center
        )
    }
}

@Composable
fun VolumeControlSection(label: String, volume: Int, commonTextStyle: TextStyle, labelStyle: TextStyle, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallButton(Icons.AutoMirrored.Filled.VolumeDown) { onValueChange(-1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = labelStyle)
            Text(
                text = volume.toString(),
                style = commonTextStyle,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        SmallButton(Icons.AutoMirrored.Filled.VolumeUp) { onValueChange(1) }
    }
}

@Composable
fun NavigationSection(scope: kotlinx.coroutines.CoroutineScope) {
    Row {
        NavIcon(Icons.AutoMirrored.Filled.ArrowBack) { 
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("input", "keyevent", "4")) 
            }
        }
        NavIcon(Icons.Default.Home) { 
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("input", "keyevent", "3")) 
            }
        }
        NavIcon(Icons.Default.Layers) { 
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                ShizukuUtils.runCommandAndGetOutput(arrayOf("input", "keyevent", "187")) 
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SmallButton(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = if (enabled) onClick else ({}),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF0F0F0F),
        modifier = Modifier.size(60.dp, 50.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AppGridItem(pkg: String, context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, onClick: () -> Unit) {
    val appLabel = remember(pkg) {
        try {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) { pkg }
    }
    val appIcon = remember(pkg) {
        try { context.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
    }

    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable { 
                onClick()
                // Launch immediately on selection
                scope.launch { 
                    br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher.launchAnyApp(context, pkg)
                }
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                AsyncImage(
                    model = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = appLabel,
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
