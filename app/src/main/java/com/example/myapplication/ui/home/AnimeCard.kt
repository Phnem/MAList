package com.example.myapplication.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.ui.shared.fluidClickable
import com.example.myapplication.ui.shared.theme.BrandOrangeBright
import com.example.myapplication.ui.shared.theme.SnProFamily
import com.example.myapplication.ui.shared.theme.LightBorder
import com.example.myapplication.ui.shared.theme.OverlayThemeTokens
import com.example.myapplication.data.models.RatingScale
import com.example.myapplication.ui.shared.theme.getRatingColor
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.io.File

/**
 * Что показывает бар на карточке:
 *  • [AIRING] — выход серий сезона («S5 4 / 14 ep.»), фиолетовый;
 *  • [WATCHING] — просмотр пользователя («62 / 80 ep.»), брендовый оранжевый.
 */
enum class CardProgressKind { AIRING, WATCHING }

/** Прогресс на карточке — выход сезона либо просмотр, см. [CardProgressKind]. */
@Immutable
data class AiringCardInfo(
    /** null — источник без графа франшизы (Shikimori/AniLibria) либо прогресс просмотра. */
    val seasonNumber: Int?,
    val airedEpisodes: Int,
    /** null — число серий сезона ещё не анонсировано (бар не рисуем, только текст). */
    val totalEpisodes: Int?,
    val kind: CardProgressKind = CardProgressKind.AIRING,
)

@Immutable
data class AnimeCardState(
    val id: String,
    val title: String,
    /** Английское название (дубляж); показывается второй строкой, если отличается от [title]. */
    val titleEn: String? = null,
    /** 10-балльная шкала, 0 = не оценено. */
    val rating: Float,
    val genres: PersistentList<String>,
    val episodesCount: Int,
    val imagePath: String?,
    val mediaTypeLabel: String,
    /** Избранное: карточка поднята в начало списка и обведена рамкой, чтобы это было видно. */
    val isFavorite: Boolean = false,
    /** Найденные прямые ссылки по одобренным сайтам (для языка [language]). */
    val webLinks: List<com.example.myapplication.domain.enrichment.weblinks.ResolvedWebLink> = emptyList(),
    val language: com.example.myapplication.network.AppLanguage = com.example.myapplication.network.AppLanguage.EN,
    /** Не-null = тайтл «в процессе»: прямо сейчас выходит сезон. */
    val airing: AiringCardInfo? = null,
)

/** Цвет заполнения бара прогресса сезона (фиолетовый, как на референсе). */
private val AiringBarColor = Color(0xFF6C4BF4)

/**
 * Секция «в процессе»: слева подпись, справа «S3 2 / 12 ep.», ниже — бар прогресса
 * выходящего сезона. Если источники не знают, сколько серий будет всего, оцениваем
 * по курам (кратно 12): вышло ≤12 → «n / 12», 13–24 → «n / 24», дальше 36, 48 и т.д.
 */
@Composable
private fun AiringProgressSection(
    airing: AiringCardInfo,
    language: com.example.myapplication.network.AppLanguage,
    labelColor: Color,
    trackColor: Color,
) {
    val label = when (language) {
        com.example.myapplication.network.AppLanguage.RU -> "Прогресс"
        com.example.myapplication.network.AppLanguage.EN -> "Progress"
    }
    val total = airing.totalEpisodes?.takeIf { it > 0 } ?: courEstimatedTotal(airing.airedEpisodes)
    val counter = buildString {
        // Префикс сезона осмыслен только для выхода серий: просмотр считается сквозной
        // нумерацией по всей франшизе, номер сезона к нему не относится.
        if (airing.kind == CardProgressKind.AIRING) {
            airing.seasonNumber?.let { append("S").append(it).append(" ") }
        }
        append(airing.airedEpisodes).append(" / ").append(total).append(" ep.")
    }
    val barColor = when (airing.kind) {
        CardProgressKind.AIRING -> AiringBarColor
        CardProgressKind.WATCHING -> BrandOrangeBright
    }
    // Компактно: секция живёт внутри фиксированных 180dp карточки, каждый dp на счету.
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SnProFamily,
                color = labelColor,
            )
            Text(
                text = counter,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SnProFamily,
                color = labelColor,
            )
        }
        val fraction = (airing.airedEpisodes.toFloat() / total).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

