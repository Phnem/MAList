-- Run in Supabase Dashboard → SQL Editor → Run
-- Fixes: "Could not find the 'category_type' column of 'anime' in the schema cache"

ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS episodes integer NOT NULL DEFAULT 0;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS rating integer NOT NULL DEFAULT 0;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT '';
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS is_favorite boolean NOT NULL DEFAULT false;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS order_index integer NOT NULL DEFAULT 0;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS date_added bigint NOT NULL DEFAULT 0;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS category_type text NOT NULL DEFAULT '';
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS is_ai_recommendation boolean NOT NULL DEFAULT false;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS mal_id integer;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS shikimori_id integer;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS anilist_not_found_at bigint;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS mal_not_found_at bigint;
ALTER TABLE public.anime ADD COLUMN IF NOT EXISTS shikimori_not_found_at bigint;

-- created_at must be bigint (epoch ms), not timestamptz — matches the Android app
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'anime'
          AND column_name = 'created_at' AND data_type = 'timestamp with time zone'
    ) THEN
        ALTER TABLE public.anime
            ALTER COLUMN created_at TYPE bigint
            USING (EXTRACT(EPOCH FROM created_at) * 1000)::bigint;
    END IF;
END $$;

NOTIFY pgrst, 'reload schema';
CREATE TABLE IF NOT EXISTS public.anime_tags (
    anime_id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tag text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, anime_id, tag),
    FOREIGN KEY (user_id, anime_id) REFERENCES public.anime(user_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS anime_user_updated ON public.anime (user_id, updated_at);
CREATE INDEX IF NOT EXISTS anime_tags_user_updated ON public.anime_tags (user_id, updated_at);

ALTER TABLE public.anime_tags ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'anime_tags' AND policyname = 'Users can view their own tags'
    ) THEN
        CREATE POLICY "Users can view their own tags" ON public.anime_tags FOR SELECT USING (auth.uid() = user_id);
        CREATE POLICY "Users can insert their own tags" ON public.anime_tags FOR INSERT WITH CHECK (auth.uid() = user_id);
        CREATE POLICY "Users can update their own tags" ON public.anime_tags FOR UPDATE USING (auth.uid() = user_id);
        CREATE POLICY "Users can delete their own tags" ON public.anime_tags FOR DELETE USING (auth.uid() = user_id);
    END IF;
END $$;
