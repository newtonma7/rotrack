-- rotrack migration 005: completed-entry history and same-user range protection
--
-- btree_gist supplies UUID equality for the exclusion constraint. The half-open
-- range keeps adjacent entries valid while making active rows extend to infinity.
-- This prerequisite is checked explicitly so an unavailable extension fails with
-- an actionable migration error instead of a later opaque constraint error.
DO $$
DECLARE
  note_violations bigint;
  overlap_violations bigint;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'btree_gist') THEN
    RAISE EXCEPTION 'migration 005 prerequisite missing: PostgreSQL extension btree_gist is unavailable'
      USING ERRCODE = '0A000';
  END IF;

  SELECT count(*) INTO note_violations
  FROM public.time_entries
  WHERE notes IS NOT NULL AND char_length(notes) > 280;
  IF note_violations > 0 THEN
    RAISE EXCEPTION 'migration 005 blocked: % existing time_entries have notes longer than 280 characters; remediate explicitly before retrying', note_violations
      USING ERRCODE = '23514';
  END IF;

  SELECT count(*) INTO overlap_violations
  FROM public.time_entries first_entry
  JOIN public.time_entries second_entry
    ON first_entry.user_id = second_entry.user_id
   AND first_entry.id < second_entry.id
   AND tstzrange(first_entry.start_time, COALESCE(first_entry.end_time, 'infinity'::timestamptz), '[)')
       && tstzrange(second_entry.start_time, COALESCE(second_entry.end_time, 'infinity'::timestamptz), '[)');
  IF overlap_violations > 0 THEN
    RAISE EXCEPTION 'migration 005 blocked: % existing same-user time_entry ranges overlap; remediate explicitly before retrying', overlap_violations
      USING ERRCODE = '23P01';
  END IF;
END
$$;

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
