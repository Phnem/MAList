package com.example.myapplication.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.myapplication.data.models.UiStrings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.DropboxSyncManager
import com.phnem.vetro.R
import com.example.myapplication.data.models.*
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import com.example.myapplication.ui.shared.theme.*
import com.example.myapplication.ui.shared.components.GlassIconButton
import com.example.myapplication.ui.shared.inertialCollision
import com.example.myapplication.ui.shared.rememberInertialCollisionState
import com.example.myapplication.ui.navigation.navigateToWelcome
import com.example.myapplication.SyncState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    dropboxSyncManager: DropboxSyncManager,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bg = MaterialTheme.colorScheme.background
    val textC = MaterialTheme.colorScheme.onBackground
    val view = LocalView.current
    val context = LocalContext.current

    val strings = getStrings(uiState.language)
    var showCloudSheet by remember { mutableStateOf(false) }
    var showContactSheet by remember { mutableStateOf(false) }
    var showUpdateChangelogSheet by remember { mutableStateOf(false) }
    val tiles = rememberSettingsTiles(
        uiState = uiState,
        strings = strings,
        viewModel = viewModel,
        context = context,
        onCloudClick = { showCloudSheet = true },
        onContactClick = { showContactSheet = true }
    )
    val collisionState = rememberInertialCollisionState()
    val settingsHazeState = remember { HazeState() }
    var showDeveloperSection by rememberSaveable { mutableStateOf(false) }
    val importDbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importDbFromFile(context, it) }
    }

    BackHandler(enabled = showCloudSheet || showContactSheet || showUpdateChangelogSheet) {
        showCloudSheet = false
        showContactSheet = false
        showUpdateChangelogSheet = false
    }

    val blurRadius by animateDpAsState(
        targetValue = if (showCloudSheet || showContactSheet || showUpdateChangelogSheet) 16.dp else 0.dp,
        animationSpec = tween(300),
        label = "backgroundBlur"
    )

    // Усиленный «punch»-эффект: мощнее удар, мягче пружина, чуть более упругий отскок.
    LaunchedEffect(Unit) {
        collisionState.triggerCollision(
            impactForce = 55f,
            stiffness = 200f,
            dampingRatio = 0.45f
        )
    }
    LaunchedEffect(uiState.importDbMessage) {
        val msg = uiState.importDbMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearImportDbMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .sharedBounds(
                    rememberSharedContentState(key = "settings_container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
                )
                .clip(RoundedCornerShape(32.dp))
                .hazeSource(settingsHazeState)
                .background(bg)
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        performHaptic(view, "light")
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textC,
                            modifier = Modifier.sharedElement(
                                rememberSharedContentState(key = "settings_icon"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = strings.settingsScreenTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textC,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = tiles,
                        key = { _: Int, tile: SettingsTile -> tile.id },
                        span = { _: Int, tile: SettingsTile -> GridItemSpan(tile.span) },
                        contentType = { _: Int, tile: SettingsTile -> tile::class.simpleName }
                    ) { index, tile ->
                        Box(modifier = Modifier.inertialCollision(collisionState, index, 1.2f)) {
                            when (tile) {
                                is ToggleTile -> ToggleTileItem(
                                    tile = tile,
                                    strings = strings,
                                    onPerformHaptic = { performHaptic(view, "light") }
                                )
                                is ActionTile -> if (tile.id == "update") {
                                    val updateState = when (uiState.updateStatus) {
                                        AppUpdateStatus.IDLE -> UpdateTileState.Idle
                                        AppUpdateStatus.LOADING -> UpdateTileState.Checking
                                        AppUpdateStatus.UPDATE_AVAILABLE -> UpdateTileState.UpdateAvailable(
                                            uiState.latestVersion ?: ""
                                        )
                                        AppUpdateStatus.NO_UPDATE -> UpdateTileState.UpToDate(uiState.currentVersion)
                                        AppUpdateStatus.ERROR -> UpdateTileState.Error
                                    }
                                    val isUpdateClickable = updateState is UpdateTileState.Idle ||
                                        updateState is UpdateTileState.UpdateAvailable ||
                                        updateState is UpdateTileState.Error
                                    BaseTile(
                                        tile = tile,
                                        modifier = Modifier
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        performHaptic(view, "light")
                                                        showUpdateChangelogSheet = true
                                                        if (uiState.updateChangelogMarkdown.isNullOrBlank() && !uiState.isUpdateChangelogLoading) {
                                                            viewModel.loadUpdateChangelog()
                                                        }
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(settingsTileIconBoxBg()),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        tile.icon,
                                                        contentDescription = null,
                                                        tint = tile.accentColor,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = tile.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontFamily = SnProFamily,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                tile.subtitle?.let {
                                                    Text(
                                                        text = it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontFamily = SnProFamily,
                                                        modifier = Modifier.padding(top = 2.dp),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(
                                                        enabled = isUpdateClickable,
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        when (updateState) {
                                                            is UpdateTileState.Idle, is UpdateTileState.Error -> {
                                                                performHaptic(view, "light")
                                                                viewModel.checkAppUpdate(context)
                                                            }
                                                            is UpdateTileState.UpdateAvailable -> {
                                                                performHaptic(view, "light")
                                                                uiState.latestDownloadUrl?.let { url ->
                                                                    context.startActivity(
                                                                        Intent(
                                                                            Intent.ACTION_VIEW,
                                                                            url.toUri()
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                            else -> {}
                                                        }
                                                    }
                                            ) {
                                                UpdateTile(
                                                    state = updateState,
                                                    checkButtonText = strings.checkButtonText,
                                                    checkingText = strings.updateTileChecking,
                                                    availableText = strings.updateTileAvailable,
                                                    upToDateText = strings.updateTileUpToDate,
                                                    versionLabel = strings.updateTileVersionLabel
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    ActionTileItem(
                                        tile = tile,
                                        strings = strings,
                                        onPerformHaptic = { performHaptic(view, "light") }
                                    )
                                }
                                is DetailTile -> {
                                    val tileVisible = when (tile.id) {
                                        "cloud" -> !showCloudSheet
                                        "contact" -> !showContactSheet
                                        else -> true
                                    }
                                    Column(modifier = Modifier.fillMaxWidth().animateItem()) {
                                        AnimatedVisibility(
                                            visible = tileVisible,
                                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                                        ) {
                                            DetailTileItem(
                                                tile = tile,
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = this@AnimatedVisibility,
                                                onPerformHaptic = { performHaptic(view, "light") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(
                        key = "developer_section",
                        span = { GridItemSpan(2) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .inertialCollision(collisionState, index = 7, baseMultiplier = 1.2f)
                        ) {
                            DeveloperSettingsSection(
                                strings = strings,
                                uiState = uiState,
                                expanded = showDeveloperSection,
                                onExpandToggle = {
                                    performHaptic(view, "light")
                                    showDeveloperSection = !showDeveloperSection
                                },
                                onMirrorDbToggle = {
                                    performHaptic(view, "light")
                                    viewModel.setDevMirrorDb(it)
                                },
                                onHideShareToggle = {
                                    performHaptic(view, "light")
                                    viewModel.setDevHideShare(it)
                                },
                                onFpsOverlayToggle = {
                                    performHaptic(view, "light")
                                    viewModel.setDevFpsOverlay(it)
                                },
                                onExportLogs = {
                                    performHaptic(view, "light")
                                    viewModel.exportLogs(context)
                                },
                                onExportPdf = {
                                    performHaptic(view, "light")
                                    viewModel.exportCollectionPdf(context)
                                },
                                onImportDb = {
                                    performHaptic(view, "light")
                                    importDbPicker.launch("*/*")
                                }
                            )
                        }
                    }
                }
            }
            if (!uiState.devHideShareButton) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 24.dp)
                        .inertialCollision(collisionState, index = 6, baseMultiplier = 2.5f)
                ) {
                    GlassIconButton(
                        icon = Icons.Default.Share,
                        onClick = {
                            performHaptic(view, "light")
                            viewModel.shareWithDb(context)
                        },
                        modifier = Modifier,
                        hazeState = settingsHazeState,
                        size = 58.dp,
                        iconSize = 29.dp,
                        backgroundColor = Color.Transparent,
                        contentDescription = "Share",
                        tint = textC,
                        iconOffsetX = (-2).dp
                    )
                }
            }
        }
    }

    // Last Known State: сохраняем ключ при закрытии для корректной Exit-анимации
    var lastSheetKey by remember { mutableStateOf("card_cloud") }
    if (showCloudSheet) lastSheetKey = "card_cloud"
    if (showContactSheet) lastSheetKey = "card_contact"
    if (showUpdateChangelogSheet) lastSheetKey = "card_update_changelog"

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = showCloudSheet || showContactSheet || showUpdateChangelogSheet,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(250))
    ) {
        with(sharedTransitionScope) {
            val sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = lastSheetKey),
                animatedVisibilityScope = this@AnimatedVisibility,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(24.dp))
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showCloudSheet = false
                            showContactSheet = false
                            showUpdateChangelogSheet = false
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {}
                ) {
                    when (lastSheetKey) {
                        "card_cloud" -> CloudSettingsSheet(
                            onDismiss = { showCloudSheet = false },
                            onLogout = {
                                showCloudSheet = false
                                dropboxSyncManager.logout()
                                navController.navigateToWelcome()
                            },
                            sharedModifier = sharedModifier
                        )
                        "card_contact" -> ContactSheet(
                            onDismiss = { showContactSheet = false },
                            sharedModifier = sharedModifier
                        )
                        "card_update_changelog" -> UpdateChangelogSheet(
                            viewModel = viewModel,
                            onDismiss = { showUpdateChangelogSheet = false },
                            sharedModifier = sharedModifier
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DeveloperSettingsSection(
    strings: UiStrings,
    uiState: SettingsUiState,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onMirrorDbToggle: (Boolean) -> Unit,
    onHideShareToggle: (Boolean) -> Unit,
    onFpsOverlayToggle: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
    onExportPdf: () -> Unit,
    onImportDb: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val tileBg =
        if (isDark) OverlayThemeTokens.TileBackgroundDark
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val accent = if (isDark) BrandBlueSoft else BrandRed
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 24.dp else 999.dp,
        animationSpec = tween(300),
        label = "devSectionCorner"
    )
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        shape = shape,
        color = tileBg,
        tonalElevation = 0.dp,
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.devSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = SnProFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeveloperSwitchRow(
                        title = strings.devMirrorDbTitle,
                        subtitle = strings.devMirrorDbSubtitle,
                        checked = uiState.devMirrorDbToDocuments,
                        accent = accent,
                        onCheckedChange = onMirrorDbToggle
                    )
                    DeveloperSwitchRow(
                        title = strings.devHideShareTitle,
                        subtitle = strings.devHideShareSubtitle,
                        checked = uiState.devHideShareButton,
                        accent = accent,
                        onCheckedChange = onHideShareToggle
                    )
                    DeveloperActionRow(
                        title = strings.devExportLogsTitle,
                        subtitle = strings.devExportLogsSubtitle,
                        isLoading = uiState.isExportingLogs,
                        iconDescription = strings.devExportLogsCd,
                        icon = Icons.Default.BugReport,
                        accent = accent,
                        onClick = onExportLogs
                    )
                    DeveloperActionRow(
                        title = strings.devExportPdfTitle,
                        subtitle = strings.devExportPdfSubtitle,
                        isLoading = uiState.isExportingPdf,
                        iconDescription = strings.devExportPdfCd,
                        icon = Icons.Default.PictureAsPdf,
                        accent = accent,
                        onClick = onExportPdf
                    )
                    DeveloperActionRow(
                        title = strings.devImportDbTitle,
                        subtitle = strings.devImportDbSubtitle,
                        isLoading = uiState.isImportingDb,
                        iconDescription = strings.devImportDbCd,
                        icon = Icons.Default.FileOpen,
                        accent = accent,
                        onClick = onImportDb
                    )
                    DeveloperSwitchRow(
                        title = strings.devFpsOverlayTitle,
                        subtitle = strings.devFpsOverlaySubtitle,
                        checked = uiState.devFpsOverlay,
                        accent = accent,
                        onCheckedChange = onFpsOverlayToggle
                    )
                }
            }
        }
    }
}

@Composable
private fun DeveloperSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = SnProFamily
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = SnProFamily
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.5f),
                uncheckedTrackColor = accent.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun DeveloperActionRow(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    iconDescription: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = SnProFamily
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = SnProFamily
            )
        }
        IconButton(
            onClick = onClick,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                    tint = accent
                )
            }
        }
    }
}

@Composable
fun rememberSettingsTiles(
    uiState: SettingsUiState,
    strings: UiStrings,
    viewModel: SettingsViewModel,
    context: Context,
    onCloudClick: () -> Unit,
    onContactClick: () -> Unit
): List<SettingsTile> = remember(
    uiState.language,
    uiState.theme,
    uiState.contentType,
    uiState.updateStatus,
    uiState.currentVersion,
    uiState.latestDownloadUrl,
    strings
) {
    listOf(
        // 2 квадратных
        ToggleTile(
            id = "lang",
            title = strings.languageCardTitle,
            subtitle = if (uiState.language == AppLanguage.RU) "Язык интерфейса" else "Interface language",
            icon = Icons.Outlined.Language,
            accentColor = SettingsAccentLangDarkBlue,
            span = 1,
            options = listOf(
                SegmentedOption(label = "EN") { viewModel.setLanguage(AppLanguage.EN) },
                SegmentedOption(label = "RU") { viewModel.setLanguage(AppLanguage.RU) }
            ),
            selectedIndex = if (uiState.language == AppLanguage.RU) 1 else 0
        ),
        ToggleTile(
            id = "theme",
            title = strings.themeTitle,
            subtitle = if (uiState.language == AppLanguage.RU) "Предпочтение темы" else "Theme preference",
            icon = Icons.Outlined.Palette,
            accentColor = SettingsAccentThemeDarkGreen,
            span = 1,
            options = listOf(
                SegmentedOption(icon = Icons.Outlined.LightMode) { viewModel.setTheme(AppTheme.LIGHT) },
                SegmentedOption(icon = Icons.Outlined.DarkMode) { viewModel.setTheme(AppTheme.DARK) },
                SegmentedOption(icon = Icons.Outlined.BrightnessAuto) { viewModel.setTheme(AppTheme.SYSTEM) }
            ),
            selectedIndex = when (uiState.theme) {
                AppTheme.LIGHT -> 0
                AppTheme.DARK -> 1
                AppTheme.SYSTEM -> 2
            }
        ),
        // 1 длинный
        DetailTile(
            id = "cloud",
            title = strings.cloudSettingsTitle,
            subtitle = strings.cloudSettingsSubtitle,
            icon = Icons.Outlined.Cloud,
            accentColor = SettingsAccentCloudLightBlue,
            span = 2,
            onClick = onCloudClick
        ),
        // 2 квадратных
        ToggleTile(
            id = "content_type",
            title = strings.contentTypeTitle,
            subtitle = if (uiState.language == AppLanguage.RU) "Аниме или ТВ" else "Anime or TV",
            icon = Icons.Outlined.Category,
            accentColor = SettingsAccentContentOrange,
            span = 1,
            options = listOf(
                SegmentedOption(label = strings.typeAnime) { viewModel.setContentType(AppContentType.ANIME) },
                SegmentedOption(label = strings.typeMovies) { viewModel.setContentType(AppContentType.MOVIE) }
            ),
            selectedIndex = if (uiState.contentType == AppContentType.MOVIE) 1 else 0
        ),
        ActionTile(
            id = "update",
            title = strings.checkForUpdateTitle,
            subtitle = uiState.currentVersion,
            icon = Icons.Outlined.SystemUpdate,
            accentColor = RateColor4,
            span = 1,
            onClick = {
                when (uiState.updateStatus) {
                    AppUpdateStatus.IDLE, AppUpdateStatus.ERROR -> viewModel.checkAppUpdate(context)
                    AppUpdateStatus.UPDATE_AVAILABLE -> uiState.latestDownloadUrl?.let { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                    else -> {}
                }
            },
            updateStatus = uiState.updateStatus,
            currentVersion = uiState.currentVersion,
            latestDownloadUrl = uiState.latestDownloadUrl
        ),
        // 1 длинный
        DetailTile(
            id = "contact",
            title = strings.contactTitle,
            subtitle = strings.contactSubtitle,
            icon = Icons.Outlined.Person,
            accentColor = SettingsAccentContactLightGreen,
            span = 2,
            onClick = onContactClick
        )
    )
}

/**
 * Сегментированный переключатель в виде «капсула внутри капсулы» (как на фото 3).
 * Без галочек, с анимацией перемещения внутренней капсулы.
 */
@Composable
fun CapsuleChipRow(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: (Int) -> String? = { null },
    onOptionClick: (Int) -> Unit
) {
    val containerHeight = 44.dp
    val containerCornerRadius = containerHeight / 2
    val containerShape = RoundedCornerShape(containerCornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .height(containerHeight)
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = containerShape
            )
    ) {
        val innerWidth = maxWidth
        val segmentWidth = innerWidth / options.size
        val pillCornerRadius = containerHeight / 2
        val innerShape = RoundedCornerShape(pillCornerRadius)
        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "pill_offset"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(innerShape)
                    .background(accentColor.copy(alpha = 0.22f))
                    .border(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.35f),
                        shape = innerShape
                    )
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, opt ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOptionClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (opt.icon != null) {
                            Icon(
                                opt.icon,
                                contentDescription = contentDescription(index),
                                modifier = Modifier.size(20.dp),
                                tint = if (selectedIndex == index) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                opt.label ?: "",
                                fontFamily = SnProFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selectedIndex == index) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleTileItem(
    tile: ToggleTile,
    strings: UiStrings,
    onPerformHaptic: () -> Unit
) {
    BaseTile(tile = tile, modifier = Modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(settingsTileIconBoxBg()),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, contentDescription = null, tint = tile.accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = SnProFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            tile.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            CapsuleChipRow(
                options = tile.options,
                selectedIndex = tile.selectedIndex,
                accentColor = tile.accentColor,
                modifier = Modifier.fillMaxWidth(),
                contentDescription = { index ->
                    when (tile.id) {
                        "theme" -> when (index) {
                            0 -> strings.themeLight
                            1 -> strings.themeDark
                            else -> strings.themeSystem
                        }
                        else -> null
                    }
                },
                onOptionClick = { index ->
                    onPerformHaptic()
                    tile.options[index].onClick()
                }
            )
        }
    }
}

@Composable
fun ActionTileItem(
    tile: ActionTile,
    strings: UiStrings,
    onPerformHaptic: () -> Unit
) {
    BaseTile(
        tile = tile,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onPerformHaptic(); tile.onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(settingsTileIconBoxBg()),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, contentDescription = null, tint = tile.accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = SnProFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            tile.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            val updateStatus = tile.updateStatus
            if (updateStatus != null) {
                UpdateStateButton(
                    status = updateStatus,
                    idleText = strings.checkButtonText,
                    onClick = tile.onClick
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DetailTileItem(
    tile: DetailTile,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPerformHaptic: () -> Unit
) {
    with(sharedTransitionScope) {
        BaseTile(
            tile = tile,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPerformHaptic(); tile.onClick() }
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "card_${tile.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
                )
        ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(settingsTileIconBoxBg()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tile.icon, contentDescription = null, tint = tile.accentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = SnProFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                tile.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tile.accentColor
            )
        }
    }
    }
}

@Composable
fun UpdateStateButton(
    status: AppUpdateStatus,
    idleText: String,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            AppUpdateStatus.IDLE -> MaterialTheme.colorScheme.secondary
            AppUpdateStatus.LOADING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            AppUpdateStatus.NO_UPDATE -> RateColor4
            AppUpdateStatus.UPDATE_AVAILABLE -> BrandBlue
            AppUpdateStatus.ERROR -> BrandRed
        }, label = "btnBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when (status) {
            AppUpdateStatus.IDLE -> MaterialTheme.colorScheme.onSecondary
            AppUpdateStatus.LOADING -> MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.5f)
            else -> Color.White
        }, label = "btnContent"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(if (status == AppUpdateStatus.IDLE) 1f else 0.6f)
            .height(44.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = status != AppUpdateStatus.LOADING) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            AppUpdateStatus.IDLE -> Text(idleText, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp, fontFamily = SnProFamily)
            AppUpdateStatus.LOADING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = contentColor, strokeWidth = 2.dp)
            AppUpdateStatus.NO_UPDATE -> Icon(Icons.Default.Check, "Up to date", tint = contentColor, modifier = Modifier.size(28.dp))
            AppUpdateStatus.UPDATE_AVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdateAlt, "Update", tint = contentColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download", fontWeight = FontWeight.Bold, color = contentColor, fontFamily = SnProFamily)
            }
            AppUpdateStatus.ERROR -> Icon(Icons.Default.Close, "Error", tint = contentColor, modifier = Modifier.size(28.dp))
        }
    }
}
