package com.example.myapplication.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.myapplication.isAppInDarkTheme
import com.phnem.vetro.R
import com.example.myapplication.data.models.AppTheme
import com.example.myapplication.data.models.AppUpdateStatus
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.navigation.navigateToWelcome
import com.example.myapplication.ui.shared.components.GlassMenuHeader
import com.example.myapplication.ui.shared.components.GrabberReservedTop
import com.example.myapplication.ui.shared.components.IosDialogAnimatedContent
import com.example.myapplication.ui.shared.components.IosIconWell
import com.example.myapplication.ui.shared.components.IosSelectionOption
import com.example.myapplication.ui.shared.components.IosSelectionSheet
import com.example.myapplication.ui.shared.components.IosSheetScaffold
import com.example.myapplication.ui.shared.components.IosListGroup
import com.example.myapplication.ui.shared.components.IosRow
import com.example.myapplication.ui.shared.components.IosSegmentedControl
import com.example.myapplication.ui.shared.components.IosSwitch
import com.example.myapplication.ui.shared.theme.BrandBlue
import com.example.myapplication.ui.shared.theme.BrandBlueSoft
import com.example.myapplication.ui.shared.theme.BrandRed
import com.example.myapplication.ui.shared.theme.IosDesign
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.SquircleShape
import com.example.myapplication.utils.DevRepairDbStrings
import com.example.myapplication.utils.GithubUpdateStrings
import com.example.myapplication.utils.formatApkSizeLabel
import com.example.myapplication.utils.getAiConnectStrings
import com.example.myapplication.utils.getDevRepairDbStrings
import com.example.myapplication.utils.getTitleDubbingStrings
import com.example.myapplication.utils.TitleDubbingStrings
import com.example.myapplication.utils.getCollectionEnrichmentStrings
import com.example.myapplication.utils.getGithubUpdateStrings
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private const val TRIBUTE_DONATION_URL = "https://web.tribute.tg/e/Tb"

