package com.example.myapplication.ui.details

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur as blurCompose
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import com.example.myapplication.data.models.Anime
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.shared.theme.IosDesign
import com.example.myapplication.ui.shared.theme.MotionTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * DetailsScreen is a NavHost destination — predictive back is handled
 * entirely by Navigation Compose 2.8+ via seekable transitions.
 * No PredictiveBackHandler or custom back animation needed here.
 */

/**
 * Содержимое шторки деталей. Открывается из Home как bottom sheet (см. [IosSheetScaffold] в AppNavGraph),
 * а не как отдельный экран навигации. ViewModel создаётся под конкретный [animeId] через Koin-параметр.
 */
@Composable
fun DetailsSheetContent(
    animeId: String,
    onDismiss: () -> Unit,
    viewModel: DetailsViewModel = koinViewModel(key = animeId) { parametersOf(animeId) },
) {
    val anime by viewModel.currentAnime.collectAsStateWithLifecycle()
    val language by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isAppInDarkTheme()

    anime?.let { currentAnime ->
        DetailsBody(
            anime = currentAnime,
            uiState = uiState,
            language = language,
            isDark = isDark,
            getImgPath = { viewModel.getImgPath(it) },
            onBack = onDismiss,
            inSheet = true,
        )
    } ?: run {
        LaunchedEffect(Unit) { onDismiss() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsBody(
    anime: Anime,
    uiState: DetailsUiState,
    language: AppLanguage,
    isDark: Boolean,
    getImgPath: (String?) -> String?,
    onBack: () -> Unit,
    inSheet: Boolean = false,
) {
    val details = (uiState as? DetailsUiState.Success)?.details
    var descriptionExpanded by remember { mutableStateOf(false) }

    val screenBg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(screenBg)
        drawContent()
    }
    val heroBlur = 14.dp
    val scrimAlpha = if (isDark) 0.30f else 0.12f

    val cardFill = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
    val onCard = MaterialTheme.colorScheme.onBackground
    val onCardMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val chipBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
    val chipFg = MaterialTheme.colorScheme.onSurfaceVariant

    val detailsTitle = if (language == AppLanguage.RU) "Детали" else "Details"
    val barTint = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)
    val barBorder = Color.White.copy(alpha = if (isDark) 0.22f else 0.28f)
    val barContent = Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (inSheet) Modifier.wrapContentHeight() else Modifier.fillMaxSize())
            .background(screenBg),
    ) {
        val imgPath = getImgPath(anime.imageFileName)
        val context = LocalContext.current

        // Узкая полоса размытого hero — только за стеклянной шапкой; ниже сразу растворяется
        // в сплошной фон, чтобы блок постера шёл сразу под шапкой (без пустого поля).
        val heroHeight = 96.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .align(Alignment.TopCenter)
                .layerBackdrop(backdrop),
        ) {
            if (imgPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(imgPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blurCompose(heroBlur),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDark) Color(0xFF1A1A2E) else Color(0xFFD8D8E0)),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to screenBg,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (inSheet) Modifier else Modifier.fillMaxSize().statusBarsPadding())
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Резервируем только высоту шапки — блок постера идёт сразу под ней.
            Spacer(Modifier.height(if (inSheet) 84.dp else heroHeight + 24.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                // ——— Постер + инфо (референс — фото 2) ———
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(168.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.06f)
                                else Color.Black.copy(alpha = 0.06f),
                            ),
                    ) {
                        if (imgPath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(File(imgPath))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val genres = details?.genres?.takeIf { it.isNotEmpty() }
                            ?: anime.tags.takeIf { it.isNotEmpty() }
                        if (!genres.isNullOrEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                genres.take(4).forEach { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(chipBg)
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = SnProFamily,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = chipFg,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Название по выбранному языку (замена, а не вторая строка).
                        val displayTitle = when (language) {
                            AppLanguage.EN -> anime.titleEn?.takeIf { it.isNotBlank() } ?: anime.title
                            AppLanguage.RU -> anime.titleRu?.takeIf { it.isNotBlank() } ?: anime.title
                        }
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = SnProFamily,
                                lineBreak = LineBreak.Heading,
                                hyphens = Hyphens.Auto,
                            ),
                            color = onCard,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ——— Рейтинг-пилюля • дата ———
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val apiRating = details?.rating?.takeIf { it > 0 }
                    val userRating = anime.rating.takeIf { it > 0f }
                    if (apiRating != null || userRating != null) {
                        val ratingText = when {
                            apiRating != null && apiRating > 10 -> "$apiRating / 100"
                            apiRating != null -> "$apiRating / 10"
                            else -> "${com.example.myapplication.data.models.RatingScale.format(userRating!!)} / 10"
                        }
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(chipBg)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFCC4D),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SnProFamily,
                                ),
                                color = onCard,
                            )
                        }
                    }

                    val airedOn = details?.airedOn
                    if (airedOn != null) {
                        Spacer(Modifier.width(14.dp))
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(IosDesign.separator(isDark)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = airedOn,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SnProFamily,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = onCardMuted,
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(IosDesign.separator(isDark)),
                )
                Spacer(Modifier.height(22.dp))

                // ——— Описание ———
                when (uiState) {
                    is DetailsUiState.Idle, DetailsUiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = if (language == AppLanguage.RU) "Загрузка..." else "Loading...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SnProFamily),
                                color = onCardMuted,
                            )
                        }
                    }

                    is DetailsUiState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = if (language == AppLanguage.RU) "Не удалось загрузить" else "Could not load details",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SnProFamily),
                                color = onCardMuted,
                            )
                        }
                    }

                    is DetailsUiState.MissingEnglishTitle -> {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = onCardMuted,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = if (language == AppLanguage.RU) {
                                    "Описание недоступно. Добавьте английское название или привяжите MAL/AniList id для более точной загрузки."
                                } else {
                                    "Description unavailable. Add an English title or link a MAL/AniList id for accurate loading."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SnProFamily),
                                color = onCardMuted,
                            )
                        }
                    }

                    is DetailsUiState.Success -> {
                        val descText = details?.description?.takeIf { it.isNotBlank() && it != "null" }
                        if (descText != null) {
                            Text(
                                text = if (language == AppLanguage.RU) "Описание" else "Description",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = SnProFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = onCard,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = descText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = SnProFamily,
                                    lineHeight = 22.sp,
                                    lineBreak = LineBreak.Paragraph,
                                    hyphens = Hyphens.Auto,
                                ),
                                color = onCardMuted,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.animateContentSize(animationSpec = MotionTokens.standard()),
                            )
                            Spacer(Modifier.height(14.dp))

                            // Нейтральная пилюля со светлым контентом — читается лучше фиолетового.
                            val pillBg = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
                            val pillContent = onCard
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (descriptionExpanded) 180f else 0f,
                                animationSpec = MotionTokens.standard(),
                                label = "descExpandChevron",
                            )
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { descriptionExpanded = !descriptionExpanded }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (descriptionExpanded) {
                                        if (language == AppLanguage.RU) "Свернуть" else "Show less"
                                    } else {
                                        if (language == AppLanguage.RU) "Подробнее" else "Show more"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = SnProFamily,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = pillContent,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = pillContent,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = chevronRotation },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (inSheet) Modifier else Modifier.statusBarsPadding())
                .padding(top = if (inSheet) 22.dp else 12.dp, start = 16.dp, end = 16.dp)
                .zIndex(4f),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(24f.dp.toPx())
                            lens(8f.dp.toPx(), 48f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(barTint) },
                    )
                    .clip(CircleShape)
                    .border(0.5.dp, barBorder, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = barContent,
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(48.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(100.dp) },
                        effects = {
                            vibrancy()
                            blur(24f.dp.toPx())
                            lens(8f.dp.toPx(), 48f.dp.toPx())
                        },
                        onDrawSurface = { drawRect(barTint) },
                    )
                    .clip(RoundedCornerShape(100.dp))
                    .border(0.5.dp, barBorder, RoundedCornerShape(100.dp))
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = detailsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SnProFamily,
                    color = barContent,
                )
            }
        }
    }
}
