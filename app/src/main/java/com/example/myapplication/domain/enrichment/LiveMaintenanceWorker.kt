package com.example.myapplication.domain.enrichment

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.data.ai.AiCredentialsStore
import com.example.myapplication.data.local.AnimeLocalDataSource
import com.example.myapplication.data.models.Anime
import com.example.myapplication.domain.settings.RepairAnimeDbUseCase
import com.example.myapplication.domain.settings.RepairDbSessionLog
import com.example.myapplication.domain.titles.AiTitleTranslationUseCase
import com.example.myapplication.domain.titles.RussianTitleEnrichmentUseCase
import com.example.myapplication.domain.titles.TitleEnrichmentUseCase
import com.example.myapplication.network.AppContentType
import com.example.myapplication.network.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * Live Maintenance: инкрементальное обогащение в фоне (периодика 6 ч + мгновенный догон при
 * добавлении записи), работает даже когда приложение закрыто.
 *
 * Заход:
 *  1. Скан пробелов ([CollectionGapDetector]).
 *  2. Порог: > [CollectionEnrichmentCoordinator.OVERFLOW_THRESHOLD] записей с пробелами → фон НЕ
 *     обрабатывает, поднимает незакрываемый диалог «сделайте полное обогащение» и выходит.
 *  3. Иначе закрывает «дешёвые» пробелы бесплатным API (поля + EN/RU названия) небольшой партией.
 *  4. Остаток, требующий AI-перевода EN, отдаёт по ОДНОМУ за заход и растягивает через
 *     [AiThrottlePolicy] (self-reschedule), чтобы не выжечь квоту.
 *  5. Пробелы, что не закрылись ничем, пишутся в журнал (полевые) / помечаются checked (названия) —
 *     чтобы следующий скан их не перепроверял до истечения TTL.
 */
class LiveMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val settingsDataStore: DataStore<Preferences> by inject(named("settings"))
    private val coordinator: CollectionEnrichmentCoordinator by inject()
    private val gapDetector: CollectionGapDetector by inject()
    private val repairUseCase: RepairAnimeDbUseCase by inject()
    private val titleEnrichmentUseCase: TitleEnrichmentUseCase by inject()
    private val russianTitleEnrichmentUseCase: RussianTitleEnrichmentUseCase by inject()
    private val aiTitleTranslationUseCase: AiTitleTranslationUseCase by inject()
    private val localDataSource: AnimeLocalDataSource by inject()
    private val journal: EnrichmentGapJournal by inject()
    private val credentialsStore: AiCredentialsStore by inject()

    override suspend fun doWork(): Result {
        return try {
            if (!coordinator.isLiveMaintenanceEnabled()) return Result.success()

            val language = readLanguage()

            // Ссылки «Где смотреть» тянет отдельный воркер — здесь лишь периодический триггер (6 ч).
            coordinator.enqueueWebLinkEnrichment()

            val gaps = gapDetector.scan()
            if (gaps.isEmpty()) return Result.success()

            if (gaps.size > CollectionEnrichmentCoordinator.OVERFLOW_THRESHOLD) {
                Log.i(TAG, "Too many gaps (${gaps.size}) — prompting full enrichment")
                coordinator.requestFullEnrichmentPrompt(gaps.size)
                return Result.success()
            }

            val hasAi = credentialsStore.getAllConnectedProviders().isNotEmpty()

            repairFields(gaps, language)
            fillTitlesViaApi(language, hasAi)
            maybeTranslateOneWithAi(hasAi)

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Live maintenance run failed", e)
            Result.failure()
        }
    }

    // ---- Фаза 1: поля (бесплатный API) ----

    private suspend fun repairFields(gaps: List<AnimeGap>, language: AppLanguage) {
        val sessionLog = RepairDbSessionLog()
        val withFieldGaps = gaps.filter { gap -> gap.kinds.any { it.isFieldGap } }
            .take(MAX_FIELDS_PER_RUN)
        for (gap in withFieldGaps) {
            if (isStopped) return
            val anime = localDataSource.getAnimeById(gap.animeId) ?: continue
            val before = repairUseCase.detectGaps(anime).fieldKinds()
            if (before.isEmpty()) continue

            runCatching { repairUseCase.repairOne(anime, language, AppContentType.ANIME, sessionLog) }
                .onFailure { Log.w(TAG, "repairOne failed for ${anime.title}", it) }

            val reloaded = localDataSource.getAnimeById(gap.animeId) ?: anime
            val after = repairUseCase.detectGaps(reloaded).fieldKinds()
            val resolved = before - after
            if (resolved.isNotEmpty()) journal.clear(gap.animeId, resolved)
            if (after.isNotEmpty()) journal.mark(gap.animeId, after)

            delay(ITEM_DELAY_MS)
        }
    }

    // ---- Фаза 2: названия EN/RU (бесплатный API) ----

    private suspend fun fillTitlesViaApi(language: AppLanguage, hasAi: Boolean) {
        var processed = 0
        loopEn@ while (!isStopped && processed < MAX_TITLES_PER_RUN) {
            val batch = localDataSource.getAnimeNeedingTitleEn(TITLE_BATCH)
            if (batch.isEmpty()) break
            for (anime in batch) {
                if (isStopped || processed >= MAX_TITLES_PER_RUN) break@loopEn
                when (titleEnrichmentUseCase.enrich(anime, markOnFailure = !hasAi)) {
                    is TitleEnrichmentUseCase.Outcome.Enriched -> Unit
                    // Нет english в базах: если AI подключён — оставляем для фазы 3; иначе enrich уже
                    // пометил checked (markOnFailure=!hasAi), чтобы не перепроверять бесконечно.
                    TitleEnrichmentUseCase.Outcome.NoEnglishFound -> Unit
                    TitleEnrichmentUseCase.Outcome.Skipped -> localDataSource.markTitleEnChecked(anime.id)
                }
                processed++
                delay(ITEM_DELAY_MS)
            }
        }

        processed = 0
        loopRu@ while (!isStopped && processed < MAX_TITLES_PER_RUN) {
            val batch = localDataSource.getAnimeNeedingTitleRu(TITLE_BATCH)
            if (batch.isEmpty()) break
            for (anime in batch) {
                if (isStopped || processed >= MAX_TITLES_PER_RUN) break@loopRu
                when (russianTitleEnrichmentUseCase.enrich(anime, markOnFailure = true)) {
                    is RussianTitleEnrichmentUseCase.Outcome.Enriched -> Unit
                    RussianTitleEnrichmentUseCase.Outcome.NoRussianFound -> Unit
                    RussianTitleEnrichmentUseCase.Outcome.Skipped -> localDataSource.markTitleRuChecked(anime.id)
                }
                processed++
                delay(ITEM_DELAY_MS)
            }
        }
    }

    // ---- Фаза 3: один AI-перевод за заход, растянуто по [AiThrottlePolicy] ----

    private suspend fun maybeTranslateOneWithAi(hasAi: Boolean) {
        if (!hasAi || isStopped) return
        val candidate = localDataSource.getAnimeNeedingTitleEn(1).firstOrNull() ?: return

        when (val outcome = translateWithBackoff(candidate)) {
            is AiTitleTranslationUseCase.Outcome.Translated -> Unit
            AiTitleTranslationUseCase.Outcome.NoEnglishFound ->
                localDataSource.markTitleEnChecked(candidate.id)
            // Квота исчерпана надолго — не помечаем, продолжим после паузы.
            is AiTitleTranslationUseCase.Outcome.RateLimited -> {
                coordinator.scheduleContinuation(outcome.retryAfterMs.coerceIn(MIN_CONTINUATION_MS, MAX_CONTINUATION_MS))
                return
            }
            AiTitleTranslationUseCase.Outcome.Failed,
            AiTitleTranslationUseCase.Outcome.NoProvider -> return // временный сбой — повторим в след. заход
        }

        val residual = localDataSource.countAnimeNeedingTitleEn()
        if (residual > 0) {
            val delayMs = AiThrottlePolicy.delayBetweenAiCallsMs(
                residualAiCount = residual,
                providerCount = credentialsStore.getAllConnectedProviders().size,
            )
            coordinator.scheduleContinuation(delayMs)
        }
    }

    /** Короткую паузу на 429 отсиживаем в заходе; длинную — отдаём RateLimited для self-reschedule. */
    private suspend fun translateWithBackoff(anime: Anime): AiTitleTranslationUseCase.Outcome {
        var attempt = 0
        while (true) {
            val outcome = aiTitleTranslationUseCase.translate(anime)
            if (outcome !is AiTitleTranslationUseCase.Outcome.RateLimited) return outcome
            attempt++
            val wait = outcome.retryAfterMs.coerceAtLeast(MIN_BACKOFF_MS) + BACKOFF_BUFFER_MS
            if (isStopped || attempt > MAX_INLINE_RETRIES || wait > MAX_INLINE_BACKOFF_MS) return outcome
            delay(wait)
        }
    }

    private suspend fun readLanguage(): AppLanguage {
        val langStr = runCatching { settingsDataStore.data.first()[LANG_KEY] }.getOrNull() ?: "EN"
        return runCatching { AppLanguage.valueOf(langStr) }.getOrDefault(AppLanguage.EN)
    }

    private fun RepairAnimeDbUseCase.FieldGaps.fieldKinds(): Set<GapKind> = buildSet {
        if (missingImage) add(GapKind.IMAGE)
        if (missingTags) add(GapKind.TAGS)
        if (missingRating) add(GapKind.RATING)
        if (missingExternalId) add(GapKind.EXTERNAL_ID)
        if (missingCategoryType) add(GapKind.CATEGORY_TYPE)
        if (missingEpisodes) add(GapKind.EPISODES)
    }

    companion object {
        private const val TAG = "LiveMaintenance"
        private val LANG_KEY = stringPreferencesKey("lang")

        private const val MAX_FIELDS_PER_RUN = 15
        private const val MAX_TITLES_PER_RUN = 20
        private const val TITLE_BATCH = 20
        private const val ITEM_DELAY_MS = 350L

        private const val MAX_INLINE_RETRIES = 2
        private const val MIN_BACKOFF_MS = 1_000L
        private const val BACKOFF_BUFFER_MS = 500L
        private const val MAX_INLINE_BACKOFF_MS = 20_000L
        private const val MIN_CONTINUATION_MS = 60_000L
        private const val MAX_CONTINUATION_MS = 60L * 60_000L
    }
}
