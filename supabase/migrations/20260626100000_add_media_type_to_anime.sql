-- Manga / TV series sync: local DB column mediaType → cloud column media_type
-- Run in Supabase Dashboard → SQL Editor if not using `supabase db push`

ALTER TABLE public.anime
    ADD COLUMN IF NOT EXISTS media_type text NOT NULL DEFAULT 'ANIME';

ALTER TABLE public.anime
    DROP CONSTRAINT IF EXISTS anime_media_type_check;

ALTER TABLE public.anime
    ADD CONSTRAINT anime_media_type_check
    CHECK (media_type IN ('ANIME', 'MANGA', 'TV_SERIES'));

COMMENT ON COLUMN public.anime.media_type IS
    'Content kind: ANIME, MANGA, or TV_SERIES. Synced from Android app mediaType.';

NOTIFY pgrst, 'reload schema';
