package com.example.myapplication.domain.enrichment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiThrottlePolicyTest {

    private val min5 = TimeUnit.MINUTES.toMillis(5)
    private val min15 = TimeUnit.MINUTES.toMillis(15)
    private val min30 = TimeUnit.MINUTES.toMillis(30)
    private val min60 = TimeUnit.MINUTES.toMillis(60)

    @Test
    fun no_residual_no_delay() {
        assertEquals(0L, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 0, providerCount = 1))
        assertEquals(0L, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = -3, providerCount = 2))
    }

    @Test
    fun small_residual_short_delay() {
        // ≤3 остатка — 15 минут (одиночный провайдер).
        assertEquals(min15, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 2, providerCount = 1))
        assertEquals(min15, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 3, providerCount = 1))
    }

    @Test
    fun larger_residual_longer_delay() {
        assertEquals(min30, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 7, providerCount = 1))
        assertEquals(min60, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 25, providerCount = 1))
    }

    @Test
    fun multiple_providers_divide_delay_but_respect_floor() {
        // 30 мин / 2 провайдера = 15 мин.
        assertEquals(min15, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 7, providerCount = 2))
        // 15 мин / 4 = 3.75 мин → зажимается снизу до 5 мин.
        assertEquals(min5, AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 2, providerCount = 4))
    }

    @Test
    fun zero_provider_count_treated_as_one() {
        assertTrue(AiThrottlePolicy.delayBetweenAiCallsMs(residualAiCount = 2, providerCount = 0) >= min5)
    }
}
