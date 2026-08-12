-- rotrack migration 003: require an immutable public username
--
-- Existing rows are deliberately not rewritten. A null or non-canonical username
-- makes this migration fail, so an operator must resolve that account state before
-- retrying instead of silently inventing, renaming, or deleting accounts.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM public.users
    WHERE username IS NULL
       OR username IS DISTINCT FROM lower(btrim(username))
       OR username !~ '^[a-z0-9_]{3,24}$'
       OR lower(btrim(username)) = ANY (ARRAY[
         'admin', 'api', 'support', 'help', 'rotrack', 'signin', 'signup',
         'confirmation', 'dashboard', 'tracker', 'settings'
       ]::text[])
  ) OR EXISTS (
    SELECT lower(btrim(username))
    FROM public.users
    GROUP BY lower(btrim(username))
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE = '23514',
      MESSAGE = 'cannot require usernames while existing data violates the username contract';
  END IF;
END
$$;

ALTER TABLE public.users
  ALTER COLUMN username SET NOT NULL;

ALTER TABLE public.users
  ADD CONSTRAINT users_username_canonical
    CHECK (username = lower(btrim(username))),
  ADD CONSTRAINT users_username_format
    CHECK (username ~ '^[a-z0-9_]{3,24}$'),
  ADD CONSTRAINT users_username_not_reserved
    CHECK (NOT (username = ANY (ARRAY[
      'admin', 'api', 'support', 'help', 'rotrack', 'signin', 'signup',
      'confirmation', 'dashboard', 'tracker', 'settings'
    ]::text[])));

-- The auth insert and profile insert share one transaction, so the username is
-- reserved before Supabase can complete confirmation for the new account.
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
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.prevent_username_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF NEW.username IS DISTINCT FROM OLD.username THEN
    RAISE EXCEPTION USING
      ERRCODE = '23514',
      MESSAGE = 'username is immutable';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER users_username_immutable
  BEFORE UPDATE OF username ON public.users
  FOR EACH ROW
  EXECUTE FUNCTION public.prevent_username_change();
