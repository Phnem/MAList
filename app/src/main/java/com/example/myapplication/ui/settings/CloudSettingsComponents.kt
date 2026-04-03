package com.example.myapplication.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.shared.theme.SnProFamily
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class CloudStrings(
    val title: String = "Синхронизация с облаком",
    val syncNow: String = "Синхронизировать сейчас",
    val lastSync: String = "Последняя синхронизация:",
    val neverSynced: String = "Никогда",
    val logout: String = "Выйти"
)

@Composable
fun CloudSettingsSection(
    strings: CloudStrings,
    lastSyncTime: Long,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = strings.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "${strings.lastSync} ${
                if (lastSyncTime > 0) {
                    DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                        Locale.getDefault()
                    ).format(Date(lastSyncTime))
                } else {
                    strings.neverSynced
                }
            }",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSyncClick,
                modifier = Modifier.weight(1f),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(strings.syncNow, fontFamily = SnProFamily)
                }
            }
            OutlinedButton(onClick = onLogout) {
                Text(strings.logout, fontFamily = SnProFamily)
            }
        }
    }
}
