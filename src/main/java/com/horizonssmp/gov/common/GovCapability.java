package com.horizonssmp.gov.common;

import java.util.Locale;
import java.util.Optional;

/**
 * The capability ids of the government system, verbatim.
 *
 * <p>Capabilities are the only authorization currency in the mod. Nothing checks a job name, a
 * rank, an operator status or a UUID allow-list: a handler asks whether the actor's effective
 * capability set contains one of these constants. That is what makes a custom job created at
 * runtime exactly as powerful as a built-in one, and what lets the matrix change without touching
 * a single handler.
 *
 * <p>The string ids are persisted in {@code gov.dat} (job definitions, personal grants) and in the
 * audit log, so they are part of the file format. Do not change an id without a schema migration.
 */
public enum GovCapability {

    PLAYER_LOOKUP("player.lookup"),
    PLAYER_LOOKUP_SELF("player.lookup.self"),
    NOTE_WRITE("note.write"),
    NOTE_VIEW_ALL("note.view.all"),
    FINE_ISSUE("fine.issue"),
    FINE_VIEW_ALL("fine.view.all"),
    FINE_WAIVE("fine.waive"),
    SUSPECT_SET("suspect.set"),
    WARRANT_ISSUE("warrant.issue"),
    JOB_ASSIGN("job.assign"),
    JOB_MANAGE("job.manage"),
    JOB_VIEW("job.view"),
    BUSINESS_REGISTER("business.register"),
    BUSINESS_MANAGE("business.manage"),
    /**
     * The government's decision on a business's requested soft close.
     *
     * <p>Deliberately separate from {@code business.manage}: registering, renaming and staff
     * management are registry work, while deciding whether a business may stop trading is the
     * executive's hand on the registry. The capability is granted only to the built-in executive
     * job. A world seeded before this build has no job carrying it, so
     * {@code GovSavedData.load} adds it to that system job once, following the M6
     * {@code military.authority} migration.
     */
    BUSINESS_CLOSE_APPROVE("business.close.approve"),
    VEHICLE_REGISTER("vehicle.register"),
    VEHICLE_IMPOUND("vehicle.impound"),
    VEHICLE_RELEASE("vehicle.release"),
    PLATE_SCAN("plate.scan"),
    WEAPON_REGISTER("weapon.register"),
    WEAPON_VIEW("weapon.view"),
    /**
     * The Class G military-duty authority of Articles 5.07, 5.08 and 5.13.
     *
     * <p>This is a capability, never a job name: a custom job granted it can issue Class G permits
     * and use military-grade ordnance on duty, and a job called "military" without it cannot. It is
     * seeded into the executive job only, which is why an older world is migrated by adding it to
     * that system job on load (see {@code GovSavedData.migrateOnLoad}).
     */
    MILITARY_AUTHORITY("military.authority"),
    JAIL_COMMIT("jail.commit"),
    JAIL_RELEASE("jail.release"),
    DUTY_TOGGLE("duty.toggle"),
    AUDIT_VIEW("audit.view"),
    /**
     * The strongest executive authority: begin or commit a season reset, and export a full
     * registry section.
     *
     * <p>Added in M9 as a capability id rather than a hard-coded job-name check, following the M6
     * migration pattern. A world seeded before this build has no job carrying it, so
     * {@code GovSavedData.migrateOnLoad} adds it to the built-in executive job; a custom job may
     * be granted it explicitly. Reads of the season status use the same id.
     */
    SEASON_ADMIN("season.admin"),
    LAW_VIEW("law.view"),
    PAYROLL_RUN("payroll.run"),
    /**
     * The bank's hand on other people's gold: a teller withdrawal, a transfer between accounts, and
     * every government building-contract act except accepting or submitting as the builder.
     *
     * <p>Value and source: the economy specification's authorization matrix (section 6.2/10), which
     * names {@code bank.manage} for a teller withdrawal, a transfer, a contract post, approve and
     * void. A deposit, a {@code /pay}, a store purchase and a payroll run stay baseline at the
     * dispatcher because the service scopes them to the actor's own account, to a business position
     * or to a named payee.
     *
     * <p>Failure both ways: granting it to a reception desk lets that desk move another account's
     * gold; withholding it from the treasury leaves the government unable to fund a builder.
     *
     * <p>Tolerance: this is a capability, never a job name, so a custom treasury job may hold it.
     * A world seeded before E0a gains it through the {@code capabilities.economy.v1} migration, and
     * a capability an operator removes afterwards is not re-added.
     */
    BANK_MANAGE("bank.manage"),
    /**
     * The authority to run the government payroll scope.
     *
     * <p>Value and source: economy specification section 6.2, which requires {@code gov.payroll}
     * for a run against the {@code government} account. It grants no transfer, no withdrawal and no
     * police payroll: no capability implies another (section 10.2).
     *
     * <p>Failure both ways: without it the treasury cannot pay public servants; granting it to a
     * job that also holds {@code bank.manage} is exactly the executive bundle the matrix names, so
     * the migration seeds the two together only in the jobs the matrix lists.
     */
    GOV_PAYROLL("gov.payroll"),
    /**
     * The authority to run the police payroll scope.
     *
     * <p>Value and source: economy specification section 6.2, which requires
     * {@code police.payroll} for a run against the {@code police} account. Seeded into the built-in
     * police and executive jobs; a custom job may be granted it explicitly.
     *
     * <p>Failure both ways: withholding it freezes police pay; granting it to the treasury alone
     * would keep the force dependent on a monthly order rather than its own authority. This is the
     * open risk the specification records: the flat job model has no police chief rank.
     *
     * <p>Tolerance: a world seeded before E0a gains it on the police and executive jobs through the
     * {@code capabilities.economy.v1} migration; an operator's later removal is respected.
     */
    POLICE_PAYROLL("police.payroll");

    private final String id;

    GovCapability(final String id) {
        this.id = id;
    }

    /** The stable string id, as it appears in the config, the data file and the audit log. */
    public String id() {
        return id;
    }

    /**
     * Looks up a capability by its stored id.
     *
     * <p>Unknown ids return {@link Optional#empty()} rather than throwing, so a data file written
     * by a newer build (or edited by hand) still loads: the unknown capability is dropped, not
     * fatal. This is the tolerant-read half of the forward-compatibility rule.
     */
    public static Optional<GovCapability> byId(final String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        final String needle = id.trim().toLowerCase(Locale.ROOT);
        for (final GovCapability capability : values()) {
            if (capability.id.equals(needle)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }
}
