## Client installation

Every player needs the matching **client distribution jar** in their
client's `mods/` directory (step 3 above); the server jar does not
belong on a client. That jar is not produced from this archive (see the
archive note under Installation). The
client half registers a K keybind in the **Gov** controls category.

- Press **K** to open the dashboard on the **Law** tab.
- `/gov dashboard` opens the **Dashboard** tab: your own government card with
  name, current job and id, rank, duty state, suspect flag, jail status and unpaid
  fine count. Every value comes from the server; the screen never guesses. A
  **Refresh** button asks again.
- The **Law** tab is the full reader: the left contents list groups all twelve
  chapters by category and scrolls when the window is short; search filters
  article ids, titles, body text, penalties, chapter titles and categories;
  clicking a result jumps to its article; the mouse wheel scrolls the article
  pane or the contents list; `PageUp`/`PageDown` and `Up`/`Down` scroll when the
  search box is not focused; `Left`/`Right` switch chapters; the Previous and Next
  buttons are disabled at the first and last chapter.
- The **Jobs** tab lists every job with its type (built-in, template, custom),
  member count and capability count. Selecting a job shows its capabilities (the
  whole registry, with the job's own marked) and its members. Controls appear only
  when the server says the viewer holds the capability: **Assign...** opens an
  online-player picker, each member row has **Unassign**, **Create** makes a
  custom job, **Delete** asks for a second click to confirm, and for non-built-in
  jobs each capability row has a `+`/`-` button. Every control sends a request and
  the screen re-reads the roster from the server, so it never shows a change the
  server refused.
- The **People** tab searches known people by name prefix (stored files plus the
  online players) and shows a person's card: name, UUID, online state, job, rank,
  note count, unpaid fines and the licence summary (class, effective state and
  active points). The notes section lists that person's notes, pages
  through them, and offers **Add note**, per-note **Edit** and **Delete** when
  the server says the viewer holds `note.write`; delete asks for a second click.
  The note field pre-fills from the row, so editing never loses text.
- The **Notes** tab lists the newest global notes across everyone, six to a page, with
  **Previous**/**Next**. Clicking a note opens that person's card in the People
  tab. The delete control appears only for `note.write` holders.
- The **Fines** tab lists fines with **All**, **Unpaid**, **Paid** and
  **Waived** filters, eight to a page. Selecting a fine shows the full record:
  band, fine, restitution and victim, total due, article and title, reason,
  receipt, collector and any exile order. **Issue fine...** opens the online
  player picker, band selector and the article, restitution, victim, exile and
  reason fields. **Pay** appears on your own unpaid fines, **Collect** for
  `fine.waive` holders collecting someone else's payment, and **Waive** opens a
  reason dialog that asks for a second click to confirm. The screen re-reads the
  list from the server after every change.
- The **Vehicles** tab lists the registry with **All**, **Valid**, **Impounded**
  and **Flagged** filters, eight to a page. Selecting a vehicle shows the plate,
  class, owner, registration, licence state and points, impound reason and
  official, release fee, release history and disposal flag. **Register...** opens
  a plate field, class selector and online-owner picker; the scan field performs
  a registry lookup. **Impound...** requires a reason and a second click to
  confirm. **Release (8g)** shows the recorded fee and asks the server for the
  whole transaction. **Licence...** opens the issue, +2/+4/+6 point and revoke
  controls; points and revocation need a reason, and revoke asks for a second
  click.
- The **Weapons** tab has **Registry**, **Permits** and **Explosion areas**
  sections. The classification row is always available: type an item id and
  press **Classify**, or press **Held** to ask the server about your main-hand
  item. The registry section filters by **All**, **Active**, **Revoked** and
  **Lost**, eight to a page; selecting a serial shows its class, type, owner,
  status, permit reference, dealer sale and transfer history, with
  **Transfer...**, **Revoke...** and **Loss...** controls for
  `weapon.register` holders. The permits section loads a person's permits,
  with **Issue permit...** (class selector including BLASTING, H and mining),
  and per-row **Suspend...** and **Revoke...** controls; all lifecycle changes
  require a reason. The explosion-area section lists the public registry and
  offers **Issue area...** (dimension, center, radius, minutes and reason) and
  **Revoke...**. Every control is a request/result round trip and the screen
  re-reads the server after a change; it never patches a local copy.
- `Escape` closes the window (and closes the fine, vehicle or weapon dialogs,
  the assign picker or a note edit first).
- The **Audit** tab has two sections. **Audit log** shows a filtered, paginated
  list (eight to a page) with actor/action/target fields and outcome/window
  selectors, plus a detail pane carrying every stored field and an **Export**
  button for `audit.view` holders. **Administration** is capability-gated on
  `season.admin`: it shows the exact reset plan and counts, **Begin reset**
  returns a token, the token field and **Commit** perform the second step, and a
  section picker with **Export** writes any registry section. An export receipt
  shows the exact relative path, byte size and sha256; a commit receipt shows
  the backup path and hash. There is no edit or delete control for an audit line.
- The sidebar lists all twelve implemented tabs, including **Treasury**
  (**Balances**, **Store**, **Contracts** and **Bounty**), so there is no tab
  that opens nothing. **Balances** carries one button row plus a pay form for
  transfer, withdraw, history, pay and payroll-run, with letter shortcuts **T**,
  **W**, **H**, **P** and **R**; **Contracts** carries post and contract-credit
  buttons with shortcuts **O** and **C**. Shortcuts never fire while a text
  field is focused. The operator-only credit form on **Balances** stays hidden
  unless the server confirms the viewer holds the credit authority. **Bounty**
  lists the active bounties with a place form (target picker plus amount) and a
  per-row cancel that asks for a second click; only the poster can cancel.

The screen is rendered with flat `fill` rectangles and the vanilla font, in one
monochrome palette: background `#080808`, panel `#F2F2F2` and text `#111111`.
Status is words, weight and rules, never colour: POSITIVE `#303030`, WARN
`#555555` and DANGER `#000000`. There are no gradients, shadows, rounded corners
or hue; the only raster art is the tab-icon set under
`assets/gov/textures/gui/icons/`.

Gov declares `displayTest = "MATCH_VERSION"` in its metadata, but note that
NeoForge 21.1.259 (FML 4.0.45) no longer reads that key. The key is documentation;
the network channel described above is what actually enforces the both-sides
requirement.

## Economy rollout

Accounts are `government`, `police`, `player:<uuid>` and `business:<id>`. Every
account is capped at 10,000,000 gold and may not go negative; all ledger writes
are atomic. `contractEscrow` is a marker, not an account.

The economy defaults are off: `gov.bank.enabled=false`,
`gov.store.enabled=false` and `gov.contract.enabled=false`. The bank requires
configured `gov.bank.bankRegions`/`gov.bank.townhallRegions`; the store requires
a catalogue and a market floor; contracts require a non-blank
`gov.bank.contractReviewer`. Invalid configuration fails startup.

Payroll keys and defaults: `perPayout` 64, `runCap` 1024, `arrearsCap` 640,
`govWage` 16, `policeWage` 16 and `defaultJobWage` 8.

Physical gold leaves custody only through a teller withdrawal. Store purchases,
payroll, contract escrow and the operator credit remedy move ledger balances
between accounts or into escrow; they do not mint physical gold. The store never
buys back.

## Rebuilding

```bash
cd ~/path/to/gov
gradle clean build --offline
```

System Gradle 9.x; no wrapper is included. `--offline` resolves NeoForge
21.1.259 from the local Gradle cache. The build produces the plain server jar.

For the full acceptance gate (build, bytecode isolation scan, isolated dedicated server, RCON
command matrix, startup contract and clean shutdown), run:

```bash
tools/acceptance.sh
```

The harness never opens `run/world` and never commits a live season reset; its server lives under
`run/build/gov-acceptance/` and the directory is confined there before anything is created. It
also fails if any non-client class names a client class, and the jar is built reproducibly. See
`docs/FUTURE-PROOF-AUDIT-M10a.md` for the M10a audit findings and residual risks,
`docs/gui/G3-VERIFICATION.md` for the GUI G3 report, `docs/gui/GUI-SPEC.md` for the binding
pre-M12 visual contract, and `docs/OPERATIONS.md` for install, command, capability, config,
backup and recovery details.

The deliverable this archive builds is:

```
build/libs/gov-1.0.1.jar      # dedicated server mods/
```

It reports mod version `1.0.1`. Verify it after every build:

```bash
sha256sum build/libs/gov-1.0.1.jar
```

See `docs/DEPLOYMENT.md` for the full install, verify and update procedure.

## License

All Rights Reserved, as declared in `src/main/resources/META-INF/neoforge.mods.toml`. No license
file or mod logo is bundled. The jar does contain 24 small GUI tab icons (16x16 and 32x32 PNGs);
they are interface art, not a mod logo, and need no separate license file.

## Regenerating the law JSON

The website is the source of truth for the law. Do not hand-edit the generated
JSON. After any change to
`../saf-baby-website/app/documentation/rules/law/law.ts`, run:

```bash
node tools/generate-law-json.mjs
gradle build --stacktrace
```

The generator uses the website's installed TypeScript package, transpiles
`law.ts` in memory, evaluates the exported data, and writes
`src/main/resources/data/gov/law/season10.json`. It hard-verifies:

- exactly 12 chapters and 159 articles;
- Chapter XII is titled `Banking, Currency and Public Finance`;
- no duplicate chapter or article ids;
- article 5.13 contains Schedule 5.A and the Class L/S/R/P/H/D/G permit text;
- article 6.09 contains the Quad (Q), Car (C), Truck (T), Helicopter (H), and
  Plane (P) license text;
- articles 6.13 and 6.14 exist.

See `tools/README.md` for details and the `HORIZONS_WEBSITE_ROOT` override.

The generated JSON is bundled in the jar, so the server and the client
reader always use the same law text.

## Known limitations

- The book handed out by the server is a **vanilla written book**: plain text,
  two pages at a time, no search, no contents sidebar, and no clickable article
  navigation. The dashboard's Law tab is the polished view and needs the mod on
  the client, which every player must install anyway.
- A vanilla written book cannot use the dashboard theme, and its page count
  grows with the law. The book is paginated for readability at roughly 240
  characters per page.
- The dashboard uses the vanilla font. Article ids are drawn with a fixed advance
  to look monospaced, but this is not a custom font asset.
- The Law tab is display-only. It does not edit the law; changes come from
  the website and the generator. The server-validated `LAW_ARTICLE` action exists
  and is exercised at startup, but the tab renders the bundled law directly.
- The dashboard's self card is refreshed on open and by the Refresh button; it is
  not pushed live yet. Notifications (`GovNotify`) are not wired in this build;
  job assignment and removal do notify the affected player in plain chat.
- Renaming a job is supported by the server action (`JOB_EDIT` with a display
  name) but is not surfaced in the M2 Jobs tab or the chat commands; the M2
  dashboard scope covers create, delete and capability edits.
- Notes are stored in `gov.dat` and are editable: an edit keeps the original
  author and creation time and stamps `editedAt`, and every accepted mutation is
  recorded in the audit log. One person may hold at most 50 `GLOBAL` notes, and
  note pages carry six rows because the note text travels in full so an edit can
  pre-fill. M11's job and business scoped notes live in the same `gov.dat` but
  are a network/GUI-only surface: they never appear in `/gov note` or the Notes
  tab, and they have their own caps (see `docs/OPERATIONS.md`).
- The licence summary on a person card is the registry's own answer: each class
  the person holds with its effective state (VALID, SUSPENDED or REVOKED) and
  active point total, or `none` when they hold no licence.
- A vehicle licence is required to register or transfer a vehicle by default.
  This is a registry policy, not a statutory rule: Article 6.09 bans unlicensed
  driving, while Article 6.10 only requires a registry entry. Set
  `gov.vehicle.requireLicenseToRegister=false` for an off-road or project
  vehicle whose owner is between licences. No driving enforcement exists because
  vanilla has no vehicle entity; a scan flags a missing or invalid licence.
- Impound and release reuse the M4 reception and gateway. A release needs the
  owner online at the configured reception with the exact fee in gold ingots;
  the acting official must also be at the reception. If the reception is not
  configured, release is refused rather than silently remote.
- A vehicle unclaimed for 30 in-game days is marked `FLAGGED_FOR_DISPOSAL` the
  next time the registry is read or a release is attempted. The mark is audited
  and is a flag only: this build never deletes a vehicle, its contents or its
  record. Actual disposal is a staff action with notice under Article 6.15.
- Plate scans are registry lookups. Vanilla 1.21.1 has no vehicle entity, so the
  physical-vehicle side is a `VehicleAdapter` boundary whose shipped adapter is
  `registry-only`; a future vehicle mod can supply an entity source without
  changing the service or the record.
- A fine is paid only in physical gold ingots at the configured reception. Set
  `gov.reception.configured=true` and the `gov.reception.dimension/x/y/z/radius`
  values; the default is deliberately unconfigured, so self-payment is refused
  until an operator places the Government Building reception. Fines remain
  physical-gold at reception and are not paid from an account;
  `gov.reception.requireReception` cannot create a remote fine payment path, and
  the bank ledger is separate.
- The store is buy-only and never buys back. Contract expiry settles the
  materials share under Article 12.14 even if the builder later became a
  govworker.
- Restitution is recorded on the fine and included in the amount due at
  reception; distributing it to the victim is staff roleplay, not a bank
  transfer. An AGGRAVATED exile order is stored on the record; enforcing the
  exile is M8/staff work and is not automated in this build.
- Fine notifications are plain chat lines on issue and on join. The full
  `GovNotify` push channel is not wired in this build.
