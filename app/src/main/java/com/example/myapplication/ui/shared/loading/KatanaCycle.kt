package com.example.myapplication.ui.shared.loading

import kotlin.math.exp
import kotlin.math.sin

/**
 * Форма движения полноэкранного индикатора загрузки: катана выходит из ножен, остриём очерчивает
 * окружность, возвращается в ножны, пауза — и всё сначала.
 *
 * Здесь только время: чистые функции нормированной фазы цикла `p ∈ [0,1)`. Ни геометрии, ни
 * Compose — иначе форму движения нельзя было бы проверить ничем, кроме глаза. Границы фаз и
 * ключевое свойство «к концу оборота катана снова поравнялась с ножнами» закреплены тестами
 * (`KatanaCycleTest`).
 *
 * Инерция ножен смоделирована аналитически (затухающая синусоида от фазы), а не интегратором
 * пружины: анимация обязана выглядеть одинаково независимо от частоты кадров и быть
 * воспроизводимой в тесте.
 */
object KatanaCycle {

    /** Конец вытягивания: 0–20 % цикла. */
    const val DRAW_END = 0.20f

    /** Конец оборота: 20–70 % цикла. */
    const val ORBIT_END = 0.70f

    /** Конец возврата в ножны: 70–90 %. Дальше до 100 % — пауза. */
    const val SHEATHE_END = 0.90f

    /** Длительность полного цикла. Единственная ручка «быстрее/медленнее». */
    const val CYCLE_MILLIS = 2600

    /** Насколько ножны утягивает вслед за клинком, в долях хода вытягивания. */
    private const val AXIAL_LAG = 0.14f

    /** Возврат мягче рывка наружу: клинок входит в ножны, а не вырывается из них. */
    private const val AXIAL_LAG_RETURN_SCALE = 0.8f

    /**
     * Полтора периода на фазу: колебание начинается и заканчивается точно в нуле, поэтому на
     * стыке фаз ножны не прыгают.
     */
    private const val AXIAL_LAG_HALF_WAVES = 3f

    private const val AXIAL_LAG_DAMPING = 4.2f

    /** Максимальное угловое отставание ножен на обороте, в долях полного оборота (~12°). */
    private const val ANGULAR_LAG_TURNS = 0.035f

    private const val PI_F = Math.PI.toFloat()

    /**
     * Насколько катана выдвинута из ножен: 0 — вложена, 1 — вынута полностью.
     * На обороте держится на 1, в паузе — на 0.
     */
    fun drawOut(p: Float): Float {
        val t = p.coerceIn(0f, 1f)
        return when {
            t < DRAW_END -> easeInOutCubic(t / DRAW_END)
            t <= ORBIT_END -> 1f
            t < SHEATHE_END -> 1f - easeInOutCubic((t - ORBIT_END) / (SHEATHE_END - ORBIT_END))
            else -> 0f
        }
    }

    /**
     * Оборот катаны вокруг центра, в полных оборотах: 0 до начала вращения, ровно 1 после.
     *
     * Именно целое число оборотов гарантирует, что к моменту возврата катана смотрит туда же,
     * куда ножны, — иначе она входила бы в них под углом.
     */
    fun orbitTurns(p: Float): Float {
        val t = p.coerceIn(0f, 1f)
        return when {
            t <= DRAW_END -> 0f
            t >= ORBIT_END -> 1f
            else -> easeInOutSine((t - DRAW_END) / (ORBIT_END - DRAW_END))
        }
    }

    /**
     * Доля очерченной окружности. Совпадает с оборотом по построению: линию оставляет остриё, и
     * разъехаться они не могут.
     */
    fun arcSweep(p: Float): Float = orbitTurns(p)

    /** Непрозрачность окружности: гаснет ровно за то время, пока катана возвращается в ножны. */
    fun arcAlpha(p: Float): Float {
        val t = p.coerceIn(0f, 1f)
        return when {
            t <= ORBIT_END -> 1f
            t >= SHEATHE_END -> 0f
            else -> 1f - easeInOutCubic((t - ORBIT_END) / (SHEATHE_END - ORBIT_END))
        }
    }

    /**
     * Осевой сдвиг ножен, в долях хода вытягивания. Положительное — наружу, вслед за уходящим
     * клинком; отрицательное — внутрь, по ходу возврата. Затухает к концу своей фазы.
     */
    fun scabbardAxialLag(p: Float): Float {
        val t = p.coerceIn(0f, 1f)
        return when {
            t < DRAW_END -> dampedSwing(t / DRAW_END) * AXIAL_LAG
            t > ORBIT_END && t < SHEATHE_END ->
                -dampedSwing((t - ORBIT_END) / (SHEATHE_END - ORBIT_END)) *
                    AXIAL_LAG * AXIAL_LAG_RETURN_SCALE
            else -> 0f
        }
    }

    /**
     * Угловое отставание ножен на обороте, в долях оборота. Отрицательное — ножны позади катаны.
     *
     * Пропорционально угловой скорости: чем быстрее катана идёт по кругу, тем сильнее волочатся
     * ножны. На концах фазы скорость нулевая, значит, к моменту вкладывания отставания нет.
     */
    fun scabbardAngularLag(p: Float): Float {
        val t = p.coerceIn(0f, 1f)
        if (t <= DRAW_END || t >= ORBIT_END) return 0f
        val x = (t - DRAW_END) / (ORBIT_END - DRAW_END)
        // Нормированная производная easeInOutSine — sin(πx), максимум 1 в середине фазы.
        return -ANGULAR_LAG_TURNS * sin(PI_F * x)
    }

    /** Затухающее колебание: 0 на обоих концах отрезка, амплитуда каждого следующего меньше. */
    private fun dampedSwing(x: Float): Float =
        sin(PI_F * AXIAL_LAG_HALF_WAVES * x) * exp(-AXIAL_LAG_DAMPING * x)

    private fun easeInOutCubic(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return if (t < 0.5f) 4f * t * t * t else 1f - cube(-2f * t + 2f) / 2f
    }

    private fun easeInOutSine(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        // 0.5·(1 − cos πt) через sin², чтобы не тащить cos ради одной строки.
        val s = sin(PI_F * t / 2f)
        return s * s
    }

    private fun cube(v: Float): Float = v * v * v
}
