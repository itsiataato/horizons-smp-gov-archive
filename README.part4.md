- No `fine.collect` capability id exists in this build. Collection is gated by
  the reception-desk `fine.waive` authority; collecting is the weaker act
  because it never erases the record and the gold must physically change hands.
- The rest of the police tools are not implemented in this build; `/gov police`
  is not registered. The Audit tab, exports and season reset are implemented as
  M9.
- An audit page is capped at eight compact rows and the detail pane carries the
  payload and reason; the log retains at most 50,000 entries and drops the
  oldest with a warning. An export is a point-in-time copy, not a live tail.
- A season reset clears in one in-memory pass after the backup verifies. The
  actual `gov.dat` rewrite is the next vanilla SavedData save, which writes a
  temporary file and moves it into place; a crash before that save leaves the
  previous file intact. The backup is never deleted by the mod, but an operator
  who wants off-site copies must copy `run/gov-backups/` themselves. A root
  section this build does not recognise is preserved verbatim on load and save,
  so a short downgrade does not erase a newer build's section; unknown fields
  inside a known record are still dropped.
- A reset token is in memory and tied to the loaded data set. A server restart
  invalidates every pending reset; begin again after a restart. A console/RCON
  actor can begin and commit, because the console is the server operator and
  already has every command.
