-- Синхронизация прогресса просмотра/чтения (волна G).
-- Запусти ОДИН РАЗ в Supabase Dashboard → SQL Editor → Run.
--
-- Две таблицы, а не одна с nullable-колонками: у эпизода и у главы разные ключи
-- (season+episode против chapter_key источника) и разная модель прогресса
-- (позиция в миллисекундах против номера страницы). Общая таблица потребовала бы
-- половину колонок держать NULL и разбирать «какой это тип» на каждой строке.
--
-- Разрешение конфликтов — LWW по updated_at, поэтому оно и лежит в обеих таблицах
-- (клиент пишет туда свой локальный updatedAt, а не now(), — иначе курсор pull
-- считал бы чужую запись новее собственной).
--
-- FK к public.anime намеренно НЕТ: прогресс может приехать по тайтлу, которого на
-- этом устройстве ещё нет (и по удалённому из коллекции — перечитать главу не грех).
-- Целостность по пользователю обеспечивает RLS + FK к auth.users.

CREATE TABLE IF NOT EXISTS public.episode_progress (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    anime_id text NOT NULL,
    season integer NOT NULL,
    episode integer NOT NULL,
    position_ms bigint NOT NULL DEFAULT 0,
    duration_ms bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, anime_id, season, episode)
);

CREATE INDEX IF NOT EXISTS episode_progress_user_updated
    ON public.episode_progress (user_id, updated_at);

CREATE TABLE IF NOT EXISTS public.manga_progress (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    anime_id text NOT NULL,
    chapter_key text NOT NULL,
    page_index integer NOT NULL DEFAULT 0,
    page_count integer NOT NULL DEFAULT 0,
    -- Липкая отметка «прочитано»: снимается только явным действием в списке глав.
    -- Имя is_read, а не read: read — ключевое слово SET TRANSACTION, колонку пришлось
    -- бы цитировать в каждом запросе PostgREST.
    is_read boolean NOT NULL DEFAULT false,
    -- Докрутка внутри страницы, 0..1. NULL = постраничный режим (докрутки не бывает).
    scroll_offset_fraction real,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, anime_id, chapter_key)
);

CREATE INDEX IF NOT EXISTS manga_progress_user_updated
    ON public.manga_progress (user_id, updated_at);

ALTER TABLE public.episode_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.manga_progress   ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'episode_progress' AND policyname = 'Users can view their own episode progress'
    ) THEN
        CREATE POLICY "Users can view their own episode progress"   ON public.episode_progress FOR SELECT USING (auth.uid() = user_id);
        CREATE POLICY "Users can insert their own episode progress" ON public.episode_progress FOR INSERT WITH CHECK (auth.uid() = user_id);
        CREATE POLICY "Users can update their own episode progress" ON public.episode_progress FOR UPDATE USING (auth.uid() = user_id);
        CREATE POLICY "Users can delete their own episode progress" ON public.episode_progress FOR DELETE USING (auth.uid() = user_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'manga_progress' AND policyname = 'Users can view their own manga progress'
    ) THEN
        CREATE POLICY "Users can view their own manga progress"   ON public.manga_progress FOR SELECT USING (auth.uid() = user_id);
        CREATE POLICY "Users can insert their own manga progress" ON public.manga_progress FOR INSERT WITH CHECK (auth.uid() = user_id);
        CREATE POLICY "Users can update their own manga progress" ON public.manga_progress FOR UPDATE USING (auth.uid() = user_id);
        CREATE POLICY "Users can delete their own manga progress" ON public.manga_progress FOR DELETE USING (auth.uid() = user_id);
    END IF;
END $$;

-- Realtime намеренно НЕ включаем: на эти таблицы никто не подписывается
-- (SupabaseSyncCoordinator слушает только `anime`), а прогресс пишется часто —
-- публикация гнала бы WAL-трафик впустую.

-- Важно: сбросить кэш схемы PostgREST — иначе таблицы ещё «не видны» API.
NOTIFY pgrst, 'reload schema';
