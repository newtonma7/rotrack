# Username Registration

**Status:** Implemented—unverified
**Scope:** First-account registration only; no public profile or username search UI
**Owner:** rotrack product/backend/frontend maintainers

### Resolved review decisions

- **Username** is the canonical domain term. “Public handle” describes a future use, not current visibility.
- Usernames are canonical lowercase, owner-only in this slice, immutable after profile creation, and reserved at initial signup before email confirmation.
- The pre-user rollout prepares disposable accounts separately, then applies one fail-closed enforcement migration.
- PostgreSQL uses a fixed reserved-name `CHECK` constraint plus an ordinary unique constraint on canonical lowercase values.
- Server-side signup failures use safe generic copy; no availability preflight endpoint is added.
- These decisions are recorded in [`ADR-0001`](../adr/0001-username-registration-lifecycle.md).

## 1. Summary

Every newly registered rotrack account must choose a unique username. The username is a public handle for future profile, friend, and group features; it is not an authentication credential. Users continue to sign in with their email address and password.

The database is the final authority for normalization, validation, reserved names, and uniqueness. The signup form provides immediate feedback but must not be trusted on its own.

## 2. Product behavior

### Signup fields

The signup form will require:

1. Username
2. Email
3. Password
4. Confirm password

Username validation happens on submit and may also happen on blur:

- Trim surrounding whitespace.
- Normalize to lowercase before submission.
- Accept only `a-z`, `0-9`, and `_`.
- Require 3–24 characters.
- Reject reserved names.
- Show an accessible field error without exposing database or SQL details.

The normalized lowercase value is stored and is the only owner-visible username value for this version. No separate display-case column is needed. It is not publicly readable until a later profile/search decision.

### Reserved usernames

Use a small, explicit list to prevent collisions with routes and system identities:

```text
admin
api
support
help
rotrack
signin
signup
confirmation
dashboard
tracker
settings
```

This is intentionally not a general reservation system. The fixed list is a database `CHECK` constraint; a reservation table, admin UI, or runtime configuration is unnecessary until usernames become user-manageable or public profile URLs exist.

### Duplicate behavior

If a username is already taken, signup remains on the signup page and shows safe copy such as:

> That username is unavailable. Try another one.

The UI must not rely on a preflight availability query or expose SQL/provider details. Concurrent signups must be resolved by the database constraint; all server-side username-registration failures may use the same safe message.

## 3. Data and security contract

### Existing schema

`public.users.username` already exists and is currently nullable and unique case-sensitively. The final contract changes it to:

- `NOT NULL`
- Canonical lowercase value
- Format: `^[a-z0-9_]{3,24}$`
- Reserved names rejected by a database `CHECK` constraint
- Unique across all users using an ordinary unique constraint/index
- Immutable after profile creation
- Owner-readable only through the existing ownership-scoped RLS policy

No email address, password, access token, or private user data is returned to the browser as part of this change.

### Supabase signup flow

The frontend will pass the username through Supabase Auth user metadata:

```ts
supabase.auth.signUp({
  email,
  password,
  options: {
    data: { username: normalizedUsername },
  },
});
```

The existing `public.handle_new_user()` trigger will read `NEW.raw_user_meta_data->>'username'`, normalize and validate it, reject reserved/taken values, and insert it into `public.users`.

The trigger remains `SECURITY DEFINER` with its fixed `search_path`. Database constraints also cover direct profile writes, and a database boundary rejects username updates after profile creation. The frontend will not insert directly into `public.users`, and no new unauthenticated username-availability endpoint will be added.

## 4. Database migration

Add an ordered migration after the current migrations:

```text
database/migrations/003_require_usernames.sql
```

Before applying it to a target database:

1. Inspect profiles with null usernames.
2. Inspect case-insensitive duplicates.
3. Inspect invalid existing values.
4. Because the current database contains only disposable test accounts, assign valid unique test usernames or recreate those accounts before enforcing the constraint.
5. Do not invent usernames for real users in a future environment.

The migration will:

