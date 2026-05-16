package com.example.myapplication.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.settingsIconWellColors
import com.example.myapplication.ui.shared.theme.softPlateShadowForLightSheet
import com.example.myapplication.isAppInDarkTheme
import java.util.Locale

sealed interface UpdateTileState {
    data object Idle : UpdateTileState
    data object Checking : UpdateTileState
    data class UpdateAvailable(
        val versionTag: String,
        val sizeLabel: String?,
    ) : UpdateTileState
    data object UpToDate : UpdateTileState
    data object Error : UpdateTileState
    data class Downloading(val progress: Float) : UpdateTileState
}

private fun updateTileCrossfadeSpec() =
    fadeIn(tween(durationMillis = 280, easing = FastOutSlowInEasing)).togetherWith(
        fadeOut(tween(durationMillis = 220, easing = FastOutSlowInEasing)),
    )

/**
 * Плитка обновления: иконка сверху, заголовок и подзаголовок под ней (как соседняя плитка настроек);
 * снизу полноширинная «кнопка» (pill).
 */
@Composable
fun UpdateTile(
    state: UpdateTileState,
    headline: String,
    checkButtonText: String,
    checkingText: String,
    newVersionSubtitle: String,
    upToDateSubtitle: String,
    installNowText: String,
    downloadProgressFormat: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { updateTileCrossfadeSpec() },
        contentKey = { s ->
            when (s) {
                is UpdateTileState.Idle -> "idle"
                is UpdateTileState.Checking -> "checking"
                is UpdateTileState.UpdateAvailable -> "available"
                is UpdateTileState.UpToDate -> "uptodate"
                is UpdateTileState.Error -> "error"
                is UpdateTileState.Downloading -> "downloading"
            }
        },
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        label = "UpdateTileTransition"
    ) { target ->
        val progressAnimated by animateFloatAsState(
            targetValue = (target as? UpdateTileState.Downloading)?.progress?.coerceIn(0f, 1f) ?: 0f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "apkTileProgressDisplayed",
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                UpdateLeadingIcon(state = target, accent = accentColor)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SnProFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when (target) {
                    is UpdateTileState.Checking -> checkingText
                    is UpdateTileState.Idle, is UpdateTileState.Error -> checkButtonText
                    is UpdateTileState.UpdateAvailable -> newVersionSubtitle
                    is UpdateTileState.UpToDate -> upToDateSubtitle
                    is UpdateTileState.Downloading -> checkingText
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = SnProFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (target is UpdateTileState.UpdateAvailable) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VersionChip(target.versionTag, accentColor)
                        target.sizeLabel?.let { SizeChip(it) }
                    }
                }
            }
            val pillShape = RoundedCornerShape(12.dp)
            when (target) {
                is UpdateTileState.Downloading -> {
                    val pct = (progressAnimated * 100f).toInt().coerceIn(0, 100)
                    val label = String.format(Locale.getDefault(), downloadProgressFormat, pct)
                    UpdateBottomPill(shape = pillShape) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { progressAnimated.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accentColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                strokeCap = StrokeCap.Round,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = SnProFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                is UpdateTileState.Checking -> {
                    UpdateBottomPill(shape = pillShape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accentColor,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = checkingText,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = SnProFamily,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                is UpdateTileState.UpdateAvailable -> {
                    UpdateBottomPill(shape = pillShape) {
                        Text(
                            text = installNowText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SnProFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                is UpdateTileState.Idle,
                is UpdateTileState.Error,
                is UpdateTileState.UpToDate,
                    -> {
                    UpdateBottomPill(shape = pillShape) {
                        Text(
                            text = checkButtonText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SnProFamily,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateBottomPill(
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    val isDark = isAppInDarkTheme()
    if (isDark) {
        val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
        val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = background,
            border = BorderStroke(width = 1.dp, color = borderColor),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .softPlateShadowForLightSheet(isDark = false, shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface, shape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun VersionChip(version: String, accent: Color) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = version,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = SnProFamily,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun SizeChip(label: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = SnProFamily,
        color = muted,
    )
}

@Composable
private fun UpdateLeadingIcon(state: UpdateTileState, accent: Color) {
    val isDark = isAppInDarkTheme()
    val iconWell = settingsIconWellColors(isDark, accent)
    val icon: ImageVector = when (state) {
        is UpdateTileState.Checking, is UpdateTileState.Downloading -> Icons.Default.KeyboardArrowDown
        is UpdateTileState.UpdateAvailable -> Icons.Default.KeyboardArrowDown
        is UpdateTileState.UpToDate -> Icons.Default.Check
        is UpdateTileState.Idle, is UpdateTileState.Error -> Icons.Default.Refresh
    }
    val tint: Color = when (state) {
        is UpdateTileState.Idle, is UpdateTileState.Error ->
            if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else iconWell.tint
        else -> if (isDark) accent else iconWell.tint
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(iconWell.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
