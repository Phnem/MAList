package com.example.myapplication.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Locale
import coil3.compose.AsyncImage
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.ui.settings.tileGlow
import com.example.myapplication.ui.shared.inertialCollision
import com.example.myapplication.ui.shared.rememberInertialCollisionState
import com.example.myapplication.ui.shared.theme.*
import androidx.compose.ui.graphics.graphicsLayer

// ==========================================
// AnimeListActionMenu — Material 3 bottom sheet content (UDF)
// ==========================================

/** Режим подтверждения: удаление или добавление в избранное. */
enum class AnimeMenuConfirmMode { DELETE, ADD_TO_FAVORITE }

/** MVI State for the anime list action menu. */
data class AnimeMenuState(
    val title: String,
    val imageUrl: String,
    val statusText: String,
    val confirmMode: AnimeMenuConfirmMode
)

/** Isolated events (Sealed Interface — Kotlin 2.x). */
sealed interface AnimeMenuEvent {
    data object OnConfirm : AnimeMenuEvent
    data object OnCancel : AnimeMenuEvent
}

@Composable
fun AnimeListActionMenu(
    state: AnimeMenuState,
    strings: UiStrings,
    onEvent: (AnimeMenuEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- БЛОК 1: УВЕЛИЧЕННОЕ ПРЕВЬЮ И ИНФО ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = state.imageUrl,
                contentDescription = "Anime Poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.medium)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // --- БЛОК 2: ДВЕ КНОПКИ — подтверждение + отмена (только обводка) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state.confirmMode) {
                AnimeMenuConfirmMode.DELETE -> {
                    Button(
                        onClick = { onEvent(AnimeMenuEvent.OnConfirm) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.deleteConfirm)
                    }
                }
                AnimeMenuConfirmMode.ADD_TO_FAVORITE -> {
                    Button(
                        onClick = { onEvent(AnimeMenuEvent.OnConfirm) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RateColor4)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.addButton)
                    }
                }
            }
            OutlinedButton(
                onClick = { onEvent(AnimeMenuEvent.OnCancel) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(strings.cancel)
            }
        }
    }
}

/** Bottom sheet wrapper for [AnimeListActionMenu] — overlay, card, spring animation, dismiss. */
@Composable
fun AnimeListMenuSheet(
    anime: Anime,
    confirmMode: AnimeMenuConfirmMode,
    strings: UiStrings,
    getImgPath: (String?) -> String?,
    onEvent: (AnimeMenuEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    fun dismiss() {
        visible = false
    }

    LaunchedEffect(visible) {
        if (!visible) {
            kotlinx.coroutines.delay(250)
            onDismiss()
        }
    }

    BackHandler { dismiss() }

    val isDark = isAppInDarkTheme()
    val panelBg = if (isDark) DarkSurface else MaterialTheme.colorScheme.surface
    val menuState = remember(anime, confirmMode) {
        AnimeMenuState(
            title = anime.title,
            imageUrl = getImgPath(anime.imageFileName) ?: "",
            statusText = if (anime.rating > 0) "${anime.rating}/10" else "${anime.episodes} ${strings.episodesShort}",
            confirmMode = confirmMode
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().zIndex(10f),
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
                    ) { dismiss() }
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
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
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = panelBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                AnimeListActionMenu(
                    state = menuState,
                    strings = strings,
                    onEvent = { event ->
                        onEvent(event)
                        dismiss()
                    }
                )
            }
        }
    }
}

// ==========================================
// MalistWorkspaceTopBar — заголовок (кнопки сортировки/уведомлений вынесены в HomeScreen поверх scrim)
// ==========================================
@Composable
fun MalistWorkspaceTopBar(strings: UiStrings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Vetro",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Black,
                fontFamily = SnProFamily,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = strings.statsSubtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = SnProFamily
            ),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

