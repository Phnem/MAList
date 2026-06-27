-- Fixes: "new row violates row-level security policy for table anime"
-- Run in Supabase Dashboard → SQL Editor

ALTER TABLE public.anime ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.anime TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.anime TO service_role;

DROP POLICY IF EXISTS "Users can insert their own anime" ON public.anime;
DROP POLICY IF EXISTS "Users can view their own anime" ON public.anime;
DROP POLICY IF EXISTS "Users can update their own anime" ON public.anime;
DROP POLICY IF EXISTS "Users can delete their own anime" ON public.anime;

CREATE POLICY "Users can insert their own anime"
    ON public.anime FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can view their own anime"
    ON public.anime FOR SELECT TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can update their own anime"
    ON public.anime FOR UPDATE TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own anime"
    ON public.anime FOR DELETE TO authenticated
    USING (auth.uid() = user_id);

-- anime_tags (if table exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'anime_tags'
    ) THEN
        ALTER TABLE public.anime_tags ENABLE ROW LEVEL SECURITY;
        GRANT SELECT, INSERT, UPDATE, DELETE ON public.anime_tags TO authenticated;
        GRANT SELECT, INSERT, UPDATE, DELETE ON public.anime_tags TO service_role;

        DROP POLICY IF EXISTS "Users can insert their own tags" ON public.anime_tags;
        DROP POLICY IF EXISTS "Users can view their own tags" ON public.anime_tags;
        DROP POLICY IF EXISTS "Users can update their own tags" ON public.anime_tags;
        DROP POLICY IF EXISTS "Users can delete their own tags" ON public.anime_tags;

        CREATE POLICY "Users can insert their own tags"
            ON public.anime_tags FOR INSERT TO authenticated
            WITH CHECK (auth.uid() = user_id);

        CREATE POLICY "Users can view their own tags"
            ON public.anime_tags FOR SELECT TO authenticated
            USING (auth.uid() = user_id);

        CREATE POLICY "Users can update their own tags"
            ON public.anime_tags FOR UPDATE TO authenticated
            USING (auth.uid() = user_id)
            WITH CHECK (auth.uid() = user_id);

        CREATE POLICY "Users can delete their own tags"
            ON public.anime_tags FOR DELETE TO authenticated
            USING (auth.uid() = user_id);
    END IF;
END $$;

NOTIFY pgrst, 'reload schema';
