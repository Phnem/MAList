package com.example.myapplication.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.ui.shared.inertialCollision
import com.example.myapplication.ui.shared.rememberInertialCollisionState
import com.example.myapplication.ui.shared.theme.DarkBackground
import com.example.myapplication.ui.shared.theme.OverlayGlassPanel
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.glassEdge
import com.example.myapplication.ui.shared.theme.glassFill
import java.util.Locale

// ==========================================
// StatsOverlay — стеклянный попап статистики (Спринт 2 редизайна)
// ==========================================

@Composable
fun StatsOverlay(
    animeList: List<Anime>,
    strings: UiStrings,
    appLanguage: AppLanguage,
    footerPhrase: String,
    onDismiss: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    fun triggerDismiss() {
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            kotlinx.coroutines.delay(250)
            onDismiss()
        }
    }

    BackHandler { triggerDismiss() }

    val totalAnime = animeList.size
    val avgRating = if (animeList.isNotEmpty()) animeList.map { it.rating }.average() else 0.0
    val totalEpisodes = animeList.sumOf { it.episodes }
    val favorites = animeList.count { it.isFavorite }
    val ratingFormatted = String.format(Locale.getDefault(), "%.1f", avgRating)

    val collisionState = rememberInertialCollisionState()
    LaunchedEffect(visible) {
        if (visible) {
            collisionState.triggerCollision(impactForce = 55f, stiffness = 180f, dampingRatio = 0.5f)
        }
    }

    val panelBg = if (isDark) DarkBackground else MaterialTheme.colorScheme.surface
    val panelCorner = 28.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { triggerDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
            ) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(panelCorner),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                OverlayGlassPanel(
                    isDark = isDark,
                    panelBg = panelBg,
                    cornerRadius = panelCorner,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatsContent(
                        animeList = animeList,
                        strings = strings,
                        appLanguage = appLanguage,
                        isDark = isDark,
                        totalAnime = totalAnime,
                        ratingFormatted = ratingFormatted,
                        totalEpisodes = totalEpisodes,
                        favorites = favorites,
                        footerPhrase = footerPhrase,
                        collisionState = collisionState,
                        onDismiss = ::triggerDismiss
                    )
                }
            }
        }
    }
}

/** Декомпозированный «контент» — заголовок, плитки, CTA. Хранится здесь, не в glass.kt/nottif.kt. */
@Composable
private fun StatsContent(
    animeList: List<Anime>,
    strings: UiStrings,
    appLanguage: AppLanguage,
    isDark: Boolean,
    totalAnime: Int,
    ratingFormatted: String,
    totalEpisodes: Int,
    favorites: Int,
    footerPhrase: String,
    collisionState: com.example.myapplication.ui.shared.InertialCollisionState,
    onDismiss: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .inertialCollision(state = collisionState, index = 0, baseMultiplier = 3f)
        ) {
            Text(
                text = strings.statsTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = SnProFamily,
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .inertialCollision(state = collisionState, index = 1, baseMultiplier = 3f)
        ) {
            Text(
                text = strings.statsSubtitle,
                style = OverlayThemeTokens.SectionLabel,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = OverlayThemeTokens.LabelMutedAlpha)
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .inertialCollision(state = collisionState, index = 2, baseMultiplier = 3f)
        ) {
            StatsChartsRow(
                animeList = animeList,
                strings = strings,
                appLanguage = appLanguage
            )
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .inertialCollision(state = collisionState, index = 3, baseMultiplier = 3f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsMetricTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        value = totalAnime.toString(),
                        label = strings.statsTotal,
                        accent = OverlayThemeTokens.AccentNeonBlue,
                        icon = Icons.Default.Visibility,
                        isDark = isDark
                    )
                    StatsMetricTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        value = ratingFormatted,
                        label = strings.avgRating,
                        accent = OverlayThemeTokens.AccentNeonYellow,
                        icon = Icons.Rounded.Star,
                        isDark = isDark
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsMetricTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        value = totalEpisodes.toString(),
                        label = strings.episodesWatched,
                        accent = OverlayThemeTokens.AccentNeonPurple,
                        icon = Icons.Default.Layers,
                        isDark = isDark
                    )
                    StatsMetricTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        value = favorites.toString(),
                        label = strings.favorites,
                        accent = OverlayThemeTokens.AccentNeonPink,
                        icon = Icons.Default.Favorite,
                        isDark = isDark
                    )
                }
            }
        }

        if (footerPhrase.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .inertialCollision(state = collisionState, index = 4, baseMultiplier = 3f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = footerPhrase,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SnProFamily,
                        lineBreak = LineBreak.Paragraph,
                        hyphens = Hyphens.Auto
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = OverlayThemeTokens.LabelMutedAlpha),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .inertialCollision(state = collisionState, index = 5, baseMultiplier = 3f),
            contentAlignment = Alignment.Center
        ) {
            StatsDoneButton(
                label = strings.statsOk,
                isDark = isDark,
                onClick = onDismiss
            )
        }
    }
}

/** Готовая «глянцевая» CTA-кнопка статистики: pill + edge-highlight + accent. */
@Composable
private fun StatsDoneButton(
    label: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isDark) {
        OverlayThemeTokens.AccentNeonBlue
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isDark) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .glassEdge(cornerRadius = 26.dp, isDark = isDark)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            )
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontFamily = SnProFamily,
                fontSize = 16.sp,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Стеклянная плитка-метрика: иконка + caps-label сверху, крупная цифра внизу.
 * Объёмность создаётся за счёт fill, rim и edge-highlight, а не толстого accent-border.
 */
@Composable
private fun StatsMetricTile(
    value: String,
    label: String,
    accent: Color,
    icon: ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val tileBg = if (isDark) {
        OverlayThemeTokens.TileBackgroundDark
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val labelMuted = if (isDark) {
        OverlayThemeTokens.LabelMutedDark
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tileCorner = OverlayThemeTokens.TileCornerRadius
    val shape = RoundedCornerShape(tileCorner)
    val accentRim = accent.copy(alpha = if (isDark) 0.35f else 0.55f)
    val accentGlow = accent.copy(alpha = if (isDark) 0.18f else 0.16f)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 116.dp)
            .clip(shape)
            .background(tileBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(accentGlow, Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 320f
                )
            )
            .glassFill(isDark)
            .glassEdge(tileCorner, isDark)
            .border(1.dp, accentRim, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDark) OverlayThemeTokens.TileIconBgDark
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = OverlayThemeTokens.MetricLabel.copy(
                    lineBreak = LineBreak.Heading,
                    hyphens = Hyphens.None
                ),
                color = labelMuted,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = OverlayThemeTokens.MetricValue,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
