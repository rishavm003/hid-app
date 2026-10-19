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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions updated; refresh location status
        if (safetyViewModel.sharingLocation.value) {
            safetyViewModel.setLocation(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAppPermissions()

        setContent {
            HavenNotesTheme {
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

    override fun onResume() {
        super.onResume()
        safetyViewModel.refresh()
        familyViewModel.reconcile()
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
        }
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
