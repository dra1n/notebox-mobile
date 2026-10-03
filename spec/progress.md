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
| 0 Foundations | done (2026-10-03, 094bc4d) |
| 1 Domain | done (2026-10-03, f988768) |
| 2 Dropbox infra | done (2026-10-03, dd76ee1) |
| 3 Storage through Luggage | done (2026-10-03, 644cba4) |
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
- [x] Without restarting the app: a view edit hot-reloads on save, and a change to the initial state (`:ready` → `:restarted`) appears only after `(dev/reset)`: 2026-10-03, 094bc4d. Done by the user on the iPhone 16 Pro simulator with `npm run cljs:repl`. Revised from "view edit + `(dev/reset)`", which proved nothing once views hot-reload on their own.

Notes
- The layer/cycle rules moved from clj-kondo to `scripts/check_deps.clj` (see roadmap §5.2).
- e2e runs against an `:advanced` build with the `:e2e` profile (`target/e2e`), not the dev build.
- Phase 0 added the `react-native-get-random-values` native module: run `pod install` and
  `npm run ios` once after pulling.
- Restart a running Krell REPL after `deps.edn` changes. A REPL started earlier can't compile
  the new namespaces, and the app shows "Could not find -main fn".

## Phase 1: Domain (L0)

Automated
- [x] `npm run test:clj` passes; Cloverage `notebox.domain.*` ≥ 95 % forms (enforced threshold): 2026-10-03, f988768. 41 tests / 510 assertions. Forms: json 99.55, meta 100, note 99.29, ops 100, ordered 100, schema 99.01, search 100, tags 98.73, time 100. Enforced per namespace by `scripts/coverage_gate.clj`, which exits 3 when a namespace is below its minimum (checked by raising the minimum).
- [x] Property tests (≥ 200 runs) for every op: idempotence, no-op on missing targets, meta = derived meta, unique slugs: 2026-10-03, f988768. `ops-test`: 300 runs each, plus "unknown keys survive every op" (200). All pass on the JVM and in Node.
- [x] The golden round-trip is byte-identical on every fixture file: 2026-10-03, f988768. All 11 export files, plus all 12 op scenarios matching the JS reference byte for byte (files and warnings), on both platforms. JSON parity with the real `JSON.stringify`: 500 generated values in Node.
- [x] The desktop-reader oracle reads every file the domain writes: 2026-10-03, f988768. Revised: Cheshire 5.10.2 `parse-string s true` / `generate-string`, the desktop's exact calls, in both directions, over every scenario plus 300 generated values. `:local/root` was dropped because it would pull in JavaFX and the Dropbox SDK.
- [x] The search parity table passes (including Cyrillic and case folding): 2026-10-03, f988768. A table of 11 queries, each checked against the web's `matches-text` copied verbatim, plus 300 generated note/query pairs that must agree with it.

Notes
- Fixtures are hand-written (`test/resources/fixtures/generate.mjs`). The real library is too
  big to commit. Re-running the generator reproduces the committed files exactly.
- Node found that `JSON.parse` orders integer-like keys first, unlike the first JVM parser. The
  JVM decoder now does the same, so both platforms agree.
- The encoder copies unescaped runs of a string in one go. On a 4.4 MB book (2,000 notes) in
  Node, `encode` takes 22 ms (it was 105 ms) and `decode` 17 ms. Re-measure on Hermes when the UI loads real books (Phase 5).
- `:note/update` merges like the web's `Object.assign` (roadmap §6.2).

## Phase 2: Dropbox infra (L1)

Automated
- [x] Client contract tests (URL, headers, mode/rev, parsing responses) for every endpoint: 2026-10-03, 054cfff. `http-test`: exact requests for download, upload (add/update), delete, list_folder (+continue), get_current_account, revoke, and the token endpoint. Also the non-ASCII `Dropbox-API-Arg`, and parsing of the documented response shapes in `fixtures/http/`. `client-test` checks the requests actually sent, with the bearer token. Revised: documented shapes rather than recordings (roadmap Phase 2).
- [x] The error normalization table, including `Retry-After`: 2026-10-03, 054cfff. `errors-test`: 14 rows covering 409 not_found/lookup/conflict/other, 401, 400 invalid_grant/other/plain text, 429 (header and body retry_after), 503, 500 and 404. `client-test`: waits 7 s per `Retry-After`, 1 s by default, caps at 60 s, gives up after 3 attempts.
- [x] Auth: the RFC 7636 PKCE vector; one refresh + retry on 401; `invalid_grant` → `:unauthorized`: 2026-10-03, 054cfff. `pkce-test` (JVM and Node); `client-test` checks the exact request sequence for 401 → refresh → retry, and that a second 401 is final; `auth-test` covers sign-in, a declined or forged redirect, single-flight refresh near expiry, a rejected refresh token removing itself, and sign-out with revoke.
- [x] `dropbox/fake` passes the same contract suite as the client: 2026-10-03, 054cfff. `contract-test` runs one suite against the fake and against the HTTP client over a fake server (with paging). The suite caught a fidelity bug in the fake server (lower-cased file names), now fixed.

