# Gov

The Horizons SMP government mod for NeoForge 1.21.1.

This build delivers the **Season 10.5 Law** and a v3 economy on top of the M9
administration surface: custodial bank accounts, a government store, payroll paid
from account ledgers and building contracts. The dashboard has twelve sidebar
tabs plus a hidden Store tab: **Dashboard** (the player's own government card),
**Law** (the full reader), **Jobs** (the job roster, detail and controls),
**People** (server-authoritative person lookup with cards and notes), **Notes**
(the newest global notes, paginated), **Fines** (fines, receipts and the issue,
pay, collect and waive controls), **Vehicles** (plate registry, licences, scans,
impound and release), **Weapons** (firearm registry, permits, explosion areas and
the classifier), **Businesses** (public registry, staff and linked job), **Jail**
(detention records with the Article 9.12 legal-basis gate and native cuffs),
**Treasury** (the **Balances**, **Store** and **Contracts** subtabs) and
**Audit** (the append-only change log, exports and the two-step season reset).
The rest of the police tools are planned as separate work; `/gov police` is not
registered in this build.

The audit log is append-only and is never edited or deleted from the dashboard or
a command. Reads do not append to it. A full export or backup goes to a fixed
directory under the server run directory, is written atomically, is verified by
re-reading it, and reports its relative path, byte size and sha256. A season
reset is two-step: `begin` returns a short-lived single-use token and the exact
counts; `commit` writes and re-verifies a full backup before clearing anything.
There is no code path that deletes a backup.

Gold ingots remain the only mint. A bank account is a custodial vault ledger,
not a second currency: physical gold moves into an account on deposit and out
only through a teller withdrawal. A 2% tax applies to deposits, teller
withdrawals, `/pay` and taxable business transfers; government and police
accounts are tax-exempt. Payroll now moves gold from the account ledger into
player accounts. A linked job still grants no capability.

Season 10.4 employment adds positions, written offers, contracts, payroll runs,
complaints, soft closure and a ten-row player history. The owner's `ceo` seat is
the fixed root of a position tree; every other seat carries `hire`, `fire`,
`promote`, `demote`, `positions.manage`, `payroll` or `notes` and can act only
on seats strictly below itself. A player may hold one government job and one
standing business contract; a one-time task contract does not occupy the
business slot. Offers are written and the target accepts or declines. Three
upheld CEO complaints on the same active contract end it and issue the Article
9.11 64-gold AGGRAVATED fine plus restitution equal to the wages actually paid,
credited to the CEO's UUID when the fine is settled. A closure is requested and
then approved or rejected by `business.close.approve`; the record and every
position are retained with status `CLOSED`. On top of employment, Season 10.5
adds banking, the store, contracts and payroll.

Jobs are capability bundles. The server ships three protected built-ins
(`citizen`, `police`, `govexecutive`) that can never be renamed or deleted, and
two deletable templates (`govworker`, `judge`). Players hold exactly one primary
job and are enrolled as `citizen` on first join. Custom jobs are created empty
and gain capabilities only through explicit edits.

The law is regenerated from the website's `law.ts`. The bundled data currently
has 12 chapters and 159 articles (Season 10.5).

There is no custom item and no mixin. There is custom networking, and it is
required: the server and the client refuse to connect without each other (see
below).

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x (built and verified against 21.1.259)
- Java 21
- System Gradle 9.x for building; no wrapper is included

## Installation (server and client)

Gov is required on **both sides**, but the two sides take **different jars**
from one build: the plain jar on the server, the client distribution jar on
clients. Both report mod version `1.0.1` (see `gradle.properties`).

> **Archive note:** this public snapshot builds only the server jar
> (`build/libs/gov-1.0.1.jar`). The matching client jar from the original
> v1.0.1 build is not redistributed here, so a fresh client install cannot
> be assembled from this archive alone.

1. Build the server jar (see below): `build/libs/gov-1.0.1.jar`.
2. Put `gov-1.0.1.jar` in the dedicated server's `mods/` directory.
3. Clients need the matching client distribution jar from the original
   v1.0.1 build (not included in this archive) in their `mods/` directory.
4. Start the server, then connect with a client that has the client jar.

The client jar hash varies per build environment: reinstall it on clients
and re-verify after every rebuild.

The server and every client must run the same gov version; update the server and all clients
together. This is enforced, not merely requested: the mod registers a **non-optional, versioned
network channel** (`gov:handshake`), and NeoForge's connection negotiation refuses the
connection when the channel is missing on either side or the two sides' versions differ. A
vanilla client cannot join this server, and a client with a different gov version cannot either.
After the connection is accepted, the server and the client exchange a handshake carrying the
wire protocol number and the mod version, and each side logs what it saw.

Nothing needs to be configured for the law, the dashboard or the job roster. The reception, the
native jail cell and the town cores are deliberately unconfigured by default: fine payment and
vehicle release need `gov.reception.configured=true` with real coordinates, physical confinement
needs a plugin jail or a configured native cell, and the Article 5.14 town rule is inert until
`gov.weapon.townCores` is set. See `docs/OPERATIONS.md` for the full config reference. On a
player's first join the server creates a vanilla written book titled **Season 10 Law**, adds it
to their inventory, and drops it at their feet if the inventory is full. The delivery is recorded
in the player's persistent data under `gov:received`, so it happens once per player.

Commands:

- `/gov` with no subcommand prints the one-line usage reminder.
- `/gov law` gives the executing player a fresh copy of the law book and opens the
  dashboard's Law tab on their client.
- `/law` is kept as a short alias for `/gov law`.
- `/gov dashboard` opens the dashboard's Dashboard tab on the executing player's client.
- `/gov me` prints the same self card the Dashboard tab shows, in chat.
- `/gov job list` and `/gov job info <job>` print the job roster and one job's
  capabilities and members. They work from the console too; they are the same
  public information the Jobs tab shows.
- `/gov job assign <player> <job>`, `/gov job unassign <player>`,
  `/gov job create <name>`, `/gov job delete <job>` and
  `/gov job caps <job> [add|remove <capability>]` are mutations. They run only for
  a player: every mutation is an audited action with an actor, a capability check
  and a rate limit. The command still answers a console with the specific
  refusals it can decide without an actor (an unknown job, a protected built-in).
- `/gov person <name>` prints the same person card the People tab shows, in chat:
  name, UUID, online state, job, rank, note count, unpaid fines and the licence
  summary. It resolves the name against the server's own sources, exactly as the
  tab does.
- `/gov note list <player>` prints that person's `GLOBAL` notes, newest first.
  Scoped job and business notes are not shown by any command yet.
- `/gov note add <player> <text...>`, `/gov note edit <player> <noteId> <text...>`
  and `/gov note delete <player> <noteId>` are mutations and run only for a
  player. The data-only refusals (an unknown person, an unknown note, empty or
  over-length text) are answered first by the same service the server action
  calls.
- `/gov fine list [player|all]` prints the first page of fines: band, amount,
  restitution, state, article and reason. A player without `fine.view.all` (or
  the reception `fine.waive`) sees only their own fines.
- `/gov fine info <fine>` prints the full record, including the receipt, the
  collector, the waiver reason and any exile order.
- `/gov fine issue <player> <band> [article] [restitution] [victim=Name]
  [exile=Days] <reason...>` issues a fine. The band is PETTY, MISDEMEANOR,
  FELONY or AGGRAVATED; the article defaults to 9.11 and is validated against
  the bundled law; restitution is optional and needs `victim=Name`; an
  AGGRAVATED fine defaults to a 7-day exile order and accepts 7 to 30.
- `/gov fine pay <fine>` pays one of your own unpaid fines with physical gold at
  the Government Building reception.
- `/gov fine waive <fine> <reason...>` clears an unpaid fine with a recorded
  reason. A waiver keeps the record and marks it WAIVED.
- `/gov vehicle list [player|all]` prints the first page of the plate registry:
  plate, class, owner, status, licence state and points, and any impound reason.
  A player without a registry capability sees only their own vehicles.
- `/gov vehicle info <plate>` prints the full registry entry, licence state and
  points, impound reason and official, release fee, release history and any
  disposal flag.
- `/gov vehicle register <plate> <class> [player]` registers a plate for the
  named player or the executing player. The plate is validated against the law
  format and stored in one canonical form, so `ABC-123` and `ABC123` are the same
  plate. Classes are `quad`, `car`, `truck`, `helicopter` and `plane`.
- `/gov vehicle scan <plate>` is a registry lookup: owner, class, licence state,
  impound state and the source of the answer. A player scan is rate-limited and
  recorded; a console scan is a read and writes no record.
- `/gov vehicle impound <plate> <reason...>` is a tow-only state change. The
  vehicle, its contents and its record are never deleted. The release fee is
  copied onto the impound from `gov.vehicle.impoundReleaseFeeGold` (default 8).
- `/gov vehicle release <plate>` releases at the Government Building reception
  when the owner is online, at the reception, and carries the recorded fee in
  physical gold ingots. The release is recorded; the registration returns VALID.
- `/gov license issue <player> <class>` issues a licence; `/gov license revoke
  <player> <class> <reason...>` revokes one with a recorded reason; `/gov license
  points <player> <class> <2|4|6> <reason...>` assesses points. Twelve points in
  30 in-game days suspends the licence for 7 in-game days; points expire from the
  window after 30 in-game days, and the raw history stays for audit.

Weapons, permits and explosion areas are the M6 system. The registry is
server-authoritative and data-driven:

- `/gov weapon list [player|all]` lists the registry (serials, class, owner,
  status, permit reference). A citizen sees only their own; `weapon.view` or
  `weapon.register` sees all; the console sees all.
- `/gov weapon info <serial>` prints one record, its permit reference, dealer
  sale stamp and transfer history. Serials are canonical uppercase and compared
  case-insensitively, so `ab-1234` and `AB-1234` are one weapon.
- `/gov weapon register <player> <class> <serial> <type>` registers a firearm.
  Classes are `l`, `s`, `r`, `p` and `h`. The owner must already hold the
  matching carry permit; Class H additionally requires a valid Class G permit.
  A duplicate serial is refused with `SERIAL_TAKEN`.
- `/gov weapon transfer <serial> <player>` records an ownership change with the
  Article 5.09 requested and completed stamps. The new owner must hold the
  matching permit, and the completion must fall inside one in-game day.
- `/gov weapon revoke <serial> <reason...>` and
  `/gov weapon loss <serial> <reason...>` keep the record; nothing is deleted.
- `/gov weapon permit list <player>` prints every permit for a person.
- `/gov weapon permit issue <player> <class> [reason...]` issues a permit.
  Classes are `l`, `s`, `r`, `p`, `h`, `d`, `g`, `blasting` and `mining`.
  Class G requires the issuer's `military.authority` capability; Class H
  requires the holder's valid Class G permit. A permit expires at the configured
  season end, or is open-ended when no season end is configured.
- `/gov weapon permit revoke <player> <class> <reason...>` and
  `/gov weapon permit suspend <player> <class> <minutes> <reason...>` change the
  lifecycle. Suspension and expiry are computed at read time; nothing needs a
  scheduler.
- `/gov weapon classify [itemId]` asks the server for the classification of an
  item id, or of the held item when the id is omitted.
- `/gov area permit list` is the public Article 8.12 explosion-area registry.
- `/gov area permit create <dim> <x> <y> <z> <radius> <minutes> <reason...>`
  issues a bounded area for a stated window. Overlap is allowed.
- `/gov area permit revoke <id> <reason...>` cancels one with a reason.
- `/gov business list|info|register|close|suspend|employee|link|unlink|payroll` is the
  M7 registry. The public roster and one business card are reads; registration, transfer,
  suspension, closing, staff edits, job linking and the legacy payroll note (read-only while banking is enabled) are
  capability-gated mutations. A linked job grants no capability. See `docs/OPERATIONS.md` for
  every argument.
- `/deposit <amount>` deposits physical gold into your account inside a bank or
  townhall region. The amount is 10-64g, a 2% tax applies, and it uses the one
  economy action per minute budget.
- `/pay <player> <amount>` moves 2-64g to an online player and charges the 2% tax.
- `/gov bank balance [account]`, `/gov bank history [account] [page]`,
  `/gov bank transfer <from> <to> <amount> <reason...>`,
  `/gov bank withdraw <player> <amount>` and the operator-only `/gov bank credit`
  cover the ledger: a teller withdrawal is 2-64g, a transfer is 2-64g, taxable
  transfers carry the 2% tax, and only the allowed account pairs are accepted.
  The operator credit is also a Treasury Balances form, visible only when the
  server confirms the viewer holds the credit authority.
- `/gov payroll run <scope> [reason...]` runs payroll for `gov`, `police` or
  `business:<id>`, at most once per 24-hour bucket. Department wages come from
  config; a business pays one day at the frozen contract wage, with a 2% tax on
  business payouts.
- `/gov contract post <builder> <materials> <labor> <description...>`,
  `list [page]`, `accept`, `submit`, `approve` and `void <id> <reason...>` run
  the book-and-quill order: 1-99 per line, total <=198, a 7-day term, five
  concurrent contracts, one active contract per builder, escrow, and a
  non-executive reviewer gate.
- `/gov store` opens the Treasury Store subtab for a player; from the console it
  prints the catalogue. It is buy-only: 1-10 stacks at the tier price, with no
  sell-back.
- `/gov bounty place <player> <amount>`, `/gov bounty list [player]` and
  `/gov bounty cancel <bounty>` run the bounty board: placing escrows the
  poster's gold, listing shows the active bounties (or one player's), and only
  the poster can cancel for a refund. The Treasury Bounty subtab sends the same
  three requests with the same outcomes.
