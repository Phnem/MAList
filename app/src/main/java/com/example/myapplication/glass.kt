package com.example.myapplication

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.phnem.vetro.R
import com.example.myapplication.data.models.AnimeUpdate
import com.example.myapplication.data.models.SortOption
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.home.WorkspaceSortNotificationActions
import com.example.myapplication.ui.navigation.navigateToAddEdit
import com.example.myapplication.ui.navigation.navigateToSettings
import com.example.myapplication.ui.shared.GlassPreset
import com.example.myapplication.ui.shared.adaptiveGlassBackdrop
import com.example.myapplication.ui.shared.rememberAdaptiveGlassEffects
import com.example.myapplication.ui.shared.components.GenreFilterPillSelection
import com.example.myapplication.ui.shared.components.GlassIconButton
import com.example.myapplication.ui.shared.fluidClickable
import com.example.myapplication.ui.shared.gradientHighlightBorder
import com.example.myapplication.ui.shared.theme.BrandBlue
import com.example.myapplication.ui.shared.theme.DarkBackground
import com.example.myapplication.ui.shared.theme.OverlayGlassPanel
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.glassEdge
import com.example.myapplication.ui.shared.theme.glassFill
import com.example.myapplication.ui.shared.theme.softPlateShadowForLightSheet
import com.example.myapplication.ui.shared.inertialCollision
import com.example.myapplication.ui.shared.rememberInertialCollisionState
import com.example.myapplication.utils.performHaptic
import com.kyant.backdrop.Backdrop

@Composable
fun isAppInDarkTheme(): Boolean {
    return MaterialTheme.colorScheme.background.toArgb() == DarkBackground.toArgb()
}

// ==========================================
// Simple glass card ("simp glass" style)
// ==========================================
@Composable
fun SimpGlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isAppInDarkTheme()
    val glassEffects = rememberAdaptiveGlassEffects(GlassPreset.Card)
    val shineColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.6f)
    val borderStroke = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .clip(shape)
            .adaptiveGlassBackdrop(backdrop = backdrop, shape = shape, effects = glassEffects)
            .border(0.5.dp, borderStroke, shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path()
            when (outline) {
                is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                is Outline.Generic -> path.addPath(outline.path)
                is Outline.Rectangle -> path.addRect(outline.rect)
            }
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        shineColor,
                        Color.Transparent,
                        Color.Transparent,
                        shineColor.copy(alpha = 0.1f)
                    )
                ),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        content()
    }
}

