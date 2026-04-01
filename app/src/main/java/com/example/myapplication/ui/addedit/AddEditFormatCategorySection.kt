package com.example.myapplication.ui.addedit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myapplication.data.models.UiStrings
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.ui.shared.components.FormatCategoryTilesWithGenres

@Composable
fun AddEditFormatCategorySection(
    strings: UiStrings,
    currentLanguage: AppLanguage,
    selectedTags: List<String>,
    activeCategory: String,
    onTagToggle: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    FormatCategoryTilesWithGenres(
        saveableStateKey = "add_edit_format_genres",
        strings = strings,
        currentLanguage = currentLanguage,
        selectedTags = selectedTags,
        activeCategory = activeCategory,
        onTagToggle = onTagToggle,
        modifier = modifier
    )
}
