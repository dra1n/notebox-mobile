# Notebox Mobile: design snapshot

A local snapshot of the Figma design, taken on 2026-10-03, so that implementation doesn't depend on
Figma access. (The Figma MCP Starter plan allows 20 calls per month, and all 20 were used on 2026-10-03
while this snapshot was being made. Manual export from the Figma app doesn't count.) The architecture, scope and decisions live in [`../roadmap.md`](../roadmap.md),
especially §1.1, §8 and §11. This folder holds the visuals and the measured values.

- **Figma file:** https://www.figma.com/design/zql5RT6q3vPP4GMgokSK9c/notebox (fileKey
  `zql5RT6q3vPP4GMgokSK9c`)
- **Page "Desktop"** (`0:403`): the mobile section "Notebox Mobile Application" (`1503:454`), the
  Books/Tags mobile frames next to it, the desktop app (1020 wide), older tablet (804) and mobile
  (420) variants, and the palette (`0:1472`).
- **Page "Logo"** (`0:1`): logo explorations (`0:2`) and the app icon (`0:215`).
- [`figma-nodes-desktop-page.xml`](figma-nodes-desktop-page.xml) is the full node tree of the
  Desktop page (ids, names, positions, sizes). Use it to find a node id before asking Figma for
  details.

## Contents

| Path | What |
|---|---|
| `screens/00-mobile-section-overview.png` | All 8 frames of the mobile section (1:1 scale plus a 40 px margin) |
| `screens/01…11-*.png` | One file per screen (see the table below); 02–08 are crops of the overview |
| `brand/palette.png` | The colour palette artboard (identical to the web `_variables.css`) |
| `brand/app-icon.png` | The app icon: teal rounded square, white band, "NOTE" |
| `brand/logo-explorations.png` | Logo page; the chosen wordmark is the two-tone Amatic SC "NoteBox" |
| `desktop-reference/*.png` | Desktop frames used for states the mobile design lacks |
| `assets/*.svg` | Icons exported from Figma: hamburger, search, book, arrow-left, divider line |
| `figma-code/*.tsx` | Figma's React/Tailwind reference output (exact values) for books-home and note-detail. Reference only; translate to RN. |

## Screens

All mobile frames are 402 × 874 (with a 44 pt status bar) unless noted. The phone outline (radius
32, border, shadow) in the PNGs is mockup chrome.

| # | File | Node | Content (exact copy in quotes) |
|---|---|---|---|
| 01 | `01-splash.png` | `742:470` (420×761) | `bg-lighter`, centred wordmark. Also the native launch screen. |
| 02 | `02-start-login.png` | `731:457` (420×761) | Photo background (notebook + teal pen, white fade at the top); wordmark; "Your personal notebook in Dropbox" (Medium); "Keep all your notes in one place. Organize it in books." (grey); white card with shadow: "Login with your Dropbox account to get started" + **LET'S GO** (`cyan-light` button). A 6 px dark strip at the top. |
| 03 | `03-books-home.png` | `1502:435` | Header: hamburger + wordmark, **ADD NOTE**. Search "Search notes, tags, books...". Stats "30 books (67 notes) in total". Book rows: book icon, title 14, "N notes" 12, 1 px separators. |
| 04 | `04-book-notes.png` | `1506:561` | Header: ← "Back", book title (centre), **ADD NOTE**. Search "Search notes, tags...". "16 notes in total". Note rows: semibold title, 2-line grey preview of the text. |
| 05 | `05-note-detail.png` | `1502:483` | Header: ← "Back", truncated note title, **EDIT**. White panel, padding 20, gap 16: book title (13, `text-grey`), title (SemiBold 24), divider, body (16/24, paragraph gap 12), tag chips pinned to the bottom. |
| 06 | `06-edit-note.png` | `1502:554` | Header: "Cancel", "Edit Note" (bold, white), **SAVE** (a darker teal, ≈ `#6CC5CF`/`cyan-dark`, than the other primary buttons; treat it as the "commit" variant). Book dropdown (`cyan-lightest` fill, `cyan` border, chevron) = move to another book. Title (Medium ~16) with a `cyan` underline when focused. Body. Bottom: chips with × plus an outlined "+ Add tag". **No delete button** (roadmap §8.1). |
| 07 | `07-new-note.png` | `1502:519` | Header: "Cancel", "New Note", **ADD NOTE**. "Select Notebook..." dropdown (white, grey border), "Enter note title..." (large placeholder, grey underline), "Add note content here...", outlined "+ Add tag...". |
| 08 | `08-side-menu.png` | `0:1134` (420×761) | Dark sheet (`bg-dark`, 223 wide) over the dimmed note screen: wordmark; account email + Dropbox glyph + "dropbox account"; "Log out". Thin mid-grey separators (≈ `text-grey-dark`). The content behind is dimmed. Roadmap §8.1 adds the missing entries (Library, Books, Tags, Settings). |
| 09 | `09-books-manage.png` | `1502:592` | Header: hamburger, "Books", **ADD BOOK**. "7 Books". Cards (`bg-lighter`, `bg-light` border, radius ~6): title (Medium), "N notes", trailing "Rename". The default book's card uses `cyan-lightest` + a `cyan` border + "• Default". Long titles truncate with "…". |
| 10 | `10-books-manage-variant.png` | `1507:685` | An older version of 09: a row list with "Search books..." and "30 books". Take **only the search field** from it. |
| 11 | `11-tags.png` | `1502:658` | Header: hamburger, "Tags", ~~ADD TAG~~. "5 tags in total". Cards: chip + "N notes" + ~~Rename~~. ~~"Create new tag" card~~. **Struck-through items are dropped for v1** (roadmap §11). |

