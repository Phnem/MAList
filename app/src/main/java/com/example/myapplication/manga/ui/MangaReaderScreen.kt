package com.example.myapplication.manga.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowConversionToBitmap
import coil3.request.crossfade
import coil3.request.transformations
import com.example.myapplication.manga.data.ChapterReadingProgress
import com.example.myapplication.manga.data.MangaReaderMode
import com.example.myapplication.manga.data.PageDirection
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaPage
import com.example.myapplication.ui.shared.components.IosSheetScaffold
import com.example.myapplication.ui.shared.components.LiquidGlassTrack
import com.example.myapplication.ui.shared.theme.BrandOrange
import com.example.myapplication.utils.performHaptic
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Ридер главы: постранично или вебтун-лентой, поверх — скрываемый хром (тап по центру).
 *
 * Экран умышленно не знает ни про источник, ни про привязку тайтла — только про список страниц
 * и оглавление, с которым его открыли.
 */
@Composable
fun MangaReaderScreen(
    state: MangaReaderUiState,
    mode: MangaReaderMode,
    direction: PageDirection,
    cropBorders: Boolean,
    chapters: List<MangaChapter>,
    chapterProgress: Map<String, ChapterReadingProgress>,
    ru: Boolean,
    onPageChanged: (Int) -> Unit,
    onLayoutChange: (MangaReaderMode, PageDirection) -> Unit,
    onToggleCrop: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var chaptersVisible by remember { mutableStateOf(false) }
    val currentChapterKey = (state as? MangaReaderUiState.Ready)?.chapter?.key.orEmpty()

    // Открытая шторка забирает «назад» себе: иначе оглавление закрывало бы ридер целиком.
    BackHandler(enabled = chaptersVisible) { chaptersVisible = false }

    IosSheetScaffold(
        sheetVisible = chaptersVisible,
        onDismiss = { chaptersVisible = false },
        sheetHeightFraction = 0.72f,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                when (state) {
                    MangaReaderUiState.Loading -> CircularProgressIndicator(
                        color = BrandOrange,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is MangaReaderUiState.Error -> ReaderError(
                        state = state,
                        ru = ru,
                        onRetry = onRetry,
                        onClose = onClose,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is MangaReaderUiState.Ready -> ReaderContent(
                        state = state,
                        mode = mode,
                        direction = direction,
                        cropBorders = cropBorders,
                        ru = ru,
                        onPageChanged = onPageChanged,
                        onLayoutChange = onLayoutChange,
                        onToggleCrop = onToggleCrop,
                        onChapters = { chaptersVisible = true },
                        onPreviousChapter = onPreviousChapter,
                        onNextChapter = onNextChapter,
                        onClose = onClose,
                    )
                }
            }
        },
        sheetContent = {
            ReaderChaptersSheet(
                chapters = chapters,
                currentKey = currentChapterKey,
                progress = chapterProgress,
                ru = ru,
                onPick = { chapter ->
                    chaptersVisible = false
                    onOpenChapter(chapter.key)
                },
            )
        },
    )
}