/** Оценка серий сезона по курам: ближайшее кратное 12 сверху (минимум 12). */
private fun courEstimatedTotal(aired: Int): Int {
    val cours = ((aired - 1).coerceAtLeast(0) / 12) + 1
    return cours * 12
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.OneUiAnimeCard(
    state: AnimeCardState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val borderStroke = when {
        state.isFavorite -> OverlayThemeTokens.FavoriteCardBorder
        isDark -> Color.White.copy(alpha = 0.15f)
        else -> LightBorder
    }
    // Рамка избранного заметно толще обычной кромки: на 1dp персиковый читался бы как чуть
    // подсвеченный край, а не как метка.
    val borderWidth = if (state.isFavorite) 2.dp else 1.dp
    val cardBg = if (isDark) Color(0xFF1C1C1C) else MaterialTheme.colorScheme.surface
    val cardShadowColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.08f)
    val subtitleColor = if (isDark) Color(0xFFA7A7A7) else Color(0xFF8E8E93)
    val chipBg = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key = "anime_${state.id}_bounds"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(24.dp))
            )
            .fillMaxWidth()
            .height(180.dp)
            .fluidClickable(scaleDown = 0.975f, onClick = onClick)
            .shadow(
                elevation = if (isDark) 8.dp else 4.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = cardShadowColor
            )
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .border(
                BorderStroke(borderWidth, borderStroke),
                RoundedCornerShape(24.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "anime_${state.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .skipToLookaheadSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0xFF2C2C2C) else Color(0xFFE8E8E8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.title.take(1).uppercase(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDark) Color.White.copy(alpha = 0.3f)
                    else Color.Black.copy(alpha = 0.15f),
                    fontFamily = SnProFamily
                )
                state.imagePath?.let { imgPath ->
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(imgPath))
                            .size(Size(280, 400))
                            .crossfade(true)
                            .build(),
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = state.mediaTypeLabel,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SnProFamily
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                fontFamily = SnProFamily
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val en = state.titleEn?.takeIf {
                            it.isNotBlank() && !it.equals(state.title, ignoreCase = true)
                        }
                        if (en != null) {
                            Text(
                                text = en,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = SnProFamily,
                                    fontSize = 12.sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = subtitleColor,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (state.rating > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isDark) Color(0xFF000000)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                )
                        ) {
                            Text(
                                text = "★ ${RatingScale.format(state.rating)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = getRatingColor(state.rating),
                                    fontFamily = SnProFamily
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (state.genres.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        // С прогресс-секцией второй ряд жанров скрываем (только визуально:
                        // сами теги остаются в данных/сортировке) — иначе низ карточки не влезает.
                        maxLines = if (state.airing != null) 1 else 2,
                    ) {
                        state.genres.forEach { genre ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(chipBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = genre,
                                    fontSize = 12.sp,
                                    color = subtitleColor,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = SnProFamily
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                state.airing?.let { airing ->
                    AiringProgressSection(
                        airing = airing,
                        language = state.language,
                        labelColor = subtitleColor,
                        trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.episodesCount} eps.",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontFamily = SnProFamily
                        ),
                        color = subtitleColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Стопка сайтов занимает гибкую середину ряда и сама сокращается до «+N»,
                    // если места мало — кнопку Details не двигает никогда.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        com.example.myapplication.ui.shared.components.WebLinkChipStack(
                            links = state.webLinks,
                            language = state.language,
                            ringColor = cardBg,
                        )
                    }
                    FilledTonalButton(
                        onClick = onEditClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 0.dp
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (state.language) {
                                    com.example.myapplication.network.AppLanguage.RU -> "Edit"
                                    com.example.myapplication.network.AppLanguage.EN -> "Edit"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SnProFamily,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