// Единая палитра icon-well по категориям (IosDesign.Category — один цвет = одна категория).
private val IconLanguage = IosDesign.Category.Language
private val IconAppearance = IosDesign.Category.Appearance
private val IconContent = IosDesign.Category.Media
private val IconCloud = IosDesign.Category.Account
private val IconContact = IosDesign.Category.Support
private val IconUpdate = IosDesign.Category.Update
private val IconUpdateReady = IosDesign.Category.Network
private val IconDonate = IosDesign.Category.Donate
private val IconDeveloper = IosDesign.Category.Storage

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isAppInDarkTheme()
    val bg = IosDesign.groupedBackground(isDark)
    val settingsBackground = remember(bg, isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF430B05),
                    0.24f to Color(0xFF210907),
                    0.58f to bg,
                    1f to bg,
                ),
            )
        } else {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFFD9CC),
                    0.28f to Color(0xFFFFEEE8),
                    0.64f to bg,
                    1f to bg,
                ),
            )
        }
    }
    val textC = MaterialTheme.colorScheme.onBackground
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val strings = getStrings(uiState.language)
    val devRepairStrings = getDevRepairDbStrings(uiState.language)
    val titleDubbingStrings = getTitleDubbingStrings(uiState.language)
    val githubUpdateStrings = getGithubUpdateStrings(uiState.language)
    val aiConnectStrings = getAiConnectStrings(uiState.language)
    val enrichmentStrings = getCollectionEnrichmentStrings(uiState.language)

    var showCloudSheet by remember { mutableStateOf(false) }
    var showAiConnectSheet by remember { mutableStateOf(false) }
    var showEnrichmentSheet by remember { mutableStateOf(false) }
    var showContactSheet by remember { mutableStateOf(false) }
    var showUpdateChangelogSheet by remember { mutableStateOf(false) }
    var showFdroidUpdateDialog by remember { mutableStateOf(false) }
    var showGithubUpdatesEnableDialog by remember { mutableStateOf(false) }
    var showDeveloperSection by rememberSaveable { mutableStateOf(false) }
    // Активный picker-лист: "lang" | "theme" | "content" | null.
    var activePicker by remember { mutableStateOf<String?>(null) }

    val importDbPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importDbFromFile(context, it) } }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onReturnedFromInstallSettings(context) }

    BackHandler(
        enabled = showCloudSheet || showAiConnectSheet || showEnrichmentSheet || showContactSheet || showUpdateChangelogSheet || activePicker != null ||
            uiState.showRepairDbLogDialog || uiState.showTitleDubbingNoAiDialog || showFdroidUpdateDialog || showGithubUpdatesEnableDialog,
    ) {
        when {
            showGithubUpdatesEnableDialog -> showGithubUpdatesEnableDialog = false
            showFdroidUpdateDialog -> showFdroidUpdateDialog = false
            uiState.showTitleDubbingNoAiDialog -> viewModel.dismissTitleDubbingNoAiDialog()
            uiState.showRepairDbLogDialog -> viewModel.discardRepairDbLog()
            activePicker != null -> activePicker = null
            else -> {
                showCloudSheet = false
                showAiConnectSheet = false
                showEnrichmentSheet = false
                showContactSheet = false
                showUpdateChangelogSheet = false
            }
        }
    }

    // Мягкий блюр фона под модальными листами (§10, но без ударной физики — тут sheet).
    val blurRadius by animateDpAsState(
        targetValue = if (showCloudSheet || showAiConnectSheet || showEnrichmentSheet || showContactSheet || showUpdateChangelogSheet) 16.dp else 0.dp,
        animationSpec = tween(300),
        label = "backgroundBlur"
    )

    LaunchedEffect(uiState.importDbMessage) {
        val msg = uiState.importDbMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearImportDbMessage()
    }
    LaunchedEffect(uiState.repairDbMessage) {
        val msg = uiState.repairDbMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        viewModel.clearRepairDbMessage()
    }
    LaunchedEffect(uiState.titleDubbingMessage) {
        val msg = uiState.titleDubbingMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        viewModel.clearTitleDubbingMessage()
    }

    if (uiState.showRepairDbLogDialog) {
        RepairDbLogDialog(
            devRepairStrings = devRepairStrings,
            onCreateLog = { performHaptic(view, "light"); viewModel.exportRepairDbLog(context) },
            onDismiss = { performHaptic(view, "light"); viewModel.discardRepairDbLog() },
        )
    }
    if (uiState.showTitleDubbingNoAiDialog) {
        TitleDubbingNoAiDialog(
            strings = titleDubbingStrings,
            onConnect = {
                performHaptic(view, "light")
                viewModel.dismissTitleDubbingNoAiDialog()
                showAiConnectSheet = true
            },
            onDismiss = { performHaptic(view, "light"); viewModel.dismissTitleDubbingNoAiDialog() },
        )
    }
    if (showFdroidUpdateDialog) {
        FdroidUpdateWarningDialog(
            strings = githubUpdateStrings,
            onDismiss = { performHaptic(view, "light"); showFdroidUpdateDialog = false },
            onContinue = {
                performHaptic(view, "light"); showFdroidUpdateDialog = false
                viewModel.openFdroidUpdateWebsite(context)
            },
        )
    }
    if (showGithubUpdatesEnableDialog) {
        FdroidUpdateWarningDialog(
            strings = githubUpdateStrings,
            onDismiss = { performHaptic(view, "light"); showGithubUpdatesEnableDialog = false },
            onContinue = {
                performHaptic(view, "light"); showGithubUpdatesEnableDialog = false
                showDeveloperSection = false
                viewModel.setDevGithubUpdatesEnabled(true)
            },
        )
    }
    uiState.fullEnrichmentPromptGapCount?.let { gapCount ->
        FullEnrichmentPromptDialog(gapCount = gapCount, viewModel = viewModel)
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    IosSheetScaffold(
        sheetVisible = showCloudSheet || showAiConnectSheet || showEnrichmentSheet || showContactSheet || showUpdateChangelogSheet || activePicker != null,
        onDismiss = {
            showCloudSheet = false
            showAiConnectSheet = false
            showEnrichmentSheet = false
            showContactSheet = false
            showUpdateChangelogSheet = false
            activePicker = null
        },
        sheetHeightFraction = null,
        // Все шторки настроек — та же панель, что и в остальном приложении (iosSheetContainer).
        // Раньше здесь стоял Transparent, и каждая шторка рисовала свою плавающую карточку.
        sheetContainerColor = null,
        showGrabber = true,
        content = {
      Box(modifier = Modifier.fillMaxSize()) {
        // Единый backdrop: список настроек пишет в него свой контент через layerBackdrop,
        // а плавающие стеклянные элементы (шапка, Share-FAB) сэмплируют его → настоящее
        // жидкое стекло с преломлением контента под ними (как бегунок рейтинга сэмплирует трек).
        val backdrop = rememberLayerBackdrop {
            drawRect(brush = settingsBackground)
            drawContent()
        }
        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
                    .sharedBounds(
                        rememberSharedContentState(key = "settings_container"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        // ScaleToBounds вместо RemeasureToBounds: список настроек не переразмечается
                        // каждый кадр открытия/закрытия (осн. причина падения FPS 120→105), а
                        // масштабируется на этапе отрисовки. Анимация сохранена (§ perf).
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                        clipInOverlayDuringTransition = OverlayClip(SquircleShape(IosDesign.RadiusLg))
                    )
                    .clip(SquircleShape(IosDesign.RadiusLg))
                    .background(settingsBackground)
            ) {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop),
                    contentPadding = PaddingValues(
                        // Резерв под плавающую стеклянную шапку (§11): statusBar + пузырёк 48 + отступы.
                        top = statusBarTop + 76.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    // ГРУППА: Общие (picker-строки → bottom sheet со списком).
                    item(key = "group_general") {
                        IosListGroup(
                            isDark = isDark,
                            rows = listOf(
                                {
                                    IosRow(
                                        title = strings.languageCardTitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_language,
                                        iconWell = false,
                                        value = if (uiState.language == AppLanguage.RU) "Русский" else "English",
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); activePicker = "lang" },
                                    )
                                },
                                {
                                    IosRow(
                                        title = strings.themeTitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_appearance,
                                        iconWell = false,
                                        value = when (uiState.theme) {
                                            AppTheme.LIGHT -> strings.themeLight
                                            AppTheme.DARK -> strings.themeDark
                                            AppTheme.SYSTEM -> strings.themeSystem
                                        },
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); activePicker = "theme" },
                                    )
                                },
                                {
                                    IosRow(
                                        title = strings.contentTypeTitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_content,
                                        iconWell = false,
                                        value = if (uiState.contentType == AppContentType.MOVIE) strings.typeMovies else strings.typeAnime,
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); activePicker = "content" },
                                    )
                                },
                            ),
                        )
                    }

                    // ГРУППА: Аккаунт / связь.
                    item(key = "group_account") {
                        IosListGroup(
                            isDark = isDark,
                            rows = listOf(
                                {
                                    IosRow(
                                        title = strings.cloudSettingsTitle,
                                        subtitle = strings.cloudSettingsSubtitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_account,
                                        iconWell = false,
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); showCloudSheet = true },
                                    )
                                },
                                {
                                    IosRow(
                                        title = enrichmentStrings.cardTitle,
                                        subtitle = enrichmentStrings.cardSubtitle,
                                        isDark = isDark,
                                        icon = Icons.Filled.AutoFixHigh,
                                        iconWell = false,
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); showEnrichmentSheet = true },
                                    )
                                },
                                {
                                    IosRow(
                                        title = aiConnectStrings.tileTitle,
                                        subtitle = aiConnectStrings.tileSubtitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.ic_ai_sparkle,
                                        iconWell = false,
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); showAiConnectSheet = true },
                                    )
                                },
                                {
                                    IosRow(
                                        title = strings.contactTitle,
                                        subtitle = strings.contactSubtitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_contact,
                                        iconWell = false,
                                        showChevron = true,
                                        onClick = { performHaptic(view, "light"); showContactSheet = true },
                                    )
                                },
                            ),
                        )
                    }

                    // ГРУППА: О приложении.
                    item(key = "group_about") {
                        val sizeLabel = formatApkSizeLabel(
                            uiState.latestApkSizeBytes, uiState.language, strings.updateApkSizeUnit,
                        )
                        val updateAvailable = uiState.devGithubUpdatesEnabled &&
                            uiState.updateStatus == AppUpdateStatus.UPDATE_AVAILABLE
                        val updateValue: String? = when {
                            !uiState.devGithubUpdatesEnabled -> uiState.currentVersion
                            uiState.isApkDownloading ->
                                (uiState.apkDownloadProgress * 100).toInt().toString() + "%"
                            uiState.updateStatus == AppUpdateStatus.LOADING -> strings.updateTileChecking
                            updateAvailable -> uiState.latestVersion ?: sizeLabel
                            uiState.updateStatus == AppUpdateStatus.ERROR -> "!"
                            else -> uiState.currentVersion
                        }
                        IosListGroup(
                            isDark = isDark,
                            rows = listOf(
                                {
                                    IosRow(
                                        title = if (updateAvailable) strings.updateTileAvailable else strings.checkForUpdateTitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_update,
                                        iconWell = false,
                                        value = updateValue,
                                        valueColor = if (updateAvailable) IconUpdateReady else null,
                                        showChevron = true,
                                        onClick = {
                                            performHaptic(view, "light")
                                            if (uiState.devGithubUpdatesEnabled) {
                                                showUpdateChangelogSheet = true
                                                viewModel.notifyUpdateChangelogSheetPresentedFromSettings()
                                                viewModel.loadUpdateChangelog(context)
                                            } else {
                                                showFdroidUpdateDialog = true
                                            }
                                        },
                                    )
                                },
                                {
                                    IosRow(
                                        title = strings.donationCardTitle,
                                        subtitle = strings.donationCardSubtitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_donate,
                                        iconWell = false,
                                        showChevron = true,
                                        onClick = {
                                            performHaptic(view, "light")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, TRIBUTE_DONATION_URL.toUri()))
                                        },
                                    )
                                },
                                {
                                    IosRow(
                                        title = strings.devSectionTitle,
                                        isDark = isDark,
                                        iconRes = R.drawable.hugeicon_developer,
                                        iconWell = false,
                                        chevronExpanded = showDeveloperSection,
                                        onClick = {
                                            performHaptic(view, "light")
                                            showDeveloperSection = !showDeveloperSection
                                        },
                                    )
                                },
                            ),
                        )
                    }

                    // ГРУППА: Разработчик (раскрывается).
                    item(key = "group_developer") {
                        AnimatedVisibility(
                            visible = showDeveloperSection,
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(tween(300)),
                            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(180)),
                        ) {
                            DeveloperGroups(
                                strings = strings,
                                githubUpdateStrings = githubUpdateStrings,
                                uiState = uiState,
                                isDark = isDark,
                                onMirrorDbToggle = { performHaptic(view, "light"); viewModel.setDevMirrorDb(it) },
                                onHideShareToggle = { performHaptic(view, "light"); viewModel.setDevHideShare(it) },
                                onFpsOverlayToggle = { performHaptic(view, "light"); viewModel.setDevFpsOverlay(it) },
                                onAdaptiveGlassToggle = { performHaptic(view, "light"); viewModel.setDevAdaptiveGlassScroll(it) },
                                onGithubUpdatesToggle = { enabled ->
                                    performHaptic(view, "light")
                                    if (enabled) showGithubUpdatesEnableDialog = true
                                    else viewModel.setDevGithubUpdatesEnabled(false)
                                },
                                onExportLogs = { performHaptic(view, "light"); viewModel.exportLogs(context) },
                                onExportPdf = { performHaptic(view, "light"); viewModel.exportCollectionPdf(context) },
                                onImportDb = { performHaptic(view, "light"); importDbPicker.launch("*/*") },
                            )
                        }
                    }

                    item(key = "version_footer") {
                        if (uiState.currentVersion.isNotBlank()) {
                            Text(
                                text = "Vetro · ${uiState.currentVersion}",
                                color = textC.copy(alpha = 0.4f),
                                fontFamily = SnProFamily,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                // Плавающая стеклянная шапка (§11): пузырёк-назад + капсула-заголовок скользят
                // поверх прокручиваемого списка (единый компонент с Details / AddEdit).
                GlassMenuHeader(
                    backdrop = backdrop,
                    title = strings.settingsScreenTitle,
                    onBack = { navController.popBackStack() },
                    isDark = isDark,
                    contentColor = textC,
                    iconModifier = Modifier.sharedElement(
                        rememberSharedContentState(key = "settings_icon"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Share FAB (стеклянный) — сохранён.
            if (!uiState.devHideShareButton) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 24.dp, end = 24.dp)
                ) {
                    // Тот же рецепт жидкого стекла, что у бегунка рейтинга: сэмплируем
                    // общий backdrop списка, слабый blur + крупная линза преломляют контент
                    // под кнопкой, сверху — стеклянный блик и светлая окантовка.
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(2f.dp.toPx())
                                    lens(16f.dp.toPx(), 44f.dp.toPx())
                                },
                            )
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.22f),
                                    0.5f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.10f),
                                ),
                                CircleShape,
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { performHaptic(view, "light"); viewModel.shareWithDb(context) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = textC,
                            modifier = Modifier.size(26.dp).offset(x = (-2).dp)
                        )
                    }
                }
            }
        }

      }
        },
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = GrabberReservedTop, bottom = 8.dp),
            ) {
                when {
                    activePicker == "lang" -> IosSelectionSheet(
                        title = strings.languageCardTitle,
                        options = listOf(
                            IosSelectionOption("English", Icons.Outlined.Language, IconLanguage),
                            IosSelectionOption("Русский", Icons.Outlined.Language, IconLanguage),
                        ),
                        selectedIndex = if (uiState.language == AppLanguage.RU) 1 else 0,
                        isDark = isDark,
                        cardCornerRadius = 22.dp,
                        onSelect = { i ->
                            performHaptic(view, "light")
                            viewModel.setLanguage(if (i == 1) AppLanguage.RU else AppLanguage.EN)
                            activePicker = null
                        },
                    )
                    activePicker == "theme" -> IosSelectionSheet(
                        title = strings.themeTitle,
                        options = listOf(
                            IosSelectionOption(strings.themeLight, Icons.Outlined.LightMode, IconAppearance),
                            IosSelectionOption(strings.themeDark, Icons.Outlined.DarkMode, IconAppearance),
                            IosSelectionOption(strings.themeSystem, Icons.Outlined.BrightnessAuto, IconAppearance),
                        ),
                        selectedIndex = when (uiState.theme) {
                            AppTheme.LIGHT -> 0
                            AppTheme.DARK -> 1
                            AppTheme.SYSTEM -> 2
                        },
                        isDark = isDark,
                        cardCornerRadius = 22.dp,
                        onSelect = { i ->
                            performHaptic(view, "light")
                            viewModel.setTheme(
                                when (i) {
                                    0 -> AppTheme.LIGHT
                                    1 -> AppTheme.DARK
                                    else -> AppTheme.SYSTEM
                                }
                            )
                            activePicker = null
                        },
                    )
                    activePicker == "content" -> IosSelectionSheet(
                        title = strings.contentTypeTitle,
                        options = listOf(
                            IosSelectionOption(strings.typeAnime, Icons.Outlined.Category, IconContent),
                            IosSelectionOption(strings.typeMovies, Icons.Outlined.Category, IconContent),
                        ),
                        selectedIndex = if (uiState.contentType == AppContentType.MOVIE) 1 else 0,
                        isDark = isDark,
                        cardCornerRadius = 22.dp,
                        onSelect = { i ->
                            performHaptic(view, "light")
                            viewModel.setContentType(if (i == 1) AppContentType.MOVIE else AppContentType.ANIME)
                            activePicker = null
                        },
                    )
                    showCloudSheet -> CloudSettingsSheet(
                        onDismiss = { showCloudSheet = false },
                        onLogout = { showCloudSheet = false; navController.navigateToWelcome() },
                    )
                    showAiConnectSheet -> AiConnectSheet(
                        onDismiss = { showAiConnectSheet = false },
                    )
                    showEnrichmentSheet -> CollectionEnrichmentSheet(
                        viewModel = viewModel,
                        onDismiss = { showEnrichmentSheet = false },
                    )
                    showContactSheet -> ContactSheet(
                        onDismiss = { showContactSheet = false },
                    )
                    showUpdateChangelogSheet -> UpdateChangelogSheet(
                        viewModel = viewModel,
                        onDismiss = { showUpdateChangelogSheet = false },
                        installPermissionLauncher = installPermissionLauncher,
                    )
                }
            }
        },
    )
}