@Composable
private fun ReaderContent(
    state: MangaReaderUiState.Ready,
    mode: MangaReaderMode,
    direction: PageDirection,
    cropBorders: Boolean,
    ru: Boolean,
    onPageChanged: (Int) -> Unit,
    onLayoutChange: (MangaReaderMode, PageDirection) -> Unit,
    onToggleCrop: () -> Unit,
    onChapters: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onClose: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(false) }
    var currentPage by remember(state.chapter.key) { mutableIntStateOf(state.startPage) }
    val scope = rememberCoroutineScope()

    // Перемотку хром получает функцией, а не самим pagerState/listState: хром живёт СНАРУЖИ
    // key(...) и не имеет права пересобираться вместе с пейджером (иначе стеклянный док терял бы
    // свой слой-задник). Текущий скроллер регистрирует себя сам при создании.
    val seek = remember { mutableStateOf<suspend (Int) -> Unit>({ }) }

    // Слой-задник для стекла дока: захватывает ТОЛЬКО страницы, поэтому док преломляет саму
    // мангу, а не собственный хром. Узел с layerBackdrop лежит над key(...) и никогда не
    // размонтируется — смена режима или главы его не трогает.
    val pagesBackdrop = rememberLayerBackdrop {
        drawRect(Color.Black)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pagesBackdrop),
        ) {
            // Перезапуск пейджера/ленты при смене главы: иначе новая глава открывается на старой
            // странице. Направление тоже в ключе — HorizontalPager читает layout direction только
            // при создании, без пересборки переключатель «классика/комикс» выглядел бы сломанным.
            // В вебтуне направление из ключа выключаем: ленте оно безразлично, а лишняя пересборка
            // сбросила бы позицию скролла.
            key(state.chapter.key, mode, direction.takeIf { mode == MangaReaderMode.Paged }) {
                when (mode) {
                    MangaReaderMode.Paged -> {
                        val pagerState = rememberPagerState(
                            initialPage = state.startPage,
                            pageCount = { state.pages.size },
                        )
                        LaunchedEffect(pagerState) {
                            seek.value = { page -> pagerState.scrollToPage(page) }
                            snapshotFlow { pagerState.currentPage }
                                .distinctUntilChanged()
                                .collect { page ->
                                    currentPage = page
                                    onPageChanged(page)
                                }
                        }
                        // Направление задаём только пейджеру, не всему экрану: список страниц
                        // остаётся в исходном порядке, поэтому сохранённый pageIndex продолжает
                        // указывать на ту же страницу, а хром и слайдер не переворачиваются
                        // вместе с ней.
                        CompositionLocalProvider(
                            LocalLayoutDirection provides when (direction) {
                                PageDirection.Rtl -> LayoutDirection.Rtl
                                PageDirection.Ltr -> LayoutDirection.Ltr
                            },
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                            ) { index ->
                                ZoomablePage(
                                    page = state.pages[index],
                                    cropBorders = cropBorders,
                                    ru = ru,
                                    onToggleChrome = { chromeVisible = !chromeVisible },
                                )
                            }
                        }
                    }

                    MangaReaderMode.Webtoon -> {
                        val listState =
                            rememberLazyListState(initialFirstVisibleItemIndex = state.startPage)
                        LaunchedEffect(listState) {
                            seek.value = { page -> listState.scrollToItem(page) }
                            snapshotFlow { listState.firstVisibleItemIndex }
                                .distinctUntilChanged()
                                .collect { page ->
                                    currentPage = page
                                    onPageChanged(page)
                                }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { chromeVisible = !chromeVisible })
                                },
                        ) {
                            items(state.pages.size) { index ->
                                PageImage(
                                    page = state.pages[index],
                                    contentScale = ContentScale.FillWidth,
                                    cropBorders = cropBorders,
                                    ru = ru,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        ReaderChrome(
            visible = chromeVisible,
            backdrop = pagesBackdrop,
            state = state,
            mode = mode,
            direction = direction,
            cropBorders = cropBorders,
            ru = ru,
            currentPage = currentPage,
            onSeek = { target -> scope.launch { seek.value(target) } },
            onLayoutChange = onLayoutChange,
            onToggleCrop = onToggleCrop,
            onChapters = onChapters,
            onPreviousChapter = onPreviousChapter,
            onNextChapter = onNextChapter,
            onClose = onClose,
        )
    }
}

/** Пинч-зум и панорама в пределах страницы; двойной тап — быстрый зум/сброс. */
@Composable
private fun ZoomablePage(
    page: MangaPage,
    cropBorders: Boolean,
    ru: Boolean,
    onToggleChrome: () -> Unit,
) {
    var scale by remember(page.url) { mutableFloatStateOf(1f) }
    var offsetX by remember(page.url) { mutableFloatStateOf(0f) }
    var offsetY by remember(page.url) { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformState)
            .pointerInput(page.url) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PageImage(
            page = page,
            contentScale = ContentScale.Fit,
            cropBorders = cropBorders,
            ru = ru,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}

/**
 * Одна страница с собственным retry: битая картинка не должна утаскивать за собой главу.
 *
 * Общая точка отрисовки для обоих режимов, поэтому заглушка автоматически работает и в пейджере,
 * и в вебтун-ленте.
 */
@Composable
private fun PageImage(
    page: MangaPage,
    contentScale: ContentScale,
    cropBorders: Boolean,
    ru: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Номер попытки живёт вместе со страницей и служит cache-bust'ом при перезагрузке.
    var attempt by remember(page.url) { mutableIntStateOf(0) }
    var failed by remember(page.url) { mutableStateOf(false) }
    var loading by remember(page.url) { mutableStateOf(true) }

    val model = remember(page.url, attempt, cropBorders) {
        ImageRequest.Builder(context)
            .data(page.url)
            .crossfade(true)
            .apply {
                // Источники, отдающие картинки только со своим Referer, иначе вернут 403.
                if (page.headers.isNotEmpty()) {
                    val headers = NetworkHeaders.Builder().apply {
                        page.headers.forEach { (name, value) -> add(name, value) }
                    }.build()
                    httpHeaders(headers)
                }
                // Сам URL не трогаем: у части источников он подписан, и лишний query-параметр
                // сломал бы подпись. Попытки различаем ключами кэша — этого достаточно, чтобы
                // Coil пошёл в сеть заново, а не отдал прошлый промах.
                if (attempt > 0) {
                    memoryCacheKey("${page.url}#$attempt")
                    diskCacheKey("${page.url}#$attempt")
                }
                if (cropBorders) {
                    transformations(EdgeCropTransformation)
                    // Тайловую вебтун-полосу из RegionBitmapDecoder Coil ради трансформации
                    // схлопнул бы в один гигантский битмап — тот самый OOM, от которого тайлы и
                    // спасают. С этим флагом обрезка на таких страницах просто не применяется.
                    allowConversionToBitmap(false)
                }
            }
            .build()
    }

    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = contentScale,
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
                failed = state is AsyncImagePainter.State.Error
            },
            modifier = modifier,
        )
        // Пока картинки нет, AsyncImage схлопывается в нулевую высоту — в вебтун-ленте это
        // дёргало бы скролл. Заглушка держит место сама и задаёт высоту элементу.
        if (failed) {
            PageLoadError(ru = ru, onRetry = { attempt++ })
        } else if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PagePlaceholderHeight),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandOrange, strokeWidth = 2.dp)
            }
        }
    }
}

