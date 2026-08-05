package com.example.myapplication.localplayer.ui

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/** Сколько плашка уровня висит после того, как палец оторвали. */
private const val LEVEL_HUD_HOLD_MS = 700L

/**
 * Оценка системной яркости на случай, когда её не удалось прочитать. Ровно середина: любое
 * додумывание тут всё равно живёт до первого свайпа, который задаёт яркость явно.
 */
private const val BRIGHTNESS_FALLBACK = 0.5f

/**
 * Громкость и яркость под вертикальными свайпами по кадру.
 *
 * Две ручки в одном объекте, потому что жест у них один и тот же (см. [verticalZoneAt]) и плашка
 * поверх кадра тоже одна: показывать обе разом невозможно — свайп начинается либо слева, либо
 * справа.
 *
 * Разница между ними — в том, куда пишется значение:
 *
 * - **Громкость** уходит в системный `STREAM_MUSIC`, то есть переживает плеер и видна остальной
 *   системе. Шкала у неё дискретная ([volumeSteps] ступеней), поэтому наружу отдаётся уже
 *   округлённое значение — см. [quantizeLevel].
 * - **Яркость** ставится атрибутом окна и умирает вместе с активностью. Это осознанно: плеер
 *   притушивает кадр на время просмотра, а не перекручивает настройку телефона.
 */
@Stable
class PlayerLevels internal constructor(
    private val window: Window?,
    private val audio: AudioManager?,
    private val systemBrightness: Float,
) {
    /** Сколько делений у системной громкости. 0 = аудио недоступно, жест ничего не делает. */
    val volumeSteps: Int = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    var volume by mutableFloatStateOf(readSystemVolume())
        private set

    var brightness by mutableFloatStateOf(systemBrightness)
        private set

    /** Что регулируется прямо сейчас; `null` — плашку показывать не надо. */
    var hud by mutableStateOf<VerticalZone?>(null)
        private set

    /** Палец ещё на экране: пока это так, плашка не гаснет, сколько бы ни длился свайп. */
    var hudPinned by mutableStateOf(false)
        private set

    /** Уровень, который сейчас показывает плашка. */
    fun levelOf(zone: VerticalZone): Float = when (zone) {
        VerticalZone.Volume -> volume
        VerticalZone.Brightness -> brightness
    }

    /**
     * Начало вертикального свайпа. Громкость перечитывается из системы: её могли покрутить
     * аппаратными клавишами или другим приложением, и продолжать от своего старого значения
     * значило бы швырнуть звук скачком.
     */
    fun begin(zone: VerticalZone) {
        if (zone == VerticalZone.Volume) volume = readSystemVolume()
        hud = zone
        hudPinned = true
    }

    /** Сдвиг уровня на [delta] (в долях полного диапазона). */
    fun nudge(zone: VerticalZone, delta: Float) {
        when (zone) {
            VerticalZone.Volume -> {
                val next = quantizeLevel(volume + delta, volumeSteps)
                if (next != volume) {
                    volume = next
                    // Без FLAG_SHOW_UI: системная шторка громкости поверх кадра — ровно то, что
                    // заменяет собственная плашка.
                    runCatching {
                        audio?.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (next * volumeSteps).toInt(),
                            0,
                        )
                    }
                }
            }
            VerticalZone.Brightness -> {
                val next = (brightness + delta).coerceIn(0f, 1f)
                if (next != brightness) {
                    brightness = next
                    applyWindowBrightness(next)
                }
            }
        }
        hud = zone
    }

    /** Палец оторван: плашка досматривает свои [LEVEL_HUD_HOLD_MS] и уходит. */
    fun release() {
        hudPinned = false
    }

    internal fun hideHud() {
        hud = null
    }

    /**
     * Вернуть окну системную яркость.
     *
     * Обязательно при уходе плеера: стриминговый плеер живёт в главной активности, и притушенный
     * ради тёмной сцены экран иначе остался бы притушенным на всё приложение.
     */
    internal fun releaseWindowBrightness() {
        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }

    private fun readSystemVolume(): Float {
        val manager = audio ?: return 0f
        if (volumeSteps <= 0) return 0f
        val current = runCatching {
            manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(0)
        return (current.toFloat() / volumeSteps).coerceIn(0f, 1f)
    }

    private fun applyWindowBrightness(value: Float) {
        val w = window ?: return
        w.attributes = w.attributes.apply { screenBrightness = value }
    }
}

/**
 * [PlayerLevels], привязанный к окну текущей активности.
 *
 * Таймер скрытия плашки живёт здесь, а не в состоянии: перезапускать его надо не на каждый пиксель
 * свайпа, а один раз — когда палец оторвали, — и ключ [PlayerLevels.hudPinned] делает это сам.
 */
@Composable
fun rememberPlayerLevels(): PlayerLevels {
    val context = LocalView.current.context
    val levels = remember(context) {
        PlayerLevels(
            window = context.findActivity()?.window,
            audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
            systemBrightness = context.readSystemBrightness(),
        )
    }
    LaunchedEffect(levels.hud, levels.hudPinned) {
        if (levels.hud != null && !levels.hudPinned) {
            delay(LEVEL_HUD_HOLD_MS)
            levels.hideHud()
        }
    }
    DisposableEffect(levels) {
        onDispose { levels.releaseWindowBrightness() }
    }
    return levels
}

/**
 * Стартовая яркость окна — оценкой по системной настройке.
 *
 * Точного значения не существует: у окна яркости нет, пока её не задали (там лежит
 * `BRIGHTNESS_OVERRIDE_NONE`), а шкала `SCREEN_BRIGHTNESS` у части прошивок не 0..255. Поэтому это
 * именно оценка — она нужна лишь для того, чтобы первый свайп
 * пошёл примерно от той яркости, которую человек видит, а не от нуля или максимума.
 */
private fun Context.readSystemBrightness(): Float {
    val raw = runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull() ?: return BRIGHTNESS_FALLBACK
    return (raw / 255f).coerceIn(0.01f, 1f)
}
