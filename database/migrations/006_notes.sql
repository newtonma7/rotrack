-- rotrack migration 006: private Notes and content-free creation replay metadata
--
-- Rich documents stay behind Spring. The browser roles receive no table privileges;
-- RLS is defense in depth while the API performs the full tree validation and ownership checks.

ALTER TABLE public.time_entries
  ADD CONSTRAINT time_entries_user_id_id_key UNIQUE (user_id, id);

CREATE TABLE public.notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  time_entry_id UUID,
  attachment_owner_id UUID,
  title TEXT,
  content_json JSON NOT NULL,
  content_text TEXT NOT NULL,
  content_schema_version INTEGER NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT notes_title_max_length CHECK (title IS NULL OR char_length(title) <= 120),
  CONSTRAINT notes_content_json_object CHECK (json_typeof(content_json) = 'object'),
  CONSTRAINT notes_content_json_size CHECK (octet_length(content_json::text) <= 262144),
  CONSTRAINT notes_schema_version CHECK (content_schema_version = 1),
  CONSTRAINT notes_content_json_schema_version_match CHECK (
    (json_typeof(content_json -> 'schemaVersion') = 'number'
      AND content_json ->> 'schemaVersion' = content_schema_version::text) IS TRUE
  ),
  CONSTRAINT notes_positive_version CHECK (version > 0),
  CONSTRAINT notes_attachment_pair CHECK (
    (time_entry_id IS NULL AND attachment_owner_id IS NULL)
    OR (time_entry_id IS NOT NULL AND attachment_owner_id = user_id)
  ),
  CONSTRAINT notes_time_entry_owner_fk
    FOREIGN KEY (attachment_owner_id, time_entry_id)
    REFERENCES public.time_entries (user_id, id)
    ON DELETE SET NULL
);

CREATE INDEX idx_notes_user_updated_id ON public.notes (user_id, updated_at DESC, id DESC);
CREATE INDEX idx_notes_user_attachment ON public.notes (user_id, time_entry_id);

ALTER TABLE public.notes ENABLE ROW LEVEL SECURITY;
CREATE POLICY notes_select_own ON public.notes
  FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY notes_insert_own ON public.notes
  FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY notes_update_own ON public.notes
  FOR UPDATE USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);
CREATE POLICY notes_delete_own ON public.notes
  FOR DELETE USING (auth.uid() = user_id);

CREATE TABLE public.note_creation_replays (
  owner_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  idempotency_key UUID NOT NULL,
  fingerprint BYTEA NOT NULL,
  note_id UUID NOT NULL,
  deleted_version BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner_id, idempotency_key),
  CONSTRAINT note_creation_replays_positive_deleted_version
    CHECK (deleted_version IS NULL OR deleted_version > 0),
  CONSTRAINT note_creation_replays_owner_note_key UNIQUE (owner_id, note_id)
);

ALTER TABLE public.note_creation_replays ENABLE ROW LEVEL SECURITY;
-- Deliberately no browser-role policies or grants: replay metadata is API-only and content-free.

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.notes, public.note_creation_replays FROM anon';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.notes, public.note_creation_replays FROM authenticated';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.notes, public.note_creation_replays FROM rotrack_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.notes TO rotrack_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE ON TABLE public.note_creation_replays TO rotrack_runtime';
  END IF;
END
$$;
