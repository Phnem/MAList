package com.example.myapplication.ui.shared.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val IconSignalGreen = Color(0xFF40F090)
    val IconAccountYellow = Color(0xFFFFDF45)
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

    /** Кнопка «Применить»: тёмная подложка + приглушённый текст без «кислотного» cyan. */
    val ApplyButtonContainerDark = Color(0xFF1C2633)
    val ApplyButtonLabelSoft = Color(0xFF9DB0BE)

    // ============================================================
    // Glass edge / fill — параметры стеклянных панелей
    // ============================================================
    /** Ширина внутренней обводки-блика для эффекта объёма стекла. */
    val GlassEdgeWidth = 1.dp

    /** Опорная альфа для glass-edge на тёмной теме (верх/левый угол блика). */
    const val GlassEdgeAlphaDark = 0.22f

    /** Опорная альфа для glass-edge на светлой теме. */
    const val GlassEdgeAlphaLight = 0.7f

    /** Финальная альфа в направлении угасания градиента (тёмная тема). */
    const val GlassEdgeFadeAlphaDark = 0.04f

    /** Финальная альфа в направлении угасания градиента (светлая). */
    const val GlassEdgeFadeAlphaLight = 0.18f

    /** Лёгкий вертикальный «тонировочный» fill поверх panelBg для имитации толщины стекла. */
    const val GlassFillTopAlphaDark = 0.06f
    const val GlassFillBottomAlphaDark = 0.0f
    const val GlassFillTopAlphaLight = 0.55f
    const val GlassFillBottomAlphaLight = 0.0f

    // ============================================================
    // Неоновые акценты — общая палитра для иконок/метрик/индикаторов
    // ============================================================
    /** Холодный голубой (синхронизация, эпизоды). */
    val AccentNeonBlue: Color = IconSyncBlue
    /** Кислотно-зелёный (статус, статистика). */
    val AccentNeonGreen: Color = IconSignalGreen
    /** Тёплый янтарь (рейтинги, аккаунт). */
    val AccentNeonYellow: Color = IconAccountYellow
    /** Тёплый оранжевый (контент, акценты). */
    val AccentNeonOrange = Color(0xFFFB923C)
    /** Электрический фиолетовый (метрики/декор). */
    val AccentNeonPurple = Color(0xFFA78BFA)
    /** Малиновый (favorites, ошибки в инфо-плитках). */
    val AccentNeonPink = Color(0xFFFF6FB1)
    /** Кнопка подтверждения «В избранное» в шите списка. */
    val FavoriteConfirmGold = Color(0xFFE6B84C)
    val OnFavoriteConfirmGold = Color(0xFF2A2010)
    /** Алый (logout, destructive). */
    val AccentNeonRed: Color = LogoutIconTint

    // ============================================================
    // Типографика метрик — отдельные роли поверх MaterialTheme
    // ============================================================
    /** Большое жирное число — крупная метрика (totalAnime, totalEpisodes и т.п.). */
    val MetricValue: TextStyle = TextStyle(
        fontFamily = SnProFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    )

    /** Метка под крупной метрикой — мелкий и приглушённый caps-style. */
    val MetricLabel: TextStyle = TextStyle(
        fontFamily = SnProFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )

    /** Заголовок секции внутри стеклянной панели (uppercase caps). */
    val SectionLabel: TextStyle = TextStyle(
        fontFamily = SnProFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp
    )

    /** Альфа для приглушённых caption-меток поверх стекла. */
    const val LabelMutedAlpha = 0.55f
}