Not saved as images (the Figma limit was hit): the older tablet and 420-wide mobile variants of every
screen. They're superseded by the frames above, and they're listed in the node tree.

### Desktop references (for states the mobile design lacks)

| File | Node | Use for |
|---|---|---|
| `desktop-reference/empty-page-and-toasts.png` | `309:365` | Empty library: illustration + "No any note" / "You have not had any note yet. Feel free to add new one." + **ADD NOTE**. Toasts: error = `bg-orange-light` + orange ⚠ "Something was wrong, can not delete your note."; success = `cyan-lightest` + teal ✓ "Your note was successfully deleted." |
| `desktop-reference/add-note-sync-pill.png` | `309:439` | "data updating…" pill with a spinner (top right, light grey, rounded) = sync indicator |
| `desktop-reference/note-not-found-search.png` | `0:1087` | 404 illustration + "This note does not found" / "You ask a note which does not exist."; search results header "Search results: 3 notes" |

Tidy the English of the copy when implementing (for example "No notes yet", "Note not found");
the layout and tone stay.

## Design tokens (measured)

The colours are in roadmap §8.4 (identical to the web `_variables.css`), plus `logo` `#6CC5CF`
(wordmark and icon only) and an extra chip tint `#C3F0F5` (use `cyan-light` instead).

| Element | Values |
|---|---|
| Status bar | `bg-dark` `#2C292B`, light content |
| Header | `bg-dark`, height 64, padding x 16, gap 8 between the icon and the label; labels Roboto Regular 14 `#C6C6C6`; centre title max width 160, ellipsis; modal title Bold 14 white |
| Primary button | `#ADE4EA`, radius 3, height 28, Roboto Medium 14, letter spacing 0.6, `#323232`, uppercase; widths ≈ 57 (EDIT) / 99 (ADD NOTE) → padding x ≈ 12–14 |
| Search bar block | white, padding 16, gap 12, bottom border 1 `#DFDFDF` |
| Search input | `#F6F6F6`, radius 8, padding 12 × 10, icon 16, gap 8, placeholder Regular 14 `#888` |
| Stats line | Roboto Medium 14 `#323232` |
| Book row | padding 16, gap 12; icon 12×14 (`book.svg`); title Regular 14, count Regular 12, gap 6; separator 1 `#DFDFDF`; list background `#F6F6F6`; pressed: title black |
| Note detail panel | white, padding 20, gap 16; book label Regular 13 `#888`; title SemiBold 24 `#323232`; body Regular 16, line height 24, paragraph gap 12 |
| Tag chip | padding 10 × 6, radius 4, Medium 13 `#323232`, background `#ADE4EA`, gap 8, wraps |
| Icons | hamburger 23×19 at 63% opacity; arrow-left 20; search 16 |
| Wordmark | Amatic SC Bold 28: "Note" `#6CC5CF`, "Box" `#C6C6C6` (on dark) / `#888` (on light) |

## Assets

All assets are in [`assets/`](assets/). The SVGs are outlined vectors (no live text, no embedded
bitmaps), so they're ready for `react-native-svg` (or `react-native-svg-transformer`).

| File | Size | Notes |
|---|---|---|
| `logo.svg` | 159×59 | Wordmark, outlined. "Note" `#6CC5CF`, "Box" `#A4A0A2` (light-background version). On the dark header the design uses `#C6C6C6` for "Box": recolour that path in code, or keep two variants. |
| `app-icon.svg` | 120×120 | `#6CC5CF` with white cut-outs (band + "NOTE"); **rounded corners (r 16) and transparency are baked in**. In Phase 6, derive a full-bleed square with an opaque background from it (iOS rejects alpha and applies its own mask; Android adaptive icons need a foreground and background split). |
| `app-icon-1024.png` | 360×360 | Exported smaller than the requested 1024 → regenerate from the SVG in Phase 6; don't use it as-is. |
| `start-background.jpg` | 1260×2283 | 3x of the 420×761 frame, cropped to it; use with `resizeMode="cover"`. Photo only: the **white top fade** (Figma "Rectangle 6", covering roughly the top 475 pt) is a separate layer, so recreate it in code with a gradient overlay. The layer name "warm-coffee-drink-1684151" suggests a stock-photo ID; check the licence before release. |
| `illustration-empty.svg` | 104×103 | Pages + teal pencil, with soft shadows (SVG filters; check they render in `react-native-svg`, or flatten them). |
| `illustration-404.svg` | 94×103 | Pages with an outlined grey "404" + teal pencil, with soft shadows (SVG filters, as in the empty illustration). |
| `arrow-left.svg`, `search.svg`, `book.svg`, `hamburger.svg`, `divider-line.svg` | — | From Figma's design-context output. Stroke/fill colours are hard-coded (for example arrow `#C6C6C6`); override them in code. |
| `chevron-down.svg` (12×8), `close-x.svg` (9×9) | — | `#323232`: the dropdown chevron and the × on a chip |
| `check.svg` (17×17) | — | `#3CB0BD`: success toast |
| `dropbox.svg` (12×10) | — | `#888888`: "dropbox account" in the side menu |
| — | — | No ⚠ icon for error toasts. Draw one or take it from an icon set. |
