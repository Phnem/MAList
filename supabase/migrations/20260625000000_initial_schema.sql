-- 1. Anime (Main entity)
CREATE TABLE public.anime (
    id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title text NOT NULL DEFAULT '',
    image_path text,
    episodes integer NOT NULL DEFAULT 0,
    rating integer NOT NULL DEFAULT 0,
    status text NOT NULL DEFAULT '',
    is_favorite boolean NOT NULL DEFAULT false,
    order_index integer NOT NULL DEFAULT 0,
    date_added bigint NOT NULL DEFAULT 0,
    category_type text NOT NULL DEFAULT '',
    comment text NOT NULL DEFAULT '',
    is_ai_recommendation boolean NOT NULL DEFAULT false,
    anilist_id integer,
    mal_id integer,
    shikimori_id integer,
    anilist_not_found_at bigint,
    mal_not_found_at bigint,
    shikimori_not_found_at bigint,
    
    is_private boolean NOT NULL DEFAULT false,
    encryption_iv text,
    media_type text NOT NULL DEFAULT 'ANIME',
    
    created_at bigint NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at bigint,
    
    PRIMARY KEY (user_id, id)
);

CREATE INDEX anime_user_updated ON public.anime (user_id, updated_at);
ALTER TABLE public.anime ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own anime" ON public.anime FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own anime" ON public.anime FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own anime" ON public.anime FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own anime" ON public.anime FOR DELETE USING (auth.uid() = user_id);

-- 2. Anime Tags (M2M)
CREATE TABLE public.anime_tags (
    anime_id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tag text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, anime_id, tag),
    FOREIGN KEY (user_id, anime_id) REFERENCES public.anime(user_id, id) ON DELETE CASCADE
);

CREATE INDEX anime_tags_user_updated ON public.anime_tags (user_id, updated_at);
ALTER TABLE public.anime_tags ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own tags" ON public.anime_tags FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own tags" ON public.anime_tags FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own tags" ON public.anime_tags FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own tags" ON public.anime_tags FOR DELETE USING (auth.uid() = user_id);

-- 3. Anime Update / Ignored Update
CREATE TABLE public.anime_update (
    anime_id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title text NOT NULL,
    current_episodes integer NOT NULL,
    new_episodes integer NOT NULL,
    source text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    PRIMARY KEY (user_id, anime_id, new_episodes),
    FOREIGN KEY (user_id, anime_id) REFERENCES public.anime(user_id, id) ON DELETE CASCADE
);

CREATE INDEX anime_update_user_updated ON public.anime_update (user_id, updated_at);
ALTER TABLE public.anime_update ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own updates" ON public.anime_update FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own updates" ON public.anime_update FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own updates" ON public.anime_update FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own updates" ON public.anime_update FOR DELETE USING (auth.uid() = user_id);

CREATE TABLE public.ignored_update (
    anime_id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    new_episodes integer NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    PRIMARY KEY (user_id, anime_id),
    FOREIGN KEY (user_id, anime_id) REFERENCES public.anime(user_id, id) ON DELETE CASCADE
);

CREATE INDEX ignored_update_user_updated ON public.ignored_update (user_id, updated_at);
ALTER TABLE public.ignored_update ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own ignored updates" ON public.ignored_update FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own ignored updates" ON public.ignored_update FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own ignored updates" ON public.ignored_update FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own ignored updates" ON public.ignored_update FOR DELETE USING (auth.uid() = user_id);

-- 4. User Settings
CREATE TABLE public.user_settings (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    theme text,
    language text,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX user_settings_user_updated ON public.user_settings (user_id, updated_at);
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own settings" ON public.user_settings FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own settings" ON public.user_settings FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own settings" ON public.user_settings FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own settings" ON public.user_settings FOR DELETE USING (auth.uid() = user_id);

-- 5. Attachments
CREATE TABLE public.attachments (
    id text NOT NULL,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entity_id text NOT NULL,
    block_id text NOT NULL,
    r2_key text NOT NULL,
    mime_type text,
    size_bytes bigint NOT NULL DEFAULT 0,
    original_name text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    r2_deleted_at timestamptz,
    PRIMARY KEY (user_id, id)
);

CREATE INDEX attachments_user_entity ON public.attachments (user_id, entity_id);
CREATE INDEX attachments_user_updated ON public.attachments (user_id, updated_at);
ALTER TABLE public.attachments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own attachments" ON public.attachments FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own attachments" ON public.attachments FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own attachments" ON public.attachments FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own attachments" ON public.attachments FOR DELETE USING (auth.uid() = user_id);

-- 6. E2EE Passphrase
CREATE TABLE public.user_sync_passphrase (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    salt text NOT NULL,
    verifier_hash text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.user_sync_passphrase ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can insert their own passphrase" ON public.user_sync_passphrase FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can view their own passphrase" ON public.user_sync_passphrase FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own passphrase" ON public.user_sync_passphrase FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own passphrase" ON public.user_sync_passphrase FOR DELETE USING (auth.uid() = user_id);

-- 7. Realtime Publication
BEGIN;
  -- remove the supabase_realtime publication
  DROP PUBLICATION IF EXISTS supabase_realtime;

  -- re-create the supabase_realtime publication with no tables
  CREATE PUBLICATION supabase_realtime;
COMMIT;

ALTER PUBLICATION supabase_realtime ADD TABLE public.anime;
ALTER PUBLICATION supabase_realtime ADD TABLE public.anime_tags;
ALTER PUBLICATION supabase_realtime ADD TABLE public.anime_update;
ALTER PUBLICATION supabase_realtime ADD TABLE public.ignored_update;
ALTER PUBLICATION supabase_realtime ADD TABLE public.attachments;

-- 8. Supabase Storage Migration (for files <= 1 MiB)
INSERT INTO storage.buckets (id, name, public) VALUES ('vetro-collection-files', 'vetro-collection-files', false) ON CONFLICT DO NOTHING;

CREATE POLICY "Users can upload their own files" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'vetro-collection-files' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "Users can view their own files" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'vetro-collection-files' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "Users can update their own files" ON storage.objects FOR UPDATE TO authenticated USING (bucket_id = 'vetro-collection-files' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "Users can delete their own files" ON storage.objects FOR DELETE TO authenticated USING (bucket_id = 'vetro-collection-files' AND (storage.foldername(name))[1] = auth.uid()::text);