// ==========================================
// GlassActionDock
// ==========================================
@Composable
fun GlassActionDock(
    backdrop: Backdrop,
    isFloating: Boolean,
    strings: UiStrings,
    filterSelectedTags: List<String>,
    updates: List<AnimeUpdate>,
    onOpenSort: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMediaTypeFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val glassEffects = rememberAdaptiveGlassEffects(GlassPreset.Card)
    val dockShape = RoundedCornerShape(32.dp)
    val topPadding by animateDpAsState(
        targetValue = if (isFloating) 16.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "dockPadding"
    )
    val borderStrokeBase = if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)
    val borderColor by animateColorAsState(
        targetValue = if (isFloating) borderStrokeBase else Color.Transparent,
        label = "border"
    )
    val shineColorBase = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.6f)
    val shineAlpha by animateFloatAsState(
        targetValue = if (isFloating) 1f else 0f,
        label = "shineAlpha"
    )
    val buttonBgColor by animateColorAsState(
        targetValue = if (isFloating) Color.Transparent else
            if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else Color.Black.copy(alpha = 0.04f),
        label = "btnBg"
    )

    AnimatedVisibility(
        visible = isFloating,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        modifier = modifier
            .padding(top = topPadding)
            .statusBarsPadding()
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(dockShape)
                    .adaptiveGlassBackdrop(backdrop = backdrop, shape = dockShape, effects = glassEffects)
                    .border(0.5.dp, borderColor, dockShape)
            ) {
                if (shineAlpha > 0f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val rect = Rect(offset = Offset.Zero, size = size)
                        val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(32.dp.toPx()))) }
                        drawPath(
                            path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    shineColorBase.copy(alpha = shineColorBase.alpha * shineAlpha),
                                    Color.Transparent,
                                    Color.Transparent,
                                    shineColorBase.copy(alpha = 0.05f * shineAlpha)
                                )
                            ),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }

            WorkspaceSortNotificationActions(
                strings = strings,
                filterSelectedTags = filterSelectedTags,
                updatesCount = updates.size,
                onOpenSort = onOpenSort,
                onOpenNotifications = onOpenNotifications,
                onOpenMediaTypeFilter = onOpenMediaTypeFilter,
                dockButtonBackground = buttonBgColor,
                useDockSizing = true,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

// ==========================================
// ПАНЕЛЬ НАВИГАЦИИ (Compact Style)
// ==========================================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun GlassBottomNavigation(
    backdrop: Backdrop,
    nav: androidx.navigation.NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onShowStats: () -> Unit,
    onShowNotifs: () -> Unit,
    onInspectClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isSearchActive: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = isAppInDarkTheme()
    val glassEffects = rememberAdaptiveGlassEffects(GlassPreset.CompactNav)
    val navShape = RoundedCornerShape(32.dp)
    val currentThemeColor = MaterialTheme.colorScheme.onSurface
    val borderStroke = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .padding(bottom = 24.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(end = 48.dp)
                .height(64.dp)
                .wrapContentWidth()
                .clip(navShape)
                .adaptiveGlassBackdrop(backdrop = backdrop, shape = navShape, effects = glassEffects)
                .border(0.5.dp, borderStroke, navShape)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .fluidClickable {
                                performHaptic(view, "light")
                                onInspectClick()
                            }
                    ) {
                        with(sharedTransitionScope) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .sharedBounds(
                                        rememberSharedContentState(key = "inspect_container"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                                    )
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.frame_inspect_24),
                                    contentDescription = "Scene search",
                                    tint = currentThemeColor.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .sharedElement(
                                            rememberSharedContentState(key = "inspect_icon"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                performHaptic(view, "light")
                                onShowStats()
                            }
                    ) {
                        Icon(
                            imageVector = HeroiconsSquaresPlus,
                            contentDescription = "Stats",
                            tint = currentThemeColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                performHaptic(view, "success")
                                nav.navigateToAddEdit()
                            }
                    ) {
                        with(sharedTransitionScope) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .sharedBounds(
                                        rememberSharedContentState(key = "fab_container"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        clipInOverlayDuringTransition = OverlayClip(CircleShape)
                                    )
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = HeroiconsPlus,
                                    contentDescription = "Add",
                                    tint = currentThemeColor.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .sharedElement(
                                            rememberSharedContentState(key = "fab_icon"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .fluidClickable {
                                performHaptic(view, "light")
                                onSettingsClick()
                            }
                    ) {
                        with(sharedTransitionScope) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .sharedBounds(
                                        rememberSharedContentState(key = "settings_container"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
                                    )
                                    .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = currentThemeColor.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .sharedElement(
                                            rememberSharedContentState(key = "settings_icon"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        GlassIconButton(
            icon = Icons.Default.Search,
            onClick = {
                performHaptic(view, "light")
                onSearchClick()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp),
            size = 64.dp,
            iconSize = 32.dp,
            backdrop = backdrop,
            backgroundColor = Color.Transparent,
            contentDescription = "Search",
            tint = if (isSearchActive) BrandBlue else currentThemeColor
        )
    }
}

// ==========================================
// Built-in vector icons (Heroicons-style paths)
// ==========================================

private var heroiconsPlusCached: ImageVector? = null
val HeroiconsPlus: ImageVector
    get() {
        if (heroiconsPlusCached != null) return heroiconsPlusCached!!
        heroiconsPlusCached = ImageVector.Builder(
            name = "plus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 20f,
            viewportHeight = 20f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.75f, 4.75f)
                arcToRelative(0.75f, 0.75f, 0f, false, false, -1.5f, 0f)
                verticalLineToRelative(4.5f)
                horizontalLineToRelative(-4.5f)
                arcToRelative(0.75f, 0.75f, 0f, false, false, 0f, 1.5f)
                horizontalLineToRelative(4.5f)
                verticalLineToRelative(4.5f)
                arcToRelative(0.75f, 0.75f, 0f, false, false, 1.5f, 0f)
                verticalLineToRelative(-4.5f)
                horizontalLineToRelative(4.5f)
                arcToRelative(0.75f, 0.75f, 0f, false, false, 0f, -1.5f)
                horizontalLineToRelative(-4.5f)
                verticalLineToRelative(-4.5f)
                close()
            }
        }.build()
        return heroiconsPlusCached!!
    }

private var heroiconsSquaresPlusCached: ImageVector? = null
val HeroiconsSquaresPlus: ImageVector
    get() {
        if (heroiconsSquaresPlusCached != null) return heroiconsSquaresPlusCached!!
        heroiconsSquaresPlusCached = ImageVector.Builder(
            name = "squares-plus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(13.5f, 16.875f)
                horizontalLineToRelative(3.375f)
                moveToRelative(0f, 0f)
                horizontalLineToRelative(3.375f)
                moveToRelative(-3.375f, 0f)
                verticalLineTo(13.5f)
                moveToRelative(0f, 3.375f)
                verticalLineToRelative(3.375f)
                moveTo(6f, 10.5f)
                horizontalLineToRelative(2.25f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, -2.25f)
                verticalLineTo(6f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, -2.25f)
                horizontalLineTo(6f)
                arcTo(2.25f, 2.25f, 0f, false, false, 3.75f, 6f)
                verticalLineToRelative(2.25f)
                arcTo(2.25f, 2.25f, 0f, false, false, 6f, 10.5f)
                close()
                moveToRelative(0f, 9.75f)
                horizontalLineToRelative(2.25f)
                arcTo(2.25f, 2.25f, 0f, false, false, 10.5f, 18f)
                verticalLineToRelative(-2.25f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, -2.25f)
                horizontalLineTo(6f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, -2.25f, 2.25f)
                verticalLineTo(18f)
                arcTo(2.25f, 2.25f, 0f, false, false, 6f, 20.25f)
                close()
                moveToRelative(9.75f, -9.75f)
                horizontalLineTo(18f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, -2.25f)
                verticalLineTo(6f)
                arcTo(2.25f, 2.25f, 0f, false, false, 18f, 3.75f)
                horizontalLineToRelative(-2.25f)
                arcTo(2.25f, 2.25f, 0f, false, false, 13.5f, 6f)
                verticalLineToRelative(2.25f)
                arcToRelative(2.25f, 2.25f, 0f, false, false, 2.25f, 2.25f)
                close()
            }
        }.build()
        return heroiconsSquaresPlusCached!!
    }

private var heroiconsRectangleStackCached: ImageVector? = null
val HeroiconsRectangleStack: ImageVector
    get() {
        if (heroiconsRectangleStackCached != null) return heroiconsRectangleStackCached!!
        heroiconsRectangleStackCached = ImageVector.Builder(
            name = "rectangle-stack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(6f, 6.878f)
                verticalLineTo(6f)
                arcToRelative(2.25f, 2.25f, 0f, false, true, 2.25f, -2.25f)
                horizontalLineToRelative(7.5f)
                arcTo(2.25f, 2.25f, 0f, false, true, 18f, 6f)
                verticalLineToRelative(0.878f)
                moveToRelative(-12f, 0f)
                curveToRelative(0.235f, -0.083f, 0.487f, -0.128f, 0.75f, -0.128f)
                horizontalLineToRelative(10.5f)
                curveToRelative(0.263f, 0f, 0.515f, 0.045f, 0.75f, 0.128f)
                moveToRelative(-12f, 0f)
                arcTo(2.25f, 2.25f, 0f, false, false, 4.5f, 9f)
                verticalLineToRelative(0.878f)
                moveToRelative(13.5f, -3f)
                arcTo(2.25f, 2.25f, 0f, false, true, 19.5f, 9f)
                verticalLineToRelative(0.878f)
                moveToRelative(0f, 0f)
                arcToRelative(2.246f, 2.246f, 0f, false, false, -0.75f, -0.128f)
                horizontalLineTo(5.25f)
                curveToRelative(-0.263f, 0f, -0.515f, 0.045f, -0.75f, 0.128f)
                moveToRelative(15f, 0f)
                arcTo(2.25f, 2.25f, 0f, false, true, 21f, 12f)
                verticalLineToRelative(6f)
                arcToRelative(2.25f, 2.25f, 0f, false, true, -2.25f, 2.25f)
                horizontalLineTo(5.25f)
                arcTo(2.25f, 2.25f, 0f, false, true, 3f, 18f)
                verticalLineToRelative(-6f)
                curveToRelative(0f, -0.98f, 0.626f, -1.813f, 1.5f, -2.122f)
            }
        }.build()
        return heroiconsRectangleStackCached!!
    }

// ==========================================
// ЦВЕТА И РАСШИРЕНИЯ ДЛЯ СТАРОГО ДИЗАЙНА
// ==========================================
private val IconFilterColor = Color(0xFFE91E63)

private sealed interface SortGridSelection {
    data class Sort(val option: SortOption, val isAscending: Boolean) : SortGridSelection
    data object Genres : SortGridSelection
}

fun SortOption.getIcon(): ImageVector = when (this) {
    SortOption.RATING -> Icons.Rounded.Star
    SortOption.EPISODES -> Icons.Outlined.Tv
    SortOption.TITLE -> Icons.AutoMirrored.Filled.Sort
}

fun SortOption.getAccentColor(): Color = when (this) {
    SortOption.RATING -> Color(0xFFFFD60A)
    SortOption.EPISODES -> Color(0xFF3E82F7)
    SortOption.TITLE -> Color(0xFF5E5CE6)
}

// ==========================================
// SORT & FILTER OVERLAYS
// ==========================================

@Composable
fun SortFilterOverlay(
    visibleState: MutableTransitionState<Boolean>,
    strings: UiStrings,
    sortOption: SortOption,
    sortAscending: Boolean = false,
    filterSelectedTags: List<String>,
    onDismiss: () -> Unit,
    onApplySort: (SortOption, Boolean) -> Unit,
    onApplyOpenGenreFilter: () -> Unit
) {
    val view = LocalView.current
    val isDark = isAppInDarkTheme()
    val panelBg = if (isDark) DarkBackground else MaterialTheme.colorScheme.surface
    val rim = if (isDark) OverlayThemeTokens.RimDark else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val muted = if (isDark) OverlayThemeTokens.LabelMutedDark else MaterialTheme.colorScheme.onSurfaceVariant
    val onCard = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val cardBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val isOpening = visibleState.targetState
    var draftSelection by remember(sortOption, sortAscending, isOpening) {
        mutableStateOf<SortGridSelection>(SortGridSelection.Sort(sortOption, sortAscending))
    }

    val updateSelection: (SortGridSelection) -> Unit = {
        performHaptic(view, "light")
        draftSelection = it
    }

    BackHandler { onDismiss() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = OverlayThemeTokens.ScrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = scaleOut(
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = OverlayThemeTokens.PanelPaddingTop, end = OverlayThemeTokens.PanelPaddingEnd)
                    .width(OverlayThemeTokens.PanelWidth)
                    .wrapContentHeight()
            ) {
                Card(
                    shape = RoundedCornerShape(OverlayThemeTokens.PanelCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDark) OverlayThemeTokens.CardElevation else 0.dp
                    ),
                    modifier = Modifier
                        .padding(
                            top = OverlayThemeTokens.CardOuterPaddingTop,
                            end = OverlayThemeTokens.CardOuterPaddingEnd
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { /* поглощаем тап, без семантики clickable */ }
                        }
                ) {
                    OverlayGlassPanel(
                        isDark = isDark,
                        panelBg = panelBg,
                        cornerRadius = OverlayThemeTokens.PanelCornerRadius,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                    Column(
                        modifier = Modifier
                            .padding(
                                start = OverlayThemeTokens.PanelInnerPaddingStart,
                                top = OverlayThemeTokens.PanelInnerPaddingTop,
                                end = OverlayThemeTokens.PanelInnerPaddingEnd
                            )
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.sortSheetTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onCard,
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.2).sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = strings.sortSheetSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = muted
                                )
                            }
                            IconButton(
                                onClick = {
                                    performHaptic(view, "light")
                                    onDismiss()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = strings.nottifCloseCd,
                                    tint = muted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        val activeFiltersCount = filterSelectedTags.size

                        Column(verticalArrangement = Arrangement.spacedBy(OverlayThemeTokens.GridSpacing)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(OverlayThemeTokens.GridSpacing)
                            ) {
                                SortSortTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    option = SortOption.RATING,
                                    title = strings.ratingHigh,
                                    subtitle = strings.sortSubtitleRating,
                                    icon = SortOption.RATING.getIcon(),
                                    accentColor = SortOption.RATING.getAccentColor(),
                                    selection = draftSelection,
                                    strings = strings,
                                    isDark = isDark,
                                    rim = rim,
                                    cardBg = cardBg,
                                    muted = muted,
                                    onSelect = updateSelection
                                )
                                SortSortTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    option = SortOption.EPISODES,
                                    title = strings.sortTileEpisodesTitle,
                                    subtitle = strings.sortSubtitleEpisodes,
                                    icon = SortOption.EPISODES.getIcon(),
                                    accentColor = SortOption.EPISODES.getAccentColor(),
                                    selection = draftSelection,
                                    strings = strings,
                                    isDark = isDark,
                                    rim = rim,
                                    cardBg = cardBg,
                                    muted = muted,
                                    onSelect = updateSelection
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(OverlayThemeTokens.GridSpacing)
                            ) {
                                SortSortTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    option = SortOption.TITLE,
                                    title = strings.titleAZ,
                                    subtitle = strings.sortSubtitleTitle,
                                    icon = SortOption.TITLE.getIcon(),
                                    accentColor = SortOption.TITLE.getAccentColor(),
                                    selection = draftSelection,
                                    strings = strings,
                                    isDark = isDark,
                                    rim = rim,
                                    cardBg = cardBg,
                                    muted = muted,
                                    onSelect = updateSelection
                                )
                                GenreSortTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    title = strings.filterByGenre,
                                    subtitle = strings.sortSubtitleGenres,
                                    icon = Icons.Outlined.FilterList,
                                    selection = draftSelection,
                                    isDark = isDark,
                                    rim = rim,
                                    cardBg = cardBg,
                                    muted = muted,
                                    badgeCount = activeFiltersCount,
                                    onSelect = updateSelection
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        SortApplyButton(
                            isDark = isDark,
                            label = strings.sortApply,
                            onClick = {
                                when (val d = draftSelection) {
                                    SortGridSelection.Genres -> {
                                        onApplyOpenGenreFilter()
                                        onDismiss()
                                    }
                                    is SortGridSelection.Sort -> {
                                        if (d.option == sortOption && d.isAscending == sortAscending) {
                                            onDismiss()
                                        } else {
                                            onApplySort(d.option, d.isAscending)
                                            onDismiss()
                                        }
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(OverlayThemeTokens.PanelInnerPaddingBottom))
                    }
                    } // OverlayGlassPanel
                }
            }
        }
    }
}

/**
 * Кнопка применения для SortFilterOverlay. Вынесена из тела [SortFilterOverlay],
 * чтобы декомпозировать большой composable и не превращать glass.kt в God Object.
 */
@Composable
private fun SortApplyButton(
    isDark: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(OverlayThemeTokens.ApplyButtonCornerRadius),
        colors = if (isDark) {
            ButtonDefaults.buttonColors(
                containerColor = OverlayThemeTokens.ApplyButtonContainerDark,
                contentColor = OverlayThemeTokens.ApplyButtonLabelSoft
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
            )
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun SortSortTile(
    option: SortOption,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selection: SortGridSelection,
    strings: UiStrings,
    isDark: Boolean,
    rim: Color,
    cardBg: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    onSelect: (SortGridSelection) -> Unit
) {
    val isActive = selection is SortGridSelection.Sort && selection.option == option
    val isAscending =
        if (selection is SortGridSelection.Sort) selection.isAscending else true

    val scheme = MaterialTheme.colorScheme
    val mutedIconTint = scheme.onSurfaceVariant.copy(alpha = 0.42f)
    val targetIconTint = if (isActive) accentColor else mutedIconTint
    val animatedIconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sortIconTint"
    )
    val targetBorderColor = if (isActive) accentColor else rim
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sortTileBorder"
    )

    val shape = RoundedCornerShape(OverlayThemeTokens.TileCornerRadius)
    val tileBg = if (isDark) OverlayThemeTokens.TileBackgroundDark else cardBg
    val glowAlpha = if (isActive) {
        if (isDark) 0.22f else OverlayThemeTokens.TileGlowAlphaLight
    } else 0f
    val accentGlow = accentColor.copy(alpha = glowAlpha)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = OverlayThemeTokens.SortTileMinHeight)
            .then(
                if (isDark) Modifier else Modifier.softPlateShadowForLightSheet(
                    isDark = false,
                    shape = shape,
                    elevation = OverlayThemeTokens.SortOverlayGridLightShadowElevation,
                )
            )
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
            .glassEdge(OverlayThemeTokens.TileCornerRadius, isDark)
            .border(1.dp, animatedBorderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onSelect(
                    if (isActive) {
                        SortGridSelection.Sort(option, !isAscending)
                    } else {
                        SortGridSelection.Sort(option, isAscending = false)
                    }
                )
            }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(OverlayThemeTokens.IconBoxCorner))
                    .background(
                        if (isDark) OverlayThemeTokens.TileIconBgDark
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) {
                        if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    } else {
                        muted
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Text(
                        text = if (isAscending) strings.sortOrderAscending else strings.sortOrderDescending,
                        style = OverlayThemeTokens.MetricLabel,
                        color = if (isDark) {
                            Color.White.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = subtitle,
                        style = OverlayThemeTokens.MetricLabel,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreSortTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selection: SortGridSelection,
    isDark: Boolean,
    rim: Color,
    cardBg: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    onSelect: (SortGridSelection) -> Unit
) {
    val isActive = selection == SortGridSelection.Genres
    val genreAccent = if (isDark) IconFilterColor else MaterialTheme.colorScheme.primary

    val scheme = MaterialTheme.colorScheme
    val mutedIconTint = scheme.onSurfaceVariant.copy(alpha = 0.42f)
    val targetIconTint = if (isActive) genreAccent else mutedIconTint
    val animatedIconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "genreIconTint"
    )
    val targetBorderColor = if (isActive) genreAccent else rim
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "genreSortTileBorder"
    )

    val shape = RoundedCornerShape(OverlayThemeTokens.TileCornerRadius)
    val tileBg = if (isDark) OverlayThemeTokens.TileBackgroundDark else cardBg
    val glowAlpha = if (isActive || badgeCount > 0) {
        if (isDark) 0.22f else OverlayThemeTokens.TileGlowAlphaLight
    } else 0f
    val accentGlow = genreAccent.copy(alpha = glowAlpha)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = OverlayThemeTokens.SortTileMinHeight)
            .then(
                if (isDark) Modifier else Modifier.softPlateShadowForLightSheet(
                    isDark = false,
                    shape = shape,
                    elevation = OverlayThemeTokens.SortOverlayGridLightShadowElevation,
                )
            )
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
            .glassEdge(OverlayThemeTokens.TileCornerRadius, isDark)
            .border(1.dp, animatedBorderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSelect(SortGridSelection.Genres) }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(OverlayThemeTokens.IconBoxCorner))
                    .background(
                        if (isDark) OverlayThemeTokens.TileIconBgDark
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedIconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) {
                        if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    } else {
                        muted
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = OverlayThemeTokens.MetricLabel,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(genreAccent.copy(alpha = 0.35f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenreFilterOverlay(
    visibleState: MutableTransitionState<Boolean>,
    strings: UiStrings,
    filterSelectedTags: List<String>,
    filterCategoryType: String,
    currentLanguage: AppLanguage,
    onTagToggle: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val panelBg = if (isDark) DarkBackground else MaterialTheme.colorScheme.surface

    val collisionState = rememberInertialCollisionState()
    LaunchedEffect(visibleState.targetState) {
        if (visibleState.targetState) {
            collisionState.triggerCollision(impactForce = 35f, stiffness = 200f, dampingRatio = 0.5f)
        }
    }

    BackHandler { onDismiss() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibleState,
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
                    ) { onDismiss() }
            )
        }
        
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDark) 12.dp else 0.dp
                )
            ) {
                OverlayGlassPanel(
                    isDark = isDark,
                    panelBg = panelBg,
                    cornerRadius = 28.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(
                    modifier = Modifier
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .inertialCollision(state = collisionState, index = 0, baseMultiplier = 2.5f)
                    ) {
                        Text(
                            text = strings.filterByGenre,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .inertialCollision(state = collisionState, index = 1, baseMultiplier = 2.5f)
                    ) {
                        GenreFilterPillSelection(
                            strings = strings,
                            currentLanguage = currentLanguage,
                            selectedTags = filterSelectedTags,
                            activeCategory = filterCategoryType,
                            onTagToggle = onTagToggle
                        )
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .inertialCollision(state = collisionState, index = 2, baseMultiplier = 2.5f)
                            .gradientHighlightBorder(24.dp, isDark)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) {
                                    OverlayThemeTokens.IconSyncBlue
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (isDark) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            ),
                            border = null
                        ) {
                            Text(
                                text = strings.genreFilterDone,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SnProFamily
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                } // OverlayGlassPanel
            }
        }
    }
}