package com.example.myapplication

import android.content.Intent
import android.graphics.Color as AndroidGraphicsColor
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.models.AppTheme
import com.example.myapplication.DropboxSyncManager
import com.example.myapplication.sync.ExternalListSyncCoordinator
import com.example.myapplication.ui.navigation.AppNavGraph
import com.example.myapplication.ui.navigation.isSplashDestination
import com.example.myapplication.ui.settings.UpdateChangelogSheet
import com.example.myapplication.ui.debug.FpsOverlay
import com.example.myapplication.ui.shared.theme.OneUiTheme
import com.example.myapplication.ui.settings.SettingsOverlayMotion
import com.example.myapplication.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val dropboxSyncManager: DropboxSyncManager by inject()
    private val externalListSyncCoordinator: ExternalListSyncCoordinator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidGraphicsColor.TRANSPARENT,
                AndroidGraphicsColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidGraphicsColor.TRANSPARENT,
                AndroidGraphicsColor.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.disableStatusBarContrastEnforced()
        }
        handleExternalListOAuthIntent(intent)
        checkPerms()

        setContent {
            val isSystemDark = isSystemInDarkTheme()
            val context = LocalContext.current

            val settingsViewModel: SettingsViewModel =
                koinViewModel(viewModelStoreOwner = this@MainActivity)
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val startupOverlayEligible by settingsViewModel.startupUpdateOverlayEligible.collectAsStateWithLifecycle()

            val installPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                settingsViewModel.onReturnedFromInstallSettings(context)
            }

            val useDarkTheme = when (settingsState.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemDark
            }

            OneUiTheme(darkTheme = useDarkTheme) {
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !useDarkTheme
                        isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }
                val navController = rememberNavController()
                val navEntry by navController.currentBackStackEntryAsState()
                val splashDestId = navEntry?.destination?.id

                var showStartupUpdateOverlay by remember { mutableStateOf(false) }
                LaunchedEffect(startupOverlayEligible, splashDestId) {
                    val onSplashScreen = navEntry?.destination.isSplashDestination()
                    val shouldOffer = startupOverlayEligible && !onSplashScreen
                    if (!startupOverlayEligible) {
                        showStartupUpdateOverlay = false
                    } else if (shouldOffer) {
                        showStartupUpdateOverlay = true
                    }
                }

                fun dismissStartupUpdateOverlay() {
                    showStartupUpdateOverlay = false
                    settingsViewModel.dismissStartupUpdateOverlayPersisted()
                }

                BackHandler(enabled = showStartupUpdateOverlay) {
                    dismissStartupUpdateOverlay()
                }

                val overlayVisible = showStartupUpdateOverlay

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNavGraph(
                        navController = navController,
                        settingsViewModel = settingsViewModel,
                    )

                    AnimatedVisibility(
                        visible = overlayVisible,
                        enter = SettingsOverlayMotion.scrimFadeIn,
                        exit = SettingsOverlayMotion.scrimFadeOut,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { dismissStartupUpdateOverlay() },
                        )
                    }
                    AnimatedVisibility(
                        visible = overlayVisible,
                        enter = SettingsOverlayMotion.panelFadeInScaleIn(),
                        exit = SettingsOverlayMotion.panelFadeOutScaleOut(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .wrapContentHeight()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { },
                            ) {
                                UpdateChangelogSheet(
                                    viewModel = settingsViewModel,
                                    onDismiss = { dismissStartupUpdateOverlay() },
                                    installPermissionLauncher = installPermissionLauncher,
                                    sharedModifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (settingsState.devFpsOverlay) {
                        FpsOverlay(modifier = Modifier.align(Alignment.TopStart))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalListOAuthIntent(intent)
    }

    private fun handleExternalListOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        externalListSyncCoordinator.handleOAuthRedirect(uri)
    }

    private fun checkPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            android.widget.Toast.makeText(this, "Need file access", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        dropboxSyncManager.onOAuthResult()
    }
}

@Suppress("DEPRECATION")
private fun Window.disableStatusBarContrastEnforced() {
    isStatusBarContrastEnforced = false
}
