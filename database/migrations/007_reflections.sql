-- Private date-label-owned Reflection documents and content-free create replay fingerprints.
-- Generated Daily Logs are projections and deliberately have no persistence table.
CREATE TABLE public.reflections (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  local_date DATE NOT NULL,
  timezone_at_creation TEXT NOT NULL,
  content_json JSON NOT NULL,
  content_text TEXT NOT NULL,
  content_schema_version INTEGER NOT NULL DEFAULT 1,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT reflections_date_range CHECK (local_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
  CONSTRAINT reflections_timezone_length CHECK (char_length(timezone_at_creation) BETWEEN 1 AND 255),
  CONSTRAINT reflections_json_object CHECK (json_typeof(content_json) = 'object'),
  CONSTRAINT reflections_json_size CHECK (octet_length(content_json::text) <= 262144),
  CONSTRAINT reflections_schema_version CHECK (content_schema_version = 1),
  CONSTRAINT reflections_json_schema_version_match CHECK (
    (json_typeof(content_json -> 'schemaVersion') = 'number'
      AND content_json ->> 'schemaVersion' = content_schema_version::text) IS TRUE
  ),
  CONSTRAINT reflections_positive_version CHECK (version > 0),
  CONSTRAINT reflections_user_local_date_unique UNIQUE (user_id, local_date)
);
ALTER TABLE public.reflections ENABLE ROW LEVEL SECURITY;
CREATE POLICY reflections_select_own ON public.reflections FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY reflections_insert_own ON public.reflections FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY reflections_update_own ON public.reflections FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY reflections_delete_own ON public.reflections FOR DELETE USING (auth.uid() = user_id);

CREATE TABLE public.reflection_creation_replays (
  owner_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  idempotency_key UUID NOT NULL,
  fingerprint BYTEA NOT NULL,
  reflection_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner_id, idempotency_key),
  CONSTRAINT reflection_creation_replays_owner_reflection_key UNIQUE (owner_id, reflection_id)
);
ALTER TABLE public.reflection_creation_replays ENABLE ROW LEVEL SECURITY;
-- Replay state contains no document snapshot and is available only to the API role.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.reflections, public.reflection_creation_replays FROM anon';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.reflections, public.reflection_creation_replays FROM authenticated';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.reflections, public.reflection_creation_replays FROM rotrack_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.reflections TO rotrack_runtime';
    EXECUTE 'GRANT SELECT, INSERT ON TABLE public.reflection_creation_replays TO rotrack_runtime';
  END IF;
END $$;
