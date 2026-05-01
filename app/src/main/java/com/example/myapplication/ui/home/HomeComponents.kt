package com.example.myapplication.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.isAppInDarkTheme
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
    val isDark = isAppInDarkTheme()
    val onCard = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val muted = if (isDark) {
        OverlayThemeTokens.LabelMutedDark.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val confirmButtonContainer = when (state.confirmMode) {
        AnimeMenuConfirmMode.DELETE -> OverlayThemeTokens.AccentNeonRed
        AnimeMenuConfirmMode.ADD_TO_FAVORITE -> OverlayThemeTokens.FavoriteConfirmGold
    }
    val confirmButtonContent = when (state.confirmMode) {
        AnimeMenuConfirmMode.DELETE -> Color.White
        AnimeMenuConfirmMode.ADD_TO_FAVORITE -> OverlayThemeTokens.OnFavoriteConfirmGold
    }
    val confirmLabel = when (state.confirmMode) {
        AnimeMenuConfirmMode.DELETE -> strings.deleteConfirm
        AnimeMenuConfirmMode.ADD_TO_FAVORITE -> strings.addButton
    }
    val confirmIcon = when (state.confirmMode) {
        AnimeMenuConfirmMode.DELETE -> Icons.Default.Delete
        AnimeMenuConfirmMode.ADD_TO_FAVORITE -> Icons.Rounded.Star
    }

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
                    .width(114.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = onCard,
                    lineHeight = 34.sp
                )
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontFamily = SnProFamily
                    ),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    color = muted
                )
            }
        }

        HorizontalDivider(
            color = if (isDark) OverlayThemeTokens.RimDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )

        // --- БЛОК 2: ДВЕ КНОПКИ — подтверждение + отмена (только обводка) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onEvent(AnimeMenuEvent.OnConfirm) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(100),
                colors = ButtonDefaults.buttonColors(
                    containerColor = confirmButtonContainer,
                    contentColor = confirmButtonContent
                )
            ) {
                Icon(confirmIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { onEvent(AnimeMenuEvent.OnCancel) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(100),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = onCard
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
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
    val menuState = remember(anime.id, confirmMode, strings.menuDeleteSnark, strings.menuAddFavoriteSnark) {
        AnimeMenuState(
            title = anime.title,
            imageUrl = getImgPath(anime.imageFileName) ?: "",
            statusText = when (confirmMode) {
                AnimeMenuConfirmMode.DELETE -> strings.menuDeleteSnark
                AnimeMenuConfirmMode.ADD_TO_FAVORITE -> strings.menuAddFavoriteSnark
            },
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
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                OverlayGlassPanel(
                    isDark = isDark,
                    panelBg = panelBg,
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    when (confirmMode) {
                                        AnimeMenuConfirmMode.DELETE -> OverlayThemeTokens.AccentNeonRed.copy(alpha = if (isDark) 0.14f else 0.1f)
                                        AnimeMenuConfirmMode.ADD_TO_FAVORITE -> OverlayThemeTokens.FavoriteConfirmGold.copy(alpha = if (isDark) 0.14f else 0.1f)
                                    },
                                    Color.Transparent
                                ),
                                center = Offset(0f, 0f),
                                radius = 540f
                            )
                        )
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

// StatsOverlay вынесён в StatsOverlay.kt (пакет com.example.myapplication.ui.home).
