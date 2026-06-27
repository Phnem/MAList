package com.example.myapplication.ui.home

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.myapplication.ui.shared.ListSyncLoadingOverlay
import com.example.myapplication.ui.shared.LocalAdaptiveGlassScrollInProgress
import com.example.myapplication.ui.shared.customOverscroll

import com.example.myapplication.GlassActionDock
import com.example.myapplication.GlassBottomNavigation
import com.example.myapplication.GenreFilterOverlay
import com.example.myapplication.NotificationSyncOverlay
import com.example.myapplication.SimpGlassCard
import com.example.myapplication.SortFilterOverlay
import com.example.myapplication.data.models.*
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.sync.ExternalListSyncCoordinator
import com.example.myapplication.sync.supabase.CollectionImageRestoreCoordinator
import com.example.myapplication.sync.supabase.SupabaseSyncCoordinator
import com.example.myapplication.utils.getCloudSyncPillStrings
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.systemAppLanguage
import com.example.myapplication.utils.performHaptic
import com.example.myapplication.ui.navigation.navigateToAddEdit
import com.example.myapplication.ui.navigation.navigateToDetails
import com.example.myapplication.ui.navigation.navigateToInspect
import com.example.myapplication.ui.navigation.navigateToWelcome
import com.example.myapplication.ui.navigation.navigateToSettings
import com.example.myapplication.ui.shared.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val genreRepository: GenreRepository = koinInject()
    val listSyncCoordinator: ExternalListSyncCoordinator = koinInject()
    val supabaseSyncCoordinator: SupabaseSyncCoordinator = koinInject()
    val collectionImageRestoreCoordinator: CollectionImageRestoreCoordinator = koinInject()
    val listSyncUi by listSyncCoordinator.syncUiState.collectAsStateWithLifecycle()
    val isSupabaseSyncing by supabaseSyncCoordinator.isSyncing.collectAsStateWithLifecycle()
    val isCloudImageRestoring by collectionImageRestoreCoordinator.isRestoring.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
    val strings = getStrings(currentLanguage)
    val cloudSyncPillStrings = getCloudSyncPillStrings(systemAppLanguage())
    val syncReport by viewModel.syncReport.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val list by viewModel.animeListFlow.collectAsStateWithLifecycle()
    var cloudSyncPillDismissed by remember { mutableStateOf(false) }
    val showCloudSyncPill =
        uiState.isListLoaded &&
            !cloudSyncPillDismissed &&
            (isSupabaseSyncing || isCloudImageRestoring)
    val dismissCloudSyncPill = { cloudSyncPillDismissed = true }
    val kbd = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val ctx = LocalContext.current
    var showCSheet by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var showNotificationsOverlay by remember { mutableStateOf(false) }
    var notificationsBlockingChildDialog by remember { mutableStateOf(false) }
    val notifVisibleState = remember { MutableTransitionState(false) }
    notifVisibleState.targetState = showNotificationsOverlay

    var showSortOverlay by remember { mutableStateOf(false) }
    val sortVisibleState = remember { MutableTransitionState(false) }
    sortVisibleState.targetState = showSortOverlay

    var showMediaTypeFilterOverlay by remember { mutableStateOf(false) }
    val mediaTypeFilterVisibleState = remember { MutableTransitionState(false) }
    mediaTypeFilterVisibleState.targetState = showMediaTypeFilterOverlay

    val genreFilterVisibleState = remember { MutableTransitionState(false) }
    genreFilterVisibleState.targetState = uiState.isGenreFilterVisible

    val searchFocusRequester = remember { FocusRequester() }
    var animeToDelete by remember { mutableStateOf<Anime?>(null) }
    var animeToFavorite by remember { mutableStateOf<Anime?>(null) }
    var pendingSwipeReset by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = ctx as? Activity ?: return@LaunchedEffect
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    LaunchedEffect(isSupabaseSyncing, isCloudImageRestoring) {
        if (!isSupabaseSyncing && !isCloudImageRestoring) {
            cloudSyncPillDismissed = false
        }
    }

    var isDockVisible by remember { mutableStateOf(true) }
    val finalDockVisible = isDockVisible || isSearchVisible
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                val threshold = 10f
                if (available.y < -threshold) {
                    if (isDockVisible) isDockVisible = false
                } else if (available.y > threshold) {
                    if (!isDockVisible) isDockVisible = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    BackHandler(enabled = isSearchVisible || uiState.searchQuery.isNotEmpty()) {
        performHaptic(view, "light")
        isSearchVisible = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
        kbd?.hide()
    }

    val listState = rememberLazyListState()
    val listScrollInProgress by remember { derivedStateOf { listState.isScrollInProgress } }

    LaunchedEffect(listScrollInProgress) {
        if (listScrollInProgress) {
            cloudSyncPillDismissed = true
        }
    }

    var overscrollAmount by remember { mutableFloatStateOf(0f) }
    val isHeaderFloating by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20 } }
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    val bgColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(bgColor)
        drawContent()
    }

    val shouldBlur = (isSearchVisible && uiState.searchQuery.isBlank()) ||
            showCSheet || animeToDelete != null || animeToFavorite != null ||
            uiState.isGenreFilterVisible || showNotificationsOverlay || showSortOverlay ||
            showMediaTypeFilterOverlay || listSyncUi.isRunning
    val blurAmount by animateDpAsState(
        targetValue = when {
            notificationsBlockingChildDialog -> 20.dp
            shouldBlur -> 10.dp
            else -> 0.dp
        },
        label = "blur"
    )

    // Кнопка «вверх» опускается к низу, когда док скрыт (как кнопка поиска), и поднимается вместе с доком
    val scrollToTopBottomPadding by animateDpAsState(
        targetValue = if (finalDockVisible) 160.dp else 88.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "scrollToTopBottom"
    )

    val openWorkspaceSort: () -> Unit = {
        performHaptic(view, "light")
        dismissCloudSyncPill()
        showSortOverlay = !showSortOverlay
        if (showSortOverlay) {
            showNotificationsOverlay = false
            showMediaTypeFilterOverlay = false
            viewModel.setGenreFilterVisible(false)
        }
    }
    val openWorkspaceNotifications: () -> Unit = {
        performHaptic(view, "light")
        dismissCloudSyncPill()
        showNotificationsOverlay = !showNotificationsOverlay
        if (showNotificationsOverlay) {
            showSortOverlay = false
            showMediaTypeFilterOverlay = false
            viewModel.setGenreFilterVisible(false)
        }
    }
    val openMediaTypeFilter: () -> Unit = {
        performHaptic(view, "light")
        dismissCloudSyncPill()
        showMediaTypeFilterOverlay = !showMediaTypeFilterOverlay
        if (showMediaTypeFilterOverlay) {
            showSortOverlay = false
            showNotificationsOverlay = false
            viewModel.setGenreFilterVisible(false)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {},
        floatingActionButton = {},
        // Иначе system bar insets добавляются в paddingValues — фон не заливает полосы сверху/снизу
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { paddingValues ->
        CompositionLocalProvider(LocalAdaptiveGlassScrollInProgress provides listScrollInProgress) {
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Box(modifier = Modifier.fillMaxSize().background(bgColor))

            if (notifVisibleState.currentState || notifVisibleState.targetState) {
                Box(modifier = Modifier.zIndex(5f).fillMaxSize()) {
                    NotificationSyncOverlay(
                        syncCoordinator = koinInject(),
                        authRepository = koinInject(),
                        visibleState = notifVisibleState,
                        strings = getStrings(currentLanguage),
                        syncReport = syncReport,
                        updates = uiState.updates,
                        isCheckingUpdates = uiState.isCheckingUpdates,
                        currentLanguage = currentLanguage,
                        onDismiss = { showNotificationsOverlay = false },
                        onLogout = {
                            navController.navigateToWelcome()
                        },
                        onCheckUpdates = {
                            performHaptic(view, "light")
                            viewModel.checkForUpdates(force = true)
                        },
                        onAcceptUpdate = { update ->
                            performHaptic(view, "success")
                            viewModel.acceptUpdate(update, ctx)
                        },
                        onDismissUpdate = { update ->
                            performHaptic(view, "light")
                            viewModel.dismissUpdate(update, ctx)
                        },
                        onBlockingChildDialogChange = { notificationsBlockingChildDialog = it }
                    )
                }
            }

            if (sortVisibleState.currentState || sortVisibleState.targetState) {
                Box(modifier = Modifier.zIndex(5f).fillMaxSize()) {
                    SortFilterOverlay(
                        visibleState = sortVisibleState,
                        strings = getStrings(currentLanguage),
                        sortOption = uiState.sortOption,
                        sortAscending = uiState.sortAscending,
                        filterSelectedTags = uiState.filterTags,
                        onDismiss = { showSortOverlay = false },
                        onApplySort = { option, isAscending ->
                            performHaptic(view, "light")
                            viewModel.applySort(option, isAscending)
                        },
                        onApplyOpenGenreFilter = {
                            performHaptic(view, "light")
                            viewModel.toggleGenreFilter()
                        }
                    )
                }
            }

            if (genreFilterVisibleState.currentState || genreFilterVisibleState.targetState) {
                Box(modifier = Modifier.zIndex(5f).fillMaxSize()) {
                    GenreFilterOverlay(
                        visibleState = genreFilterVisibleState,
                        strings = getStrings(currentLanguage),
                        filterSelectedTags = uiState.filterTags,
                        filterCategoryType = uiState.filterCategory,
                        currentLanguage = currentLanguage,
                        onTagToggle = { tag, categoryType ->
                            val currentTags = uiState.filterTags.toMutableList()
                            if (currentTags.contains(tag)) {
                                currentTags.remove(tag)
                                val newCategory = if (currentTags.isEmpty()) "" else uiState.filterCategory
                                viewModel.updateFilterTags(currentTags, newCategory)
                            } else {
                                if (currentTags.size < 3 && (uiState.filterCategory.isEmpty() || uiState.filterCategory == categoryType)) {
                                    currentTags.add(tag)
                                    viewModel.updateFilterTags(currentTags, categoryType)
                                }
                            }
                        },
                        onDismiss = { viewModel.toggleGenreFilter() }
                    )
                }
            }

            if (mediaTypeFilterVisibleState.currentState || mediaTypeFilterVisibleState.targetState) {
                Box(modifier = Modifier.zIndex(5f).fillMaxSize()) {
                    MediaTypeFilterOverlay(
                        visibleState = mediaTypeFilterVisibleState,
                        strings = getStrings(currentLanguage),
                        selected = uiState.libraryMediaTypeFilter,
                        onSelect = { viewModel.setLibraryMediaTypeFilter(it) },
                        onDismiss = { showMediaTypeFilterOverlay = false },
                    )
                }
            }

            if (listSyncUi.isRunning) {
                Box(
                    modifier = Modifier
                        .zIndex(9f)
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = OverlayThemeTokens.ScrimAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    ListSyncLoadingOverlay(
                        message = listSyncUi.message,
                        processed = listSyncUi.processed,
                        total = listSyncUi.total,
                        strings = strings,
                        onDismiss = {
                            performHaptic(view, "light")
                            listSyncCoordinator.cancelListSync()
                        }
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize().homeScrollBlur(blurAmount)) {
                Box(modifier = Modifier.fillMaxSize().weight(1f).background(bgColor)) {
                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                    val apiSearchModels by viewModel.apiSearchWithStatus.collectAsStateWithLifecycle()

                    CompositionLocalProvider(LocalOverscrollFactory provides null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                                .customOverscroll(listState) { overscrollAmount = it }
                                .offset { IntOffset(0, overscrollAmount.roundToInt()) }
                        ) {
                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    dismissCloudSyncPill()
                                    viewModel.refreshList()
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(top = 0.dp, bottom = 0.dp, start = 0.dp, end = 0.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .layerBackdrop(backdrop)
                                ) {
                                    item {
                                        VetroWorkspaceTopBar(
                                            strings = getStrings(currentLanguage),
                                        )
                                    }

                                    val showApiFirst = list.isEmpty() && uiState.searchQuery.isNotEmpty()

                                    if (showApiFirst) {
                                        apiSearchResultsSection(
                                            strings = strings,
                                            apiSearchModels = apiSearchModels,
                                            uiState = uiState,
                                            currentLanguage = currentLanguage,
                                            genreRepository = genreRepository,
                                            view = view,
                                            viewModel = viewModel,
                                            topPadding = 8.dp
                                        )
                                        when {
                                            uiState.apiSearchLoading -> Unit
                                            uiState.apiSearchError != null -> Unit
                                            apiSearchModels.isNotEmpty() -> item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 24.dp, horizontal = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = strings.noResultsInLibrary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                            else -> item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 32.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    EmptyStateView(
                                                        title = strings.noResults,
                                                        subtitle = ""
                                                    )
                                                }
                                            }
                                        }
                                    } else if (list.isNotEmpty()) {
                                        items(
                                            items = list,
                                            key = { it.id },
                                            contentType = { "anime_card" }
                                        ) { anime ->
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                positionalThreshold = { totalDistance -> totalDistance * 0.4f }
                                            )
                                            LaunchedEffect(dismissState.currentValue) {
                                                when (dismissState.currentValue) {
                                                    SwipeToDismissBoxValue.StartToEnd -> {
                                                        performHaptic(view, "success")
                                                        animeToFavorite = anime
                                                        pendingSwipeReset = { dismissState.reset() }
                                                    }
                                                    SwipeToDismissBoxValue.EndToStart -> {
                                                        performHaptic(view, "warning")
                                                        animeToDelete = anime
                                                        pendingSwipeReset = { dismissState.reset() }
                                                    }
                                                    SwipeToDismissBoxValue.Settled -> Unit
                                                }
                                            }
                                            SwipeToDismissBox(
                                                state = dismissState,
                                                backgroundContent = { SwipeBackground(dismissState) },
                                                modifier = Modifier.padding(horizontal = 16.dp) // .animateItem() убран: конфликт с SharedTransition при возврате
                                            ) {
                                                val cardState = remember(anime, currentLanguage) {
                                                    AnimeCardState(
                                                        id = anime.id,
                                                        title = anime.title,
                                                        rating = anime.rating,
                                                        genres = persistentListOf(
                                                            *anime.tags.take(3)
                                                                .mapNotNull { genreRepository.getLabel(it, currentLanguage).takeIf { n -> n.isNotBlank() } }
                                                                .toTypedArray()
                                                        ),
                                                        episodesCount = anime.episodes,
                                                        categoryLabel = anime.categoryType.takeIf { it.isNotBlank() },
                                                        imagePath = viewModel.getImgPath(anime.imageFileName),
                                                        mediaTypeLabel = when (anime.mediaType) {
                                                            com.example.myapplication.data.models.MediaType.ANIME -> strings.typeAnime
                                                            com.example.myapplication.data.models.MediaType.MANGA -> strings.typeManga
                                                            com.example.myapplication.data.models.MediaType.TV_SERIES -> strings.typeSeries
                                                        }
                                                    )
                                                }
                                                with(sharedTransitionScope) {
                                                    OneUiAnimeCard(
                                                        state = cardState,
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        onClick = { navController.navigateToAddEdit(anime.id) },
                                                        onDetailsClick = {
                                                            performHaptic(view, "light")
                                                            navController.navigateToDetails(anime.id)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (uiState.searchQuery.isNotEmpty()) {
                                            apiSearchResultsSection(
                                                strings = strings,
                                                apiSearchModels = apiSearchModels,
                                                uiState = uiState,
                                                currentLanguage = currentLanguage,
                                                genreRepository = genreRepository,
                                                view = view,
                                                viewModel = viewModel,
                                                topPadding = 24.dp
                                            )
                                        }
                                    } else {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillParentMaxSize()
                                                    .padding(bottom = 120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                EmptyStateView(
                                                    title = strings.emptyTitle,
                                                    subtitle = strings.emptySubtitle
                                                )
                                            }
                                        }
                                    }
                                    item(key = "home_bottom_dock_spacer") {
                                        Spacer(Modifier.height(220.dp))
                                    }
                                }
                            }
                        }
                    }
                    CloudSyncPill(
                        visible = showCloudSyncPill,
                        label = cloudSyncPillStrings.label,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 8.dp)
                            .zIndex(4f),
                    )
                }
            }

            if (!isSearchVisible && animeToDelete == null && animeToFavorite == null && !showCSheet) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).zIndex(3f).navigationBarsPadding()) {
                    AnimatedVisibility(
                        visible = finalDockVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(animationSpec = tween(250)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                        ) + fadeOut(animationSpec = tween(200))
                    ) {
                        GlassBottomNavigation(
                            backdrop = backdrop,
                            nav = navController,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onShowStats = {
                                dismissCloudSyncPill()
                                showCSheet = true
                            },
                            onShowNotifs = {},
                            onInspectClick = {
                                performHaptic(view, "light")
                                dismissCloudSyncPill()
                                navController.navigateToInspect()
                            },
                            onSearchClick = {
                                performHaptic(view, "light")
                                dismissCloudSyncPill()
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    viewModel.updateSearchQuery("")
                                    focusManager.clearFocus()
                                    kbd?.hide()
                                }
                            },
                            onSettingsClick = {
                                dismissCloudSyncPill()
                                navController.navigateToSettings()
                            },
                            isSearchActive = isSearchVisible,
                            modifier = Modifier,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isSearchVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).windowInsetsPadding(WindowInsets.ime).padding(bottom = 16.dp).zIndex(10f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val filters = listOf(
                            com.example.myapplication.data.models.MediaType.ANIME to strings.typeAnime,
                            com.example.myapplication.data.models.MediaType.MANGA to strings.typeManga,
                            com.example.myapplication.data.models.MediaType.TV_SERIES to strings.typeSeries
                        )
                        filters.forEach { (type, label) ->
                            val isSelected = uiState.searchMediaTypeFilter == type
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isSelected) 1.1f else 1.0f,
                                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
                                label = "scale"
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .scale(scale)
                                    .clip(CircleShape)
                                    .clickable { viewModel.setSearchMediaTypeFilter(type) }
                            ) {
                                com.example.myapplication.SimpGlassCard(
                                    backdrop = backdrop,
                                    shape = CircleShape,
                                    modifier = Modifier.matchParentSize()
                                ) {}
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = SnProFamily
                                    )
                                }
                            }
                        }
                    }
                    SimpGlassCard(backdrop = backdrop, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = {
                                viewModel.updateSearchQuery(it)
                                if (it.isNotEmpty()) performHaptic(view, "light")
                            },
                            modifier = Modifier.fillMaxSize().focusRequester(searchFocusRequester).padding(horizontal = 20.dp),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = SnProFamily),
                            cursorBrush = SolidColor(BrandBlue),
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = BrandBlue)
                                    Spacer(Modifier.width(12.dp))
                                    Box {
                                        if (uiState.searchQuery.isEmpty()) {
                                            Text("Search in collection...", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, fontFamily = SnProFamily)
                                        }
                                        innerTextField()
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                kbd?.hide()
                                focusManager.clearFocus()
                            })
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    searchFocusRequester.requestFocus()
                    kbd?.show()
                }
            }

            if (shouldBlur && animeToDelete == null && animeToFavorite == null && !showNotificationsOverlay && !showSortOverlay && !uiState.isGenreFilterVisible) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    focusManager.clearFocus()
                    if (isSearchVisible) {
                        viewModel.updateSearchQuery("")
                    }
                    isSearchVisible = false
                    kbd?.hide()
                }.zIndex(2f))
            }

            if (animeToDelete != null) {
                AnimeListMenuSheet(
                    anime = animeToDelete!!,
                    confirmMode = AnimeMenuConfirmMode.DELETE,
                    strings = strings,
                    getImgPath = { viewModel.getImgPath(it) },
                    onEvent = { event ->
                        when (event) {
                            is AnimeMenuEvent.OnConfirm -> viewModel.deleteAnime(animeToDelete!!.id)
                            is AnimeMenuEvent.OnCancel -> { }
                        }
                    },
                    onDismiss = {
                        val reset = pendingSwipeReset
                        pendingSwipeReset = null
                        animeToDelete = null
                        if (reset != null) scope.launch { reset() }
                    }
                )
            }
            if (animeToFavorite != null) {
                AnimeListMenuSheet(
                    anime = animeToFavorite!!,
                    confirmMode = AnimeMenuConfirmMode.ADD_TO_FAVORITE,
                    strings = strings,
                    getImgPath = { viewModel.getImgPath(it) },
                    onEvent = { event ->
                        when (event) {
                            is AnimeMenuEvent.OnConfirm -> viewModel.toggleFavorite(animeToFavorite!!.id)
                            is AnimeMenuEvent.OnCancel -> { }
                        }
                    },
                    onDismiss = {
                        val reset = pendingSwipeReset
                        pendingSwipeReset = null
                        animeToFavorite = null
                        if (reset != null) scope.launch { reset() }
                    }
                )
            }

            AnimatedVisibility(
                visible = showScrollToTop && !isSearchVisible && animeToDelete == null && animeToFavorite == null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = scrollToTopBottomPadding, end = 24.dp)
                    .zIndex(1f)
            ) {
                SimpGlassCard(
                    backdrop = backdrop,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).clickable {
                        performHaptic(view, "light")
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Up",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Поверх scrim/blur оверлеев: TopBar-кнопки и Glass dock остаются чёткими
            Box(modifier = Modifier.fillMaxSize().zIndex(30f)) {
                if (!isHeaderFloating) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 20.dp, bottom = 8.dp)
                            .padding(horizontal = 24.dp)
                    ) {
                        WorkspaceSortNotificationActions(
                            strings = strings,
                            filterSelectedTags = uiState.filterTags,
                            updatesCount = uiState.updates.size,
                            onOpenSort = openWorkspaceSort,
                            onOpenNotifications = openWorkspaceNotifications,
                            onOpenMediaTypeFilter = openMediaTypeFilter,
                            dockButtonBackground = Color.Transparent,
                            useDockSizing = false,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp)) {
                    GlassActionDock(
                        backdrop = backdrop,
                        isFloating = isHeaderFloating,
                        strings = strings,
                        filterSelectedTags = uiState.filterTags,
                        updates = uiState.updates,
                        onOpenSort = openWorkspaceSort,
                        onOpenNotifications = openWorkspaceNotifications,
                        onOpenMediaTypeFilter = openMediaTypeFilter,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        if (showCSheet) {
            LaunchedEffect(showCSheet) {
                viewModel.loadStatsAnimeList()
            }
            StatsOverlay(
                animeList = uiState.statsAnimeList,
                strings = getStrings(currentLanguage),
                appLanguage = currentLanguage,
                footerPhrase = uiState.statsFooterPhrase,
                onDismiss = { showCSheet = false }
            )
        }
        }
    }
}

