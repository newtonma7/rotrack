-- rotrack migration 002: harden time-entry ownership and reporting indexes
--
-- 001_initial_schema.sql is already applied in the development Supabase project.
-- Keep this migration separate so existing databases and clean databases converge.
-- Existing duplicate active rows must be resolved before the unique index can apply.

CREATE INDEX IF NOT EXISTS idx_time_entries_user_start_time
  ON public.time_entries(user_id, start_time);

CREATE UNIQUE INDEX IF NOT EXISTS idx_time_entries_one_active_per_user
  ON public.time_entries(user_id)
  WHERE end_time IS NULL;

-- 001 already enforces:
--   end_time IS NULL OR end_time > start_time
-- Duration remains timestamp-derived in application code while the
-- duration_minutes column is retained as transitional compatibility data.