Manual
- [x] After login on the simulator, `(dev/check-dropbox)` prints the email and parsed meta of the real account: 2026-10-03, dd76ee1. Done by the user: `(dev/login!)` via Safari with the `notebox://oauth` redirect on the web app key `2t7xyn3a902rv0z`, then `(dev/check-dropbox)` against the real `/notes`.
- [x] `(dev/expire-token!)`, then `(dev/check-dropbox)`, succeeds after a logged refresh: 2026-10-03, dd76ee1. Done by the user.

Notes
- Login uses our own PKCE flow plus `Linking` (system browser, then iOS's "Open in Notebox?"
  prompt), not `react-native-app-auth` (roadmap §7). A native auth session is a Phase 6 option.
- Totals: 50 JVM tests / 589 assertions, 57 Node tests / 517. `check:release` 300 KB; e2e smoke
  passes.
- Before the manual items: register `notebox://oauth` in the Dropbox App Console for
  `2t7xyn3a902rv0z`. Use a REPL started after this phase's `deps.edn` changes, and rebuild the
  app (`pod install` + `npm run ios`), because `react-native-keychain` is a new native module.

## Phase 3: Storage through Luggage (L1)

Automated
- [x] Golden scenarios through Luggage: all 12 Phase 1 scenarios via `repository/apply-op!` on the seeded fake give byte-identical files: 2026-10-03, ed5edf5. `repository-test/golden-scenarios-through-luggage`: every file and the set of files match the JS reference, warnings included.
- [x] Luggage edge cases: missing files as `[]`/`{}`; `create`/`delete` keep `collectionsList`; unknown meta keys survive; failed writes reject with the client's error type: 2026-10-03, ed5edf5. Also: a repeated create never wipes a book or resets its meta (a bug the test found, now fixed); a failed write leaves the book unchanged, and the same op succeeds once back online.
- [x] Serial queue: concurrent ops apply one at a time in order; a failure doesn't block later ops: 2026-10-03, ed5edf5. Six concurrent adds are all kept, in order, with count 6; an op after a failing one still applies.
- [x] Interop rule: `check-deps` fails on a JS require outside the interop namespaces: 2026-10-03, ed5edf5. Fixture `lint-violations/notebox/storage/cache.cljs`; the real source passes.

Manual (simulator, real account, REPL)
- [x] `(dev/load-meta)` / `(dev/load-book slug)` show real data through Luggage: 2026-10-03, 644cba4. Done by the user on the simulator against the real account.
- [x] A test book created, given a note, renamed and deleted via `(dev/apply-op! …)`; each step visible in the web app, which still reads the files: 2026-10-03, 644cba4. Done by the user; the rest of the library was untouched.

Notes
- Replanned 2026-10-03: Luggage (the user's choice) instead of the rev-based sync engine; online
  only, with no cache or outbox (moved to Phase 7). See roadmap §6.3.
- Spike: Luggage's core builds through Metro and passes `hermesc` (92 KB, no Dropbox SDK). The
  e2e smoke run starts the app with Luggage under Hermes.
- Totals: 50 JVM tests / 599 assertions, 62 Node tests / 709. `check:release` 314 KB.

## Phase 4: re-frame features (L2 + L3)

Automated
- [ ] Every web save flow (§3.3) and every §1.1 addition has an event test, including rollback and auth expiry
- [ ] The event/sub coverage meta-test passes (every registered id was exercised)
- [ ] Derived sub tests: books with counts, tag index, search results, default-book fallback

Manual
- [ ] The user has reviewed the re-frame implementation against the web app (`src/notebox/feature/`, `fx/`, `shell/events.cljc`)

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
