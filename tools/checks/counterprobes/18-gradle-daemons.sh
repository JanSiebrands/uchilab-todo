#!/usr/bin/env bash
# UCH-69: every rule of tools/ci/gradle-daemon-check.sh, broken one at a time in the copy, must turn the
# check red with its own message; the unchanged copy stays green.
source "$(dirname "$0")/lib.sh"
make_copy
check=tools/ci/gradle-daemon-check.sh
root_props=gradle.properties
checks_props=tools/checks/gradle.properties
strategy=kotlin.compiler.execution.strategy=in-process
idle=org.gradle.daemon.idletimeout=600000
expect_green "unchanged gradle.properties" "$check" .
cp "$root_props" root.bak; cp "$checks_props" checks.bak
restore() { cp root.bak "$root_props"; cp checks.bak "$checks_props"; }

replace "$root_props" "$strategy" ""
expect_red "(a) strategy removed in the root" "root gradle.properties: kotlin.compiler.execution.strategy must be in-process (got: '')" "$check" .
restore
replace "$checks_props" "$strategy" ""
expect_red "(b) strategy removed in tools/checks" "tools/checks/gradle.properties: kotlin.compiler.execution.strategy must be in-process (got: '')" "$check" .
restore
replace "$root_props" "$strategy" "kotlin.compiler.execution.strategy=daemon"
expect_red "(c) strategy daemon in the root" "root gradle.properties: kotlin.compiler.execution.strategy must be in-process (got: 'daemon')" "$check" .
restore
replace "$checks_props" "$strategy" "kotlin.compiler.execution.strategy = daemon"
expect_red "(d) strategy daemon in tools/checks (with spaces)" "tools/checks/gradle.properties: kotlin.compiler.execution.strategy must be in-process (got: 'daemon')" "$check" .
restore
# A later entry overrides an earlier one, as in Gradle.
echo "kotlin.compiler.execution.strategy=daemon" >>"$root_props"
expect_red "(e) later strategy entry daemon in the root" "root gradle.properties: kotlin.compiler.execution.strategy must be in-process (got: 'daemon')" "$check" .
restore
rm "$checks_props"
expect_red "(f) tools/checks/gradle.properties missing" "tools/checks/gradle.properties: kotlin.compiler.execution.strategy must be in-process (got: '')" "$check" .
restore
replace "$root_props" "$idle" ""
expect_red "(g) idle timeout removed" "root gradle.properties: org.gradle.daemon.idletimeout must be 1..600000 ms (got: '')" "$check" .
restore
replace "$root_props" "$idle" "org.gradle.daemon.idletimeout=10800000"
expect_red "(h) idle timeout 3 h (Gradle default)" "root gradle.properties: org.gradle.daemon.idletimeout must be 1..600000 ms (got: '10800000')" "$check" .
restore
replace "$root_props" "$idle" "org.gradle.daemon.idletimeout=600001"
expect_red "(i) idle timeout 1 ms too long" "root gradle.properties: org.gradle.daemon.idletimeout must be 1..600000 ms (got: '600001')" "$check" .
restore
replace "$root_props" "$idle" "org.gradle.daemon.idletimeout=0"
expect_red "(j) idle timeout 0" "root gradle.properties: org.gradle.daemon.idletimeout must be 1..600000 ms (got: '0')" "$check" .
restore
replace "$root_props" "$idle" "org.gradle.daemon.idletimeout=300000"
expect_green "(k) shorter idle timeout (5 min) is allowed" "$check" .
restore
