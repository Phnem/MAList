package com.example.myapplication.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.myapplication.NotificationSyncOverlay
import com.example.myapplication.ui.addedit.AddEditScreen
import com.example.myapplication.ui.addedit.AddEditViewModel
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.home.HomeViewModel
import com.example.myapplication.ui.inspect.InspectScreen
import com.example.myapplication.ui.inspect.InspectViewModel
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.settings.SettingsViewModel
import com.example.myapplication.ui.navigation.navigateToWelcome
import com.example.myapplication.ui.shared.LocalAdaptiveGlassScrollInProgress
import com.example.myapplication.ui.shared.theme.MotionTokens
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Рабочая область — корень приложения при включённом флаге `SELECT_DOCK_NAVIGATION`.
 *
 * Пять равноправных разделов ([WorkspacePage]) живут страницами одного пейджера; переключение —
 * свайпом или тапом по [WorkspaceDock]. Details и редактирование по id остаются push-экранами
 * поверх рабочей области и открываются через тот же [navController].
 *
 * TICKET-01: переход пока дефолтный пейджерный, «наезд» — TICKET-03; стекло и автоскрытие дока —
 * TICKET-04; статистика — TICKET-05.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun WorkspaceScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    inspectViewModel: InspectViewModel,
    settingsViewModel: SettingsViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val language by homeViewModel.uiLanguage.collectAsStateWithLifecycle()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val syncReport by homeViewModel.syncReport.collectAsStateWithLifecycle()

    // Выделенная долгим удержанием карточка запирает рабочую область: страницы не листаются,
    // док гаснет и не нажимается. Скрим самого меню лежит внутри главной и до дока не достаёт —
    // он ей сосед, а не потомок, поэтому состояние приходит наверх колбэком.
    var cardSelectionActive by remember { mutableStateOf(false) }
    // Любая шторка или диалог на странице → док уезжает вниз. Страницы сообщают об этом
    // колбэком: док им сосед, а не потомок, и сам про их состояние не знает.
    var homeOverlayVisible by remember { mutableStateOf(false) }
    var settingsOverlayVisible by remember { mutableStateOf(false) }
    // Автоскрытие дока (D9) и экономный режим его стекла живут здесь по той же причине: страницы
    // доку не родители, `LocalAdaptiveGlassScrollInProgress` внутри них до него не достаёт.
    var pageDockVisible by remember { mutableStateOf(true) }
    var pageScrollInProgress by remember { mutableStateOf(false) }
    var showSyncPanel by remember { mutableStateOf(false) }
    val syncPanelState = remember { MutableTransitionState(false) }
    syncPanelState.targetState = showSyncPanel
    val dockHidden = homeOverlayVisible || settingsOverlayVisible || showSyncPanel || !pageDockVisible

    val pagerState = rememberPagerState(
        initialPage = WorkspacePage.Start.index,
        pageCount = { WorkspacePage.PageCount },
    )

    // Стекло дока на время движения: и скролл списка страницы, и сам «наезд» между страницами
    // переводят его в статичный режим (D10) — режим меняется свойствами узла, структура прежняя.
    val pagerScrolling by remember { derivedStateOf { pagerState.isScrollInProgress } }
    val dockGlassStatic = pageScrollInProgress || pagerScrolling

    // Своя инстанция формы: у push-экрана редактирования она общая на весь граф, и
    // `loadAnime(id)` затирал бы черновик страницы (и наоборот). Сброс черновика при уходе —
    // TICKET-06.
    val addPageViewModel: AddEditViewModel = koinViewModel(key = WORKSPACE_ADD_VM_KEY)

    val goTo: (WorkspacePage) -> Unit = { page ->
        scope.launch {
            pagerState.animateScrollToPage(page.index, animationSpec = MotionTokens.sheetPresent())
        }
    }

    // targetPage, а не currentPage: во время броска пальцем «назад» должен считаться от того,
    // куда мы едем, иначе Back посреди жеста уводит не туда.
    val backTarget = WorkspacePage.backTargetFrom(WorkspacePage.ofIndex(pagerState.targetPage))
    BackHandler(enabled = backTarget != null) {
        backTarget?.let { target ->
            performHaptic(view, "light")
            goTo(target)
        }
    }

    // Живой бэкдроп для дока — тот же приём, что в Details: страницы лежат ПОД узлом
    // layerBackdrop, и капсула дока преломляет их содержимое. Узел не размонтируется.
    val screenBg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(screenBg)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Соседняя страница обязана быть готова заранее: иначе переход показывает пустой кадр.
            beyondViewportPageCount = 1,
            userScrollEnabled = !cardSelectionActive,
        ) { index ->
            Box(modifier = Modifier.workspacePageMotion(pagerState, index)) {
            when (WorkspacePage.ofIndex(index)) {
                // onBack не передаём: на странице кнопки «назад» нет (TICKET-07), системный
                // Back перехватывает BackHandler рабочей области выше.
                WorkspacePage.FRAME -> InspectScreen(
                    navController = navController,
                    viewModel = inspectViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onOpenSettings = { goTo(WorkspacePage.SETTINGS) },
                    bottomInset = WorkspaceDockInset,
                )

                WorkspacePage.HOME -> HomeScreen(
                    navController = navController,
                    viewModel = homeViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    hostedInWorkspace = true,
                    onCardSelectionChange = { cardSelectionActive = it },
                    onOverlayVisibleChange = { homeOverlayVisible = it },
                    onContentScrollChange = { pageScrollInProgress = it },
                    onDockVisibleChange = { pageDockVisible = it },
                )

                WorkspacePage.ADD -> AddEditScreen(
                    navController = navController,
                    viewModel = addPageViewModel,
                    animeId = null,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onSaved = { goTo(WorkspacePage.HOME) },
                )

                WorkspacePage.SETTINGS -> SettingsScreen(
                    navController = navController,
                    viewModel = settingsViewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    bottomInset = WorkspaceDockInset,
                    onOpenSyncPanel = { showSyncPanel = true },
                    onOverlayVisibleChange = { settingsOverlayVisible = it },
                    onContentScrollChange = { pageScrollInProgress = it },
                    onDockVisibleChange = { pageDockVisible = it },
                )
            }
            }
        }
        }

        // Панель подключения и синхронизации: раньше жила в верхнем доке главной, теперь
        // открывается пунктом настроек. Рендерим её здесь, поверх пейджера, — своим скримом
        // она накрывает и док.
        if (syncPanelState.currentState || syncPanelState.targetState) {
            Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                NotificationSyncOverlay(
                    syncCoordinator = koinInject(),
                    authRepository = koinInject(),
                    visibleState = syncPanelState,
                    strings = getStrings(language),
                    syncReport = syncReport,
                    updates = homeUiState.updates,
                    isCheckingUpdates = homeUiState.isCheckingUpdates,
                    currentLanguage = language,
                    onDismiss = { showSyncPanel = false },
                    onLogout = { navController.navigateToWelcome() },
                    onCheckUpdates = {
                        performHaptic(view, "light")
                        homeViewModel.checkForUpdates(force = true)
                    },
                )
            }
        }

        CompositionLocalProvider(LocalAdaptiveGlassScrollInProgress provides dockGlassStatic) {
        WorkspaceDock(
            backdrop = backdrop,
            hidden = dockHidden,
            // targetPage, а не settledPage: как только жест перешёл порог, пилюля уже едет к
            // новому разделу. С settledPage она стояла бы на старом до конца анимации и потом
            // прыгала.
            selected = WorkspacePage.ofIndex(pagerState.targetPage),
            language = language,
            dimmed = cardSelectionActive,
            onSelect = { page ->
                performHaptic(view, "light")
                goTo(page)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .zIndex(6f),
        )
        }
    }
}

