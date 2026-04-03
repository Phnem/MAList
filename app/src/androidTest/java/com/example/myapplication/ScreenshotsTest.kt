package com.example.myapplication

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * ==========================================
 * АВТОМАТИЧЕСКАЯ ГЕНЕРАЦИЯ СКРИНШОТОВ (Fastlane)
 * ==========================================
 *
 * Этот тест реализует функционал "Скриншоты приложения", запрошенный в ТЗ.
 * Он предназначен для запуска через Fastlane Screengrab, а не через кнопку в UI приложения.
 *
 * Команда для запуска (Terminal):
 * ./gradlew screengrab
 *
 * Требования:
 * 1. Зависимость `tools.fastlane:screengrab:x.x.x` в build.gradle
 * 2. Разрешения:
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
 */

@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {

    // Правило для работы с Compose в тестах
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // Правило для автоматической смены локалей (RU, EN) через fastlane
    @get:Rule
    val localeTestRule: LocaleTestRule = LocaleTestRule()

    @Test
    fun takeScreenshots() {
        // 1. Ждем загрузки контента
        // В реальном сценарии здесь можно добавить мок-данные в ViewModel,
        // чтобы скриншоты всегда были красивыми и одинаковыми.
        composeTestRule.waitForIdle()

        // Небольшая задержка для анимаций входа
        Thread.sleep(1000)

        // 2. Скриншот Главного экрана
        Screengrab.screenshot("01_home_screen")

        // 3. Открываем настройки (через навигацию или клик, если нужно)
        // В данном примере ограничимся главным экраном, так как это основной flow.
        // Для скриншота настроек можно добавить:
        // composeTestRule.onNodeWithContentDescription("Settings").performClick()
        // Screengrab.screenshot("02_settings_screen")
    }
}