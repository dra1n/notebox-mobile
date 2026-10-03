# Notebox Mobile: progress

This file tracks the phase gates defined in [`roadmap.md` §9](roadmap.md#9-phased-plan).

**Rules**
- Tick an item only after doing it on the phase's final commit. Next to it, write the evidence:
  the date, the commit hash, and either the command output summary (for example "212 tests,
  0 failures, domain coverage 97.1 %") or a screenshot path.
- A phase is **done** when all of its items are ticked. Set its status to `done (<date>, <commit>)`.
- If a gate item turns out to be wrong or impossible, don't silently skip it. Change it in the
  roadmap, say why here, and then tick the revised item.
- Deviations from the design or the roadmap go in the phase's "Notes".

| Phase | Status |
|---|---|
| 0 Foundations | in progress: automated gate passed; manual item pending |
| 1 Domain | not started |
| 2 Dropbox infra | not started |
| 3 Repository, cache, sync | not started |
| 4 re-frame features | not started |
| 5 UI | not started |
| 6 Hardening and release | not started |

---

## Phase 0: Foundations

Automated
- [x] `npm run verify` exits 0 and runs ≥ 1 test in each runner (clj, cljs node, jest): 2026-10-03, 9ee203f. JVM 9 tests / 30 assertions; node 5 tests / 19 assertions; jest 1; clj-kondo 0 warnings; check-deps OK. Also checked that it **fails** (non-zero) when app code is broken.
- [x] `notebox.lint-rules-test`: the checker reports every violation in `test/resources/lint-violations/` (7: cycle, domain→RN, infra→re-frame, ui→infra, cross-feature events, feature-order breach, unclassified ns) and none in `src`/`dev`: 2026-10-03, 9ee203f. Revised: the checker is `check_deps.clj`, not clj-kondo (roadmap §5.2 item 6).
- [x] `notebox.system-test`: the test profile inits and halts cleanly, in order: 2026-10-03, 9ee203f. Runs on the JVM and in node; `:notebox/ui` (React Native) is excluded on both.
- [x] `npm run check:release`: the `:advanced` build compiles and `hermesc` accepts it: 2026-10-03, 9ee203f. 280,690 B JS → 575,499 B bytecode, no compiler warnings.
- [x] `npm run test:e2e`: the Maestro smoke flow sees text from a re-frame sub on the iOS simulator: 2026-10-03, 9ee203f. `smoke` passed in 3 s on the iPhone 16 Pro (iOS 18.3) simulator, asserting "Status: ready (e2e)".

Manual
- [ ] A view edit + `(dev/reset)` in the Krell REPL shows up on the simulator without restarting the app

Notes
- The layer/cycle rules moved from clj-kondo to `scripts/check_deps.clj` (see roadmap §5.2).
- e2e runs against an `:advanced` build with the `:e2e` profile (`target/e2e`), not the dev build.
- Phase 0 added the `react-native-get-random-values` native module: run `pod install` and
  `npm run ios` once after pulling.
- Restart a running Krell REPL after `deps.edn` changes. A REPL started earlier can't compile
  the new namespaces, and the app shows "Could not find -main fn".

## Phase 1: Domain (L0)

Automated
- [ ] `npm run test:clj` passes; Cloverage `notebox.domain.*` ≥ 95 % forms (enforced threshold)
- [ ] Property tests (≥ 200 runs) for every op: idempotence, no-op on missing targets, meta = derived meta, unique slugs
- [ ] The golden round-trip is byte-identical on every fixture file
- [ ] The desktop-reader oracle reads every file the domain writes (or a note here on why it was dropped)
- [ ] The search parity table passes (including Cyrillic and case folding)

Notes
-

## Phase 2: Dropbox infra (L1)

Automated
- [ ] Client contract tests (URL, headers, mode/rev, parsing recorded responses) for every endpoint
- [ ] The error normalization table, including `Retry-After`
- [ ] Auth: the RFC 7636 PKCE vector; one refresh + retry on 401; `invalid_grant` → `:unauthorized`
- [ ] `dropbox/fake` passes the same contract suite as the client

Manual
- [ ] After login on the simulator, `(dev/check-dropbox)` prints the email and parsed meta of the real account
- [ ] `(dev/expire-token!)`, then `(dev/check-dropbox)`, succeeds after a logged refresh

Notes
-

## Phase 3: Repository, cache, sync engine (L1)

Automated
- [ ] The simulation property test (≥ 500 scenarios, two clients incl. legacy, fault injection) holds all invariants
- [ ] Named scenarios: crash mid-upload, airplane → reconnect, two-client conflict, edit vs. remote delete
- [ ] Cloverage `notebox.storage.sync-core` ≥ 95 %
- [ ] Driver tests: every command type against `dropbox/fake` + the in-memory kv-store

Manual (simulator, real account)
- [ ] Kill the app right after saving → relaunch → the note is in Dropbox
- [ ] Web and mobile edit the same book, mobile saving last → both notes survive
- [ ] With the network off, create a note, then turn the network on → it syncs

Notes
-

## Phase 4: re-frame features (L2 + L3)

Automated
- [ ] Every web save flow (§3.3) and every §1.1 addition has an event test, including rollback and auth expiry
- [ ] The event/sub coverage meta-test passes (every registered id was exercised)
- [ ] Derived sub tests: books with counts, tag index, search results, default-book fallback

Notes
-

## Phase 5: UI (L4 + L5)

Automated
- [ ] View tests for every presentational view state and handler
- [ ] Maestro flows pass on iOS: login, browse, create, edit/move, delete, search ×3, books CRUD + default, tags, empty library, not found, logout

Manual: design review (screenshot vs. `spec/design/screens/`)
- [ ] 01 splash
- [ ] 02 start/login
- [ ] 03 books home
- [ ] 04 book notes
- [ ] 05 note detail
- [ ] 06 edit note
- [ ] 07 new note
- [ ] 08 side menu
- [ ] 09 books manage (with the search from 10)
- [ ] 11 tags
- [ ] Design gaps (delete note/book, empty, 404, toasts, sync indicator): consistent with the desktop references

Manual: parity run on the real account
- [ ] Every row in roadmap §1 and §1.1 is checked on the simulator and confirmed in the web app

Notes
-

## Phase 6: Hardening and release

Automated
- [ ] The whole Maestro suite passes on the Android emulator
- [ ] `npm run check:release` + the Maestro smoke flow on release builds (iOS and Android)

Manual
- [ ] Release build on a real iPhone (+ Android): log in to the real account, then a create/edit/delete round trip
- [ ] Icon and launch screen are correct on the home screen and at cold start
- [ ] The privacy-policy link opens; the stock-photo licence is confirmed (record the source here)

Notes
-
