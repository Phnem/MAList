package com.example.myapplication.ui.inspect

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image as BgImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.phnem.vetro.R
import com.example.myapplication.domain.inspect.InspectContentMode
import com.example.myapplication.ui.home.ApiSearchResultCard
import com.example.myapplication.ui.shared.theme.InspectVisualSearchTheme
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun InspectScreen(
    navController: NavController,
    viewModel: InspectViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lang by viewModel.uiLanguage.collectAsStateWithLifecycle()
    val strings = getStrings(lang)
    val contentMode by viewModel.contentMode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val addingId by viewModel.addingFromApiId.collectAsStateWithLifecycle()
    val geminiKeyState by viewModel.geminiKeyUiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val inspectHazeState = remember { HazeState() }
    val requiresGeminiSetup =
        contentMode == InspectContentMode.MoviesSeries && !geminiKeyState.hasValidSavedKey
    val onSelectMode: (Int) -> Unit = { index ->
        performHaptic(view, "light")
        when (index) {
            0 -> viewModel.setContentMode(InspectContentMode.Anime)
            1 -> viewModel.setContentMode(InspectContentMode.MoviesSeries)
        }
    }

    val vignetteBrush = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 0.82f),
                0.24f to Color.Black.copy(alpha = 0.10f),
                0.76f to Color.Black.copy(alpha = 0.10f),
                1f to Color.Black.copy(alpha = 0.88f)
            )
        )
    }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.analyzeImage(context, it) }
    }

    DisposableEffect(lifecycleOwner, windowInfo.isWindowFocused, contentMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_RESUME &&
                contentMode == InspectContentMode.MoviesSeries &&
                windowInfo.isWindowFocused
            ) {
                val clipboardText = runCatching {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    manager?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                }.getOrNull()
                viewModel.tryImportGeminiKeyFromClipboard(
                    isWindowFocused = windowInfo.isWindowFocused,
                    clipboardText = clipboardText
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "inspect_container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(32.dp))
                )
                .clip(RoundedCornerShape(32.dp))
                .hazeSource(inspectHazeState)
        ) {
            InspectVisualSearchTheme {
                val cardShape = RoundedCornerShape(22.dp)
                val outlineMuted = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

                Box(modifier = Modifier.fillMaxSize()) {
                    BgImage(
                        painter = painterResource(R.drawable.vsbg),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(vignetteBrush)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .pointerInput(contentMode) {
                                var totalHorizontalDrag = 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        totalHorizontalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (abs(totalHorizontalDrag) > 56f) {
                                            when {
                                                totalHorizontalDrag < 0 && contentMode == InspectContentMode.Anime -> {
                                                    onSelectMode(1)
                                                }
                                                totalHorizontalDrag > 0 && contentMode == InspectContentMode.MoviesSeries -> {
                                                    onSelectMode(0)
                                                }
                                            }
                                        }
                                        totalHorizontalDrag = 0f
                                    },
                                    onDragCancel = { totalHorizontalDrag = 0f }
                                )
                            }
                    ) {
                        InspectHeader(
                            toolbarTitle = strings.inspectVisualSearchToolbarTitle,
                            brandLabel = strings.appName.uppercase(),
                            onBack = {
                                performHaptic(view, "light")
                                navController.popBackStack()
                            },
                            backContentDescription = strings.inspectBack,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = strings.inspectTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            textAlign = TextAlign.Start
                        )
                        VisualSearchSegmentedControl(
                            options = listOf(
                                strings.inspectModeAnime,
                                strings.inspectSegmentMoviesTv
                            ),
                            selectedIndex = if (contentMode == InspectContentMode.Anime) 0 else 1,
                            onSelect = onSelectMode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (requiresGeminiSetup) {
                            AnimatedContent(
                                targetState = geminiKeyState.onboardingStep,
                                transitionSpec = {
                                    val initialIndex = initialState.ordinal
                                    val targetIndex = targetState.ordinal
                                    if (targetIndex > initialIndex) {
                                        (slideInHorizontally { fullWidth -> fullWidth } + fadeIn())
                                            .togetherWith(slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut())
                                    } else {
                                        (slideInHorizontally { fullWidth -> -fullWidth } + fadeIn())
                                            .togetherWith(slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                label = "GeminiOnboardingStep"
                            ) { step ->
                                when (step) {
                                    MoviesTvOnboardingStep.Instruction -> GeminiInstructionCard(
                                        strings = strings,
                                        onOpenAiStudio = {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://aistudio.google.com/app/apikey")
                                                )
                                            )
                                        },
                                        onNext = {
                                            performHaptic(view, "light")
                                            viewModel.openGeminiKeyInputStep()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    MoviesTvOnboardingStep.KeyInput -> GeminiKeySetupCard(
                                        input = geminiKeyState.input,
                                        status = geminiKeyState.status,
                                        statusDetail = geminiKeyState.statusDetail,
                                        onInputChanged = viewModel::onGeminiKeyInputChanged,
                                        onBack = {
                                            performHaptic(view, "light")
                                            viewModel.returnToGeminiInstruction()
                                        },
                                        onCheckAndSaveKey = {
                                            performHaptic(view, "light")
                                            viewModel.checkAndSaveGeminiKey()
                                        },
                                        strings = strings,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    MoviesTvOnboardingStep.CheckError -> GeminiCheckErrorCard(
                                        strings = strings,
                                        details = geminiKeyState.statusDetail,
                                        onNext = {
                                            performHaptic(view, "light")
                                            viewModel.returnToGeminiInstruction()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(268.dp)
                                    .padding(horizontal = 16.dp)
                                    .clip(cardShape)
                                    .border(1.dp, outlineMuted, cardShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f))
                                    .clickable {
                                        performHaptic(view, "light")
                                        pickLauncher.launch("image/*")
                                    }
                            ) {
                                when {
                                    selectedUri != null -> {
                                        AsyncImage(
                                            model = selectedUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = {
                                                performHaptic(view, "light")
                                                viewModel.clearPreviewAndResults()
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp)
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                                                )
                                        ) {
                                            Icon(
                                                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                                contentDescription = strings.inspectClearPhoto,
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    else -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                                    .sharedElement(
                                                        sharedContentState = rememberSharedContentState(key = "inspect_icon"),
                                                        animatedVisibilityScope = animatedVisibilityScope
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Image,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Text(
                                                text = strings.inspectPickScreenshot,
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = strings.inspectImageFormatsHint,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (!requiresGeminiSetup) {
                                Text(
                                    text = strings.inspectPoweredByFooter,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .zIndex(0f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(1f)
                                ) {
                                    when (val s = uiState) {
                                        is InspectUiState.Loading -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = s.message,
                                                        color = MaterialTheme.colorScheme.onBackground,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(horizontal = 24.dp)
                                                    )
                                                }
                                            }
                                        }

                                        is InspectUiState.Error -> {
                                            Text(
                                                text = s.message,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.padding(24.dp)
                                            )
                                        }

                                        is InspectUiState.Idle -> { /* empty */ }
                                        is InspectUiState.Success -> {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 56.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(
                                                    items = s.results,
                                                    key = { m ->
                                                        "${m.result.source}_${m.result.externalId ?: m.result.title}"
                                                    }
                                                ) { uiModel ->
                                                    val r = uiModel.result
                                                    val addKey = "${r.source}_${r.externalId ?: r.title}"
                                                    ApiSearchResultCard(
                                                        result = r,
                                                        isAdded = uiModel.isAdded,
                                                        isLoading = addingId == addKey,
                                                        onAddClick = {
                                                            performHaptic(view, "light")
                                                            viewModel.addFromApi(r)
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        displayGenres = null,
                                                        addLabel = strings.addButton,
                                                        addedLabel = strings.addedButton,
                                                        forceDarkCardStyle = true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiInstructionCard(
    strings: com.example.myapplication.data.models.UiStrings,
    onOpenAiStudio: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = strings.inspectGeminiInstructionTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = strings.inspectGeminiInstructionBody,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
        Text(
            "1. ${strings.inspectGeminiInstructionStepOpen}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Text(
            "2. ${strings.inspectGeminiInstructionStepCreate}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Text(
            "3. ${strings.inspectGeminiInstructionStepCopy}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        TextButton(
            onClick = onOpenAiStudio,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(strings.inspectGeminiGetKeyOneClick)
        }
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.inspectGeminiNextStep)
        }
    }
}

@Composable
private fun GeminiKeySetupCard(
    input: String,
    status: GeminiKeyStatus?,
    statusDetail: String?,
    onInputChanged: (String) -> Unit,
    onBack: () -> Unit,
    onCheckAndSaveKey: () -> Unit,
    strings: com.example.myapplication.data.models.UiStrings,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.inspectBack,
                    tint = Color.White
                )
            }
            Text(
                text = strings.inspectGeminiKeyTitle,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChanged,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            placeholder = {
                Text(
                    text = strings.inspectGeminiKeyInputPlaceholder,
                    color = Color.White.copy(alpha = 0.6f)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.8f),
                cursorColor = Color.White
            )
        )
        Text(
            text = strings.inspectGeminiTenSecondsHint,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
        Button(
            onClick = onCheckAndSaveKey,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(strings.inspectGeminiCheckAndSave)
        }

        val statusMessage = when (status) {
            GeminiKeyStatus.InvalidFormat -> strings.inspectGeminiInvalidFormat
            GeminiKeyStatus.Saved -> strings.inspectGeminiSaved
            GeminiKeyStatus.CheckFailed -> statusDetail ?: strings.inspectGeminiSaveError
            GeminiKeyStatus.Checking -> strings.inspectGeminiCheckingKey
            GeminiKeyStatus.InsertedFromClipboard -> strings.inspectGeminiKeyFoundInserted
            null -> null
        }
        if (statusMessage != null) {
            Text(
                text = statusMessage,
                color = if (status == GeminiKeyStatus.InvalidFormat || status == GeminiKeyStatus.CheckFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun GeminiCheckErrorCard(
    strings: com.example.myapplication.data.models.UiStrings,
    details: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f), shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = strings.inspectGeminiCheckErrorTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        if (!details.isNullOrBlank()) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.inspectGeminiBackToInstruction)
        }
    }
}
