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
import com.example.myapplication.data.models.Anime
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.network.AppLanguage
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

/**
 * DetailsScreen is a NavHost destination — predictive back is handled
 * entirely by Navigation Compose 2.8+ via seekable transitions.
 * No PredictiveBackHandler or custom back animation needed here.
 */

@Composable
fun DetailsScreen(
    animeId: String,
    navController: NavController,
    onBackClick: () -> Unit = { navController.popBackStack() },
    viewModel: DetailsViewModel = koinViewModel()
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
            onBack = onBackClick
        )
    } ?: run {
        LaunchedEffect(Unit) { onBackClick() }
    }
}

@Composable
fun AnimeDetailsSheet(
    viewModel: DetailsViewModel,
    anime: Anime,
    language: AppLanguage,
    getImgPath: (String?) -> String?,
    onDismiss: () -> Unit,
    embedded: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isAppInDarkTheme()

    if (embedded) {
        DetailsBody(
            anime = anime,
            uiState = uiState,
            language = language,
            isDark = isDark,
            getImgPath = getImgPath,
            onBack = onDismiss
        )
        return
    }

    var enterProgress by remember { mutableFloatStateOf(0f) }
    var backDismissProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
        ) { value, _ -> enterProgress = value }
    }

    fun animateDismiss(onComplete: () -> Unit) {
        scope.launch {
            animate(
                initialValue = enterProgress,
                targetValue = 0f,
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) { value, _ -> enterProgress = value }
            onComplete()
        }
    }

    PredictiveBackHandler { flow ->
        try {
            flow.collect { event ->
                backDismissProgress = event.progress
            }
            animate(
                initialValue = backDismissProgress,
                targetValue = 1f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) { value, _ -> backDismissProgress = value }
            onDismiss()
        } catch (_: CancellationException) {
            animate(
                initialValue = backDismissProgress,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { value, _ -> backDismissProgress = value }
        }
    }

    val sheetScale = enterProgress * (1f - backDismissProgress * 0.15f) + (1f - enterProgress) * 0.92f
    val sheetAlpha = enterProgress * (1f - backDismissProgress * 0.5f)
    val scrimAlpha = enterProgress * (1f - backDismissProgress * 0.8f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = scrimAlpha }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { animateDismiss(onDismiss) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .graphicsLayer {
                    scaleX = sheetScale
                    scaleY = sheetScale
                    alpha = sheetAlpha
                }
                .clip(RoundedCornerShape(28.dp))
        ) {
            DetailsBody(
                anime = anime,
                uiState = uiState,
                language = language,
                isDark = isDark,
                getImgPath = getImgPath,
                onBack = { animateDismiss(onDismiss) }
            )
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        val imgPath = getImgPath(anime.imageFileName)
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            if (imgPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imgPath)
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
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .statusBarsPadding()
                .padding(top = 56.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardFill)
                    .padding(20.dp),
            ) {
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
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
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
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = SnProFamily,
                        lineHeight = 36.sp,
                        lineBreak = LineBreak.Heading,
                        hyphens = Hyphens.Auto,
                    ),
                    color = onCard,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val apiRating = details?.rating
                    val displayRating = apiRating ?: anime.rating.takeIf { it > 0 }

                    if (displayRating != null && displayRating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFCC4D),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            val ratingText = if (apiRating != null && apiRating > 10) {
                                "${apiRating}/100"
                            } else if (apiRating != null && apiRating > 5) {
                                "${apiRating}/10"
                            } else {
                                "${displayRating}/5"
                            }
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SnProFamily,
                                ),
                                color = onCard,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    val airedOn = details?.airedOn
                    if (airedOn != null) {
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
            }

            Spacer(Modifier.height(12.dp))

            when (uiState) {
                is DetailsUiState.Idle, DetailsUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardFill)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
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
                }

                is DetailsUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = if (language == AppLanguage.RU) {
                                    "Не удалось загрузить"
                                } else {
                                    "Could not load details"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SnProFamily),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                is DetailsUiState.Success -> {
                    val descText = details?.description?.takeIf { it.isNotBlank() && it != "null" }
                    if (descText != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardFill)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { descriptionExpanded = !descriptionExpanded }
                                .padding(20.dp)
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                ),
                        ) {
                            Text(
                                text = if (language == AppLanguage.RU) "Описание" else "Description",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = SnProFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = onCardMuted,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = descText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = SnProFamily,
                                    lineHeight = 22.sp,
                                    lineBreak = LineBreak.Paragraph,
                                    hyphens = Hyphens.Auto,
                                ),
                                color = onCard,
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!descriptionExpanded) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (language == AppLanguage.RU) {
                                        "Нажмите, чтобы развернуть"
                                    } else {
                                        "Tap to expand"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = SnProFamily,
                                    ),
                                    color = onCardMuted.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
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
