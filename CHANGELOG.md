# Changelog

> Archived 2026-10-27 as read-only public v1.0.1. Internal docs and
> evidence were stripped from this public tree; behavior is unchanged
> from the final verified build except the two archive notes below.

## [1.0.1-archived] - 2026-10-27

### Added

- `LICENSE` (MIT, copyright itsiataato): anyone may fork, every fork must
  keep the copyright + license notice.

### Changed

- `build.gradle`: removed the client-jar post-processing steps (their
  tooling is not distributed here). `gradle build` produces the server
  jar. No behavior change to the mod itself.
- `tools/acceptance-part1.sh`: default localhost RCON password scrubbed
  to a `changeme` placeholder; set `GOV_ACCEPTANCE_RCON_PASSWORD` to run
  the harness. Test-only credential, never used on any live server.

All notable changes to the Gov mod. Format follows Keep a Changelog
(`Added` / `Changed` / `Fixed` / `Removed`). Mod version stays `1.0.1`
(`gradle.properties`) unless explicitly requested; every entry names its
TASK-ID. Open deferrals live in `docs/tech-debt.md` (TD-001 through TD-007) and are
referenced, not duplicated, here.

## [Unreleased]

### Added

- Bounty placement free-text target + sane amount entry: typed offline
  names route to the unchanged server resolver (`BAD_TARGET` on unknown),
  coarse stepper (x10) with edit-box clamping, bounds-aware invalid
  message, three new lang keys
  (`bounty_target_hint`, `bounty_amount_hint`, `bounty_amount_invalid`)
  (TASK-022).
- Offline-capable fine-issue and vehicle-register target entry via shared
  `GovOfflineTarget` helper (typed name wins, stepper as quick-pick,
  blank-key selection), 15/15 unit tests (TASK-023).
- Full 265-row live-server command matrix over the proven NeoForge
  negotiation path: 265/265 executed, 47 explicit unop denials, 0
  ERROR/FATAL (TASK-018).
- Element-level UI fit (replaces the TASK-019 zoom lens): dashboard shell
  stays normal off width/height; new `GovFit` helper (rows-that-fit,
  fits-within, shrink-step) + shared `fitBold` title shrink; bounty/card
  row counts derive from available height; fines/treasury/vehicles guards
  render full rows only (TASK-030).

### Changed

- Jail CUFF + UNCUFF pair replaced by one state-flip button, same dispatch
  and gates, top-bar space freed (TASK-024).

### Fixed

- Contract-credit infinite-money exploit: `CONTRACT_CREDIT` re-gated from
  `bank.manage` to `season.admin` at the dispatcher plus a handler
  `season.admin` re-check before the live permission-4 read, so op'd
  tellers are refused `FORBIDDEN` before any gold moves; opped-executive
  mint remains as audited parity with `/gov bank credit` per user scope
  decision 2026-10-26 (TASK-032).
- Empty-state text clipping in businesses/notes panels (bold-fit loop,
  y-advance ghost fix), screenshot re-check at 960x540 in Phase 10
  (TASK-026).
- Detention PUT IN CELL input popup (1..N, Enter/Esc) + scale-drag
  unscale (TASK-020, TASK-025). The TASK-019 global zoom + UI-Scale slider
  + tick poll were removed and replaced by element fit (TASK-030).

Deferred to follow-ups (not in this release): TASK-027 shared-emptyState
migration (low), TASK-028 fines free-text typing UI (medium), TASK-029
matrix leftover contract/bounty-cancel leaves (low).

## [1.0.1] - 2026-10-26

Command-to-dashboard parity is 100%: every `/gov` leaf maps to a dashboard
request with the same opcode and equivalent payload
(`docs/parity-matrix.md`, TASK-009; gaps closed by TASK-013/014/015).

### Added

- This changelog, `docs/DEPLOYMENT.md`, and README corrections for the
  1.0.1 two-jar install, bounty commands, jail cell commands, Treasury
  shortcuts and the real build command (TASK-007). No behavior change.