- The business employment actions are `BIZ_RENAME`, `BIZ_POSITION_LIST`,
  `BIZ_POSITION_CREATE`, `BIZ_POSITION_EDIT`, `BIZ_POSITION_DELETE`,
  `BIZ_POSITION_MOVE`, `BIZ_OFFER_LIST`, `BIZ_OFFER_SEND`, `BIZ_OFFER_RESPOND`,
  `BIZ_CONTRACT_LIST`, `BIZ_CONTRACT_END`, `BIZ_CONTRACT_PROMOTE`,
  `BIZ_PAYROLL_RUN`, `BIZ_COMPLAINT_LIST`, `BIZ_COMPLAINT_FILE`,
  `BIZ_CLOSE_REQUEST`, `BIZ_CLOSE_APPROVE`, `BIZ_CLOSE_REJECT` and
  `HISTORY_LIST`. The five list actions are reads. The rest are audited
  mutations. Every self-service action is baseline at the dispatcher and is
  authorized by the service; the two close decisions require
  `business.close.approve`, which only the executive job seeds.
- `/gov jail list|info|detain|release|extend|cell` is the M8 detention surface, with
  `/gov cuff <player>` and `/gov uncuff <player>`. `/gov jail cell <player> <n>`
  moves a detainee to a numbered cell; `/gov jail new <name> <here|x,y,z>` adds a
  runtime cell at your position or coordinates and `/gov jail delete <name>`
  removes it. Runtime cells persist in `gov.dat` and detain auto-selects the
  closest free cell. Detention requires consent, a violent offence
  or a warrant under Article 9.12; a record gates release and expiry even when no plugin jail or
  native cell is configured. See `docs/OPERATIONS.md` for the legal-basis argument shape.

The economy flags default to false; see `docs/OPERATIONS.md`.

The complete command reference, capability matrix, config keys and runbooks are in
`docs/OPERATIONS.md`. The summary below focuses on the M9 audit and administration surface.

M9 adds the audit and administration commands:

- `/gov audit list` prints the first page of the append-only log, newest first,
  with the retained count, the matching count and the oldest/newest timestamps.
  The window is bounded to the last seven days by default; the GUI offers 1 hour,
  24 hours, 7 days, 30 days and all.
- `/gov audit list actor <name>`, `/gov audit list action <op>` and
  `/gov audit list target <name>` filter the same page. `/gov audit info <seq>`
