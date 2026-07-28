package com.example.myapplication.manga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.myapplication.manga.data.DownloadProgress
import com.example.myapplication.manga.domain.MangaChapter
import com.example.myapplication.manga.domain.MangaItem
import com.example.myapplication.manga.source.MangaSourceResults
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.shared.theme.BrandOrange
import com.example.myapplication.ui.shared.theme.SquircleShape
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Вкладка «Главы» в Details для тайтлов с [com.example.myapplication.data.models.MediaType.MANGA].
 *
 * Два состояния по существу: тайтл ещё не сопоставлен с мангой у источника (выбор из результатов
 * поиска) или уже сопоставлен (список глав). Третьего не бывает — именно поэтому привязка живёт
 * отдельным подтверждённым фактом, а не эвристикой при каждом открытии.
 */
@Composable
fun MangaChaptersPage(
    animeId: String,
    animeTitle: String,
    animeTitleEn: String?,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    // Ключ по тайтлу: без него разные тайтлы в одном ViewModelStore делят одну модель и оглавление.
    viewModel: MangaLibraryViewModel = koinViewModel(key = "manga_chapters_$animeId") {
        parametersOf(animeId, animeTitle, animeTitleEn.orEmpty())
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ru = language == AppLanguage.RU
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            MangaLibraryUiState.Loading -> CircularProgressIndicator(
                color = BrandOrange,
                modifier = Modifier.align(Alignment.Center),
            )

            is MangaLibraryUiState.SourcePicker -> SourcePickerContent(
                state = current,
                ru = ru,
                onSearch = viewModel::search,
                onPick = viewModel::bind,
            )

            is MangaLibraryUiState.Chapters -> ChaptersContent(
                state = current,
                ru = ru,
                onOpen = { chapter ->
                    MangaReaderActivity.start(
                        context = context,
                        animeId = animeId,
                        chapter = chapter,
                        // Платные главы в ридер не отдаём: «следующая» не должна упираться в замок.
                        chapters = current.chapters.filterNot { it.paid },
                    )
                },
                onToggleRead = viewModel::setRead,
                onToggleDownload = viewModel::toggleDownload,
                onLanguage = viewModel::selectLanguage,
                onRefresh = viewModel::refresh,
                onChangeSource = viewModel::unbind,
            )

            is MangaLibraryUiState.Error -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (ru) {
                        "У источника нет глав для этого тайтла"
                    } else {
                        "The source has no chapters for this title"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (ru) "Выбрать другой источник" else "Pick another source",
                    color = BrandOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { viewModel.unbind() },
                )
            }
        }
    }
}

@Composable
private fun SourcePickerContent(
    state: MangaLibraryUiState.SourcePicker,
    ru: Boolean,
    onSearch: (String) -> Unit,
    onPick: (MangaItem) -> Unit,
) {
    var query by remember(state.query) { mutableStateOf(state.query) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = {
                Text(if (ru) "Название у источника" else "Title at the source")
            },
            trailingIcon = {
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = BrandOrange)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text = if (ru) {
                "Выберите мангу, которая соответствует тайтлу — привязка сохранится."
            } else {
                "Pick the manga matching this title — the binding is saved."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (state.searching) {
            CircularProgressIndicator(
                color = BrandOrange,
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.CenterHorizontally),
            )
            return@Column
        }

        if (state.results.isEmpty()) {
            Text(
                text = if (ru) "Ничего не найдено" else "Nothing found",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.results.forEach { group: MangaSourceResults ->
                item(key = "header_${group.sourceId.value}") {
                    Text(
                        text = group.sourceName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(group.items, key = { "${group.sourceId.value}_${it.key}" }) { item ->
                    SearchResultRow(item = item, onClick = { onPick(item) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: MangaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 44.dp, height = 62.dp)
                .clip(SquircleShape(10.dp))
                .background(Color.Black.copy(alpha = 0.2f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                item.altTitle?.takeIf { it != item.title },
                item.year?.toString(),
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChaptersContent(
    state: MangaLibraryUiState.Chapters,
    ru: Boolean,
    onOpen: (MangaChapter) -> Unit,
    onToggleRead: (MangaChapter, Boolean) -> Unit,
    onToggleDownload: (MangaChapter) -> Unit,
    onLanguage: (String?) -> Unit,
    onRefresh: () -> Unit,
    onChangeSource: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.binding.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (ru) {
                        "${state.chapters.size} гл. · ${state.binding.sourceId}"
                    } else {
                        "${state.chapters.size} ch. · ${state.binding.sourceId}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            if (state.refreshing) {
                CircularProgressIndicator(
                    color = BrandOrange,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onChangeSource) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.availableLanguages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageChip(
                    label = if (ru) "Все" else "All",
                    selected = state.binding.preferredLanguage == null,
                    onClick = { onLanguage(null) },
                )
                state.availableLanguages.forEach { lang ->
                    LanguageChip(
                        label = lang.uppercase(),
                        selected = state.binding.preferredLanguage == lang,
                        onClick = { onLanguage(lang) },
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.chapters, key = { it.key }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    progressFraction = state.progress[chapter.key]?.fraction ?: 0f,
                    read = state.progress[chapter.key]?.read == true,
                    downloaded = chapter.key in state.downloadedKeys,
                    downloading = state.downloading[chapter.key],
                    ru = ru,
                    onClick = { onOpen(chapter) },
                    onToggleRead = { onToggleRead(chapter, it) },
                    onToggleDownload = { onToggleDownload(chapter) },
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(SquircleShape(10.dp))
            .background(
                if (selected) BrandOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Одна кнопка на три состояния: скачать → идёт загрузка (кольцо прогресса, тап отменяет) →
 * скачано (тап удаляет). Отдельных кнопок «отменить» и «удалить» нет намеренно — в строке главы
 * им негде поместиться, а действие всегда однозначно.
 */
@Composable
private fun DownloadButton(
    downloaded: Boolean,
    downloading: DownloadProgress?,
    ru: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        when {
            downloading != null -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { downloading.fraction },
                    color = BrandOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }

            downloaded -> Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = if (ru) "Удалить загрузку" else "Delete download",
                tint = BrandOrange,
                modifier = Modifier.size(20.dp),
            )

            else -> Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = if (ru) "Скачать главу" else "Download chapter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: MangaChapter,
    progressFraction: Float,
    read: Boolean,
    downloaded: Boolean,
    downloading: DownloadProgress?,
    ru: Boolean,
    onClick: () -> Unit,
    onToggleRead: (Boolean) -> Unit,
    onToggleDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .clickable(enabled = !chapter.paid, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.readerTitle(ru),
                color = if (read || chapter.paid) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                chapter.language?.uppercase(),
                chapter.scanlator,
                chapter.pageCount.takeIf { it > 0 }?.let { if (ru) "$it стр." else "$it p." },
                if (downloaded) (if (ru) "офлайн" else "offline") else null,
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Полосу тянем только для начатых-недочитанных: у прочитанных её роль берёт галочка.
            if (progressFraction > 0f && !read) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = BrandOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        if (!chapter.paid) {
            DownloadButton(
                downloaded = downloaded,
                downloading = downloading,
                ru = ru,
                onClick = onToggleDownload,
            )
        }
        if (chapter.paid) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = if (ru) "Платная глава" else "Paid chapter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(12.dp)
                    .size(18.dp),
            )
        } else {
            IconButton(onClick = { onToggleRead(!read) }) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (read) {
                        BrandOrange
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
