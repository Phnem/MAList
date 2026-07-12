package com.example.myapplication.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.ui.details.DetailsSheetContent
import com.example.myapplication.ui.shared.components.IosSheetScaffold
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.myapplication.ui.splash.SplashState
import com.example.myapplication.ui.splash.SplashViewModel
import com.example.myapplication.ui.splash.VetroSplashScreen
import com.example.myapplication.network.AppLanguage
import com.example.myapplication.utils.getStrings
import com.example.myapplication.utils.getWelcomeStrings
import com.example.myapplication.utils.systemAppLanguage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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

    // Детали открываются как iOS bottom sheet поверх всего навстека (фон «вдавливается»),
    // а не как отдельный экран навигации.
    var detailsAnimeId by rememberSaveable { mutableStateOf<String?>(null) }

    // Жест/кнопка «назад» при открытой шторке деталей должны закрывать шторку с фирменной
    // анимацией scaffold'а, а не проваливаться в Activity (иначе — выход из приложения).
    androidx.activity.compose.BackHandler(enabled = detailsAnimeId != null) {
        detailsAnimeId = null
    }

    SharedTransitionLayout {
      IosSheetScaffold(
        sheetVisible = detailsAnimeId != null,
        onDismiss = { detailsAnimeId = null },
        // Панель по высоте контента (medium-детент ~60%), а не фикс. 92% — шапка сразу над
        // блоком постера, без пустого поля сверху.
        sheetHeightFraction = null,
        sheetContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        sheetContent = {
            detailsAnimeId?.let { id ->
                DetailsSheetContent(animeId = id, onDismiss = { detailsAnimeId = null })
            }
        },
        content = {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable<SplashRoute> {
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

            composable<WelcomeRoute> {
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val isUserSignedIn by authRepository.isUserSignedIn.collectAsStateWithLifecycle(initialValue = false)
                val welcomeStrings = getWelcomeStrings(systemAppLanguage())
                
                androidx.compose.runtime.LaunchedEffect(isUserSignedIn) {
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

            composable<HomeRoute> {
                HomeScreen(
                    navController = navController,
                    viewModel = homeViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onOpenDetails = { detailsAnimeId = it },
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
                    animatedVisibilityScope = this
                )
            }

            composable<InspectRoute> {
                InspectScreen(
                    navController = navController,
                    viewModel = inspectViewModel,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this
                )
            }

        }
        },
      )
    }
}
