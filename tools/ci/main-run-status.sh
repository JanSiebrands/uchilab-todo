#!/usr/bin/env bash
# Reports whether the latest completed workflow runs on a branch are green or red (read-only, gh run list).
# Called daily by the PO in the pull request sync (UCH-28), and by anyone who wants to know the state of main.
# Usage: tools/ci/main-run-status.sh [--branch main] [--workflow ci.yml --workflow renovate.yml]
# Output: one line per workflow, see main-run-status.jq.
# Exit: 0 all green, 1 at least one red, 2 query failed or no completed run (never looks like green).
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
branch=main
workflows=()
while [ $# -gt 0 ]; do
  case "$1" in
    --branch) branch="${2:?--branch needs a value}"; shift 2 ;;
    --workflow) workflows+=("${2:?--workflow needs a value}"); shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done
[ "${#workflows[@]}" -gt 0 ] || workflows=(ci.yml renovate.yml)

result=0
for wf in "${workflows[@]}"; do
  if ! json="$(gh run list --workflow "$wf" --branch "$branch" --limit 20 \
      --json databaseId,status,conclusion,event,createdAt,url,displayTitle)"; then
    echo "ERROR $wf query failed (gh run list)"
    result=2; continue
  fi
  if ! line="$(jq -r --arg wf "$wf" -f "$here/main-run-status.jq" <<<"$json")" || [ -z "$line" ]; then
    echo "ERROR $wf could not evaluate the answer of gh run list"
    result=2; continue
  fi
  echo "$line"
  case "$line" in
    "GREEN $wf "*) ;;
    "RED $wf "*) [ "$result" -eq 2 ] || result=1 ;;
    *) result=2 ;;
  esac
done
exit "$result"
