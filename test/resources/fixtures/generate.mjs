// Generates the golden fixtures for notebox.domain.golden-test.
//
//   node test/resources/fixtures/generate.mjs
//
// notes-export/  a hand-written /notes folder, serialized with JSON.stringify —
//                byte for byte what the web client writes. It carries the drift
//                real data has (stale counts and tags, a ghost collectionsList
//                entry, varied key orders, unknown keys) and awkward strings.
// scenarios/     ops applied to that folder, with the expected files written by
//                an independent JavaScript implementation of the op semantics in
//                spec/roadmap.md §6.2 (mirroring the web client's JS behaviour:
//                Object.assign merges, appended keys, push).
//
// Re-running must reproduce the committed files exactly; the test checks the
// committed files, so change this script and the fixtures together.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const exportDir = path.join(here, 'notes-export');
const scenariosDir = path.join(here, 'scenarios');

// --- the library -------------------------------------------------------------

const B = {
  anna:    'k3J9aZ0qLx',
  comics:  'Cm1c5_bk-0',
  mine:    'myNoteB00k',
  novel:   'R0man-Mo1_',
  inbox:   'inBox_0001',
  recipes: 'R3c1p3s-xx',
  work:    'W0rk-n0t3s',
  travel:  'Tr4v3l_l0g',
  empty:   'Emp7y-B00k',
  ideas:   'Id3a5_box9',
  ghost:   'Gh0st-g0ne',   // in collectionsList only: deleted by the web (hazard 4)
};

const books = {
  [B.anna]: [
    { slug: 'Vh2xQ1aaaa', title: 'Новое поколение дворянства',
      text: '— Это новое поколение дворянства.\n— Новое-то новое. Но не дворянство.',
      tags: ['классика', 'прочитанное', '5+'], 'created-at': '2021-03-03T03:03:03.033Z' },
    { slug: 'Vh2xQ1bbbb', title: 'Если добро имеет причину',
      text: 'Если добро имеет причину, оно уже не добро; если оно имеет последствие — награду, оно тоже не добро.',
      tags: ['классика'], 'created-at': '2021-03-04T10:11:12.131Z',
      'updated-at': '2021-05-06T07:08:09.101Z' },
    { slug: 'Vh2xQ1cccc', 'created-at': '2021-03-05T00:00:00.000Z',
      text: 'Вопрос для него состоял в следующем: «Если я не признаю тех ответов…»',
      tags: ['классика'] },   // no title (the web wrote it before the title was typed)
  ],
  [B.comics]: [
    { slug: 'c0m1c00001', 'created-at': '2020-01-01T00:00:00.000Z', title: 'Watchmen', text: 'Who watches the watchmen?', tags: ['favorite'] },
    { slug: 'c0m1c00002', 'created-at': '2020-01-02T00:00:00.000Z', title: 'Maus', text: 'A survivor\'s tale', tags: [] },
    { slug: 'c0m1c00003', 'created-at': '2020-01-03T00:00:00.000Z', title: 'Saga', text: 'Space opera / fantasy', tags: ['new', 'favorite'] },
    { slug: 'c0m1c00004', 'created-at': '2020-01-04T00:00:00.000Z', title: 'Sandman', text: 'Dream of the Endless' },
    { slug: 'c0m1c00005', 'created-at': '2020-01-05T00:00:00.000Z', title: 'Bone', text: '', tags: ['new'],
      pinned: true, color: null },   // keys added by some other client
  ],
  [B.mine]: [
    { slug: 'my00000001', title: 'Shopping', text: 'milk\neggs\n\tbread', tags: ['todo'], 'created-at': '2022-02-02T02:02:02.002Z' },
    { slug: 'my00000002', title: 'Quotes "with" \\backslashes\\ and </script>', text: 'Tab\there, CR\r\nLF, bell \u0007, line sep   para sep  ', tags: ['odd', 'todo'], 'created-at': '2022-02-03T00:00:00.000Z' },
    { slug: 'my00000003', title: 'Emoji 📚✍️ and 漢字', text: 'Surrogate pair 😀, lone high \uD83D here', tags: ['😀'], 'created-at': '2022-02-04T00:00:00.000Z' },
  ],
  [B.novel]: [
    { slug: 'n0v3l00001', title: 'Глава 1', text: 'Был холодный ясный апрельский день.', tags: ['черновик'], 'created-at': '2023-04-01T09:00:00.000Z' },
    { slug: 'n0v3l00002', title: 'Глава 2', text: '…', tags: ['черновик'], 'created-at': '2023-04-02T09:00:00.000Z' },
  ],
  [B.inbox]: [
    { slug: 'inb0x00001', title: 'Call Anna', text: '', tags: [], 'created-at': '2024-01-01T00:00:00.000Z' },
  ],
  [B.recipes]: [
    { slug: 'r3c1p00001', title: 'Борщ', text: 'Свёкла, капуста, картофель.', tags: ['суп', 'favorite'], 'created-at': '2019-11-11T11:11:11.111Z' },
  ],
  [B.work]: [
    { slug: 'w0rk000001', title: 'Standup', text: 'Yesterday / today / blockers', tags: ['work'], 'created-at': '2024-06-01T08:00:00.000Z' },
  ],
  [B.travel]: [
    { slug: 'tr4v300001', title: 'Lisbon', text: 'Pastéis de nata — café ☕', tags: ['travel', 'new'], 'created-at': '2024-07-07T07:07:07.007Z' },
  ],
  [B.empty]: [],
  [B.ideas]: [
    { slug: '1d3a000001', title: 'App idea', text: 'Notes in Dropbox, on the phone.', tags: ['ideas'], 'created-at': '2025-01-01T00:00:00.000Z' },
  ],
};