- `/gov bounty place <player> <amount>`, `/gov bounty list [player]` and
  `/gov bounty cancel <bounty>` plus a Treasury Bounty subtab sending the
  same three requests. Escrow, caps, refund and poster-only cancel are
  enforced server-side in `BountyService` (TASK-013).
- `/gov bank credit` dashboard parity: `BANK_CREDIT` opcode plus a Balances
  credit form behind a dual gate (capability plus live permission
  re-check), audited through the same call as the command (TASK-014).
- `canCredit` affordance flag on `BANK_*` balance answers so the credit
  form stays hidden fail-closed without the authority; forged flags are
  still refused server-side (TASK-010 step-2).
- Treasury Balances/Contracts buttons, letter shortcuts (**T**/**W**/**H**/
  **P**/**R** on Balances, **O**/**C** on Contracts) and a pay form for
  `BANK_TRANSFER`, `BANK_WITHDRAW`, `BANK_HISTORY`, `PAY_PLAYER`,
  `CONTRACT_POST`, `PAYROLL_RUN` and `CONTRACT_CREDIT`. No new opcodes;
  payload keys match the command path verbatim (TASK-015).
- `JAIL_CELL_CREATE` / `JAIL_CELL_DELETE` opcodes plus a dashboard cell
  list with per-row delete. Runtime cells persist in `gov.dat` and detain
  auto-selects the closest free cell (TASK-002, TASK-003).
- `GovGlobalGuard` never-throw boundary on dispatch and command paths:
  null payloads become refusals, throwing handlers become `INVALID_STATE`,
  failing startup checks are recorded without aborting boot (TASK-012).

### Changed

- `bankCredit` and `contractCredit` folded into one `consoleCreditToOwner`
  path with identical behavior (TASK-006; see TD-001).
- Business-employment client requests consolidated and shared parsing
  helpers canonicalized (`GovFields`, `GovTargets`), behavior-preserving
  (TASK-001, TASK-011).
- Dashboard Enter handling is focus-aware with a repeat guard on
  Balances/Bounty submits (TASK-006).

### Fixed

- Jail cell create/delete bypassed the dispatcher, so any player could
  mutate cells: player paths now dispatch through the capability gate,
  console is permission-gated, failures return 0 (TASK-002, TASK-011/012).
- Credit button/form was visible without the authority (server still
  refused): now hidden fail-closed on `canCredit` (TASK-010 step-2).
- Junk cell dimensions and other hostile inputs return `BAD_PAYLOAD`
  instead of crashing (TASK-012).
- Phase 8 gate: full regression 47/47 plus `gradle clean build --offline`
  green (TASK-006).
- `job caps add` no longer lets a `job.manage` holder grant authority
  they do not hold: unheld adds are `FORBIDDEN`, mirroring the assign
  path (TASK-008 Cycle 1, SEC-M1).
- Bounty payout and cancel debit the government escrow first and pay
  only on success, so an empty escrow leaves the bounty `ACTIVE` instead
  of minting unbacked gold; failed pays roll back, and a failed rollback
  logs a `WARN` with bounty id and amount (TASK-008, SEC-M2/LOW-2).
- Console and command-block audit reads are permission-gated instead of
  implicitly omniscient (TASK-008 Cycle 1, SEC-L1).
- Removed dead trap stubs (`BountyService.activeFor`,
  `FineIssueParse.parseIssueResult`), hold scaffolding, and seven dead
  helpers; `clearSeason` routes through the store clear methods
  (TASK-007).
- Phase 9B hardening loop verdict Production Hardened: full security
  re-audit PASS (all Cycle-1 fixes hold, no new HIGH/MEDIUM) and
  reviewer APPROVE WITH NOTES; final headless regression 86/86 across
  12 suites plus `gradle clean build --offline` green (TASK-008).
