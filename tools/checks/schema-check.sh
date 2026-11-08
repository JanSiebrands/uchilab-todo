#!/usr/bin/env bash
# DSGVO P4: the Room schema under app/schemas must match the code. Run after the build (privacyCheck depends
# on copyRoomSchemas, which writes the schema generated from the current code). Red if the build changed a
# checked-in schema file or created one that is not checked in, i.e. the commit contains a stale schema.
set -euo pipefail
cd "$(dirname "$0")/../.."
dir=app/schemas
[ -d "$dir" ] || { echo "schema-check: $dir not found" >&2; exit 1; }
changes="$(git status --porcelain --untracked-files=all -- "$dir")"
if [ -n "$changes" ] || ! git diff --quiet -- "$dir"; then
  echo "$changes" >&2
  git --no-pager diff --stat -- "$dir" >&2
  echo "schema-check: $dir differs from the commit; the build regenerated it from the code. Commit the regenerated schema (and update tools/checks/stored-fields.txt after asking DSGVO)." >&2
  exit 1
fi
echo "schema-check: OK ($dir matches the commit)"
