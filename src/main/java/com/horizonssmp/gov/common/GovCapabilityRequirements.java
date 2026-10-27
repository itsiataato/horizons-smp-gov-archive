package com.horizonssmp.gov.common;

/**
 * Helper: maps each {@link OpCode} to the single capability it requires.
 */
final class GovCapabilityRequirements {

    private GovCapabilityRequirements() {
    }

    /**
     * The one capability an action requires.
     *
     * @param op the action
     * @return the required capability, never {@code null}
     */
    public static GovCapability requiredFor(final OpCode op) {
        return switch (op) {
            case OPEN_TAB -> GovCapability.PLAYER_LOOKUP_SELF;
            case LOOKUP_PERSON, PERSON_SEARCH -> GovCapability.PLAYER_LOOKUP;
            case NOTE_ADD, NOTE_EDIT, NOTE_DELETE -> GovCapability.NOTE_WRITE;
            case NOTE_LIST -> GovCapability.NOTE_VIEW_ALL;
            // M11 scoped notes. Every player holds the baseline, and the scoped-note service does
            // the real work: job membership, job.manage, note.write, business ownership, staff
            // roles and status. Gating these on note.view.all would lock out the business staff
            // whose own board this feature exists for; gating them on note.write would lock out a
            // member reading their job's notes without a write grant.
            case NOTE_SCOPES, NOTE_SCOPED_LIST, NOTE_SCOPED_ADD, NOTE_SCOPED_EDIT,
                    NOTE_SCOPED_DELETE -> GovCapability.PLAYER_LOOKUP_SELF;
            case FINE_ISSUE -> GovCapability.FINE_ISSUE;
            // The fine reads and a self-payment are reachable by every player: the service scopes
            // them to the actor's own record unless the actor holds fine.view.all. A player paying
            // their own fine is exercising a duty, not an authority.
            case FINE_LIST, FINE_INFO, FINE_PAY -> GovCapability.PLAYER_LOOKUP_SELF;
            // No fine.collect id exists in this build. The reception-desk authority that may clear
            // a fine (fine.waive, held by govworker and gated by gov.govworker.canWaiveFines) is
            // also the authority that may accept gold for it. Collecting is the weaker act: it
            // never erases a record, because the gold must physically change hands.
            case FINE_COLLECT, FINE_WAIVE -> GovCapability.FINE_WAIVE;
            case SUSPECT_SET, SUSPECT_CLEAR -> GovCapability.SUSPECT_SET;
            case WARRANT_ISSUE -> GovCapability.WARRANT_ISSUE;
            case JOB_CREATE, JOB_EDIT, JOB_DELETE -> GovCapability.JOB_MANAGE;
            case JOB_ASSIGN -> GovCapability.JOB_ASSIGN;
            case JOB_LIST, JOB_INFO -> GovCapability.JOB_VIEW;
            case BIZ_REGISTER -> GovCapability.BUSINESS_REGISTER;
            // The registrar act: transfer, suspend and close are the government's hand on a
            // registration, exactly as a vehicle transfer is, so they are business.manage.
            // Recording a physical payment is also a registrar act: the spec says the stamp is set
            // by a business.manage holder writing down gold that changed hands in roleplay.
            case BIZ_TRANSFER, BIZ_SUSPEND, BIZ_REVOKE, BIZ_DELETE, BIZ_PAYROLL_RECORD ->
                    GovCapability.BUSINESS_MANAGE;
            // These four are reachable by the business owner as well as by business.manage, so the
            // dispatcher gates them at the baseline and the service decides which of the two the
            // actor is. The pattern is FINE_PAY's: a baseline capability opens the door, the
            // service refuses anyone it does not recognize. No handler ever looks at a job name.
            case BIZ_EMPLOYEE_ADD, BIZ_EMPLOYEE_REMOVE, BIZ_LINK, BIZ_PAYROLL_SET ->
                    GovCapability.PLAYER_LOOKUP_SELF;
            // The registry reads are scoped by the service: without a business or payroll
            // authority a viewer sees only their own businesses and their workplace.
            case BIZ_LIST, BIZ_INFO -> GovCapability.PLAYER_LOOKUP_SELF;
            // The employment self-service group. Every player holds the baseline, and the service
            // decides whether this actor is the owner, the holder of the relevant position, the
            // employee answering an offer, or the CEO filing a complaint. Gating these on
            // business.manage would lock out every owner who does not hold a government office,
            // which is the whole point of a player-run business. The two close decisions are the
            // exception: they are a government act and use the dedicated executive capability.
            case BIZ_RENAME, BIZ_POSITION_LIST, BIZ_POSITION_CREATE, BIZ_POSITION_EDIT,
                    BIZ_POSITION_DELETE, BIZ_POSITION_MOVE, BIZ_OFFER_LIST, BIZ_OFFER_SEND,
                    BIZ_OFFER_RESPOND, BIZ_CONTRACT_LIST, BIZ_CONTRACT_END,
                    BIZ_CONTRACT_PROMOTE, BIZ_PAYROLL_RUN, BIZ_COMPLAINT_LIST,
                    BIZ_COMPLAINT_FILE, BIZ_CLOSE_REQUEST, HISTORY_LIST ->
                    GovCapability.PLAYER_LOOKUP_SELF;
            case BIZ_CLOSE_APPROVE, BIZ_CLOSE_REJECT -> GovCapability.BUSINESS_CLOSE_APPROVE;
            case VEH_REGISTER, VEH_EDIT -> GovCapability.VEHICLE_REGISTER;
            case VEH_IMPOUND -> GovCapability.VEHICLE_IMPOUND;
            case VEH_RELEASE -> GovCapability.VEHICLE_RELEASE;
            case PLATE_SCAN -> GovCapability.PLATE_SCAN;
            // Article 8.12 makes the registrar the office that keeps the licence registry, and the
            // same office registers plates. No separate licence capability id exists: adding one
            // would not be present in job sets seeded by an earlier build (write-once seeding), so
            // the registrar act reuses vehicle.register.
            case LICENSE_ISSUE, LICENSE_REVOKE, LICENSE_POINTS -> GovCapability.VEHICLE_REGISTER;
            // Registry reads are scoped by the service: without a registry capability a viewer sees
            // only their own vehicles, exactly as the Fines tab scopes its reads. A baseline read
            // capability is enough to ask the question; the answer decides the scope.
            case VEH_LIST, VEH_INFO -> GovCapability.PLAYER_LOOKUP_SELF;
            case WPN_REGISTER, WPN_REVOKE, WPN_REPORT_LOSS -> GovCapability.WEAPON_REGISTER;
            // The Article 8.12 registrar keeps the weapon, permit and explosion-area registries, so
            // the same registry authority covers the new mutations. Reads stay baseline and are
            // scoped by the service: a citizen sees only their own registration, a weapon-viewer
            // sees the whole public registry.
            case WPN_TRANSFER, WPN_PERMIT_ISSUE, WPN_PERMIT_REVOKE, WPN_PERMIT_SUSPEND,
                    AREA_PERMIT_CREATE, AREA_PERMIT_REVOKE -> GovCapability.WEAPON_REGISTER;
            case WPN_LIST, WPN_INFO, WPN_CLASSIFY, WPN_PERMIT_LIST, AREA_PERMIT_LIST ->
                    GovCapability.PLAYER_LOOKUP_SELF;
            case JAIL_COMMIT -> GovCapability.JAIL_COMMIT;
            case JAIL_RELEASE -> GovCapability.JAIL_RELEASE;
            // The jail reads are baseline and scoped by the service: a citizen may see their own
            // detention record, a holder of jail.commit, jail.release or audit.view sees all.
            case JAIL_LIST, JAIL_INFO -> GovCapability.PLAYER_LOOKUP_SELF;
            // Extending a warrant-backed term is a detention act. Cuffing is force under the same
            // authority; removing cuffs is the release-side act, so an officer who may let someone
            // out may also take the cuffs off.
            case JAIL_EXTEND, CUFF, JAIL_CELL, JAIL_CELL_CREATE, JAIL_CELL_DELETE ->
                    GovCapability.JAIL_COMMIT;
            case UNCUFF -> GovCapability.JAIL_RELEASE;
            case DUTY_TOGGLE -> GovCapability.DUTY_TOGGLE;
            case PAYROLL_INFO -> GovCapability.PAYROLL_RUN;
            case AUDIT_PAGE, AUDIT_INFO, AUDIT_EXPORT -> GovCapability.AUDIT_VIEW;
            // The season reset and a full-section export are the executive's hand on the whole
            // registry, so they use the dedicated season.admin id seeded into the executive job.
            // It is not audit.view: a judge may read the log but must not erase a season.
            case ADMIN_EXPORT, SEASON_STATUS, SEASON_BEGIN, SEASON_COMMIT ->
                    GovCapability.SEASON_ADMIN;
            case LAW_ARTICLE -> GovCapability.LAW_VIEW;
            // The economy group. A deposit, a /pay, a payroll run, a store buy and the two builder
            // acts are reachable by an ordinary player, so the dispatcher gates them at the
            // baseline and the service decides: the actor's own account, the named payee, the
            // payroll scope, the catalogue, or the contract's named builder holding the builder
            // job (ContractSupport.builderEligible is the single choke point). Gating them on
            // bank.manage would force every player to hold a government office to deposit gold.
            case BANK_BALANCE, BANK_HISTORY, BANK_DEPOSIT, PAY_PLAYER, PAYROLL_RUN, STORE_LIST,
                    STORE_BUY, CONTRACT_LIST, CONTRACT_ACCEPT, CONTRACT_SUBMIT ->
                    GovCapability.PLAYER_LOOKUP_SELF;
            // A teller withdrawal, a transfer and the government's contract acts move or commit
            // another account's gold, so they are the treasury authority. CONTRACT_POST names the
            // builder but debits the Government account; approve and void release or redirect the
            // escrow.
            case BANK_WITHDRAW, BANK_TRANSFER, CONTRACT_POST, CONTRACT_APPROVE, CONTRACT_VOID ->
                    GovCapability.BANK_MANAGE;
            // BANK_CREDIT and CONTRACT_CREDIT are the two dashboard parity paths for
            // `/gov bank credit`, the audited crash remedy. The command gates on vanilla
            // permission level 4, the game's strongest gate: only an in-game operator may
            // mint gold without moving an item. No treasury capability is that narrow —
            // bank.manage is held by every teller — so both ops map to season.admin, the
            // strongest named authority in this vocabulary, held only by the built-in
            // executive job. The dispatcher gate is therefore at least as restrictive as
            // the command's, and the handler additionally re-checks both season.admin and
            // live permission level 4. TASK-032: CONTRACT_CREDIT used to sit in the
            // bank.manage group under a "console-only, never dispatched" note, but it IS
            // dispatched (EconomyHandlers registers it and the Treasury Contracts tab
            // sends it), so any op'd teller could mint. It belongs here, not there.
            case CONTRACT_CREDIT, BANK_CREDIT -> GovCapability.SEASON_ADMIN;
            // The bounty group. Placing spends the poster's own physical gold, cancelling is
            // owner-only by the service, and listing is a read, so the dispatcher gates all
            // three at the baseline and the service refuses anyone it does not recognize. The
            // pattern is FINE_PAY's and PAY_PLAYER's: a baseline capability opens the door, the
            // service decides. Gating place on bank.manage would force every poster to hold a
            // government office to spend their own ingots.
            case BOUNTY_PLACE, BOUNTY_LIST, BOUNTY_CANCEL -> GovCapability.PLAYER_LOOKUP_SELF;
        };
    }
}
