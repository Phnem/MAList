-- Английские названия для облачной таблицы anime (автообогащение через API/AI).
-- Запусти ОДИН РАЗ в Supabase Dashboard → SQL Editor → Run.
--
-- Клиент пушит поля title_en / title_en_checked_at в составе строки anime; без этих колонок
-- upsert падал бы целиком («Could not find the column …»).

ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS title_en text;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS title_en_checked_at bigint;

-- Сброс кэша схемы PostgREST — иначе новые колонки ещё «не видны» API.
NOTIFY pgrst, 'reload schema';
