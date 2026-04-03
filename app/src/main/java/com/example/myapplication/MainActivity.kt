package com.example.myapplication

import android.os.Build
import org.koin.android.ext.android.inject
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.models.AppTheme
import com.example.myapplication.DropboxSyncManager
import com.example.myapplication.ui.navigation.AppNavGraph
import com.example.myapplication.ui.debug.FpsOverlay
import com.example.myapplication.ui.shared.theme.OneUiTheme
import com.example.myapplication.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private val dropboxSyncManager: DropboxSyncManager by inject()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        checkPerms()

        setContent {
            val context = LocalContext.current
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
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph(navController = navController)
                    if (settingsState.devFpsOverlay) {
                        FpsOverlay(modifier = Modifier.align(Alignment.TopStart))
                    }
                }
            }
        }
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
