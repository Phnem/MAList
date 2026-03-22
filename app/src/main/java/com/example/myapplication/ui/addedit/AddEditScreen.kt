package com.example.myapplication.ui.addedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.myapplication.isAppInDarkTheme
import com.example.myapplication.safeHaze
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import com.example.myapplication.ui.shared.components.StarRatingBar
import com.example.myapplication.ui.shared.InertialCollisionState
import com.example.myapplication.ui.shared.inertialCollision
import com.example.myapplication.ui.shared.rememberInertialCollisionState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeSource

/** Как у дока на главном: только blur, без tint и шума ([cleanHazeStyle] в glass.kt). */
private val AddEditSaveFabGlassStyle = HazeStyle(
    tints = emptyList(),
    noiseFactor = 0f,
    blurRadius = 20.dp
)

@Composable
private fun AnimatedFormRow(
    index: Int,
    collisionState: InertialCollisionState,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.inertialCollision(
            state = collisionState,
            index = index,
            baseMultiplier = 4.5f
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AddEditScreen(
    navController: NavController,
    viewModel: AddEditViewModel,
    animeId: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
    val strings = getStrings(currentLanguage)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        viewModel.onEvent(AddEditEvent.OnImageUriChanged(it))
    }
    val ctx = LocalContext.current
    val view = LocalView.current
    val textC = MaterialTheme.colorScheme.onBackground
    val collisionState = rememberInertialCollisionState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AddEditEffect.NavigateBack -> navController.popBackStack()
                is AddEditEffect.ShowError -> android.widget.Toast.makeText(
                    ctx, effect.message, android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(animeId) {
        viewModel.loadAnime(animeId)
        collisionState.triggerCollision(
            impactForce = 55f,
            stiffness = 200f,
            dampingRatio = 0.45f
        )
    }

    val commentHazeState = remember { HazeState() }

    with(sharedTransitionScope) {
        val sharedModifier = if (animeId == null) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "fab_container"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
            )
        } else {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "anime_${animeId}_bounds"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = textC,
                            modifier = if (animeId == null) Modifier.sharedElement(
                                rememberSharedContentState(key = "fab_icon"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ) else Modifier
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (animeId == null) strings.addTitle else strings.editTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textC
                    )
                }
            },
            floatingActionButton = {
                val fabAlpha by animateFloatAsState(
                    targetValue = if (uiState.isValid && !uiState.isLoading) 1f else 0.5f,
                    animationSpec = spring(dampingRatio = 0.7f),
                    label = "fabAlpha"
                )
                val fabBorderStroke =
                    if (isAppInDarkTheme()) Color.White.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.8f)
                val fabIconTint = Color.White
                Box(
                    modifier = Modifier
                        .inertialCollision(collisionState, index = 20, baseMultiplier = 2.5f)
                        .graphicsLayer { alpha = fabAlpha }
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .safeHaze(commentHazeState, AddEditSaveFabGlassStyle)
                            .border(0.5.dp, fabBorderStroke, CircleShape)
                            .clickable {
                                if (uiState.isLoading) return@clickable
                                performHaptic(view, "success")
                                if (uiState.isValid) {
                                    viewModel.onEvent(AddEditEvent.OnSave)
                                } else {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        strings.enterTitleToast,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = fabIconTint,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(sharedModifier)
                    .clip(RoundedCornerShape(32.dp))
                    .hazeSource(commentHazeState)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .imePadding()
                        .padding(horizontal = 24.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Cover photo ──
                    AnimatedFormRow(index = 0, collisionState = collisionState) {
                        Spacer(Modifier.height(8.dp))
                    }
                    AnimatedFormRow(index = 1, collisionState = collisionState) {
                        AddEditCoverPhotoSlot(
                            imageUri = uiState.imageUri,
                            imageFilePath = uiState.imageFilePath,
                            coverPhotoCta = strings.addEditCoverPhotoCta,
                            animeId = animeId,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                performHaptic(view, "light")
                                launcher.launch("image/*")
                            }
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Title ──
                    AnimatedFormRow(index = 2, collisionState = collisionState) {
                        AddEditSectionLabel(strings.addEditSectionTitle)
                    }
                    AnimatedFormRow(index = 3, collisionState = collisionState) {
                        PillTextFieldWithCopy(
                            value = uiState.title,
                            onValueChange = {
                                viewModel.onEvent(AddEditEvent.OnTitleChanged(it))
                            },
                            placeholder = strings.addEditTitlePlaceholder,
                            singleLine = false,
                            maxLines = 4
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Episodes ──
                    AnimatedFormRow(index = 4, collisionState = collisionState) {
                        AddEditSectionLabel(strings.addEditSectionEpisodes)
                    }
                    AnimatedFormRow(index = 5, collisionState = collisionState) {
                        PillTextField(
                            value = uiState.episodes,
                            onValueChange = {
                                viewModel.onEvent(AddEditEvent.OnEpisodesChanged(it))
                            },
                            placeholder = strings.addEditEpisodesPlaceholder,
                            keyboardType = KeyboardType.Number
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Quick select ──
                    AnimatedFormRow(index = 6, collisionState = collisionState) {
                        AddEditSectionLabel(strings.addEditSectionQuickSelect)
                    }
                    AnimatedFormRow(index = 7, collisionState = collisionState) {
                        AddEditEpisodeQuickSelect(
                            selectedEpisodes = uiState.episodes,
                            onSelect = { selectedEp ->
                                performHaptic(view, "light")
                                viewModel.onEvent(AddEditEvent.OnEpisodesChanged(selectedEp))
                            }
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Format category ──
                    AnimatedFormRow(index = 8, collisionState = collisionState) {
                        AddEditSectionLabel(strings.addEditSectionFormat)
                    }
                    AnimatedFormRow(index = 9, collisionState = collisionState) {
                        AddEditFormatCategorySection(
                            strings = strings,
                            currentLanguage = currentLanguage,
                            selectedTags = uiState.selectedTags,
                            activeCategory = uiState.categoryType,
                            onTagToggle = { tag, categoryType ->
                                val currentTags = uiState.selectedTags.toMutableList()
                                if (currentTags.contains(tag)) {
                                    currentTags.remove(tag)
                                    if (currentTags.isEmpty()) {
                                        viewModel.onEvent(
                                            AddEditEvent.OnTagsChanged(emptyList(), "")
                                        )
                                    } else {
                                        viewModel.onEvent(
                                            AddEditEvent.OnTagsChanged(
                                                currentTags,
                                                uiState.categoryType
                                            )
                                        )
                                    }
                                } else {
                                    val categoryMatches = uiState.categoryType.isEmpty() ||
                                            uiState.categoryType.equals(
                                                categoryType,
                                                ignoreCase = true
                                            )
                                    if (currentTags.size < 5 && categoryMatches) {
                                        currentTags.add(tag)
                                        viewModel.onEvent(
                                            AddEditEvent.OnTagsChanged(currentTags, categoryType)
                                        )
                                    }
                                }
                                performHaptic(view, "light")
                            }
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // ── Rating ──
                    AnimatedFormRow(index = 10, collisionState = collisionState) {
                        AddEditSectionLabel(strings.addEditSectionRating)
                    }
                    AnimatedFormRow(index = 11, collisionState = collisionState) {
                        StarRatingBar(rating = uiState.rating) { newRate ->
                            performHaptic(view, "light")
                            viewModel.onEvent(AddEditEvent.OnRatingChanged(newRate))
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Comment ──
                    AnimatedFormRow(index = 12, collisionState = collisionState) {
                        AddEditSectionLabel(strings.addEditSectionComment)
                    }
                    AnimatedFormRow(index = 13, collisionState = collisionState) {
                        CommentMorphingContainer(
                            state = uiState,
                            hazeState = commentHazeState,
                            onModeChange = {
                                viewModel.onEvent(AddEditEvent.OnCommentModeChanged(it))
                            },
                            onSaveComment = {
                                viewModel.onEvent(AddEditEvent.OnSaveComment(it))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }
}
