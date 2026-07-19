-- Создаёт таблицу жанров anime_tags, если её нет на боевой базе.
-- Запусти это ОДИН РАЗ в Supabase Dashboard → SQL Editor → Run.
-- Симптом, который это чинит:
--   "Could not find the table 'public.anime_tags' in the schema cache"
--
-- FK к public.anime намеренно НЕ ставим (composite PK там может отличаться) — целостность
-- по пользователю обеспечивает RLS + FK к auth.users; клиент сам чистит теги при удалении.

CREATE TABLE IF NOT EXISTS public.anime_tags (
    anime_id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tag text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, anime_id, tag)
);

CREATE INDEX IF NOT EXISTS anime_tags_user_updated
    ON public.anime_tags (user_id, updated_at);

ALTER TABLE public.anime_tags ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'anime_tags' AND policyname = 'Users can view their own tags'
    ) THEN
        CREATE POLICY "Users can view their own tags"   ON public.anime_tags FOR SELECT USING (auth.uid() = user_id);
        CREATE POLICY "Users can insert their own tags" ON public.anime_tags FOR INSERT WITH CHECK (auth.uid() = user_id);
        CREATE POLICY "Users can update their own tags" ON public.anime_tags FOR UPDATE USING (auth.uid() = user_id);
        CREATE POLICY "Users can delete their own tags" ON public.anime_tags FOR DELETE USING (auth.uid() = user_id);
    END IF;
END $$;

-- Опционально: realtime, как у остальных таблиц.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND tablename = 'anime_tags'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.anime_tags;
    END IF;
END $$;

-- Важно: сбросить кэш схемы PostgREST — иначе таблица ещё «не видна» API.
NOTIFY pgrst, 'reload schema';
