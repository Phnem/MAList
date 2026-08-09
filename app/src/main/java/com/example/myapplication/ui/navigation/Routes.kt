package com.example.myapplication.ui.navigation

import androidx.navigation.NavDestination
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Type-safe маршруты (Navigation Compose 2.8+). ID аниме — String. */
@Serializable
data object SplashRoute

@Serializable
data object WelcomeRoute

@Serializable
data object HomeRoute

@Serializable
data class DetailsRoute(val animeId: String, val openEpisodes: Boolean = false)

@Serializable
data class AddEditRoute(val animeId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data object InspectRoute

/**
 * Определяет, что текущий destination — [SplashRoute], независимо от формата строки route
 * (kotlinx.serialization + Navigation Compose).
 */
fun NavDestination?.isSplashDestination(): Boolean {
    val route = this?.route ?: return false
    val serialName = SplashRoute.serializer().descriptor.serialName
    return route == serialName ||
        route.endsWith(".SplashRoute") ||
        route.contains("SplashRoute")
}

/**
 * Граф дошёл до основного экрана и на него можно push'ить [DetailsRoute]
 * (тап по пушу о новой серии). На сплэше и логине навигация ещё бессмысленна.
 */
fun NavDestination?.isDeepLinkReady(): Boolean {
    val route = this?.route ?: return false
    return !isSplashDestination() && !route.contains("WelcomeRoute")
}

/** Определяет, что destination — [DetailsRoute] (для эффекта «вдавливания» Home под деталями). */
fun NavDestination?.isDetailsDestination(): Boolean {
    val route = this?.route ?: return false
    return route.contains("DetailsRoute")
}
