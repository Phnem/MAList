package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.sync.ExternalListService
import com.example.myapplication.ui.shared.theme.IosDesign
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.SquircleShape
import com.example.myapplication.ui.shared.theme.iosRowHighlight
import com.phnem.vetro.R

private val ConnectedGreen = Color(0xFF32D74B)
private val LogoutRed = Color(0xFFFF453A)

/**
 * Лист «Синхронизация» — композиция по вертикальному ритму на ЕДИНОЙ поверхности:
 *
 *   hero (аватар → имя → статус подключения)   — фокус
 *        ↓ воздух
 *   группа инфо-строк (последняя синхр / результат / режим)
 *        ↓ воздух
 *   строка выхода (красный текст, без плашки)
 *        ↓ большой воздух
 *   секция «Внешние сервисы» (заголовок + счётчик) → список
 *
 * Глубина — за счёт двух уровней материала: тёмная база панели (groupedBackground) + светлые
 * группы (rowBackground), а не цветных блоков и теней. Каждый ниже уровень — визуально легче.
 */
@Composable
internal fun NottifSyncServiceGrid(
    isDark: Boolean,
    strings: UiStrings,
    ru: Boolean,
    badgeText: String,
    timeStr: String,
    datePart: String,
    statusWord: String,
    detailLine: String,
    syncingCloud: Boolean,
    isCheckingUpdates: Boolean,
    hasToken: Boolean,
    syncState: SyncState,
    onSyncNow: () -> Unit,
    onCheckUpdates: () -> Unit,
    onLogoutClick: () -> Unit,
    onServiceClick: (ExternalListService) -> Unit,
) {
    val connected = hasToken && syncState != SyncState.AUTH_REQUIRED &&
        syncState != SyncState.ERROR && syncState != SyncState.CONFLICT
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = onSurface.copy(alpha = 0.5f)
    val statusColor = if (connected) ConnectedGreen else muted
    val statusText = if (connected) strings.nottifStatusConnected else strings.nottifStatusDisconnected

    Column(modifier = Modifier.fillMaxWidth()) {

        // ——— HERO ———
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF7C6BFF), Color(0xFFFF6FB1)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = strings.nottifAccountTitle,
                fontFamily = SnProFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = (-0.3).sp,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            // Статус подключения — приоритет №2, компактная строка с точкой (не плашка).
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !syncingCloud,
                        onClick = onSyncNow,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = statusText,
                    fontFamily = SnProFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = statusColor,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ——— Инфо-группа (одна вложенная группа статистики) ———
        NottifGroup(isDark) {
            NottifInfoRow(
                label = if (ru) "Последняя синхронизация" else "Last sync",
                value = datePart.ifBlank { timeStr },
            )
            NottifRowDivider(isDark)
            NottifInfoRow(
                label = if (ru) "Результат" else "Result",
                value = statusWord,
                valueColor = if (connected) ConnectedGreen else null,
            )
            NottifRowDivider(isDark)
            NottifInfoRow(
                label = if (ru) "Режим" else "Mode",
                value = badgeText,
            )
        }

        if (hasToken) {
            Spacer(Modifier.height(16.dp))
            // ——— Выход: строка-карточка с красным текстом, без цветной плашки ———
            NottifGroup(isDark) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = iosRowHighlight(isDark, SquircleShape(0.dp)),
                            onClick = onLogoutClick,
                        )
                        .defaultMinSize(minHeight = 44.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = LogoutRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (ru) "Выйти из аккаунта" else "Sign out",
                        fontFamily = SnProFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = LogoutRed,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ——— Секция «Внешние сервисы» ———
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = (if (ru) "Внешние сервисы" else "External services").uppercase(),
                fontFamily = SnProFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp,
                color = muted,
            )
            Text(
                text = if (ru) "3 доступно" else "3 available",
                fontFamily = SnProFamily,
                fontSize = 12.sp,
                color = onSurface.copy(alpha = 0.4f),
            )
        }
        Spacer(Modifier.height(4.dp))
        NottifGroup(isDark) {
            NottifServiceRow(strings.nottifServiceShikimori, R.drawable.ic_shikimori, Color(0xFF2AA3C4), isDark, ru) { onServiceClick(ExternalListService.SHIKIMORI) }
            NottifRowDivider(isDark, inset = 58.dp)
            NottifServiceRow(strings.nottifServiceMal, R.drawable.ic_myanimelist, Color(0xFF2E51A2), isDark, ru) { onServiceClick(ExternalListService.MYANIMELIST) }
            NottifRowDivider(isDark, inset = 58.dp)
            NottifServiceRow(strings.nottifServiceAnilist, R.drawable.ic_anilist, Color(0xFF3577FF), isDark, ru) { onServiceClick(ExternalListService.ANILIST) }
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** Светлая группа (уровень материала выше базы панели). */
@Composable
private fun NottifGroup(isDark: Boolean, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(18.dp))
            .background(IosDesign.rowBackground(isDark)),
    ) { content() }
}

@Composable
private fun NottifInfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = SnProFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = SnProFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NottifRowDivider(isDark: Boolean, inset: Dp = 16.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(IosDesign.SeparatorThickness)
            .background(IosDesign.separator(isDark)),
    )
}

@Composable
private fun NottifServiceRow(
    name: String,
    @DrawableRes iconRes: Int,
    accent: Color,
    isDark: Boolean,
    ru: Boolean,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = iosRowHighlight(isDark, SquircleShape(0.dp)),
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                fontFamily = SnProFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (ru) "Не подключено" else "Not connected",
                fontFamily = SnProFamily,
                fontSize = 13.sp,
                color = onSurface.copy(alpha = 0.5f),
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IosDesign.chevron(isDark),
            modifier = Modifier.size(20.dp),
        )
    }
}
