# Gov — Horizons SMP government mod (ARCHIVED, v1.0.1)

> **This repository is archived and read-only.** There is no active
> maintenance. Forks are welcome — if you pick this up, you own it.

The Horizons SMP government mod for NeoForge 1.21.1: custodial bank
accounts, a government store, payroll from account ledgers, building
contracts, bounties, fines, vehicles, weapons registry, businesses, jail
with detention records, a 13-tab dashboard, and an append-only audit log
with exports and two-step season reset. Full feature documentation lives
in the part-files below.

- [README.part1.md](README.part1.md) — overview, requirements, install
- [README.part2.md](README.part2.md)
- [README.part3.md](README.part3.md) — rebuilding, law regen
- [README.part4.md](README.part4.md) — known limitations (tail)
- [CHANGELOG.md](CHANGELOG.md)

## Archived state (honest)

- Tag `v1.0.1` is the final state. Verified before archiving: 3042-harness
  assertions 38/38 PASS, headless suites 148/148 PASS, dashboard
  button-matrix 250 passed / 0 failed / 9 expected-blocked.
- **Known issue:** the Businesses → Members → Add Member dialog crashed
  (`NoClassDefFoundError`) on one client build. A rebuilt jar showed zero
  dangling references, but the in-game retest was never completed before
  archiving. Treat member-add as unverified.
- **Known issue:** deleting the `builder` job resurrects it on reboot
  (seed runs unconditionally). No data loss; needs a product decision.
- **Known issue:** console jail cell create/delete writes no audit entry
  (forensics gap only, needs stolen console — not privilege escalation).
- **What this archive cannot rebuild:** the client distribution jar.
  It required commercial tooling that is intentionally not distributed
  here. `gradle build` (system Gradle 9.x + Java 21) produces the plain
  server jar; the first build needs network for NeoForge/Minecraft
  dependencies.
- The acceptance harness default RCON password was scrubbed to a
  `changeme` placeholder — set `GOV_ACCEPTANCE_RCON_PASSWORD` to run it.
  Internal dev notes, agent bookkeeping, and run/build evidence were
  deliberately left out of this public archive.

## License

MIT License — copyright (c) 2026
[itsiataato](https://github.com/itsiataato). Forks are welcome, but every
fork must keep the copyright + license notice (see [LICENSE](LICENSE)).
