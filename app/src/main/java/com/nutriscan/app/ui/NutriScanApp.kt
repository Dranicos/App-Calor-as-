package com.nutriscan.app.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nutriscan.app.ui.screens.HistoryScreen
import com.nutriscan.app.ui.screens.HomeScreen
import com.nutriscan.app.ui.screens.PlanScreen
import com.nutriscan.app.ui.screens.ReviewScreen
import com.nutriscan.app.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Hoy", Icons.Filled.Home),
    HISTORY("history", "Historial", Icons.Filled.CalendarMonth),
    PLAN("plan", "Mi plan", Icons.Filled.MonitorWeight),
    SETTINGS("settings", "Ajustes", Icons.Filled.Settings),
}

private fun reviewRoute(photo: String? = null, entryId: Long = -1) =
    "review?photo=${Uri.encode(photo.orEmpty())}&entryId=$entryId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutriScanApp() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }

    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        vm.refreshDay()
        onPauseOrDispose { }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCapture
        pendingCapture = null
        if (ok && path != null && File(path).length() > 0) {
            nav.navigate(reviewRoute(photo = path))
        } else if (path != null) {
            File(path).delete()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = runCatching { withContext(Dispatchers.IO) { vm.photos.importFromUri(uri) } }.getOrNull()
                if (file != null) nav.navigate(reviewRoute(photo = file.absolutePath))
                else Toast.makeText(context, "No se pudo abrir la imagen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openCamera() {
        val (file, uri) = vm.photos.newCaptureTarget()
        pendingCapture = file.absolutePath
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCapture = null
            Toast.makeText(context, "No se encontró una app de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == Tab.HOME || currentTab == Tab.HISTORY) {
                ExtendedFloatingActionButton(
                    onClick = { showScanSheet = true },
                    icon = { Icon(Icons.Outlined.CameraAlt, null) },
                    text = { Text("Escanear") },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    vm = vm,
                    contentPadding = padding,
                    onOpenMeal = { nav.navigate(reviewRoute(entryId = it)) },
                    onGoToPlan = { nav.navigate(Tab.PLAN.route) { launchSingleTop = true } },
                    onGoToSettings = { nav.navigate(Tab.SETTINGS.route) { launchSingleTop = true } },
                )
            }
            composable(Tab.HISTORY.route) {
                HistoryScreen(vm, padding, onOpenMeal = { nav.navigate(reviewRoute(entryId = it)) })
            }
            composable(Tab.PLAN.route) { PlanScreen(vm, padding) }
            composable(Tab.SETTINGS.route) { SettingsScreen(vm, padding) }
            composable(
                "review?photo={photo}&entryId={entryId}",
                arguments = listOf(
                    navArgument("photo") { type = NavType.StringType; defaultValue = "" },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                ReviewScreen(onDone = { nav.popBackStack() })
            }
        }
    }

    if (showScanSheet) {
        ModalBottomSheet(onDismissRequest = { showScanSheet = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                SheetOption(Icons.Outlined.CameraAlt, "Hacer foto", "Fotografía tu plato o producto") {
                    showScanSheet = false; openCamera()
                }
                SheetOption(Icons.Outlined.PhotoLibrary, "Elegir de la galería", "Analiza una foto que ya tengas") {
                    showScanSheet = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetOption(Icons.Outlined.EditNote, "Añadir a mano", "Sin foto, escribiendo los ingredientes") {
                    showScanSheet = false; nav.navigate(reviewRoute())
                }
            }
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
