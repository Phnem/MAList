-- Таблица зашифрованных ключей AI Connect (E2EE-синхронизация BYOK).
-- Запусти ОДИН РАЗ в Supabase Dashboard → SQL Editor → Run.
--
-- Сервер хранит только шифротекст (ciphertext) + IV: расшифровка возможна лишь на
-- устройстве пользователя ключом, выведенным из recovery-пассфразы (см. user_sync_passphrase).
-- Удаление — tombstone через deleted_at, чтобы другие устройства подхватили его при pull.

CREATE TABLE IF NOT EXISTS public.user_api_keys (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider text NOT NULL,
    ciphertext text NOT NULL,
    iv text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    PRIMARY KEY (user_id, provider)
);

CREATE INDEX IF NOT EXISTS user_api_keys_user_updated
    ON public.user_api_keys (user_id, updated_at);

ALTER TABLE public.user_api_keys ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'user_api_keys' AND policyname = 'Users can view their own api keys'
    ) THEN
        CREATE POLICY "Users can view their own api keys"   ON public.user_api_keys FOR SELECT USING (auth.uid() = user_id);
        CREATE POLICY "Users can insert their own api keys" ON public.user_api_keys FOR INSERT WITH CHECK (auth.uid() = user_id);
        CREATE POLICY "Users can update their own api keys" ON public.user_api_keys FOR UPDATE USING (auth.uid() = user_id);
        CREATE POLICY "Users can delete their own api keys" ON public.user_api_keys FOR DELETE USING (auth.uid() = user_id);
    END IF;
END $$;

-- Сброс кэша схемы PostgREST — иначе таблица ещё «не видна» API.
NOTIFY pgrst, 'reload schema';