private fun Modifier.homeScrollBlur(blur: Dp): Modifier = when {
    blur == 0.dp -> this
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> this.then(
        Modifier.graphicsLayer {
            val px = blur.toPx()
            renderEffect = RenderEffect.createBlurEffect(
                px,
                px,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
            clip = true
        }
    )
    else -> this.then(Modifier.blur(blur))
}

private fun LazyListScope.apiSearchResultsSection(
    strings: UiStrings,
    apiSearchModels: List<ApiSearchUiModel>,
    uiState: HomeUiState,
    currentLanguage: AppLanguage,
    genreRepository: GenreRepository,
    view: android.view.View,
    viewModel: HomeViewModel,
    topPadding: Dp
) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = strings.externalResults,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = SnProFamily
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.apiSearch,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = SnProFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${apiSearchModels.size} ${strings.viaApi}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = SnProFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (uiState.apiSearchLoading) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
    uiState.apiSearchError?.let { err ->
        item {
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    items(
        items = apiSearchModels,
        key = { "${it.result.source}_${it.result.externalId ?: it.result.title}" },
        contentType = { "api_card" }
    ) { uiModel ->
        val result = uiModel.result
        val key = "${result.source}_${result.externalId ?: result.title}"
        val isLoading = uiState.addingFromApiId == key
        val apiGenres = remember(result.genres, currentLanguage) {
            persistentListOf(
                *result.genres.take(3)
                    .map { genreRepository.getLabel(it, currentLanguage) }
                    .toTypedArray()
            )
        }
        ApiSearchResultCard(
            result = result,
            isAdded = uiModel.isAdded,
            isLoading = isLoading,
            onAddClick = {
                performHaptic(view, "light")
                viewModel.addFromApi(result)
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            displayGenres = apiGenres,
            addLabel = strings.addButton,
            addedLabel = strings.addedButton,
            mediaTypeLabel = when (uiState.searchMediaTypeFilter) {
                com.example.myapplication.data.models.MediaType.ANIME -> strings.typeAnime
                com.example.myapplication.data.models.MediaType.MANGA -> strings.typeManga
                com.example.myapplication.data.models.MediaType.TV_SERIES -> strings.typeSeries
            }
        )
    }
}
