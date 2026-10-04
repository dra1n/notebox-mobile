#!/usr/bin/env bash
# Release check (spec/roadmap.md §10.2): the :advanced build compiles without
# warnings and Hermes accepts the output. Builds into target/release so the dev
# build in target/ is untouched; Krell rewrites index.js on every build, so it
# is restored afterwards.
set -euo pipefail
cd "$(dirname "$0")/.."

out=target/release
hermesc=node_modules/hermes-compiler/hermesc/osx-bin/hermesc
log=$(mktemp)

cp index.js "$out.index.js.bak" 2>/dev/null || { mkdir -p target; cp index.js "$out.index.js.bak"; }
trap 'mv "$out.index.js.bak" index.js; rm -f "$log"' EXIT

# Krell's krell_requires.edn cache is additive: start clean so renamed
# namespaces can't linger.
rm -rf "$out"
echo "→ :advanced build ($out)"
clojure -M -m krell.main -co build.edn -O advanced -d "$out" -o "$out/main.js" -c 2>&1 | tee "$log"
if grep -E "^WARNING|Exception|ERROR" "$log" \
     | grep -vE "SLF4J|sun\.misc\.Unsafe|terminally deprecated|consider reporting" >/dev/null; then
  echo "✗ compiler reported warnings/errors"; exit 1
fi
test -s "$out/main.js" || { echo "✗ no $out/main.js"; exit 1; }

echo "→ hermesc"
"$hermesc" -emit-binary -out "$out/main.hbc" "$out/main.js"

echo "✓ release build OK: $(wc -c < "$out/main.js" | tr -d ' ') bytes JS, $(wc -c < "$out/main.hbc" | tr -d ' ') bytes bytecode"
