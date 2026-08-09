package com.example.myapplication.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.details.DetailsScreen
import com.example.myapplication.ui.shared.theme.MotionTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

import com.example.myapplication.WelcomeScreen
import com.example.myapplication.ui.addedit.AddEditScreen
import com.example.myapplication.ui.addedit.AddEditViewModel
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.home.HomeViewModel
import com.example.myapplication.ui.inspect.InspectScreen
import com.example.myapplication.ui.inspect.InspectViewModel
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.settings.SettingsViewModel
import com.example.myapplication.ui.splash.SplashViewModel
import com.example.myapplication.ui.workspace.WorkspaceScreen
import com.example.myapplication.ui.splash.VetroSplashScreen
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.getWelcomeStrings
import com.example.myapplication.utils.systemAppLanguage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val SplashZoomMillis = 450

private fun splashExitTransition() =
    fadeOut(animationSpec = tween(SplashZoomMillis, easing = FastOutSlowInEasing)) +
        scaleOut(
            targetScale = 1.08f,
            animationSpec = tween(SplashZoomMillis, easing = FastOutSlowInEasing),
        )

private fun splashEnterZoom() =
    fadeIn(animationSpec = tween(SplashZoomMillis, easing = FastOutSlowInEasing)) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(SplashZoomMillis, easing = FastOutSlowInEasing),
        )

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    startDestination: Any = SplashRoute
) {
    val homeViewModel: HomeViewModel = koinViewModel()
    val addEditViewModel: AddEditViewModel = koinViewModel()
    val inspectViewModel: InspectViewModel = koinViewModel()

    val context = LocalContext.current
    val authRepository: com.example.myapplication.sync.supabase.AuthRepository = koinInject()

    LaunchedEffect(Unit) {
        homeViewModel.scheduleBackgroundWork(context)
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable<SplashRoute>(
                exitTransition = { splashExitTransition() },
                popExitTransition = { splashExitTransition() },
            ) {
                val splashViewModel: SplashViewModel = koinViewModel()
                val splashState by splashViewModel.uiState.collectAsStateWithLifecycle()
                val splashStrings = getStrings(systemAppLanguage())
                val legacyFolderLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    splashViewModel.onLegacyFolderSelected(uri)
                }
                VetroSplashScreen(
                    uiState = splashState,
                    migrationTitle = splashStrings.splashStorageMigrationTitle,
                    migrationSubtitle = splashStrings.splashStorageMigrationSubtitle,
                    jsonMigrationTitle = splashStrings.splashJsonMigrationTitle,
                    jsonMigrationSubtitle = splashStrings.splashJsonMigrationSubtitle,
                    legacyFolderTitle = splashStrings.splashLegacyFolderTitle,
                    legacyFolderSubtitle = splashStrings.splashLegacyFolderSubtitle,
                    legacyFolderAction = splashStrings.splashLegacyFolderAction,
                    legacyFolderSkip = splashStrings.splashLegacyFolderSkip,
                    cloudRestoreTitle = splashStrings.cloudRestoreTitle,
                    cloudRestoreSubtitle = splashStrings.cloudRestoreSubtitle,
                    onPickLegacyFolder = { legacyFolderLauncher.launch(null) },
                    onSkipLegacyFolder = { splashViewModel.skipLegacyFolderMigration() },
                    onSplashComplete = { nextRoute ->
                        when (nextRoute) {
                            "home" -> navController.navigate(HomeRoute) {
                                popUpTo(SplashRoute) { inclusive = true }
                            }
                            else -> navController.navigate(WelcomeRoute) {
                                popUpTo(SplashRoute) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable<WelcomeRoute>(
                enterTransition = {
                    if (initialState.destination.isSplashDestination()) splashEnterZoom()
                    else fadeIn(animationSpec = tween(300))
                },
            ) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val isUserSignedIn by authRepository.isUserSignedIn.collectAsStateWithLifecycle(initialValue = false)
                val welcomeStrings = getWelcomeStrings(systemAppLanguage())

                LaunchedEffect(isUserSignedIn) {
                    if (isUserSignedIn) {
                        navController.navigateToHome()
                    }
                }
                WelcomeScreen(
                    strings = welcomeStrings,
                    onGoogleSignInClick = {
                        scope.launch {
                            val result = authRepository.signInWithGoogle(context)
                            if (result.isFailure) {
                                val msg = result.exceptionOrNull()?.message ?: "?"
                                android.widget.Toast.makeText(
                                    context,
                                    welcomeStrings.loginFailedFormat.format(msg),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onGithubSignInClick = {
                        scope.launch {
                            val result = authRepository.signInWithGithub()
                            if (result.isFailure) {
                                val msg = result.exceptionOrNull()?.message ?: "?"
                                android.widget.Toast.makeText(
                                    context,
                                    welcomeStrings.loginFailedFormat.format(msg),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onForgotPasswordClick = { email ->
                        scope.launch {
                            val result = authRepository.resetPasswordForEmail(email)
                            if (result.isSuccess) {
                                android.widget.Toast.makeText(
                                    context,
                                    welcomeStrings.resetPasswordSuccessFormat.format(email),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val msg = result.exceptionOrNull()?.message ?: "?"
                                android.widget.Toast.makeText(
                                    context,
                                    welcomeStrings.resetPasswordErrorFormat.format(msg),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onEmailSignInClick = { email, password ->
                        scope.launch {
                            val result = authRepository.signInWithEmail(email, password)
                            if (result.isFailure) {
                                val msg = result.exceptionOrNull()?.message ?: "?"
                                android.widget.Toast.makeText(
                                    context,
                                    welcomeStrings.loginFailedFormat.format(msg),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onGuestClick = { 
                        authRepository.signInAsGuest()
                        navController.navigateToHome() 
                    }
                )
            }

            composable<HomeRoute>(
                enterTransition = {
                    if (initialState.destination.isSplashDestination()) splashEnterZoom()
                    else fadeIn(animationSpec = tween(300))
                },
                // «Вдавливание» под деталями (физика IosSheetScaffold): фон уезжает назад
                // и слегка гаснет, при закрытии деталей — физично возвращается. Predictive
                // back сикает popEnter — возврат следует за пальцем.
                exitTransition = {
                    if (targetState.destination.isDetailsDestination()) {
                        scaleOut(
                            targetScale = 0.92f,
                            animationSpec = MotionTokens.sheetPresent(),
                        ) + fadeOut(animationSpec = tween(300), targetAlpha = 0.55f)
                    } else null
                },
                popEnterTransition = {
                    if (initialState.destination.isDetailsDestination()) {
                        scaleIn(
                            initialScale = 0.92f,
                            animationSpec = MotionTokens.sheetDismissForced(),
                        ) + fadeIn(animationSpec = tween(220), initialAlpha = 0.55f)
                    } else if (initialState.destination.isSplashDestination()) {
                        splashEnterZoom()
                    } else {
                        fadeIn(animationSpec = tween(300))
                    }
                },
            ) {
                // ЕДИНСТВЕННАЯ точка чтения флага новой навигации: ниже по дереву его не знают.
                // Две навигации никогда не смонтированы одновременно — иначе задвоятся ключи
                // shared-element (иконки старого дока и экраны-цели).
                val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                if (settingsState.devSelectDockNavigation) {
                    WorkspaceScreen(
                        navController = navController,
                        homeViewModel = homeViewModel,
                        inspectViewModel = inspectViewModel,
                        settingsViewModel = settingsViewModel,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                    )
                } else {
                    HomeScreen(
                        navController = navController,
                        viewModel = homeViewModel,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                    )
                }
            }

            // Полноэкранные детали (iOS push): въезжают справа, назад — уезжают вправо.
            composable<DetailsRoute>(
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = MotionTokens.sheetOffset,
                    ) + fadeIn(animationSpec = tween(220))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = MotionTokens.dismissOffset,
                    ) + fadeOut(animationSpec = tween(220))
                },
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<DetailsRoute>()
                // Скругление краёв уезжающего окна (референс — Telegram): радиус растёт
                // по прогрессу перехода, predictive back сикает его вместе с жестом.
                val windowCorner by transition.animateDp(label = "detailsWindowCorner") { state ->
                    if (state == EnterExitState.Visible) 0.dp else 42.dp
                }
                DetailsScreen(
                    navController = navController,
                    animeId = route.animeId,
                    openEpisodes = route.openEpisodes,
                    modifier = Modifier.clip(RoundedCornerShape(windowCorner)),
                )
            }

            composable<AddEditRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AddEditRoute>()
                AddEditScreen(
                    navController = navController,
                    viewModel = addEditViewModel,
                    animeId = route.animeId,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    navController = navController,
                    viewModel = settingsViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    // Маршрут — значит есть куда возвращаться, и кнопка «назад» в шапке нужна.
                    // На странице рабочей области onBack не передают, и кнопки нет (TICKET-07).
                    onBack = { navController.popBackStack() },
                )
            }

            composable<InspectRoute> {
                InspectScreen(
                    navController = navController,
                    viewModel = inspectViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onBack = { navController.popBackStack() },
                )
            }

        }
    }
}
