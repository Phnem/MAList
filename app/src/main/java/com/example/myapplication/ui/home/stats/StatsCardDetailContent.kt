package com.example.myapplication.ui.home.stats

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.Anime
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.domain.stats.StatsCardExplanationState
import com.example.myapplication.domain.stats.StatsCardKind
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.shared.fluidClickable
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.ui.shared.theme.SnProFamily

// ==========================================
// StatsCardDetailContent — детальный режим карточки статистики:
// та же карточка сверху (статично, без драга), под ней AI-объяснение,
// ниже — footer-цитата как и была. Стрелка «назад» возвращает в колоду.
// ==========================================

@Composable
fun StatsCardDetailContent(
    kind: StatsCardKind,
    animeList: List<Anime>,
    strings: UiStrings,
    appLanguage: AppLanguage,
    isDark: Boolean,
    explanationState: StatsCardExplanationState,
    footerPhrase: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Стрелка назад — в колоду (скрим/системная «назад» закрывают шторку целиком)
        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) OverlayThemeTokens.TileIconBgDark
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .fluidClickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }

        StatsCardSurface(
            isDark = isDark,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / StatsCardHeightRatio),
        ) {
            StatsCardBody(
                kind = kind,
                animeList = animeList,
                strings = strings,
                appLanguage = appLanguage,
                isDark = isDark,
            )
        }

        Spacer(Modifier.height(20.dp))

        StatsAiExplanationBlock(
            state = explanationState,
            strings = strings,
            isDark = isDark,
        )

        if (footerPhrase.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = footerPhrase,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = SnProFamily,
                    lineBreak = LineBreak.Paragraph,
                    hyphens = Hyphens.Auto,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = OverlayThemeTokens.LabelMutedAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatsAiExplanationBlock(
    state: StatsCardExplanationState,
    strings: UiStrings,
    isDark: Boolean,
) {
    val mutedColor = if (isDark) OverlayThemeTokens.LabelMutedDark
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = strings.statsAiTitle,
            style = OverlayThemeTokens.SectionLabel,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        when (state) {
            is StatsCardExplanationState.Ready -> Text(
                text = state.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = SnProFamily,
                    lineHeight = 21.sp,
                    lineBreak = LineBreak.Paragraph,
                    hyphens = Hyphens.Auto,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
            is StatsCardExplanationState.Loading -> ExplanationSkeleton(isDark)
            is StatsCardExplanationState.Unavailable -> Text(
                text = strings.statsAiUnavailable,
                fontFamily = SnProFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                color = mutedColor,
            )
            is StatsCardExplanationState.InsufficientData -> Text(
                text = strings.statsAiInsufficient,
                fontFamily = SnProFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                color = mutedColor,
            )
        }
    }
}

/**
 * Короткий шиммер-скелетон (3 строки) вместо спиннера: текст обычно уже готов из кэша,
 * поэтому Loading практически не виден пользователю.
 */
@Composable
private fun ExplanationSkeleton(isDark: Boolean) {
    val transition = rememberInfiniteTransition(label = "aiSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aiSkeletonAlpha",
    )
    val lineColor = (if (isDark) OverlayThemeTokens.LabelMutedDark
    else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1f, 0.92f, 0.6f).forEach { widthFraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(13.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(lineColor),
            )
        }
    }
}
