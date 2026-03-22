package com.example.myapplication.ui.shared.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.models.GenreDefinition
import com.example.myapplication.network.AppLanguage
data class GenreCategoryData(
    val key: String,
    val label: String,
    val genres: List<GenreDefinition>,
    val accentColor: Color
)

/**
 * Stateless expandable genre flow with a pluggable header slot.
 * The caller owns [expandedCategoryKey] and [onExpandedChange].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableGenreFlow(
    categories: List<GenreCategoryData>,
    activeCategory: String,
    selectedTags: List<String>,
    expandedCategoryKey: String?,
    onExpandedChange: (String?) -> Unit,
    onTagToggle: (tagId: String, categoryKey: String) -> Unit,
    currentLanguage: AppLanguage,
    modifier: Modifier = Modifier,
    header: @Composable (
        categoryData: GenreCategoryData,
        isExpanded: Boolean,
        hasSelectedInCategory: Boolean,
        selectedCount: Int,
        onToggle: () -> Unit
    ) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        categories.forEach { catData ->
            val isActive = activeCategory.isEmpty() ||
                    activeCategory.equals(catData.key, ignoreCase = true)
            val hasSelected = selectedTags.any { tag -> catData.genres.any { it.id == tag } }
            val isExpanded = expandedCategoryKey == catData.key

            if (isActive || hasSelected) {
                val selectedCount = selectedTags.count { tag -> catData.genres.any { it.id == tag } }

                header(
                    catData,
                    isExpanded,
                    hasSelected,
                    selectedCount
                ) {
                    onExpandedChange(if (isExpanded) null else catData.key)
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        clip = true,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + slideInVertically(
                        initialOffsetY = { full -> -(full / 14).coerceAtLeast(1) },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        clip = true,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + slideOutVertically(
                        targetOffsetY = { full -> -(full / 14).coerceAtLeast(1) },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(animationSpec = tween(160))
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        catData.genres.forEach { genreDef ->
                            val isSelected = selectedTags.contains(genreDef.id)
                            val displayName = if (currentLanguage == AppLanguage.RU)
                                genreDef.ru else genreDef.en

                            FilterChip(
                                selected = isSelected,
                                onClick = { onTagToggle(genreDef.id, catData.key) },
                                label = { Text(displayName, fontSize = 13.sp) },
                                enabled = true,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = catData.accentColor.copy(alpha = 0.15f),
                                    selectedLabelColor = catData.accentColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
