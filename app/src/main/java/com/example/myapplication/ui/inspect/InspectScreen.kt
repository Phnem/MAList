package com.example.myapplication.ui.inspect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image as BgImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.myapplication.R
import com.example.myapplication.domain.inspect.InspectContentMode
import com.example.myapplication.ui.home.ApiSearchResultCard
import com.example.myapplication.ui.shared.theme.InspectVisualSearchTheme
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.performHaptic
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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
    val inspectHazeState = remember { HazeState() }

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
                            onSelect = { index ->
                                performHaptic(view, "light")
                                when (index) {
                                    0 -> viewModel.setContentMode(InspectContentMode.Anime)
                                    1 -> viewModel.setContentMode(InspectContentMode.MoviesSeries)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
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
                                                    displayGenres = null,
                                                    addLabel = strings.addButton,
                                                    addedLabel = strings.addedButton,
                                                    onAddClick = {
                                                        performHaptic(view, "light")
                                                        viewModel.addFromApi(r)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
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