const meta = {
  collectionsList: [B.anna, B.comics, B.mine, B.ghost, B.novel, B.inbox, B.recipes,
                    B.work, B.travel, B.empty, B.ideas],
  notesInfo: [
    { slug: B.anna, title: 'Л. Н. Толстой. Анна Каренина', count: 3 },
    { slug: B.comics, title: 'Comics', count: 7 },                     // drift: really 5
    { title: 'My notebook', slug: B.mine, count: 3 },                  // web add-book key order
    { slug: B.novel, title: 'Мой роман', count: 2 },
    { slug: B.inbox, title: 'Inbox', count: 1 },
    { slug: B.recipes, title: 'Recipes', count: 1 },
    { slug: B.work, title: 'Work', count: 1 },
    { slug: B.travel, title: 'Travel', count: 1 },
    { slug: B.empty, title: 'Empty book', count: 0 },
    { slug: B.ideas, title: 'Ideas', count: 1 },
  ],
  tagsInfo: {
    [B.anna]: ['классика', 'прочитанное', '5+'],
    [B.comics]: ['favorite', 'new'],
    [B.mine]: ['todo'],                                                // drift: missing 'odd', '😀'
    [B.novel]: ['черновик'],
    [B.inbox]: [],
    [B.recipes]: ['суп', 'favorite'],
    [B.work]: ['work'],
    [B.travel]: ['travel', 'new'],
    // no entry for B.empty
    [B.ideas]: ['ideas'],
  },
  syncedBy: 'desktop',                                                 // a key we don't know
};

// --- op semantics (spec/roadmap.md §6.2), in plain JS -------------------------

const clone = (x) => JSON.parse(JSON.stringify(x));
const distinctTags = (notes) => [...new Set(notes.flatMap((n) => n.tags || []))];

function refreshBook(m, slug, notes) {
  const info = (m.notesInfo || []).find((b) => b.slug === slug);
  if (info) info.count = notes.length;
  if (!m.tagsInfo) m.tagsInfo = {};
  m.tagsInfo[slug] = distinctTags(notes);
}