/** Строка-контейнер с сегментированным переключателем: icon + title, ниже — сам контрол. */
@Composable
private fun IosSegmentedRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    isDark: Boolean,
    optionCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    segment: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = IosDesign.ListHorizontalInset, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IosIconWell(icon = icon, background = iconBg)
            Spacer(Modifier.width(IosDesign.ListIconTextGap))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = SnProFamily,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        IosSegmentedControl(
            optionCount = optionCount,
            selectedIndex = selectedIndex,
            isDark = isDark,
            modifier = Modifier.fillMaxWidth(),
            onSelect = onSelect,
            segment = segment,
        )
    }
}

@Composable
private fun SegLabel(text: String, selected: Boolean, isDark: Boolean) {
    Text(
        text = text,
        fontFamily = SnProFamily,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        fontSize = 13.sp,
        color = if (selected) {
            if (isDark) Color.White else Color.Black
        } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SegIcon(icon: ImageVector, selected: Boolean, isDark: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = if (selected) {
            if (isDark) Color.White else Color.Black
        } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

/** Группы раздела «Разработчик» в iOS-стиле. */
@Composable
private fun DeveloperGroups(
    strings: UiStrings,
    githubUpdateStrings: GithubUpdateStrings,
    uiState: SettingsUiState,
    isDark: Boolean,
    onMirrorDbToggle: (Boolean) -> Unit,
    onHideShareToggle: (Boolean) -> Unit,
    onFpsOverlayToggle: (Boolean) -> Unit,
    onAdaptiveGlassToggle: (Boolean) -> Unit,
    onGithubUpdatesToggle: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
    onExportPdf: () -> Unit,
    onImportDb: () -> Unit,
) {
    val devIcon = Color(0xFF8E8E93)
    Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
        IosListGroup(
            isDark = isDark,
            header = strings.devSectionTitle,
            rows = listOf(
                {
                    IosRow(
                        title = githubUpdateStrings.devTitle,
                        subtitle = githubUpdateStrings.devSubtitle,
                        isDark = isDark,
                        icon = Icons.Outlined.SystemUpdate,
                        iconBackground = devIcon,
                        trailing = { IosSwitch(checked = uiState.devGithubUpdatesEnabled, onCheckedChange = onGithubUpdatesToggle) },
                    )
                },
                {
                    IosRow(
                        title = strings.devMirrorDbTitle,
                        subtitle = strings.devMirrorDbSubtitle,
                        isDark = isDark,
                        icon = Icons.Filled.FileOpen,
                        iconBackground = devIcon,
                        trailing = { IosSwitch(checked = uiState.devMirrorDbToDocuments, onCheckedChange = onMirrorDbToggle) },
                    )
                },
                {
                    IosRow(
                        title = strings.devHideShareTitle,
                        subtitle = strings.devHideShareSubtitle,
                        isDark = isDark,
                        icon = Icons.Filled.Share,
                        iconBackground = devIcon,
                        trailing = { IosSwitch(checked = uiState.devHideShareButton, onCheckedChange = onHideShareToggle) },
                    )
                },
                {
                    IosRow(
                        title = strings.devAdaptiveGlassTitle,
                        subtitle = strings.devAdaptiveGlassSubtitle,
                        isDark = isDark,
                        icon = Icons.Filled.Code,
                        iconBackground = devIcon,
                        trailing = { IosSwitch(checked = uiState.devAdaptiveGlassScroll, onCheckedChange = onAdaptiveGlassToggle) },
                    )
                },
                {
                    IosRow(
                        title = strings.devFpsOverlayTitle,
                        subtitle = strings.devFpsOverlaySubtitle,
                        isDark = isDark,
                        icon = Icons.Filled.BugReport,
                        iconBackground = devIcon,
                        trailing = { IosSwitch(checked = uiState.devFpsOverlay, onCheckedChange = onFpsOverlayToggle) },
                    )
                },
            ),
        )
        IosListGroup(
            isDark = isDark,
            rows = listOf(
                {
                    DevActionRow(
                        title = strings.devExportLogsTitle, subtitle = strings.devExportLogsSubtitle,
                        icon = Icons.Filled.BugReport, iconBg = devIcon, isLoading = uiState.isExportingLogs,
                        isDark = isDark, onClick = onExportLogs,
                    )
                },
                {
                    DevActionRow(
                        title = strings.devExportPdfTitle, subtitle = strings.devExportPdfSubtitle,
                        icon = Icons.Filled.PictureAsPdf, iconBg = devIcon, isLoading = uiState.isExportingPdf,
                        isDark = isDark, onClick = onExportPdf,
                    )
                },
                {
                    DevActionRow(
                        title = strings.devImportDbTitle, subtitle = strings.devImportDbSubtitle,
                        icon = Icons.Filled.FileOpen, iconBg = devIcon, isLoading = uiState.isImportingDb,
                        isDark = isDark, onClick = onImportDb,
                    )
                },
            ),
        )
    }
}

@Composable
private fun DevActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    isLoading: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    IosRow(
        title = title,
        subtitle = subtitle,
        isDark = isDark,
        icon = icon,
        iconBackground = iconBg,
        showChevron = !isLoading,
        trailing = if (isLoading) {
            { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BrandBlue, strokeWidth = 2.dp) }
        } else null,
        onClick = if (isLoading) null else onClick,
    )
}

