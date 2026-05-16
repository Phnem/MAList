package com.example.myapplication.ui.settings

import androidx.annotation.RawRes
import com.example.myapplication.data.models.AppUpdateStatus
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Базовый контракт плитки настроек (SDUI mindset).
 * Плитки рендерятся динамически на основе стейта из ViewModel.
 */
sealed interface SettingsTile {
    val id: String
    val title: String
    val subtitle: String?
    val icon: ImageVector?
    val accentColor: Color
    val span: Int // 1 = половина экрана, 2 = на всю ширину

    /** Когда не null: [BaseTile] подставляет это как width/height вместо стандартного по span. */
    val preferredAspectRatio: Float?
        get() = null
}

/**
 * Опция для сегментированного переключателя.
 * label — для текста (EN/RU), icon — для иконок (Light/Dark/Auto).
 */
data class SegmentedOption(
    val label: String? = null,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)

/**
 * Плитка с сегментированным переключателем (Language, Theme, Content Type).
 */
data class ToggleTile(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val icon: ImageVector?,
    override val accentColor: Color,
    override val span: Int = 1,
    val options: List<SegmentedOption>,
    val selectedIndex: Int
) : SettingsTile

/**
 * Плитка с детальным экраном (Shared Transition).
 */
data class DetailTile(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val icon: ImageVector?,
    override val accentColor: Color,
    override val span: Int = 2,
    val onClick: () -> Unit
) : SettingsTile

/**
 * Плитка-кнопка (простое действие).
 * Для Update: updateStatus, currentVersion, latestDownloadUrl задают отображение UpdateStateButton.
 */
data class ActionTile(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val icon: ImageVector?,
    override val accentColor: Color,
    override val span: Int = 1,
    override val preferredAspectRatio: Float? = null,
    val onClick: () -> Unit,
    val updateStatus: AppUpdateStatus? = null,
    val currentVersion: String? = null,
    val latestDownloadUrl: String? = null,
) : SettingsTile

/**
 * Полноширинная плитка со ссылкой: текст и Lottie-анимация, без системной иконки в заголовке.
 */
data class LottieLinkTile(
    override val id: String,
    override val title: String,
    override val subtitle: String? = null,
    override val icon: ImageVector? = null,
    override val accentColor: Color,
    override val span: Int = 2,
    @RawRes val lottieRawRes: Int,
    val onClick: () -> Unit
) : SettingsTile {

    /** ~на треть ниже типовой длинной плитки: было 2.1 (W/H), нужно H×2/3 ⇒ W/H = 2.1×3/2. */
    override val preferredAspectRatio: Float?
        get() = 3.15f
}
