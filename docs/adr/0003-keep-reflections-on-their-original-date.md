# Keep reflections on their original date

A Reflection remains associated with the local-date label on which the user created it, even when a timezone change re-buckets the generated facts in that Daily Log. Clearing preserves an empty versioned Reflection rather than deleting and recreating it, avoiding stale-client ABA conflicts and preserving the user's chosen historical context.
