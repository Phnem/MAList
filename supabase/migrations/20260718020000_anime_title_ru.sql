-- Русские названия для облачной таблицы anime (обратное обогащение через Shikimori).
-- Запусти ОДИН РАЗ в Supabase Dashboard → SQL Editor → Run.
--
-- Клиент пушит title_ru / title_ru_checked_at в составе строки anime; без этих колонок
-- upsert падал бы целиком («Could not find the column …»).

ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS title_ru text;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS title_ru_checked_at bigint;

NOTIFY pgrst, 'reload schema';
