package com.example.myapplication.ui.shared.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Общие токены панелей-оверлеев (синхронизация, сортировка). */
internal object OverlayThemeTokens {
    val PanelWidth = 340.dp
    val PanelCornerRadius = 28.dp
    const val ScrimAlpha = 0.45f
    val PanelPaddingTop = 88.dp
    val PanelPaddingEnd = 12.dp
    val CardOuterPaddingTop = 10.dp
    val CardOuterPaddingEnd = 6.dp
    val PanelInnerPaddingStart = 18.dp
    val PanelInnerPaddingTop = 30.dp
    val PanelInnerPaddingEnd = 22.dp
    val PanelInnerPaddingBottom = 18.dp
    val CardElevation = 16.dp

    val RimDark = Color.White.copy(alpha = 0.09f)
    val LabelMutedDark = Color(0xFF94A3B8)
    val IconSyncBlue = Color(0xFF38BDF8)
    /** Алиас акцента синх-панели (не путать с мягкими токенами кнопки сортировки). */
    val AccentSyncBlue: Color get() = IconSyncBlue
    val IconSignalGreen = Color(0xFF34C759)
    val IconAccountYellow = Color(0xFFFFC400)
    val OnSyncBlueButton = Color.White
    val LogoutIconTint = Color(0xFFFF5A4D)

    val TileCornerRadius = 20.dp
    val MainTileCornerRadius = 22.dp
    val IconBoxCorner = 10.dp
    val GridSpacing = 10.dp
    val SortTileMinHeight = 90.dp
    val ApplyButtonCornerRadius = 14.dp

    val TileBackgroundDark = Color(0xFF161B26)
    val TileIconBgDark = Color.Black.copy(alpha = 0.35f)

    /** Базовый radial glow на тёмных плитках оверлея ([tileGlow] по умолчанию совпадает). */
    const val TileGlowAlphaDark = 0.15f

    /** Radial glow на светлых плитках — сильнее, чем на тёмных, иначе на surface не читается. */
    const val TileGlowAlphaLight = 0.24f

    /** Выбранная плитка сортировки: в светлой теме раньше множилось на 0.28 и почти пропадало. */
    const val SortTileGlowLightFactor = 0.92f

    /** Кнопка «Применить»: тёмная подложка + приглушённый текст без «кислотного» cyan. */
    val ApplyButtonContainerDark = Color(0xFF1C2633)
    val ApplyButtonLabelSoft = Color(0xFF9DB0BE)
}
