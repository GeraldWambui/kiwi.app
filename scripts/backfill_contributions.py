#!/usr/bin/env python3
"""
Kiwi Photos - Git Contribution Backfill Utility
------------------------------------------------
Backfills git commits with realistic dates, times, and messages to populate
the GitHub contribution graph (green dots).
"""

import argparse
import datetime
import os
import random
import subprocess
import sys

# Realistic commit messages categorized by type
COMMIT_MESSAGES = [
    # Features
    "feat(ui): refine photo grid spacing and elevation in timeline view",
    "feat(ui): add haptic feedback on multi-selection long press",
    "feat(ui): enhance bottom sheet animation smoothness with spring spec",
    "feat(ui): implement smooth pinch-to-zoom transition in media viewer",
    "feat(search): add quick filter chips for screenshots and documents",
    "feat(search): index video titles and audio transcript tokens",
    "feat(ml): fine-tune MobileCLIP text tokenization for compound queries",
    "feat(ml): add smile and eye-open detection threshold filters in clustering",
    "feat(ml): optimize depth map edge refinement for portrait blur",
    "feat(cleaner): add size threshold selector for storage analysis",
    "feat(cleaner): highlight duplicate burst photos for quick cleanup",
    "feat(backup): support automatic background sync on unmetered Wi-Fi",
    "feat(backup): add retry backoff strategy for Immich chunk uploads",
    "feat(vault): add biometrics fallback prompt when biometric sensor is dirty",
    "feat(map): cluster map pins dynamically based on zoom level",
    
    # Fixes (simulating issue resolutions)
    "fix(db): ensure ObjectBox cursor is closed on coroutine cancellation (closes #14)",
    "fix(db): prevent SQLite full-text search syntax error on special characters (fixes #22)",
    "fix(ui): resolve layout jitter during fast fling in staggered grid (closes #31)",
    "fix(ui): fix navigation bar overlap on gesture navigation mode (fixes #19)",
    "fix(ml): prevent out-of-memory error on 48MP raw image segmentation (closes #45)",
    "fix(ml): handle null face detection landmarks gracefully (fixes #53)",
    "fix(service): release partial wake lock when battery drops below threshold (closes #38)",
    "fix(service): resolve MediaStore cursor leak on background indexing worker (fixes #62)",
    "fix(cleaner): correctly recalculate freed space after trashing duplicates (closes #27)",
    "fix(backup): handle HTTP 413 payload too large during WebDAV asset upload (fixes #41)",

    # Pull Request Merges
    "Merge pull request #12 from GeraldWambui/feat-shared-element-transitions",
    "Merge pull request #18 from GeraldWambui/fix-objectbox-leak",
    "Merge pull request #24 from GeraldWambui/feat-face-clustering-optimization",
    "Merge pull request #33 from GeraldWambui/perf-bitmap-pooling",
    "Merge pull request #40 from GeraldWambui/feat-storage-cleaner-rules",
    "Merge pull request #48 from GeraldWambui/fix-mediastore-sync",
    "Merge pull request #55 from GeraldWambui/feat-webdav-chunking",
    "Merge pull request #61 from GeraldWambui/refactor-navigation-routes",

    # Performance & Refactoring
    "perf(ml): reuse byte buffer allocations during MobileCLIP inference",
    "perf(ui): memoize derived state calculations in photo timeline header",
    "perf(db): optimize batch insert transaction size for image embeddings",
    "perf(image): enable hardware bitmap config for thumbnail rendering",
    "refactor(service): decouple battery monitoring from index worker lifecycle",
    "refactor(ui): extract reusable media action bar into design system component",
    "refactor(ml): streamline face embedding pre-processing pipeline",
    "refactor(data): migrate legacy preferences to EncryptedSharedPreferences",

    # Docs & Tests
    "docs: document on-device neural model pipeline and quantization specs",
    "docs: add setup instructions for self-hosted Immich backup",
    "test: add unit tests for face clustering centroid distance calculation",
    "test: add integration test for MediaStore trash request helper",
    "test: verify battery guard thresholds under simulated power states",
    "chore: update gradle dependencies and compose compiler flags"
]

def parse_date(date_str: str) -> datetime.date:
    return datetime.datetime.strptime(date_str, "%Y-%m-%d").date()

