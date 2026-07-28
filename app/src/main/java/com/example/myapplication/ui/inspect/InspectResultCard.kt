package com.example.myapplication.ui.inspect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.myapplication.data.models.RatingScale
import com.example.myapplication.domain.search.apiRatingTo10
import com.example.myapplication.network.ApiSearchResult
import com.example.myapplication.ui.shared.theme.SnProFamily

/**
 * Карточка найденного по кадру тайтла. Своя, а не [com.example.myapplication.ui.home.ApiSearchResultCard]:
 * на главном экране карточка — строка списка результатов поиска, здесь же это единственный ответ
 * движка, поэтому она подаётся крупно — кадр во всю ширину, чипы поверх него, название и
 * основное действие во всю ширину.
 *
 * Показываем ровно то, что отдаёт пайплайн (постер, рейтинг, тип/серии, источник, жанры) — макет
 * задаёт стиль, а не набор полей.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspectResultCard(
    result: ApiSearchResult,
    isAdded: Boolean,
    isLoading: Boolean,
    addLabel: String,
    addedLabel: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            if (!result.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Скрим снизу — чип с типом/сериями читается на любом кадре.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            // Та же конвертация, что и у карточек поиска, — не своя арифметика.
            val rating10 = apiRatingTo10(result.rating).takeIf { it > 0f }
            if (rating10 != null) {
                OverlayChip(
                    text = "★ ${RatingScale.format(rating10)}",
                    container = Color.Black.copy(alpha = 0.62f),
                    content = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )
            }
            OverlayChip(
                text = buildString {
                    append(if (result.categoryType == "MOVIE") result.type.ifBlank { "Movie" } else "${result.episodes} эп.")
                    append(" · ")
                    append(result.source)
                },
                container = Color.Black.copy(alpha = 0.62f),
                content = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = SnProFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            ),
            color = onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        result.altTitle?.takeIf { it.isNotBlank() && it != result.title }?.let { alt ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = alt,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SnProFamily),
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        val genres = result.genres.filter { it.isNotBlank() }.take(3)
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                genres.forEachIndexed { index, genre ->
                    // Первый жанр — акцентный, как ведущий тег в референсе; остальные нейтральные.
                    if (index == 0) {
                        OverlayChip(
                            text = genre,
                            container = accent.copy(alpha = 0.20f),
                            content = accent,
                        )
                    } else {
                        OverlayChip(
                            text = genre,
                            container = Color.White.copy(alpha = 0.08f),
                            content = onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        AddToCollectionButton(
            isAdded = isAdded,
            isLoading = isLoading,
            addLabel = addLabel,
            addedLabel = addedLabel,
            accent = accent,
            onClick = onAddClick,
        )
    }
}

@Composable
private fun OverlayChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = SnProFamily,
            fontWeight = FontWeight.SemiBold,
        ),
        color = content,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun AddToCollectionButton(
    isAdded: Boolean,
    isLoading: Boolean,
    addLabel: String,
    addedLabel: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val container = if (isAdded) Color.White.copy(alpha = 0.10f) else accent
    val content = if (isAdded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(container)
            .clickable(
                enabled = !isAdded && !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )

            else -> {
                Icon(
                    imageVector = if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isAdded) addedLabel else addLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SnProFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = content,
                )
            }
        }
    }
}
