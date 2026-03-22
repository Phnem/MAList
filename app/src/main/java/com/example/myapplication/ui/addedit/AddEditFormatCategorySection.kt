package com.example.myapplication.ui.addedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.data.models.GenreCategory
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.shared.components.ExpandableGenreFlow
import com.example.myapplication.ui.shared.components.GenreCategoryData
import com.example.myapplication.ui.shared.theme.SnProFamily
import org.koin.compose.koinInject

@Composable
fun AddEditFormatCategorySection(
    strings: UiStrings,
    currentLanguage: AppLanguage,
    selectedTags: List<String>,
    activeCategory: String,
    onTagToggle: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val genreRepository: GenreRepository = koinInject()
    val animeGenres = remember { genreRepository.getGenresForCategory(GenreCategory.ANIME) }
    val movieGenres = remember { genreRepository.getGenresForCategory(GenreCategory.MOVIE) }
    val seriesGenres = remember { genreRepository.getGenresForCategory(GenreCategory.SERIES) }

    var expandedCategory by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var hasAutoExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(activeCategory) {
        if (!hasAutoExpanded && activeCategory.isNotEmpty()) {
            val match = listOf("Anime", "Movies", "Series").find {
                it.equals(activeCategory, ignoreCase = true)
            }
            if (match != null) {
                expandedCategory = match
                hasAutoExpanded = true
            }
        }
    }

    val categoryIcons = mapOf(
        "Anime" to Icons.Outlined.AutoAwesome,
        "Movies" to Icons.Outlined.Movie,
        "Series" to Icons.Outlined.Tv
    )

    val categories = listOf(
        GenreCategoryData("Anime", strings.addEditFormatAnimeTitle, animeGenres, Color(0xFFFF2D55)),
        GenreCategoryData("Movies", strings.addEditFormatMoviesTitle, movieGenres, Color(0xFF5AC8FA)),
        GenreCategoryData("Series", strings.addEditFormatSeriesTitle, seriesGenres, Color(0xFFFFCC00))
    )

    ExpandableGenreFlow(
        categories = categories,
        activeCategory = activeCategory,
        selectedTags = selectedTags,
        expandedCategoryKey = expandedCategory,
        onExpandedChange = { expandedCategory = it },
        onTagToggle = onTagToggle,
        currentLanguage = currentLanguage,
        modifier = modifier,
        header = { catData, isExpanded, _, _, onToggle ->
            val icon = categoryIcons[catData.key] ?: Icons.Outlined.AutoAwesome
            FormatCategoryCard(
                icon = icon,
                iconBgColor = formatCategoryIconBg(catData.key),
                title = catData.label,
                isExpanded = isExpanded,
                gradientKey = catData.key,
                onClick = onToggle
            )
        }
    )
}

@Composable
private fun FormatCategoryCard(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    isExpanded: Boolean,
    gradientKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pillBrush: Brush = if (isDark) {
        formatCategoryPillBrush(gradientKey)
    } else {
        formatCategoryPillBrushLight(iconBgColor, scheme.surfaceVariant)
    }
    val borderColor =
        if (isDark) Color.White.copy(alpha = 0.1f)
        else scheme.outline.copy(alpha = 0.12f)
    val titleColor = if (isDark) Color.White else scheme.onSurface
    val chevronTint =
        if (isDark) Color.White.copy(alpha = 0.55f)
        else scheme.onSurfaceVariant.copy(alpha = 0.72f)
    val innerCircleBg =
        if (isDark) Color.Black.copy(alpha = 0.35f)
        else Color.White
    val glowAlpha = if (isDark) 0.5f else 0.22f

    val capsuleHeight = 64.dp
    val pillShape = RoundedCornerShape(capsuleHeight / 2)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(capsuleHeight)
            .clip(pillShape)
            .background(pillBrush)
            .border(
                width = 0.5.dp,
                color = borderColor,
                shape = pillShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .neonGlow(
                        color = iconBgColor,
                        radius = 16.dp,
                        alpha = glowAlpha
                    )
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(innerCircleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = SnProFamily,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
        )

        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
            else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = chevronTint,
            modifier = Modifier.size(22.dp)
        )
    }
    Spacer(Modifier.height(10.dp))
}
