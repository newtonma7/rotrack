-- rotrack migration 004: private per-user preferences
--
-- One row is maintained for every profile. Existing profiles are backfilled and
-- the signup trigger creates the row in the same transaction as the profile.

CREATE TABLE public.user_preferences (
  user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
  timezone TEXT,
  daily_work_goal_minutes INTEGER,
  share_study_summary BOOLEAN NOT NULL DEFAULT false,
  share_active_study_status BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT user_preferences_daily_work_goal_range
    CHECK (daily_work_goal_minutes IS NULL OR daily_work_goal_minutes BETWEEN 1 AND 1440)
);

ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;

CREATE POLICY user_preferences_select_own ON public.user_preferences
  FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY user_preferences_insert_own ON public.user_preferences
  FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY user_preferences_update_own ON public.user_preferences
  FOR UPDATE USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- Keep direct SQL writes from bypassing the API's IANA timezone contract.
CREATE OR REPLACE FUNCTION public.validate_user_preferences_timezone()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
  IF NEW.timezone IS NOT NULL AND NOT EXISTS (
    SELECT 1
    FROM pg_timezone_names
    WHERE name = NEW.timezone
      AND (name = 'UTC' OR name LIKE '%/%')
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE = '22023',
      MESSAGE = 'timezone must be a valid IANA identifier';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER user_preferences_timezone_valid
  BEFORE INSERT OR UPDATE OF timezone ON public.user_preferences
  FOR EACH ROW
  EXECUTE FUNCTION public.validate_user_preferences_timezone();

INSERT INTO public.user_preferences (user_id)
SELECT id FROM public.users
ON CONFLICT (user_id) DO NOTHING;

-- Preserve the existing security-definer signup boundary and create private
-- defaults atomically for newly registered users.
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  canonical_username TEXT;
BEGIN
  canonical_username := lower(btrim(NEW.raw_user_meta_data->>'username'));
  IF canonical_username IS NULL OR canonical_username !~ '^[a-z0-9_]{3,24}$' THEN
    RAISE EXCEPTION USING
      ERRCODE = '23514',
      MESSAGE = 'username must contain 3 to 24 lowercase letters, digits, or underscores';
  END IF;

  INSERT INTO public.users (id, email, username)
  VALUES (NEW.id, NEW.email, canonical_username)
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.user_preferences (user_id)
  VALUES (NEW.id)
  ON CONFLICT (user_id) DO NOTHING;
  RETURN NEW;
END;
$$;

-- The dedicated pooled Spring role bypasses RLS by design; ownership remains
-- enforced by the service's every-query user_id scope.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.user_preferences FROM rotrack_runtime';
    EXECUTE 'GRANT SELECT, INSERT, UPDATE ON TABLE public.user_preferences TO rotrack_runtime';
  END IF;
END
$$;
