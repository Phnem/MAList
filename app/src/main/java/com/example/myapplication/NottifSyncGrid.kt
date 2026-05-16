package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.sync.ExternalListService
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.glassEdge
import com.example.myapplication.ui.shared.theme.glassFill
import com.example.myapplication.ui.shared.theme.softPlateShadowForLightSheet
import java.util.Locale

/**
 * Строка 1: две квадратные плитки 2×2 (синхронизация облака, аккаунт).
 * Строка 2: три квадратные плитки сервисов.
 */
@Composable
internal fun NottifSyncServiceGrid(
    isDark: Boolean,
    strings: UiStrings,
    cardBg: Color,
    darkTileBase: Color,
    onCard: Color,
    muted: Color,
    badgeText: String,
    timeStr: String,
    detailLine: String,
    syncingCloud: Boolean,
    isCheckingUpdates: Boolean,
    hasToken: Boolean,
    syncState: SyncState,
    accent: Color,
    onSyncNow: () -> Unit,
    onCheckUpdates: () -> Unit,
    onLogoutClick: () -> Unit,
    onServiceClick: (ExternalListService) -> Unit
) {
    val spacing = OverlayThemeTokens.GridSpacing
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val w = maxWidth
        val tileSide = (w - spacing) / 2
        val serviceW = (w - spacing * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                NottifSquareCloudSyncTile(
                    modifier = Modifier.size(tileSide),
                    isDark = isDark,
                    strings = strings,
                    badgeText = badgeText,
                    timeStr = timeStr,
                    detailLine = detailLine,
                    syncingCloud = syncingCloud,
                    isCheckingUpdates = isCheckingUpdates,
                    hasToken = hasToken,
                    syncState = syncState,
                    cardBg = cardBg,
                    darkTileBase = darkTileBase,
                    onCard = onCard,
                    muted = muted,
                    accent = accent,
                    onSyncNow = onSyncNow,
                    onCheckUpdates = onCheckUpdates
                )
                NottifSquareAccountTile(
                    modifier = Modifier.size(tileSide),
                    isDark = isDark,
                    strings = strings,
                    cardBg = cardBg,
                    onCard = onCard,
                    muted = muted,
                    hasToken = hasToken,
                    onLogoutClick = onLogoutClick
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                NottifServiceStubTile(
                    modifier = Modifier.size(serviceW),
                    isDark = isDark,
                    label = strings.nottifServiceShikimori,
                    accent = Color(0xFF34C759),
                    cardBg = cardBg,
                    darkTileBase = darkTileBase,
                    onCard = onCard,
                    muted = muted,
                    onClick = { onServiceClick(ExternalListService.SHIKIMORI) }
                )
                NottifServiceStubTile(
                    modifier = Modifier.size(serviceW),
                    isDark = isDark,
                    label = strings.nottifServiceMal,
                    accent = Color(0xFF2E51A2),
                    cardBg = cardBg,
                    darkTileBase = darkTileBase,
                    onCard = onCard,
                    muted = muted,
                    onClick = { onServiceClick(ExternalListService.MYANIMELIST) }
                )
                NottifServiceStubTile(
                    modifier = Modifier.size(serviceW),
                    isDark = isDark,
                    label = strings.nottifServiceAnilist,
                    accent = OverlayThemeTokens.AccentNeonPurple,
                    cardBg = cardBg,
                    darkTileBase = darkTileBase,
                    onCard = onCard,
                    muted = muted,
                    onClick = { onServiceClick(ExternalListService.ANILIST) }
                )
            }
        }
    }
}

