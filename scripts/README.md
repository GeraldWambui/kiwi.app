# Kiwi Photos Developer Scripts & Tooling

This directory contains standalone developer and maintainer automation scripts.

## Scripts Overview

### `backfill_contributions.py`
A developer utility to generate realistic, backdated commits with natural timestamps and Android-domain messages (features, bug fixes, pull request merges, performance optimizations).

```bash
# Preview commits for a date range without modifying git history
python3 scripts/backfill_contributions.py --dry-run --start 2026-03-01 --end 2026-08-03

# Execute backfill
python3 scripts/backfill_contributions.py --start 2026-03-01 --end 2026-08-03
```
