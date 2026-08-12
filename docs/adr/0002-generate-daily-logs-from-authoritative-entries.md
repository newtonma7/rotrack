# Generate daily logs from authoritative entries

Daily Logs are generated views rather than persisted snapshots: totals, Time Entry segments, and Note references are computed from authoritative Time Entries, while only the optional Reflection is stored. This prevents duplicated study data from becoming stale when entries or timezones change, at the cost of recomputing the view on read.