@Composable
private fun NottifSquareCloudSyncTile(
    modifier: Modifier,
    isDark: Boolean,
    strings: UiStrings,
    badgeText: String,
    timeStr: String,
    detailLine: String,
    syncingCloud: Boolean,
    isCheckingUpdates: Boolean,
    hasToken: Boolean,
    syncState: SyncState,
    cardBg: Color,
    darkTileBase: Color,
    onCard: Color,
    muted: Color,
    accent: Color,
    onSyncNow: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val tileShape = RoundedCornerShape(OverlayThemeTokens.TileCornerRadius)
    val tileBg = if (isDark) darkTileBase else cardBg
    val accentRim = accent.copy(alpha = if (isDark) 0.45f else 0.6f)
    val accentGlow = accent.copy(
        alpha = if (isDark) 0.18f else OverlayThemeTokens.TileGlowAlphaLight
    )
    val connected = hasToken && syncState != SyncState.AUTH_REQUIRED

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isDark) Modifier else Modifier.softPlateShadowForLightSheet(
                        isDark = false,
                        shape = tileShape,
                        elevation = OverlayThemeTokens.SortOverlayGridLightShadowElevation,
                    )
                )
                .clip(tileShape)
                .background(tileBg)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentGlow, Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 280f
                    )
                )
                .glassFill(isDark)
                .glassEdge(OverlayThemeTokens.TileCornerRadius, isDark)
                .border(1.dp, accentRim, tileShape)
                .clickable(
                    enabled = !syncingCloud,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSyncNow
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(OverlayThemeTokens.IconBoxCorner))
                            .background(nottifOverlayIconBoxBg(isDark)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onCard,
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                        maxLines = 1
                    )
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = badgeText,
                    style = OverlayThemeTokens.MetricLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = if (
                    connected && syncState != SyncState.ERROR && syncState != SyncState.CONFLICT
                ) {
                    strings.nottifStatusConnected
                } else {
                    strings.nottifStatusDisconnected
                }
                NottifStatusPill(
                    text = statusText,
                    accent = accent,
                    isDark = isDark,
                    state = syncState,
                    isCheckingUpdates = isCheckingUpdates,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCheckUpdates
                    ),
                )
            }
        }
    }
}

@Composable
private fun NottifSquareAccountTile(
    modifier: Modifier,
    isDark: Boolean,
    strings: UiStrings,
    cardBg: Color,
    onCard: Color,
    muted: Color,
    hasToken: Boolean,
    onLogoutClick: () -> Unit
) {
    val tileShape = RoundedCornerShape(OverlayThemeTokens.TileCornerRadius)
    val tileBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else cardBg
    val accent = if (isDark) OverlayThemeTokens.IconSyncBlue else MaterialTheme.colorScheme.primary
    val accentRim = accent.copy(alpha = if (isDark) 0.4f else 0.55f)
    val accentGlow = accent.copy(
        alpha = if (isDark) 0.18f else OverlayThemeTokens.TileGlowAlphaLight
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isDark) Modifier else Modifier.softPlateShadowForLightSheet(
                    isDark = false,
                    shape = tileShape,
                    elevation = OverlayThemeTokens.SortOverlayGridLightShadowElevation,
                )
            )
            .clip(tileShape)
            .background(tileBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(accentGlow, Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 280f
                )
            )
            .glassFill(isDark)
            .glassEdge(OverlayThemeTokens.TileCornerRadius, isDark)
            .border(1.dp, accentRim, tileShape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(OverlayThemeTokens.IconBoxCorner))
                        .background(nottifOverlayIconBoxBg(isDark)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.nottifSectionAccount.uppercase(Locale.getDefault()),
                    style = OverlayThemeTokens.MetricLabel,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = strings.nottifAccountTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onCard,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasToken) strings.nottifAccountSignedIn else strings.nottifAccountGuest,
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                        contentDescription = strings.nottifLogoutConfirmTitle,
                        tint = OverlayThemeTokens.AccentNeonRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
}

@Composable
private fun NottifServiceStubTile(
    modifier: Modifier,
    isDark: Boolean,
    label: String,
    accent: Color,
    cardBg: Color,
    darkTileBase: Color,
    onCard: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val tileShape = RoundedCornerShape(OverlayThemeTokens.TileCornerRadius)
    val tileBg = if (isDark) darkTileBase else cardBg
    val accentRim = accent.copy(alpha = if (isDark) 0.4f else 0.55f)
    val accentGlow = accent.copy(
        alpha = if (isDark) 0.16f else OverlayThemeTokens.TileGlowAlphaLight
    )

    Column(
        modifier = modifier
            .then(
                if (isDark) Modifier else Modifier.softPlateShadowForLightSheet(
                    isDark = false,
                    shape = tileShape,
                    elevation = OverlayThemeTokens.SortOverlayGridLightShadowElevation,
                )
            )
            .clip(tileShape)
            .background(tileBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(accentGlow, Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 220f
                )
            )
            .glassFill(isDark)
            .glassEdge(OverlayThemeTokens.TileCornerRadius, isDark)
            .border(1.dp, accentRim, tileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (label.firstOrNull()?.uppercaseChar() ?: '·').toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = onCard,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}
