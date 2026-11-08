#!/usr/bin/env bash
# Screenshots of the feature PR (plan UCH-81 section 5, Z1-Z3, Z5, Z6, Z8, Z9): the files in <dir> are exactly
# the names in screenshots-expected.txt (missing and unexpected files both fail), every name follows the
# schema <build>-<state>-<light|dark>-<de|en>.png, every picture has content (png-not-blank.py) and the theme
# of its name (png-theme.py, measured brightness in the log). Writes the list of pictures to
# $GITHUB_STEP_SUMMARY if set. Usage: screenshots-check.sh <dir>   The exit code decides.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
dir="$1"
expected="$here/screenshots-expected.txt"
schema='^release-[a-z0-9]+(-[a-z0-9]+)*-(light|dark)-(de|en)\.png$'
errors=0
err() { echo "screenshots-check: $*" >&2; errors=$((errors + 1)); }

[ -d "$dir" ] || { echo "screenshots-check: FAILED: $dir is no directory" >&2; exit 1; }
actual="$(find "$dir" -mindepth 1 -maxdepth 1 -printf '%f\n' | LC_ALL=C sort)"
want="$(grep -v '^$' "$expected" | LC_ALL=C sort)"
while IFS= read -r f; do
  [ -n "$f" ] || continue
  [[ "$f" =~ $schema ]] || err "name does not follow the schema <build>-<state>-<light|dark>-<de|en>.png: $f"
done <<<"$actual"
while IFS= read -r f; do err "missing: $f"; done < <(LC_ALL=C comm -13 <(echo "$actual") <(echo "$want") | grep .)
while IFS= read -r f; do err "unexpected: $f"; done < <(LC_ALL=C comm -23 <(echo "$actual") <(echo "$want") | grep .)

while IFS= read -r f; do
  [ -f "$dir/$f" ] || continue
  [[ "$f" =~ $schema ]] || continue
  theme="${BASH_REMATCH[2]}"
  python3 -I "$here/png-not-blank.py" "$dir/$f" || err "blank: $f"
  python3 -I "$here/png-theme.py" "$dir/$f" "$theme" || err "theme: $f"
done <<<"$actual"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Screenshots ($(echo "$actual" | grep -c .) files, artifact screenshots-<sha>, folder screenshots)"
    echo
    while IFS= read -r f; do [ -n "$f" ] && echo "- \`$f\`"; done <<<"$actual"
  } >>"$GITHUB_STEP_SUMMARY"
fi

if [ "$errors" -gt 0 ]; then
  echo "screenshots-check: FAILED with $errors error(s)" >&2
  exit 1
fi
echo "screenshots-check: OK, $(echo "$want" | grep -c .) screenshots as expected, schema, content and theme checked"