@Composable
private fun TitleDubbingNoAiDialog(
    strings: TitleDubbingStrings,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isAppInDarkTheme()
    val accent = if (isDark) BrandBlueSoft else BrandRed
    val surface = IosDesign.rowBackground(isDark)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val shape = SquircleShape(IosDesign.RadiusMd)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        IosDialogAnimatedContent {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                shape = shape,
                color = surface,
                tonalElevation = 0.dp,
                shadowElevation = if (isDark) 0.dp else 8.dp,
            ) {
                DialogBody(
                    title = strings.noAiTitle,
                    body = strings.noAiMessage,
                    cancel = strings.noAiCancel,
                    confirm = strings.noAiConnect,
                    accent = accent,
                    onSurface = onSurface,
                    muted = muted,
                    onDismiss = onDismiss,
                    onConfirm = onConnect,
                )
            }
        }
    }
}

@Composable
private fun FdroidUpdateWarningDialog(
    strings: GithubUpdateStrings,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val isDark = isAppInDarkTheme()
    val accent = if (isDark) BrandBlueSoft else BrandRed
    val surface = IosDesign.rowBackground(isDark)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val shape = SquircleShape(IosDesign.RadiusMd)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        IosDialogAnimatedContent {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                shape = shape,
                color = surface,
                tonalElevation = 0.dp,
                shadowElevation = if (isDark) 0.dp else 8.dp,
            ) {
                DialogBody(
                    title = strings.warningTitle,
                    body = strings.warningBody,
                    cancel = strings.warningCancel,
                    confirm = strings.warningContinue,
                    accent = accent,
                    onSurface = onSurface,
                    muted = muted,
                    onDismiss = onDismiss,
                    onConfirm = onContinue,
                )
            }
        }
    }
}