1. Add/validate the username format check.
2. Ensure stored usernames are canonical lowercase and add the format/reserved-name `CHECK` constraints.
3. Preserve a plain unique constraint/index because all stored values are canonical lowercase.
4. Update `handle_new_user()` to consume Auth metadata.
5. Reject missing, invalid, reserved, and duplicate usernames.
6. Reject username updates after profile creation at the database boundary.
7. Set `public.users.username` to `NOT NULL` after the separate disposable-account preflight is clean.
8. Preserve the existing trigger, RLS, ownership, and profile-creation behavior.

The migration must fail closed if existing data violates the new contract. It must not silently delete or rename accounts.

### Rollout ordering

Because an old frontend would submit no username, deployment must avoid leaving the schema ahead of the application:

- **Selected pre-user path:** verify only disposable test accounts exist, prepare valid test usernames separately (or recreate those accounts), apply the fail-closed enforcement migration, then deploy the matching frontend commit.
- If a future environment has real users or cannot coordinate the deployment, split this into a compatibility migration and a final enforcement migration. The compatibility migration adds validation/indexing and trigger support while nullable; the final migration sets `NOT NULL` after the frontend is live and all profiles are backfilled.

## 5. Frontend implementation

Modify:

- `frontend/src/components/auth/SignUp.tsx`
- Add/update signup component tests.

Implementation requirements:

- Keep the existing Supabase Auth flow and confirmation redirect.
- Add controlled username state.
- Normalize before calling `signUp`.
- Prevent submission for invalid local values.
- Include `aria-describedby` and an error/status region.
- Map duplicate/trigger failures to safe generic copy such as “That username is unavailable. Try another one.”; never expose SQLSTATE, provider details, or trigger text.
- Never include username, email, or password in logs or query strings.

The confirmation page does not need a username field or username in its URL.

## 6. Backend and fixture implementation

Modify:

- `backend/src/main/java/com/rotrack/model/User.java`
- PostgreSQL migration/repository test fixtures and assertions.

The JPA model will mark `username` non-null so `ddl-auto: validate` reflects the database contract. No registration controller or service is needed because Supabase Auth owns registration.

Update minimal test `auth.users` schemas to include `raw_user_meta_data JSONB`. Update fixture inserts to provide valid usernames. Existing ownership and timer tests should remain behaviorally unchanged.

## 7. Test plan

### Frontend tests

Add coverage for:

- Username is required.
- Invalid characters and lengths are rejected.
- Input is trimmed and lowercased before submission.
- Supabase receives `options.data.username`.
- Duplicate/signup-trigger errors produce safe, useful feedback.
- Password mismatch behavior remains unchanged.

### Database tests

Extend migration integration coverage for:

- Valid username profile creation from Auth metadata.
- Missing metadata rejection.
- Invalid format rejection.
- Reserved-name rejection.
- Case normalization.
- Case-insensitive duplicate rejection.
- Concurrent duplicate claims, where the unique database constraint is the authority.
- `username IS NOT NULL`, canonical lowercase, format, and reserved-name constraint inspection.
- Direct username-update rejection and existing RLS/trigger security-definer properties.

### Browser flow

Add or update the disposable-user signup scenario to prove:

1. A valid username is required.
2. Signup creates the Auth user and profile with the same normalized username.
3. A duplicate username cannot create a second profile, including while the first account is unconfirmed.
4. Email confirmation and subsequent sign-in still work.
5. Existing tracker/dashboard flows are unaffected.

## 8. Acceptance criteria

This change is complete only when:

- A new account cannot be registered without a username.
- Valid usernames are normalized and stored lowercase.
- Invalid and reserved usernames are rejected by both UI and database.
- Two accounts cannot claim the same username, including case variants or concurrent attempts.
- Disposable existing test accounts are migrated or recreated with valid usernames.
- Usernames remain owner-only; no email/password/token/private data or database/provider details are exposed in username errors or logs.
- Existing authentication, confirmation, tracker, dashboard, ownership, and RLS tests pass.
- Migration apply/verify, frontend tests, backend tests, typecheck, lint, build, and the disposable browser flow pass.
- `arch.plan.md` and `todo.md` record the final contract and evidence.

## 9. Deliberately out of scope

- Username changes after registration.
- Username availability API.
- Public profile pages.
- Username search.
- Friend requests or group membership.
- Display-name casing separate from the canonical username.
- Admin-managed reserved-name configuration.