function applyOp(op, state) {
  const { meta: m, books: bs } = state;
  const slug = op.book;
  switch (op.op) {
    case 'note/add': {
      const notes = bs[slug];
      if (!notes.some((n) => n.slug === op.note.slug)) notes.push(clone(op.note));
      refreshBook(m, slug, notes);
      return [];
    }
    case 'note/update': {
      const notes = bs[slug];
      const i = notes.findIndex((n) => n.slug === op.note.slug);
      let warnings = [];
      if (i >= 0) notes[i] = Object.assign({}, notes[i], clone(op.note));
      else { notes.push(clone(op.note)); warnings = [{ type: 'note/re-added', book: slug, slug: op.note.slug }]; }
      refreshBook(m, slug, notes);
      return warnings;
    }
    case 'note/remove': {
      bs[slug] = bs[slug].filter((n) => n.slug !== op.slug);
      refreshBook(m, slug, bs[slug]);
      return [];
    }
    case 'book/create': {
      if (!(m.notesInfo || []).some((b) => b.slug === slug)) {
        if (!m.notesInfo) m.notesInfo = [];
        m.notesInfo.push({ slug, title: op.title, count: 0 });
      }
      if (!m.tagsInfo || !(slug in m.tagsInfo)) {
        if (!m.tagsInfo) m.tagsInfo = {};
        m.tagsInfo[slug] = [];
      }
      if (!(m.collectionsList || []).includes(slug)) {
        if (!m.collectionsList) m.collectionsList = [];
        m.collectionsList.push(slug);
      }
      if (!bs[slug]) bs[slug] = [];
      refreshBook(m, slug, bs[slug]);
      return [];
    }
    case 'book/rename': {
      const info = (m.notesInfo || []).find((b) => b.slug === slug);
      if (info) info.title = op.title;
      return [];
    }
    case 'book/delete': {
      if (m.notesInfo) m.notesInfo = m.notesInfo.filter((b) => b.slug !== slug);
      if (m.tagsInfo) delete m.tagsInfo[slug];
      if (m.collectionsList) m.collectionsList = m.collectionsList.filter((s) => s !== slug);
      delete bs[slug];
      return [];
    }
    default: throw new Error('unknown op ' + op.op);
  }
}

// --- scenarios -----------------------------------------------------------------

const newNote = { slug: 'N3wN0te001', title: 'Купить билеты', text: 'Поезд в 9:15',
                  tags: ['todo', 'travel'], 'created-at': '2026-10-03T12:00:00.000Z' };

const scenarios = [
  { name: 'add-note', ops: [{ op: 'note/add', book: B.mine, note: newNote }] },
  { name: 'add-note-existing-slug',
    ops: [{ op: 'note/add', book: B.mine, note: { ...newNote, slug: 'my00000001' } }] },
  { name: 'update-note',
    ops: [{ op: 'note/update', book: B.anna,
            note: { slug: 'Vh2xQ1cccc', title: 'Вопрос', tags: ['классика', 'цитаты'],
                    'updated-at': '2026-10-03T12:30:00.000Z' } }] },
  { name: 'update-note-missing',
    ops: [{ op: 'note/update', book: B.inbox,
            note: { slug: 'g0n3n0t3xx', title: 'Edited here, deleted elsewhere', text: 'edit wins',
                    tags: [], 'created-at': '2026-01-01T00:00:00.000Z',
                    'updated-at': '2026-10-03T12:31:00.000Z' } }] },
  { name: 'remove-note', ops: [{ op: 'note/remove', book: B.comics, slug: 'c0m1c00002' }] },
  { name: 'remove-note-missing', ops: [{ op: 'note/remove', book: B.comics, slug: 'n0th3r3xxx' }] },
  { name: 'create-book', ops: [{ op: 'book/create', book: 'N3wB00kSlg', title: 'Новая книга' }] },
  { name: 'rename-book', ops: [{ op: 'book/rename', book: B.comics, title: 'Comics & graphic novels' }] },
  { name: 'rename-book-missing', ops: [{ op: 'book/rename', book: 'n0b00kxxxx', title: 'Nope' }] },
  { name: 'delete-book', ops: [{ op: 'book/delete', book: B.novel }] },
  { name: 'delete-ghost-book', ops: [{ op: 'book/delete', book: B.ghost }] },
  { name: 'move-note',
    ops: [{ op: 'note/add', book: B.ideas, note: books[B.inbox][0] },
          { op: 'note/remove', book: B.inbox, slug: books[B.inbox][0].slug }] },
];

// --- write ---------------------------------------------------------------------

function writeFolder(dir, m, bs) {
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, '.meta.json'), JSON.stringify(m));
  for (const [slug, notes] of Object.entries(bs)) {
    fs.writeFileSync(path.join(dir, slug + '.json'), JSON.stringify(notes));
  }
}

writeFolder(exportDir, meta, books);

fs.rmSync(scenariosDir, { recursive: true, force: true });
for (const s of scenarios) {
  const state = { meta: clone(meta), books: clone(books) };
  const warnings = s.ops.flatMap((op) => applyOp(op, state));
  const dir = path.join(scenariosDir, s.name);
  writeFolder(path.join(dir, 'expected'), state.meta, state.books);
  fs.writeFileSync(path.join(dir, 'scenario.json'),
                   JSON.stringify({ ops: s.ops, warnings }, null, 2) + '\n');
}

console.log(`wrote ${Object.keys(books).length} books and ${scenarios.length} scenarios`);