@Composable
private fun RepairDbLogDialog(
    devRepairStrings: DevRepairDbStrings,
    onCreateLog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isAppInDarkTheme()
    val accent = if (isDark) BrandBlueSoft else BrandRed
    val surface = IosDesign.rowBackground(isDark)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val shape = SquircleShape(IosDesign.RadiusMd)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        IosDialogAnimatedContent {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                shape = shape,
                color = surface,
                tonalElevation = 0.dp,
                shadowElevation = if (isDark) 0.dp else 8.dp,
            ) {
                DialogBody(
                    title = devRepairStrings.logDialogTitle,
                    body = devRepairStrings.logDialogMessage,
                    cancel = devRepairStrings.logDialogCancel,
                    confirm = devRepairStrings.logDialogCreate,
                    accent = accent,
                    onSurface = onSurface,
                    muted = muted,
                    onDismiss = onDismiss,
                    onConfirm = onCreateLog,
                )
            }
        }
    }
}

@Composable
private fun DialogBody(
    title: String,
    body: String,
    cancel: String,
    confirm: String,
    accent: Color,
    onSurface: Color,
    muted: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SnProFamily,
            fontWeight = FontWeight.SemiBold,
            color = onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = SnProFamily,
            color = muted,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = cancel, fontFamily = SnProFamily, color = muted)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = onConfirm,
                shape = SquircleShape(IosDesign.RadiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            ) {
                Text(text = confirm, fontFamily = SnProFamily, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Сегментированный переключатель (капсульный) — используется на экране Inspect.
 * Оставлен для обратной совместимости; в самих настройках теперь [IosSegmentedControl].
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
    val isDark = isAppInDarkTheme()
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.92f else 0.86f)
    val selectedContentColor = Color.White
    val containerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val selectedPillColor = if (isDark) accentColor.copy(alpha = 0.9f) else Color(0xFF2C2C2E)
    val selectedPillBorder = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f)

    BoxWithConstraints(
        modifier = modifier
            .height(containerHeight)
            .clip(containerShape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.36f else 0.24f),
                shape = containerShape
            )
    ) {
        val innerWidth = maxWidth
        val segmentWidth = innerWidth / options.size
        val pillInsetHorizontal = 2.dp
        val pillInsetVertical = 3.dp
        val pillCornerRadius = (containerHeight - pillInsetVertical * 2) / 2
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
                    .padding(pillInsetHorizontal, pillInsetVertical)
                    .clip(innerShape)
                    .background(selectedPillColor, innerShape)
                    .border(1.dp, selectedPillBorder, innerShape),
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
                                tint = if (selectedIndex == index) selectedContentColor else unselectedContentColor
                            )
                        } else {
                            Text(
                                opt.label ?: "",
                                fontFamily = SnProFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selectedIndex == index) selectedContentColor else unselectedContentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
