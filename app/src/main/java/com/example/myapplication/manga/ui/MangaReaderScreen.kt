package com.example.myapplication.manga.ui

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
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.myapplication.manga.data.MangaReaderMode
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaPage
import com.example.myapplication.ui.shared.theme.BrandOrange
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Ридер главы: постранично или вебтун-лентой, поверх — скрываемый хром (тап по центру).
 *
 * Экран умышленно не знает ни про источник, ни про привязку тайтла — только про список страниц.
 */
@Composable
fun MangaReaderScreen(
    state: MangaReaderUiState,
    mode: MangaReaderMode,
    ru: Boolean,
    onPageChanged: (Int) -> Unit,
    onModeChange: (MangaReaderMode) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
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
                ru = ru,
                onPageChanged = onPageChanged,
                onModeChange = onModeChange,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun ReaderContent(
    state: MangaReaderUiState.Ready,
    mode: MangaReaderMode,
    ru: Boolean,
    onPageChanged: (Int) -> Unit,
    onModeChange: (MangaReaderMode) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onClose: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(false) }
    var currentPage by remember(state.chapter.key) { mutableIntStateOf(state.startPage) }
    val scope = rememberCoroutineScope()

    // Перезапуск пейджера/ленты при смене главы: иначе новая глава открывается на старой странице.
    key(state.chapter.key, mode) {
        when (mode) {
            MangaReaderMode.Paged -> {
                val pagerState = rememberPagerState(
                    initialPage = state.startPage,
                    pageCount = { state.pages.size },
                )
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }
                        .distinctUntilChanged()
                        .collect { page ->
                            currentPage = page
                            onPageChanged(page)
                        }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { index ->
                    ZoomablePage(
                        page = state.pages[index],
                        onToggleChrome = { chromeVisible = !chromeVisible },
                    )
                }
                ReaderChrome(
                    visible = chromeVisible,
                    state = state,
                    mode = mode,
                    ru = ru,
                    currentPage = currentPage,
                    onSeek = { target ->
                        scope.launch { pagerState.scrollToPage(target) }
                    },
                    onModeChange = onModeChange,
                    onPreviousChapter = onPreviousChapter,
                    onNextChapter = onNextChapter,
                    onClose = onClose,
                )
            }

            MangaReaderMode.Webtoon -> {
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.startPage)
                LaunchedEffect(listState) {
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                ReaderChrome(
                    visible = chromeVisible,
                    state = state,
                    mode = mode,
                    ru = ru,
                    currentPage = currentPage,
                    onSeek = { target ->
                        scope.launch { listState.scrollToItem(target) }
                    },
                    onModeChange = onModeChange,
                    onPreviousChapter = onPreviousChapter,
                    onNextChapter = onNextChapter,
                    onClose = onClose,
                )
            }
        }
    }
}

/** Пинч-зум и панорама в пределах страницы; двойной тап — быстрый зум/сброс. */
@Composable
private fun ZoomablePage(
    page: MangaPage,
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

@Composable
private fun PageImage(
    page: MangaPage,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val model = remember(page.url) {
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
            }
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
private fun ReaderChrome(
    visible: Boolean,
    state: MangaReaderUiState.Ready,
    mode: MangaReaderMode,
    ru: Boolean,
    currentPage: Int,
    onSeek: (Int) -> Unit,
    onModeChange: (MangaReaderMode) -> Unit,
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
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                IconButton(
                    onClick = {
                        onModeChange(
                            if (mode == MangaReaderMode.Paged) {
                                MangaReaderMode.Webtoon
                            } else {
                                MangaReaderMode.Paged
                            },
                        )
                    },
                ) {
                    Icon(
                        imageVector = if (mode == MangaReaderMode.Paged) {
                            Icons.Filled.ViewDay
                        } else {
                            Icons.Filled.Style
                        },
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
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
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { onSeek(it.roundToInt().coerceIn(0, state.pages.lastIndex)) },
                        valueRange = 0f..state.pages.lastIndex.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = BrandOrange,
                            activeTrackColor = BrandOrange,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                        ),
                    )
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

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f
