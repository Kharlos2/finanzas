package com.kharlos.finanzas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kharlos.finanzas.ui.AnalyticsScreen
import com.kharlos.finanzas.ui.FILTER_REVIEW
import com.kharlos.finanzas.ui.GoalsScreen
import com.kharlos.finanzas.ui.HomeScreen
import com.kharlos.finanzas.ui.SettingsScreen
import com.kharlos.finanzas.ui.TxEditScreen
import com.kharlos.finanzas.ui.TxListScreen
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { FinanzasApp() } }
    }
}

private val permissions = buildList {
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Resumen", Icons.Default.Home),
    Tab("Movimientos", Icons.Default.List),
    Tab("Analítica", Icons.Default.DateRange),
    Tab("Metas", Icons.Default.Star),
    Tab("Ajustes", Icons.Default.Settings),
)

@Composable
private fun FinanzasApp() {
    val ctx = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var tab by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    val hasSms = remember(permTick, tab) { granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.READ_SMS) }
    val hasNotif = remember(permTick, tab) { Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    LaunchedEffect(Unit) { if (!hasSms || !hasNotif) launcher.launch(permissions) }

    var month by remember { mutableStateOf(YearMonth.now()) }
    var filter by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Long?>(null) }     // id del movimiento abierto
    var creating by remember { mutableStateOf(false) }          // movimiento manual nuevo
    var newGoal by remember { mutableStateOf(false) }

    if (creating || editing != null) {
        BackHandler { creating = false; editing = null }
        TxEditScreen(editing) { creating = false; editing = null }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; if (i != 1) filter = null },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                0, 1 -> FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Registrar movimiento") }
                3 -> FloatingActionButton(onClick = { newGoal = true }) { Icon(Icons.Default.Add, "Nueva meta") }
            }
        },
    ) { pad ->
        Surface(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(month, { month = it },
                    onCategory = { filter = it; tab = 1 },
                    onPendingReview = { filter = FILTER_REVIEW; tab = 1 })
                1 -> TxListScreen(month, { month = it }, filter, { filter = it }, onOpen = { editing = it })
                2 -> AnalyticsScreen(month, { month = it }, onCategory = { filter = it; tab = 1 })
                3 -> GoalsScreen(newGoal) { newGoal = false }
                else -> SettingsScreen(hasSms, hasNotif) { launcher.launch(permissions) }
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF80CBC4))
        else -> lightColorScheme(primary = Color(0xFF00796B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
