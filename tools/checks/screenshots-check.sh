#!/usr/bin/env bash
# Screenshots of the feature PR are evidence, not a check (UCH-116 R2): every name in screenshots-expected.txt is
# a file in <dir> and not empty. Whether a picture is right, the designer and the PO judge by looking at it.
# Writes the list of pictures to $GITHUB_STEP_SUMMARY if set. Usage: screenshots-check.sh <dir>
set -euo pipefail
dir="$1"
errors=0
while IFS= read -r f; do
  [ -n "$f" ] || continue
  if [ ! -e "$dir/$f" ]; then echo "screenshots-check: missing: $f" >&2; errors=$((errors + 1))
  elif [ ! -s "$dir/$f" ]; then echo "screenshots-check: empty: $f" >&2; errors=$((errors + 1)); fi
done <"$(dirname "$0")/screenshots-expected.txt"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ] && [ -d "$dir" ]; then
  { echo "### Screenshots (artifact screenshots-<sha>, folder screenshots)"; echo
    find "$dir" -mindepth 1 -maxdepth 1 -printf '- `%f`\n' | LC_ALL=C sort; } >>"$GITHUB_STEP_SUMMARY"
fi
[ "$errors" -eq 0 ] || { echo "screenshots-check: FAILED with $errors error(s)" >&2; exit 1; }
echo "screenshots-check: OK, all expected screenshots there and not empty"
