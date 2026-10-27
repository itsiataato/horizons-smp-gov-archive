  prints every field of one line, including the full payload summary and reason.
  Reads and pages never append to the log they read.
- `/gov audit export` writes the whole log to
  `run/gov-exports/gov-audit-<timestamp>-<random>.json`. The reply is the exact
  relative path, byte size and sha256. A console/RCON export is allowed and is
  audited as a console action; the dashboard sends the same request through the
  dispatcher and needs `audit.view`.
- `/gov admin export <section>` writes one registry section for a `season.admin`
  holder. Sections: `audit`, `players`, `jobs`, `fines`, `notes`, `businesses`,
  `vehicles`, `weapons`, `permits`, `area-permits`, `detentions`. The section is
  an enum, never a path: a raw `../../world` cannot become a filename and is
  refused before any file is opened.
- `/gov admin season status` prints the plan: the exact sections that would be
  cleared, the counts now, the sections always kept, the token lifetime, any
  pending token and the two fixed directories.
- `/gov admin season reset begin` starts the two-step flow. It clears nothing and
  returns a single-use token valid for `gov.season.resetTokenSeconds` (default
  300 seconds), tied to the actor and the loaded data. It can be run from the
  console/RCON because the console is the server operator; a player needs
  `season.admin`.
- `/gov admin season reset commit <token>` consumes the token, writes a full
  backup to `run/gov-backups/`, re-reads and verifies its size, parse and sha256,
  and only then clears the season records. The backup is never deleted. A token
  is single-use even if the commit is later refused.

The default reset scope is explicit: licences, vehicles, weapons, weapon
permits, explosion-area permits, detentions, fines and notes are cleared, and
every player's primary job is reset to `citizen` (duty returns to `N_A` and the
denormalised jail summary is cleared). Job definitions, personal capability
grants, the audit log, warrants, plate scans and the config are always kept.
Business records are kept by default; set `gov.season.clearBusinesses=true` to
include them. The begin answer reports which policy is in force, and a pending
token keeps the policy it was begun with.

Every op has a rate-limit policy: reads and ordinary mutations cost one token,
an export costs three and a season step costs five. The bucket is per actor,
never global. `gov.security.maxRequestsPerTick` (default 64) is a final
per-tick backstop across all actors; a request over it is refused with
`RATE_LIMITED`. The startup check asserts the policy coverage, the
limit-1/limit/refill boundaries and the per-actor isolation.

The startup check also runs a deterministic hostile-input corpus of more than
900 cases across payload decoding, field blocks, op/error/enum parsers, the
sanitizer, plates, names, articles, ids, fine/permit/detention bounds and
pathological strings (control characters, unicode, lone surrogates, oversized
values, overflow numbers, NaN/Infinity, path traversal and unknown fields). A
case that throws fails the boot; the dispatcher boundary returns a clean error
code for an unknown op, unknown field, bad codec or oversized payload.

The classifier is `data/gov/weapons/classes.json`, bundled with the build. In
order it uses an exact item-registry id (`minecraft:tnt` is Class B Blasting),
an item tag (`gov:explosive`, `gov:nuclear`, `gov:weapon_like`), a soft TACZ
namespace/category table (pistol/handgun L, shotgun S, rifle/smg R,
sniper/marksman P, machine gun/RPG/launcher/grenade/explosive H, with the law's
example families such as M4A1, MP5, AWP and M249), and conservative whole-token
heuristics for uranium/nuclear and explosive ids. The file also names the TNT
items and the ignition items (`tntItems`, `ignitionItems`) so the Blasting
exception and the flint-on-TNT check are data, not code. An item that is not
listed and
not weapon-like is `NONE`, so ordinary blocks and tools are never classified as
weapons. An unlisted TACZ or weapon-like item is Class H until classified (law
5.13). There is no compile-time TACZ dependency.

Enforcement is server-side and cancels the interaction; it never removes an item
or searches a container (Article 9.04). Uranium and nuclear material are denied
at pickup, use and placement without the Class G military-duty authority
(`military.authority` + a valid Class G permit + on-duty). Every explosive other
than TNT is military-grade in the same way. TNT is the sole Blasting exception:
a valid Class B Blasting permit and at most 16 TNT in the whole inventory, and a
TNT placement, ignition or detonation needs an active explosion-area permit
covering the position. Firearm use, attack and placement need the matching
permit; Class H needs Class H plus Class G and on-duty status. Town cores are
only the regions configured in `gov.weapon.townCores` (default empty); inside
one, only a Class L handgun with a Class L permit or an on-duty Class G officer
may carry. Denial messages are rate-limited and denials are audited at most once
per audit window per player, so a client in a loop cannot fill the log.

The M6 server config keys are:

- `gov.weapon.seasonEnd` - an ISO-8601 instant or date, or empty (default). A
  permit issued while it is set expires at that moment; empty means no expiry.
- `gov.weapon.townCores` - a list of
  `dimension:minX,minY,minZ:maxX,maxY,maxZ` regions, default empty. The Article
  5.14 town rule is inert until a region is configured.
- `gov.weapon.enforcementMessageCooldownMs` (default 1500) and
  `gov.weapon.enforcementAuditCooldownMs` (default 10000) bound denial chat and
  audit spam per player.

The military gate is the `military.authority` capability, seeded into the
executive job and grantable to a custom job; it is never a job-name check. An
older world is migrated on load by adding that one capability to the stored
executive job, preserving every other capability an administrator put there.

M9 adds one security key and two season keys:

- `gov.security.maxRequestsPerTick` (default 64) - the global per-tick request
  backstop across all actors. It is not a shared quota; each actor still has
  their own bucket.
- `gov.season.resetTokenSeconds` (default 300) - how long a season-reset token
  stays valid. The token is in memory only, so a restart invalidates it.
- `gov.season.clearBusinesses` (default false) - whether a reset also clears
  business records. Every other section is fixed by the explicit plan.

The `season.admin` capability is new in M9. It is seeded into the built-in
executive job and added to an older world's stored executive job on load by the
same additive, idempotent migration as M6's `military.authority`. Judges hold
`audit.view` and may read and export the log, but cannot begin or commit a
season reset.

Fines are gated by capability: issuing needs `fine.issue` (police and
govexecutive); waiving and collecting at the desk need `fine.waive` (govworker
while `gov.govworkerCanWaiveFines` is true, judge and govexecutive); any player
may read and pay their own. `fine.view.all` adds every fine to the list, and a
`fine.waive` holder also sees every fine because a receptionist cannot clear a
fine they cannot find.

Reading the law, your own card and the job roster has no permission requirement.
The job mutations require capabilities: `job.manage` (held by `govexecutive`) for
create, rename, delete and capability edits, and `job.assign` (held by
`govexecutive`, and by `govworker` while `gov.govworkerCanAssign` is true) for
assign and unassign. Person lookup and notes are gated the same way: searching
people and opening a card need `player.lookup` (police, govworker, judge,
govexecutive), reading notes needs `note.view.all` (police, judge, govexecutive),
and adding, editing or deleting a note needs `note.write` (police, judge,
govexecutive). Those two ids govern `GLOBAL` notes; the M11 job and business
scopes are checked by their own service and a member or staff member reads them
without `note.view.all`. The console is the server operator and may read the same views.
A console or command block gets an explicit failure for the
player-only commands because a book cannot be handed to a non-player source, a
screen cannot be opened for one, and a mutation needs an actor.

