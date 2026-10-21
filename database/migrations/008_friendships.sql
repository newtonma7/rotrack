-- M6.1 storage foundation only. The API service must serialize pair mutations and
-- enforce request transitions/block cleanup before any social endpoints are exposed.
CREATE TABLE public.friendships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_low_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  user_high_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  requester_id UUID NOT NULL,
  state VARCHAR(8) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT friendships_canonical_pair CHECK (user_low_id < user_high_id),
  CONSTRAINT friendships_requester_is_participant CHECK (requester_id IN (user_low_id, user_high_id)),
  CONSTRAINT friendships_state CHECK (state IN ('PENDING', 'ACCEPTED')),
  CONSTRAINT friendships_pair_unique UNIQUE (user_low_id, user_high_id)
);
CREATE INDEX idx_friendships_low_state_created ON public.friendships (user_low_id, state, created_at DESC, id DESC);
CREATE INDEX idx_friendships_high_state_created ON public.friendships (user_high_id, state, created_at DESC, id DESC);

CREATE TABLE public.user_blocks (
  blocker_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (blocker_id, blocked_id),
  CONSTRAINT user_blocks_distinct_users CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocked ON public.user_blocks (blocked_id, blocker_id);
CREATE INDEX idx_user_blocks_blocker_created ON public.user_blocks (blocker_id, created_at DESC, blocked_id DESC);

-- Cursor order is database-owned. The runtime may change only friendship state;
-- a trigger stamps its update time without granting it access to timestamp columns.
CREATE FUNCTION public.stamp_friendship_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
  NEW.updated_at := statement_timestamp();
  RETURN NEW;
END;
$$;
CREATE TRIGGER friendships_stamp_updated_at
  BEFORE UPDATE ON public.friendships
  FOR EACH ROW EXECUTE FUNCTION public.stamp_friendship_updated_at();

ALTER TABLE public.friendships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY friendships_select_participant ON public.friendships
  FOR SELECT USING (auth.uid() IN (user_low_id, user_high_id));
CREATE POLICY user_blocks_select_blocker ON public.user_blocks
  FOR SELECT USING (auth.uid() = blocker_id);

-- Browser roles have no table access. Read-only policies are defense in depth:
-- even an accidental DML grant must not permit lifecycle writes through the Data API.
REVOKE ALL PRIVILEGES ON TABLE public.friendships, public.user_blocks FROM PUBLIC;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.friendships, public.user_blocks FROM anon';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.friendships, public.user_blocks FROM authenticated';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime') THEN
    EXECUTE 'REVOKE ALL PRIVILEGES ON TABLE public.friendships, public.user_blocks FROM rotrack_runtime';
    EXECUTE 'GRANT SELECT, DELETE ON TABLE public.friendships, public.user_blocks TO rotrack_runtime';
    EXECUTE 'GRANT INSERT (id, user_low_id, user_high_id, requester_id, state) ON TABLE public.friendships TO rotrack_runtime';
    EXECUTE 'GRANT UPDATE (state) ON TABLE public.friendships TO rotrack_runtime';
    EXECUTE 'GRANT INSERT (blocker_id, blocked_id) ON TABLE public.user_blocks TO rotrack_runtime';
  END IF;
END
$$;