/** Заглушка вместо не загрузившейся страницы: соседние страницы при этом остаются рабочими. */
@Composable
private fun PageLoadError(
    ru: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PagePlaceholderHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (ru) "Страница не загрузилась" else "This page failed to load",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = BrandOrange,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (ru) "Обновить" else "Retry",
                color = BrandOrange,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReaderChrome(
    visible: Boolean,
    backdrop: Backdrop,
    state: MangaReaderUiState.Ready,
    mode: MangaReaderMode,
    direction: PageDirection,
    cropBorders: Boolean,
    ru: Boolean,
    currentPage: Int,
    onSeek: (Int) -> Unit,
    onLayoutChange: (MangaReaderMode, PageDirection) -> Unit,
    onToggleCrop: () -> Unit,
    onChapters: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Градиент вместо плоской заливки: под доком должна просвечивать страница,
                    // иначе преломлять ему нечего — сплошная чёрная плашка выглядит как рамка.
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.75f),
                            1f to Color.Transparent,
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.chapter.readerTitle(ru),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${currentPage + 1} / ${state.pages.size}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ReaderDock(
                    backdrop = backdrop,
                    mode = mode,
                    direction = direction,
                    cropBorders = cropBorders,
                    ru = ru,
                    onChapters = onChapters,
                    onLayoutChange = onLayoutChange,
                    onToggleCrop = onToggleCrop,
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (state.pages.size > 1) {
                    ReaderPageSlider(
                        page = currentPage,
                        pageCount = state.pages.size,
                        onSeek = onSeek,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChapterNavButton(
                        icon = Icons.AutoMirrored.Filled.NavigateBefore,
                        label = if (ru) "Предыдущая" else "Previous",
                        enabled = state.hasPrevious,
                        onClick = onPreviousChapter,
                    )
                    ChapterNavButton(
                        icon = Icons.AutoMirrored.Filled.NavigateNext,
                        label = if (ru) "Следующая" else "Next",
                        enabled = state.hasNext,
                        onClick = onNextChapter,
                        iconFirst = false,
                    )
                }
            }
        }
    }
}

/**
 * Перемотка по страницам на общем стеклянном треке. Значение здесь дискретное — номер страницы,
 * поэтому отклик тоже дискретный: тик на каждый снап, а не непрерывная вибрация под пальцем.
 */
@Composable
private fun ReaderPageSlider(
    page: Int,
    pageCount: Int,
    onSeek: (Int) -> Unit,
) {
    val view = LocalView.current
    val lastIndex = (pageCount - 1).coerceAtLeast(1)
    // Последний отданный снап: без него тик срабатывал бы на каждое движение пальца внутри
    // одной и той же страницы.
    var lastSnap by remember { mutableIntStateOf(page) }

    LiquidGlassTrack(
        fraction = page.toFloat() / lastIndex,
        ticks = if (pageCount <= MaxSliderTicks) pageCount else 0,
        onScrubStart = { lastSnap = page },
        onScrub = { fraction ->
            val target = (fraction * lastIndex).roundToInt().coerceIn(0, lastIndex)
            if (target != lastSnap) {
                lastSnap = target
                performHaptic(view, "tick")
                onSeek(target)
            }
        },
        onScrubEnd = { fraction ->
            val target = (fraction * lastIndex).roundToInt().coerceIn(0, lastIndex)
            performHaptic(view, "light")
            onSeek(target)
        },
        thumbContent = {
            Text(
                text = "${page + 1}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun ChapterNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconFirst: Boolean = true,
) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (iconFirst) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = tint, fontSize = 13.sp)
        } else {
            Text(label, color = tint, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ReaderError(
    state: MangaReaderUiState.Error,
    ru: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.message.readerErrorText(ru),
            color = Color.White,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = BrandOrange)
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

private fun String.readerErrorText(ru: Boolean): String = when (this) {
    "pages_empty" -> if (ru) "Источник не отдал страницы этой главы" else "Source returned no pages"
    "chapter_missing" -> if (ru) "Глава больше недоступна" else "Chapter is no longer available"
    else -> if (ru) "Не удалось открыть главу" else "Failed to open the chapter"
}

/** «Гл. 12 — Название» / «Ch. 12 — Title»; у безномерных экстр остаётся только название. */
fun MangaChapter.readerTitle(ru: Boolean): String {
    val number = numberLabel?.let { if (ru) "Гл. $it" else "Ch. $it" }
    val name = title?.takeIf { it.isNotBlank() }
    return listOfNotNull(number, name).joinToString(" — ")
        .ifBlank { if (ru) "Глава" else "Chapter" }
}

/** Высота места под ещё не загруженную или битую страницу — чтобы лента вебтуна не дёргалась. */
private val PagePlaceholderHeight = 220.dp

/** Насечки под страницы имеют смысл только у коротких глав; дальше это сплошная штриховка. */
private const val MaxSliderTicks = 20

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f
