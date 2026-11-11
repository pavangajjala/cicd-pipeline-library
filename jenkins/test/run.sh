#!/usr/bin/env bash
# Compiles the shared library and runs the unit tests with plain Groovy.
# The vars/ steps need a Jenkins runtime to execute, so they are only
# compiled here, which still catches syntax errors before a pipeline does.
set -euo pipefail

cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

echo "== compile src/"
find src -name '*.groovy' -print0 | xargs -0 groovyc -d "$out"

echo "== syntax check vars/"
for step in vars/*.groovy; do
  groovyc -cp "$out" -d "$out/vars" "$step"
  echo "ok  $step"
done

echo "== unit tests"
for test in test/*Test.groovy; do
  groovy -cp "$out" "$test"
done