/**
 * «Наезд» между страницами (TICKET-03).
 *
 * Страница с бо́льшим индексом лежит выше по `zIndex` и едет за пальцем один в один; соседка
 * слева уходит вглубь и параллаксит. Значения считает чистая [workspacePageTransform].
 *
 * Состояние пейджера читается ВНУТРИ лямбд `graphicsLayer` и `drawWithContent`, а не в
 * композиции: иначе каждый кадр жеста перекомпоновывал бы обе страницы целиком. Сами модификаторы
 * при этом не появляются и не исчезают — узел над `layerBackdrop` остаётся неизменным.
 */
private fun Modifier.workspacePageMotion(pagerState: PagerState, index: Int): Modifier = this
    .zIndex(index.toFloat())
    .graphicsLayer {
        val transform = pagerState.transformFor(index)
        scaleX = transform.scale
        scaleY = transform.scale
        translationX = transform.translationXFraction * size.width
        shape = RoundedCornerShape(transform.cornerDp.dp)
        clip = transform.cornerDp > 0f
        // Тень по ведущему краю наезжающей страницы — обычная теневая высота слоя: рисовать её
        // самим нельзя, снаружи клипа кисть не достаёт.
        shadowElevation = transform.shadowAlpha * PAGE_SHADOW_ELEVATION.toPx()
    }
    .drawWithContent {
        drawContent()
        // Второй расчёт вместо общего состояния: функция чистая и дешёвая, а лишний
        // `mutableStateOf` между слоем и отрисовкой добавил бы кадр рассинхрона.
        val dim = pagerState.transformFor(index).dimAlpha
        if (dim > 0f) drawRect(color = Color.Black, alpha = dim)
    }

private fun PagerState.transformFor(index: Int): PageTransform =
    workspacePageTransform(workspacePageOffset(index, currentPage, currentPageOffsetFraction))

private val PAGE_SHADOW_ELEVATION = 24.dp

/** Ключ Koin для формы-страницы: отделяет её ViewModel от push-экрана редактирования. */
private const val WORKSPACE_ADD_VM_KEY = "workspace_add_edit"
