#!/usr/bin/env bash
# S4b (plan UCH-80 section 5, Z10): every name of the SourceCheck rules of ADR 0003/UCH-78 in a copy of
# TaskDatabase.kt, one per line (as comments: the check is text based). privacyCheck must fail and report each
# line with its rule. One Gradle run for all names instead of one per name: a name without a rule is missing in
# the output, so this counterprobe fails just the same.
source "$(dirname "$0")/lib.sh"
make_copy
f=app/src/main/kotlin/de/uchilab/todo/data/TaskDatabase.kt
[ -f "$f" ] || { echo "COUNTERPROBE BROKEN: $f not found" >&2; exit 2; }
names=(fallbackToDestructiveMigration allowDataLossOnRecovery dropAllTables createFromAsset createFromFile
  createFromInputStream deleteDatabase DefaultDatabaseErrorHandler openOrCreateDatabase AndroidSQLiteDriver)
first=$(($(wc -l <"$f") + 1))
for n in "${names[@]}"; do echo "// $n" >>"$f"; done
log="$(mktemp)"
status=0
./gradlew "${GRADLE_ARGS[@]}" privacyCheck >"$log" 2>&1 || status=$?
[ "$status" -ne 0 ] || { tail -n 40 "$log"; echo "COUNTERPROBE FAILED: privacyCheck stayed green" >&2; exit 1; }
missing=0
for i in "${!names[@]}"; do
  expected="TaskDatabase.kt:$((first + i)): ${names[$i]} is not allowed"
  if grep -qF -- "$expected" "$log"; then echo "counterprobe OK: $expected"
  else echo "COUNTERPROBE FAILED: privacyCheck red (exit $status), but without: $expected" >&2; missing=1; fi
done
[ "$missing" -eq 0 ] || { tail -n 60 "$log"; exit 1; }