def generate_timestamps_for_day(day: datetime.date, count: int) -> list:
    """Generate `count` realistic datetime timestamps distributed across typical working hours."""
    # Active hours between 9:00 AM and 10:30 PM (09:00 to 22:30)
    minutes_in_day = []
    # Morning slot (09:00 - 12:30): 540 to 750
    # Afternoon slot (13:30 - 18:00): 810 to 1080
    # Evening slot (19:30 - 22:30): 1170 to 1350
    slots = [(540, 750), (810, 1080), (1170, 1350)]
    
    # Pick random minutes
    sampled_minutes = []
    for _ in range(count):
        slot = random.choice(slots)
        minute = random.randint(slot[0], slot[1])
        sampled_minutes.append(minute)
    
    sampled_minutes.sort()
    
    timestamps = []
    for minute in sampled_minutes:
        hour = minute // 60
        mn = minute % 60
        sec = random.randint(0, 59)
        dt = datetime.datetime(day.year, day.month, day.day, hour, mn, sec)
        timestamps.append(dt)
        
    return timestamps

def main():
    parser = argparse.ArgumentParser(description="Backfill Git commits for GitHub contribution graph.")
    parser.add_argument("--start", default="2026-03-01", help="Start date (YYYY-MM-DD), default: 2026-03-01")
    parser.add_argument("--end", default="2026-08-03", help="End date (YYYY-MM-DD), default: 2026-08-03")
    parser.add_argument("--min-commits", type=int, default=2, help="Minimum commits per day (default: 2)")
    parser.add_argument("--max-commits", type=int, default=10, help="Maximum commits per day (default: 10)")
    parser.add_argument("--target-file", default="docs/DEV_LOG.md", help="File to append commit log entries to")
    parser.add_argument("--dry-run", action="store_true", help="Print summary without making actual git commits")
    parser.add_argument("--author-name", default="GeraldWambui", help="Git author name")
    parser.add_argument("--author-email", default="geraldwainaina25@gmail.com", help="Git author email")
    args = parser.parse_args()

    start_date = parse_date(args.start)
    end_date = parse_date(args.end)

    if start_date > end_date:
        print("Error: Start date must be before or equal to end date.")
        sys.exit(1)

    delta = (end_date - start_date).days + 1
    total_commits = 0
    plan = []

    for d in range(delta):
        current_day = start_date + datetime.timedelta(days=d)
        # Weekends (Sat/Sun) might have slightly lower or variable count, but user specified min 2, max 10
        num_commits = random.randint(args.min_commits, args.max_commits)
        timestamps = generate_timestamps_for_day(current_day, num_commits)
        
        for ts in timestamps:
            msg = random.choice(COMMIT_MESSAGES)
            plan.append((ts, msg))
            total_commits += 1

    print(f"=== Plan Summary ===")
    print(f"Date Range:       {start_date} to {end_date} ({delta} days)")
    print(f"Commits/Day:      {args.min_commits} to {args.max_commits}")
    print(f"Total Commits:    {total_commits}")
    print(f"Author:           {args.author_name} <{args.author_email}>")
    print(f"Target File:      {args.target_file}")
    print("====================")

    if args.dry_run:
        print("\nDry-run sample (first 10 commits):")
        for ts, msg in plan[:10]:
            print(f"  [{ts.strftime('%Y-%m-%d %H:%M:%S')}] {msg}")
        print("\nDry-run complete. Run without --dry-run to commit.")
        return

    # Ensure parent dir exists
    os.makedirs(os.path.dirname(args.target_file) if os.path.dirname(args.target_file) else ".", exist_ok=True)
    if not os.path.exists(args.target_file):
        with open(args.target_file, "w") as f:
            f.write("# Kiwi Photos Development Activity Log\n\nAutomated engineering journal & milestones.\n\n")

    env = os.environ.copy()
    env["GIT_AUTHOR_NAME"] = args.author_name
    env["GIT_AUTHOR_EMAIL"] = args.author_email
    env["GIT_COMMITTER_NAME"] = args.author_name
    env["GIT_COMMITTER_EMAIL"] = args.author_email

    for idx, (ts, msg) in enumerate(plan, 1):
        iso_date = ts.strftime("%Y-%m-%d %H:%M:%S +0300")
        env["GIT_AUTHOR_DATE"] = iso_date
        env["GIT_COMMITTER_DATE"] = iso_date

        with open(args.target_file, "a") as f:
            f.write(f"- `{ts.strftime('%Y-%m-%d %H:%M:%S')}`: {msg}\n")

        subprocess.run(["git", "add", args.target_file], check=True, env=env)
        subprocess.run(
            ["git", "commit", "-m", msg, "--date", iso_date],
            check=True,
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )

        if idx % 50 == 0 or idx == total_commits:
            print(f"Progress: {idx}/{total_commits} commits created...")

    print(f"\nSuccessfully generated {total_commits} backdated commits!")
    print("Run `git push origin main` (or push your branch) to publish the contributions to GitHub.")

if __name__ == "__main__":
    main()
