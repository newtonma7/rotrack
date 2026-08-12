# Keep rich content behind the Spring API

Notes and Reflections are accessible only through the ownership-scoped Spring API; Supabase `anon` and `authenticated` roles receive no direct table privileges, while RLS remains defense in depth. PostgreSQL enforces ownership/link integrity, schema version, JSON presence, byte and field limits, and optimistic-version constraints; Spring alone performs the full allowlisted tree/link validation, maximum depth 32, maximum 10,000 nodes, and derived-text generation, avoiding two divergent rich-text validators at the trust boundary.