// ==========================================
// EmptyStateView — gentle, spacious
// ==========================================
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(48.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = SnProFamily
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SnProFamily
                    ),
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==========================================
// CloudRestoreIndicator — восстановление из облака
// ==========================================
@Composable
fun CloudRestoreIndicator(
    isRestoring: Boolean,
    strings: UiStrings,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRestoring,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        ElevatedCard(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(0.85f),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "SyncTransition")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "SyncRotation"
                )
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Restoring from cloud",
                    modifier = Modifier
                        .size(48.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = strings.cloudRestoreTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SnProFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.cloudRestoreSubtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SnProFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

// ==========================================
// SwipeBackground
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeBackground(dismissState: SwipeToDismissBoxState) {
    // Используем dismissDirection вместо targetValue — показывает реальное направление свайпа.
    val direction = dismissState.dismissDirection

    val color by animateColorAsState(
        targetValue = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> RateColor3.copy(alpha = 0.2f)
            SwipeToDismissBoxValue.EndToStart -> BrandRed.copy(alpha = 0.2f)
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "swipeBg"
    )

    val icon = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.Star
        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
        SwipeToDismissBoxValue.Settled -> Icons.Rounded.Star
    }

    val alignment = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            val scale by animateFloatAsState(
                targetValue = if (dismissState.progress > 0.05f) 1f else 0.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "iconScale"
            )

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

// ==========================================
// StatsOverlay — cleaner layout
// ==========================================
@Composable
fun StatsOverlay(
    animeList: List<Anime>,
    strings: UiStrings,
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
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = panelBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
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
                                fontFamily = SnProFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .inertialCollision(state = collisionState, index = 1, baseMultiplier = 3f)
                    ) {
                        Text(
                            text = strings.statsSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SnProFamily
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .inertialCollision(state = collisionState, index = 2, baseMultiplier = 3f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatsMetricTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    value = totalAnime.toString(),
                                    label = strings.statsTotal,
                                    accent = BrandBlue,
                                    icon = Icons.Default.Visibility,
                                    isDark = isDark
                                )
                                StatsMetricTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    value = ratingFormatted,
                                    label = strings.avgRating,
                                    accent = RatingColor,
                                    icon = Icons.Rounded.Star,
                                    isDark = isDark
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatsMetricTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    value = totalEpisodes.toString(),
                                    label = strings.episodesWatched,
                                    accent = EpisodesColor,
                                    icon = Icons.Default.Layers,
                                    isDark = isDark
                                )
                                StatsMetricTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    value = favorites.toString(),
                                    label = strings.favorites,
                                    accent = BrandRed,
                                    icon = Icons.Default.Favorite,
                                    isDark = isDark
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .inertialCollision(state = collisionState, index = 3, baseMultiplier = 3f),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { triggerDismiss() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OverlayThemeTokens.IconSyncBlue,
                                contentColor = Color.Black
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp
                            )
                        ) {
                            Text(
                                text = strings.statsOk,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SnProFamily,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsMetricTile(
    value: String,
    label: String,
    accent: Color,
    icon: ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val tileBg =
        if (isDark) OverlayThemeTokens.TileBackgroundDark
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val glowAlpha = if (isDark) 0.15f else 0.11f
    val labelMuted =
        if (isDark) OverlayThemeTokens.LabelMutedDark
        else MaterialTheme.colorScheme.onSurfaceVariant
    val rim =
        if (isDark) OverlayThemeTokens.RimDark
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val barColor = lerp(accent, Color.Black, 0.45f)
    val shape = RoundedCornerShape(OverlayThemeTokens.TileCornerRadius)

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 118.dp)
            .clip(shape)
            .background(tileBg)
            .tileGlow(accent, glowAlpha)
            .border(1.dp, rim, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.4.sp,
                    fontFamily = SnProFamily,
                    fontWeight = FontWeight.SemiBold,
                    lineBreak = LineBreak.Heading,
                    hyphens = Hyphens.None
                ),
                color = labelMuted,
                maxLines = 3,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = SnProFamily
            ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
