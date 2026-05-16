package com.example.myapplication.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.DropboxSyncManager
import com.example.myapplication.data.models.AppUpdateStatus
import com.example.myapplication.isAppInDarkTheme
import com.phnem.vetro.R
import com.example.myapplication.SyncState
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.utils.formatApkSizeLabel
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.softPlateShadowForLightSheet
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

@Composable
fun CloudSettingsSheet(
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    sharedModifier: Modifier = Modifier
) {
    val dropboxSyncManager: DropboxSyncManager = koinInject()
    val context = LocalContext.current
    val settingsVm: SettingsViewModel = koinViewModel()
    val uiState by settingsVm.uiState.collectAsStateWithLifecycle()
    val strings = getStrings(uiState.language)
    val scope = rememberCoroutineScope()
    val lastSyncTimestamp = remember {
        context.getSharedPreferences("dropbox_prefs", Context.MODE_PRIVATE).getLong("last_sync_time", 0L)
    }
    val syncState by dropboxSyncManager.syncState.collectAsStateWithLifecycle()

    val sheetInDarkTheme = isAppInDarkTheme()
    val sheetSurface = if (sheetInDarkTheme) {
        Color(0xFF0D1117).copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
    val cloudPanelShape = RoundedCornerShape(24.dp)

    Card(
        modifier = sharedModifier.fillMaxWidth(),
        shape = cloudPanelShape,
        colors = CardDefaults.cardColors(containerColor = sheetSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = if (uiState.language == AppLanguage.RU) "Облачные настройки" else "Cloud Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = SnProFamily,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                CloudSettingsSection(
                    strings = CloudStrings(
                        title = if (uiState.language == AppLanguage.RU) "Синхронизация с облаком" else "Cloud Sync",
                        subtitle = strings.cloudSettingsSubtitle,
                        syncNow = strings.syncLabel,
                        lastSync = strings.lastSync,
                        neverSynced = strings.never,
                        logout = if (uiState.language == AppLanguage.RU) "Выйти" else "Logout",
                    ),
                    lastSyncTime = lastSyncTimestamp,
                    isSyncing = syncState == SyncState.SYNCING,
                    onSyncClick = { scope.launch { dropboxSyncManager.syncNow() } },
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
fun ContactSheet(
    onDismiss: () -> Unit,
    sharedModifier: Modifier = Modifier
) {
    val settingsVm: SettingsViewModel = koinViewModel()
    val uiState by settingsVm.uiState.collectAsStateWithLifecycle()
    val strings = getStrings(uiState.language)
    val context = LocalContext.current
    val view = LocalView.current
    val sheetInDark = isAppInDarkTheme()

    Column(
        modifier = sharedModifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow.copy(
                    alpha = if (sheetInDark) 0.75f else 0.95f,
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = strings.contactTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                fontFamily = SnProFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = strings.contactSupportSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = SnProFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ContactActionCard(
                iconId = R.drawable.ic_github,
                title = "GitHub",
                onClick = {
                    performHaptic(view, "light")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Phnem/Vetra")))
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
            ContactActionCard(
                iconId = R.drawable.tg,
                title = "Telegram",
                onClick = {
                    performHaptic(view, "light")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/H415base")))
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun UpdateChangelogSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    installPermissionLauncher: ActivityResultLauncher<Intent>,
    sharedModifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = getStrings(uiState.language)
    val changelog = uiState.updateChangelogMarkdown
    val context = LocalContext.current
    val view = LocalView.current
    val uriHandler = LocalUriHandler.current
    var whatsNewExpanded by remember { mutableStateOf(false) }
    val accent = OverlayThemeTokens.AccentNeonGreen
    val sheetInDarkTheme = isAppInDarkTheme()

    val sheetSurface = if (sheetInDarkTheme) {
        Color(0xFF0D1117).copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }

    val versionTag = uiState.latestVersion.orEmpty().ifBlank { "—" }
    val apkSizeLabel = formatApkSizeLabel(
        uiState.latestApkSizeBytes,
        uiState.language,
        strings.updateApkSizeUnit,
    ) ?: "—"
    val updatePitchText = strings.updateDialogDescription.format(versionTag, apkSizeLabel)

    val sheetHeadline = when {
        uiState.isUpdateChangelogLoading -> strings.checkForUpdateTitle
        uiState.updateStatus == AppUpdateStatus.UPDATE_AVAILABLE -> strings.updateDialogTitle
        else -> strings.checkForUpdateTitle
    }

    val whatsPreview = remember(changelog) {
        changelog?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removePrefix("#")
            ?.trim()
            .orEmpty()
            .take(160)
    }
    val whatsSubtitle = when {
        whatsPreview.isNotBlank() -> whatsPreview
        else -> strings.updateWhatsNewFallback
    }

    val mdTypography = markdownTypography(
        h1 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        h2 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        h3 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        h4 = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        h5 = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        h6 = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        text = MaterialTheme.typography.bodySmall,
        paragraph = MaterialTheme.typography.bodySmall,
        bullet = MaterialTheme.typography.bodySmall,
        ordered = MaterialTheme.typography.bodySmall,
        list = MaterialTheme.typography.bodySmall,
    )

    val needsInstallPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    val showInstallPermissionAction =
        uiState.updateStatus == AppUpdateStatus.UPDATE_AVAILABLE &&
            needsInstallPermission &&
            uiState.pendingApkPathForInstall != null &&
            !uiState.isApkDownloading

    val downloadProgressDisplayed by animateFloatAsState(
        targetValue = uiState.apkDownloadProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "apkSheetProgressDisplayed",
    )

    val sheetNestedShadowDp = OverlayThemeTokens.UpdateSheetNestedShadowElevation
    val starPlateShape = RoundedCornerShape(18.dp)
    val whatsNewShape = RoundedCornerShape(16.dp)

    Column(
        modifier = sharedModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(sheetSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = {
                    performHaptic(view, "light")
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.updateCloseCd,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val starBg = if (sheetInDarkTheme) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .softPlateShadowForLightSheet(
                            sheetInDarkTheme,
                            starPlateShape,
                            sheetNestedShadowDp,
                        )
                        .clip(starPlateShape)
                        .background(starBg)
                        .then(
                            if (sheetInDarkTheme) {
                                Modifier
                            } else {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    starPlateShape,
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = sheetHeadline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = SnProFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (uiState.isUpdateChangelogLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = accent)
            }
        } else {
            when (uiState.updateStatus) {
                AppUpdateStatus.UPDATE_AVAILABLE -> {
                    Text(
                        text = updatePitchText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SnProFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                AppUpdateStatus.NO_UPDATE -> {
                    val installed = uiState.currentVersion.ifBlank { "—" }
                    val gh = uiState.latestVersion ?: "—"
                    Text(
                        text = strings.updateSheetNoUpdateDetail.format(installed, gh),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SnProFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                AppUpdateStatus.ERROR -> {
                    Text(
                        text = uiState.updateChangelogError ?: strings.updateChangelogLoadError,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SnProFamily,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                AppUpdateStatus.IDLE,
                AppUpdateStatus.LOADING,
                    -> {
                    Text(
                        text = strings.updateTileNewVersionSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SnProFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                ),
        ) {
            val whatsNewBg = if (sheetInDarkTheme) {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
            val whatsNewInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .softPlateShadowForLightSheet(
                        sheetInDarkTheme,
                        whatsNewShape,
                        sheetNestedShadowDp,
                    )
                    .clip(whatsNewShape)
                    .background(whatsNewBg, whatsNewShape)
                    .then(
                        if (sheetInDarkTheme) {
                            Modifier
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                whatsNewShape,
                            )
                        },
                    )
                    .clickable(
                        interactionSource = whatsNewInteractionSource,
                        indication = ripple(bounded = true),
                        onClick = {
                            performHaptic(view, "light")
                            whatsNewExpanded = !whatsNewExpanded
                        },
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.updateWhatsNewTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = SnProFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = whatsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = SnProFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (whatsNewExpanded) Int.MAX_VALUE else 2,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (whatsNewExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!changelog.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                            Markdown(
                                content = changelog,
                                typography = mdTypography,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings.updateChangelogEmpty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = SnProFamily,
                        )
                    }
                }
            }
        }

        if (uiState.isApkDownloading) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { downloadProgressDisplayed.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                strokeCap = StrokeCap.Round,
            )
            val pct = (downloadProgressDisplayed * 100f).toInt().coerceIn(0, 100)
            Text(
                text = strings.updateDownloadProgressFormat.format(pct),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = SnProFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        if (!uiState.isUpdateChangelogLoading) {
            when (uiState.updateStatus) {
                AppUpdateStatus.UPDATE_AVAILABLE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                performHaptic(view, "light")
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(strings.updateInstallLater, fontFamily = SnProFamily)
                        }
                        Button(
                            onClick = {
                                performHaptic(view, "light")
                                viewModel.startApkDownload(context)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isApkDownloading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                strings.updateInstallNow,
                                fontFamily = SnProFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                AppUpdateStatus.ERROR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                performHaptic(view, "light")
                                viewModel.loadUpdateChangelog(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(strings.updateRetryCheck, fontFamily = SnProFamily)
                        }
                        OutlinedButton(
                            onClick = {
                                performHaptic(view, "light")
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(strings.updateCloseCd, fontFamily = SnProFamily)
                        }
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = {
                            performHaptic(view, "light")
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(strings.updateCloseCd, fontFamily = SnProFamily)
                    }
                }
            }
        }

        if (showInstallPermissionAction) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    performHaptic(view, "light")
                    installPermissionLauncher.launch(viewModel.manageUnknownAppSourcesIntent(context))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(strings.updateOpenInstallPermission, fontFamily = SnProFamily)
            }
        }
    }
}

@Composable
private fun ContactActionCard(
    @DrawableRes iconId: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardInDarkTheme = isAppInDarkTheme()
    val cardShape = RoundedCornerShape(20.dp)
    val cardBg = if (cardInDarkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .softPlateShadowForLightSheet(
                cardInDarkTheme,
                cardShape,
                OverlayThemeTokens.UpdateSheetNestedShadowElevation,
            )
            .clip(cardShape)
            .background(cardBg, cardShape)
            .then(
                if (cardInDarkTheme) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        cardShape,
                    )
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = title,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit,
                colorFilter = when (iconId) {
                    R.drawable.ic_github -> ColorFilter.tint(
                        if (cardInDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    else -> null
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                fontFamily = SnProFamily,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
