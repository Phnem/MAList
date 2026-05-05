package com.example.myapplication

import android.content.Intent
import android.graphics.Color as AndroidGraphicsColor
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.models.AppTheme
import com.example.myapplication.DropboxSyncManager
import com.example.myapplication.sync.ExternalListSyncCoordinator
import com.example.myapplication.ui.navigation.AppNavGraph
import com.example.myapplication.ui.debug.FpsOverlay
import com.example.myapplication.ui.shared.theme.OneUiTheme
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

            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            val useDarkTheme = when (settingsState.theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemDark
            }

            OneUiTheme(darkTheme = useDarkTheme) {
                val navController = rememberNavController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNavGraph(navController = navController)
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
