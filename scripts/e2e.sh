#!/usr/bin/env bash
# End-to-end tests (spec/roadmap.md §10): Maestro flows in e2e/flows against an
# :advanced build with the :e2e profile, served by Metro to the installed app.
#
# Needs: a booted iOS simulator with the app installed (npm run ios once), and
# Metro running (npm start). The dev build in target/ is untouched; index.js is
# pointed at target/e2e for the run and restored afterwards.
#
#   E2E_PLATFORM=android scripts/e2e.sh   # (Phase 6)
set -euo pipefail
cd "$(dirname "$0")/.."

if ! lsof -iTCP:8081 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "✗ Metro isn't running on :8081 — start it with: npm start"; exit 1
fi

cp index.js target/.index.js.e2e.bak
trap 'mv target/.index.js.e2e.bak index.js' EXIT

rm -rf target/e2e   # Krell's requires cache is additive; start clean
echo "→ :e2e build (target/e2e)"
log=$(mktemp)
clojure -M:e2e -m krell.main -co "build.edn:e2e/build.edn" -O advanced -c 2>&1 | tee "$log" | grep -v SLF4J || true
if grep -E "^WARNING|Exception|ERROR" "$log" \
     | grep -vE "SLF4J|sun\.misc\.Unsafe|terminally deprecated|consider reporting" >/dev/null; then
  echo "✗ compiler reported warnings/errors"; exit 1
fi
grep -q "target/e2e/main.js" index.js || { echo "✗ index.js doesn't point at the e2e build"; exit 1; }

echo "→ maestro"
rm -rf target/e2e-output
status=0
maestro test ${E2E_PLATFORM:+--platform "$E2E_PLATFORM"} --test-output-dir target/e2e-output \
  ${E2E_FLOWS:-e2e/flows} || status=$?

# Screenshots for the design review (roadmap Phase 5): e2e/screenshots/<name>.png
mkdir -p e2e/screenshots
find target/e2e-output -path "*takeScreenshot*" -name "*.png" -exec cp {} e2e/screenshots/ \;
echo "screenshots: $(ls e2e/screenshots | wc -l | tr -d ' ') in e2e/screenshots/"
exit $status
