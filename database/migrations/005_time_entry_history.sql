-- rotrack migration 005: completed-entry history and same-user range protection
--
-- btree_gist supplies UUID equality for the exclusion constraint. The half-open
-- range keeps adjacent entries valid while making active rows extend to infinity.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE public.time_entries
  ADD CONSTRAINT time_entries_notes_max_length
  CHECK (notes IS NULL OR char_length(notes) <= 280);

ALTER TABLE public.time_entries
  ADD CONSTRAINT time_entries_no_overlap_per_user
  EXCLUDE USING gist (
    user_id WITH =,
    tstzrange(start_time, COALESCE(end_time, 'infinity'::timestamptz), '[)') WITH &&
  );

-- The DELETE API requires the same narrow table grant as the other entry mutations.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rotrack_runtime') THEN
    EXECUTE 'GRANT DELETE ON TABLE public.time_entries TO rotrack_runtime';
  END IF;
END
$$;
