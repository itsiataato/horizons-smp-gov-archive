package com.horizonssmp.gov.common.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * The kinds of event a player's recent-history strip can show.
 *
 * <p>History is a <b>display and accountability</b> aid, not a second audit log. The audit log is
 * append-only and unbounded within its own ceiling; this list is a bounded per-player window of the
 * ten newest events across every registry, so a player can see what happened to them without an
 * official opening the audit tab. The audit log remains the authoritative record and is never
 * derived from history.
 *
 * <p>The ids are persisted verbatim in {@code gov.dat}. Do not rename one without a schema
 * migration. Unknown ids are dropped on read with one log line, which is the deliberate exception
 * to "preserve unknown values": a single unknown history kind has no authority and no record
 * semantics, so keeping it would only produce a row the client cannot render. Every other record
 * (a contract, a position, a complaint) is kept because losing it would change a real relationship.
 */
public enum HistoryKind {

    BUSINESS_FOUNDED("business.founded"),
    BUSINESS_RENAMED("business.renamed"),
    BUSINESS_TRANSFERRED("business.transferred"),
    BUSINESS_CLOSED("business.closed"),
    BUSINESS_CLOSE_REQUESTED("business.close_requested"),
    BUSINESS_CLOSE_REJECTED("business.close_rejected"),
    POSITION_CREATED("position.created"),
    POSITION_EDITED("position.edited"),
    POSITION_MOVED("position.moved"),
    POSITION_DELETED("position.deleted"),
    CONTRACT_OFFERED("contract.offered"),
    CONTRACT_ACCEPTED("contract.accepted"),
    CONTRACT_DECLINED("contract.declined"),
    CONTRACT_FIRED("contract.fired"),
    CONTRACT_RESIGNED("contract.resigned"),
    CONTRACT_COMPLETED("contract.completed"),
    CONTRACT_PROMOTED("contract.promoted"),
    CONTRACT_TRANSFERRED("contract.transferred"),
    COMPLAINT_FILED("complaint.filed"),
    COMPLAINT_UPHELD("complaint.upheld"),
    PAYROLL_RECORDED("payroll.recorded"),
    GOV_JOB_ASSIGNED("gov_job.assigned"),
    GOV_JOB_REMOVED("gov_job.removed"),
    FINE_ISSUED("fine.issued"),
    FINE_PAID("fine.paid"),
    COMPENSATION_PAID("compensation.paid"),
    VEHICLE_REGISTERED("vehicle.registered"),
    WEAPON_PERMIT_ISSUED("weapon_permit.issued"),
    DETENTION_STARTED("detention.started"),
    DETENTION_RELEASED("detention.released");

    private final String id;

    HistoryKind(final String id) {
        this.id = id;
    }

    /** The stable lowercase id, as stored and as sent on the wire. */
    public String id() {
        return id;
    }

    /**
     * Parses a kind by its stable id, or by its enum name, case-insensitively.
     *
     * <p>Unknown values return {@link Optional#empty()}; the caller drops the row and logs, as the
     * class documentation explains. Accepting the enum name as well keeps a hand-written or
     * pre-migration tag readable without inventing a second id space.
     */
    public static Optional<HistoryKind> byId(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (final HistoryKind value : values()) {
            if (value.id.equals(needle) || value.name().equalsIgnoreCase(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** A comma-and-space list of the ids, for help text. */
    public static String ids() {
        final StringBuilder out = new StringBuilder();
        for (final HistoryKind value : values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value.id);
        }
        return out.toString();
    }
}
