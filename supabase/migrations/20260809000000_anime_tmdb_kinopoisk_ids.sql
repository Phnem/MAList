-- TMDB/Kinopoisk id для облачной таблицы anime (фильмы/сериалы).
-- Запусти ОДИН РАЗ в Supabase Dashboard → SQL Editor → Run.
--
-- ВАЖНО: клиентский код (SyncRepository/AnimeRemoteDto) намеренно НЕ пушит и НЕ читает эти
-- поля, пока эта миграция не применена — до этого момента tmdb_id/kinopoisk_id остаются
-- только локальными (не синкаются между устройствами). Включение синка для них — отдельный
-- шаг ПОСЛЕ применения этой миграции (см. .scratch/movie-series-infra/MASTER_PLAN.md →
-- Deferred work).

ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS tmdb_id bigint;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS tmdb_not_found_at bigint;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS kinopoisk_id bigint;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS kinopoisk_not_found_at bigint;

-- Сброс кэша схемы PostgREST — иначе новые колонки ещё «не видны» API.
NOTIFY pgrst, 'reload schema';
