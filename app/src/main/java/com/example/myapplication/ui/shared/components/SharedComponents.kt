package com.example.myapplication.ui.shared.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.data.models.GenreCategory
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.data.repository.GenreRepository
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.settings.tileGlowLeading
import com.example.myapplication.ui.shared.theme.*
import org.koin.compose.koinInject

// ==========================================
// AnimatedOneUiTextField
// ==========================================
@Composable
fun AnimatedOneUiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BrandBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        label = "border"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .onFocusChanged { isFocused = it.isFocused },
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(BrandBlue),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

// ==========================================
// AnimatedCopyButton
// ==========================================
@Composable
@Suppress("DEPRECATION")
fun AnimatedCopyButton(textToCopy: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    IconButton(onClick = {
        clipboardManager.setText(AnnotatedString(textToCopy))
        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
    }) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ==========================================
// EpisodeSuggestions
// ==========================================
@Composable
fun EpisodeSuggestions(onSelect: (String) -> Unit) {
    val suggestions = listOf("12", "13", "24", "25", "26")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { ep ->
            SuggestionChip(
                onClick = { onSelect(ep) },
                label = { Text(ep) },
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

// ==========================================
// StarRatingBar
// ==========================================
@Composable
fun StarRatingBar(rating: Int, onRatingChanged: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 1..5) {
            IconButton(onClick = { onRatingChanged(i) }) {
                Icon(
                    imageVector = if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Star $i",
                    tint = if (i <= rating) getRatingColor(i) else RateColorEmpty,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// ==========================================
// GenreSelectionSection — collapsible categories (delegates to ExpandableGenreFlow)
// ==========================================
@Composable
fun GenreSelectionSection(
    strings: UiStrings,
    currentLanguage: AppLanguage,
    selectedTags: List<String>,
    activeCategory: String,
    onTagToggle: (String, String) -> Unit
) {
    val genreRepository: GenreRepository = koinInject()
    val animeGenres = remember { genreRepository.getGenresForCategory(GenreCategory.ANIME) }
    val movieGenres = remember { genreRepository.getGenresForCategory(GenreCategory.MOVIE) }
    val seriesGenres = remember { genreRepository.getGenresForCategory(GenreCategory.SERIES) }

    var expandedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
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
        "Anime" to Icons.Outlined.Animation,
        "Movies" to Icons.Outlined.Movie,
        "Series" to Icons.Outlined.Tv
    )

    val categories = listOf(
        GenreCategoryData("Anime", strings.genreAnime, animeGenres, Color(0xFFFF2D55)),
        GenreCategoryData("Movies", strings.genreMovies, movieGenres, Color(0xFF5AC8FA)),
        GenreCategoryData("Series", strings.genreSeries, seriesGenres, Color(0xFFFFCC00))
    )

    ExpandableGenreFlow(
        categories = categories,
        activeCategory = activeCategory,
        selectedTags = selectedTags,
        expandedCategoryKey = expandedCategory,
        onExpandedChange = { expandedCategory = it },
        onTagToggle = onTagToggle,
        currentLanguage = currentLanguage,
        header = { catData, isExpanded, hasSelected, selectedCount, onToggle ->
            val catIcon = categoryIcons[catData.key] ?: Icons.Outlined.Animation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggle)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    catIcon,
                    contentDescription = null,
                    tint = catData.accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = catData.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hasSelected) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(catData.accentColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "$selectedCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

// ==========================================
// GenreFilterPillSelection — оверлей «По жанрам» из Sort (pill-строки)
// ==========================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreFilterPillSelection(
    strings: UiStrings,
    currentLanguage: AppLanguage,
    selectedTags: List<String>,
    activeCategory: String,
    onTagToggle: (String, String) -> Unit
) {
    val genreRepository: GenreRepository = koinInject()
    val isDark = isAppInDarkTheme()
    val animeGenres = remember { genreRepository.getGenresForCategory(GenreCategory.ANIME) }
    val movieGenres = remember { genreRepository.getGenresForCategory(GenreCategory.MOVIE) }
    val seriesGenres = remember { genreRepository.getGenresForCategory(GenreCategory.SERIES) }

    var expandedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
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

    val categories = listOf(
        Triple("Anime", strings.genreFilterAnimeTitle, animeGenres),
        Triple("Movies", strings.genreFilterMoviesTitle, movieGenres),
        Triple("Series", strings.genreFilterSeriesTitle, seriesGenres)
    )

    val categoryIcons = mapOf(
        "Anime" to Icons.Outlined.AutoAwesome,
        "Movies" to Icons.Outlined.Movie,
        "Series" to Icons.Outlined.Tv
    )
    val categoryColors = mapOf(
        "Anime" to Color(0xFFFF2D55),
        "Movies" to Color(0xFF5AC8FA),
        "Series" to Color(0xFFFFCC00)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { (categoryType, label, genres) ->
            val isActive = activeCategory.isEmpty() || activeCategory.equals(categoryType, ignoreCase = true)
            val hasSelectedTags = selectedTags.any { tag -> genres.any { it.id == tag } }
            val isExpanded = expandedCategory == categoryType

            if (isActive || hasSelectedTags) {
                val accentColor = categoryColors[categoryType] ?: BrandBlue
                val catIcon = categoryIcons[categoryType] ?: Icons.Outlined.AutoAwesome
                val selectedCount = selectedTags.count { tag -> genres.any { it.id == tag } }
                val rowIconTint = when {
                    !isDark -> MaterialTheme.colorScheme.onSurface
                    categoryType == "Anime" -> Color.White
                    else -> accentColor
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    GenreFilterCategoryPillRow(
                        title = label,
                        accentColor = accentColor,
                        catIcon = catIcon,
                        iconTint = rowIconTint,
                        isExpanded = isExpanded,
                        hasSelectedTags = hasSelectedTags,
                        selectedCount = selectedCount,
                        isDark = isDark,
                        onClick = {
                            expandedCategory = if (isExpanded) null else categoryType
                        }
                    )
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            genres.forEach { genreDef ->
                                val isSelected = selectedTags.contains(genreDef.id)
                                val displayName = if (currentLanguage == AppLanguage.RU) genreDef.ru else genreDef.en
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onTagToggle(genreDef.id, categoryType) },
                                    label = { Text(displayName, fontSize = 13.sp) },
                                    enabled = true,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.15f),
                                        selectedLabelColor = accentColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreFilterCategoryPillRow(
    title: String,
    accentColor: Color,
    catIcon: ImageVector,
    iconTint: Color,
    isExpanded: Boolean,
    hasSelectedTags: Boolean,
    selectedCount: Int,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileBg =
        if (isDark) OverlayThemeTokens.TileBackgroundDark
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val glowAlpha = if (isDark) 0.26f else 0.14f
    val rim =
        if (isDark) OverlayThemeTokens.RimDark
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val capsuleHeight = 76.dp
    val capsuleShape = RoundedCornerShape(capsuleHeight / 2)
    val haloOuterAlpha = if (isDark) 0.62f else 0.45f
    val innerCircleBg =
        if (isDark) lerp(accentColor, Color.Black, 0.58f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(capsuleHeight)
            .clip(capsuleShape)
            .background(tileBg)
            .tileGlowLeading(accentColor, glowAlpha)
            .border(1.dp, rim, capsuleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = haloOuterAlpha),
                                accentColor.copy(alpha = 0.08f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(innerCircleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = catIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = SnProFamily,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (hasSelectedTags) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "$selectedCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp)
        )
    }
}
