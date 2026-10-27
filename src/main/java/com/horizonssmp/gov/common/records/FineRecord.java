package com.horizonssmp.gov.common.records;

import java.util.UUID;

import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.FineStatus;

import net.minecraft.nbt.CompoundTag;

/**
 * One fine.
 *
 * <p>Payment is physical and happens at one place: the Government Building reception. The record
 * therefore names the reception reference in {@code paidAtReceptionRef} and there is deliberately
 * no account reference in this type, because a fine is never debited from an account. The name is
 * locked: an earlier draft called this field {@code paidByBankRef}, and it was renamed so the
 * field could not suggest that a fine is paid by bank transfer.
 *
 * <p>{@code restitution} is a second {@link GoldAmount} rather than a number, so the unit travels
 * with it and a fine cannot be paid in the wrong thing. It is enabled or disabled by {@code
 * gov.fine.allowRestitution}. {@code victim} names who the restitution is owed to: the payment
 * receipt must name the victim, and the distribution of the gold is staff roleplay, never a bank
 * transfer.
 *
 * <p>{@code exileDays} is the AGGRAVATED band's exile order, 7 to 30 in-game days under Article
 * 9.11. This record stores the order; enforcing it is M8 staff work, and this build does not
 * pretend otherwise.
 *
 * <p>All four fields added in M4 read tolerantly: a tag written by M3 has no exile, victim or
 * collector keys and loads with {@code 0}, empty and {@code false}. That is what keeps an existing
 * {@code gov.dat} loadable across the upgrade.
 *
 * @param id the stable fine id
 * @param targetUuid the person fined, never {@code null}
 * @param issuerUuid the official who issued it, or {@code null} if unknown
 * @param lawArticle the law article id the fine rests on, validated against the bundled law
 * @param band the severity band
 * @param amount the fine in gold ingots, {@code currency} always {@link GoldAmount#GOLD_INGOT}
 * @param restitution additional gold owed to a victim, zero when none
 * @param reason the stated reason, already sanitized by the service that issued it
 * @param createdAt epoch milliseconds the fine was issued
 * @param dueAt epoch milliseconds the fine falls due, {@code 0} for no deadline
 * @param status the payment state
 * @param paidAt epoch milliseconds the fine was paid, {@code 0} while unpaid
 * @param paidAtReceptionRef the reception payment reference, or empty while unpaid
 * @param waivedBy the official who waived it, or {@code null} if not waived
 * @param waivedReason the stated reason for a waiver, or empty
 * @param exileDays the exile order in in-game days, {@code 0} when none (only AGGRAVATED)
 * @param victim the restitution victim's name, or empty when no restitution is owed
 * @param collectedBy the official who accepted payment, or the payer's name for a self-payment
 * @param selfPaid true when the payer paid their own fine at the reception
 * @param victimUuid the restitution victim's server-confirmed UUID, or {@code null} for a fine
 *     written before compensation credited a real recipient and for the ordinary display-only path
 * @param compensationPaidAt epoch milliseconds the restitution was physically handed to the
 *     victim, {@code 0} while none was owed, none could be delivered, or the fine is unpaid
 */
public record FineRecord(
        String id,
        UUID targetUuid,
        UUID issuerUuid,
        String lawArticle,
        FineBand band,
        GoldAmount amount,
        GoldAmount restitution,
        String reason,
        long createdAt,
        long dueAt,
        FineStatus status,
        long paidAt,
        String paidAtReceptionRef,
        UUID waivedBy,
        String waivedReason,
        int exileDays,
        String victim,
        String collectedBy,
        boolean selfPaid,
        UUID victimUuid,
        long compensationPaidAt) {

    public FineRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("FineRecord requires an id");
        }
        lawArticle = lawArticle == null ? "" : lawArticle;
        band = band == null ? FineBand.PETTY : band;
        amount = amount == null ? GoldAmount.ZERO : amount;
        restitution = restitution == null ? GoldAmount.ZERO : restitution;
        reason = reason == null ? "" : reason;
        status = status == null ? FineStatus.UNPAID : status;
        paidAtReceptionRef = paidAtReceptionRef == null ? "" : paidAtReceptionRef;
        waivedReason = waivedReason == null ? "" : waivedReason;
        exileDays = Math.max(0, exileDays);
        victim = victim == null ? "" : victim;
        collectedBy = collectedBy == null ? "" : collectedBy;
        compensationPaidAt = Math.max(0L, compensationPaidAt);
    }

    /**
     * The pre-M12 constructor: the exact shape every older caller already uses.
     *
     * <p>It delegates with no UUID victim and no compensation stamp, which is the truthful state
     * of an old fine: restitution was named but never credited by this mod. Keeping this overload
     * means every existing caller compiles and behaves exactly as before; the two appended
     * components are additive and the schema version stays 1.
     */
    public FineRecord(
            final String id,
            final UUID targetUuid,
            final UUID issuerUuid,
            final String lawArticle,
            final FineBand band,
            final GoldAmount amount,
            final GoldAmount restitution,
            final String reason,
            final long createdAt,
            final long dueAt,
            final FineStatus status,
            final long paidAt,
            final String paidAtReceptionRef,
            final UUID waivedBy,
            final String waivedReason,
            final int exileDays,
            final String victim,
            final String collectedBy,
            final boolean selfPaid) {
        this(id, targetUuid, issuerUuid, lawArticle, band, amount, restitution, reason,
                createdAt, dueAt, status, paidAt, paidAtReceptionRef, waivedBy, waivedReason,
                exileDays, victim, collectedBy, selfPaid, null, 0L);
    }

    /** True while the target still owes gold on this fine. */
    public boolean outstanding() {
        return status == FineStatus.UNPAID;
    }

    /** True when the fine has a deadline that has already passed. */
    public boolean overdueAt(final long now) {
        return outstanding() && dueAt > 0L && now >= dueAt;
    }

    /** The full amount due at reception: the fine plus any restitution, in gold ingots. */
    public GoldAmount totalDue() {
        return GoldAmount.of(amount.count() + restitution.count());
    }

    /** True when this fine carries an exile order. Only an AGGRAVATED fine may. */
    public boolean exileOrdered() {
        return exileDays > 0;
    }

    /**
     * True when restitution is owed to a real recipient but has not been handed over yet.
     *
     * <p>Only a settled fine can have a pending compensation: while the fine is UNPAID the gold has
     * not been taken from the payer, so there is nothing to deliver. A fine written before M12 has
     * no {@code victimUuid} and is therefore never pending; its restitution remains the old
     * display-only record, exactly as it was.
     */
    public boolean compensationPending() {
        return status == FineStatus.PAID
                && restitution.count() > 0L
                && victimUuid != null
                && compensationPaidAt <= 0L;
    }

    /**
     * Returns a copy stamped with the time the restitution was physically handed to the victim.
     *
     * <p>A non-positive time is kept as zero, which means "still pending". The service calls this
     * only after the gateway has actually added the ingots; the stamp is the proof that the same
     * restitution will not be paid twice.
     */
    public FineRecord withCompensationPaidAt(final long at) {
        return new FineRecord(
                id, targetUuid, issuerUuid, lawArticle, band, amount, restitution, reason,
                createdAt, dueAt, status, paidAt, paidAtReceptionRef, waivedBy, waivedReason,
                exileDays, victim, collectedBy, selfPaid, victimUuid, Math.max(0L, at));
    }

    /**
     * This fine, settled by a physical payment at reception.
     *
     * <p>The status, the stamp, the receipt reference and the collector are written together so a
     * paid record can never be half-paid. The method does not check the state; the service that
     * calls it has already refused a fine that is not {@code UNPAID}.
     */
    public FineRecord paid(
            final long paidAt, final String receptionRef, final String collector, final boolean self) {
        return new FineRecord(
                id, targetUuid, issuerUuid, lawArticle, band, amount, restitution, reason,
                createdAt, dueAt, FineStatus.PAID, paidAt, receptionRef, waivedBy, waivedReason,
                exileDays, victim, collector, self, victimUuid, compensationPaidAt);
    }

    /**
     * This fine, cleared by an official with a recorded reason.
     *
     * <p>Waiving never deletes the record and never moves gold. The status, the official and the
     * reason are the whole change.
     */
    public FineRecord waived(final UUID waivedBy, final String waivedReason) {
        return new FineRecord(
                id, targetUuid, issuerUuid, lawArticle, band, amount, restitution, reason,
                createdAt, dueAt, FineStatus.WAIVED, paidAt, paidAtReceptionRef,
                waivedBy, waivedReason, exileDays, victim, collectedBy, selfPaid,
                victimUuid, compensationPaidAt);
    }

    /** Serializes this fine into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "targetUuid", targetUuid);
        NbtSupport.putUuid(tag, "issuerUuid", issuerUuid);
        tag.putString("lawArticle", lawArticle);
        tag.putString("band", band.name());
        tag.put("amount", amount.write());
        tag.put("restitution", restitution.write());
        tag.putString("reason", reason);
        tag.putLong("createdAt", createdAt);
        tag.putLong("dueAt", dueAt);
        tag.putString("status", status.name());
        tag.putLong("paidAt", paidAt);
        tag.putString("paidAtReceptionRef", paidAtReceptionRef);
        NbtSupport.putUuid(tag, "waivedBy", waivedBy);
        tag.putString("waivedReason", waivedReason);
        tag.putInt("exileDays", exileDays);
        tag.putString("victim", victim);
        tag.putString("collectedBy", collectedBy);
        tag.putBoolean("selfPaid", selfPaid);
        NbtSupport.putUuid(tag, "victimUuid", victimUuid);
        tag.putLong("compensationPaidAt", compensationPaidAt);
        return tag;
    }

    /** Reads a fine. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static FineRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("FineRecord tag has no id and no fallback");
        }
        return new FineRecord(
                id,
                NbtSupport.getUuid(tag, "targetUuid"),
                NbtSupport.getUuid(tag, "issuerUuid"),
                NbtSupport.getString(tag, "lawArticle", ""),
                FineBand.parse(NbtSupport.getString(tag, "band", "")),
                GoldAmount.read(NbtSupport.getCompoundOr(tag, "amount")),
                GoldAmount.read(NbtSupport.getCompoundOr(tag, "restitution")),
                NbtSupport.getString(tag, "reason", ""),
                NbtSupport.getLong(tag, "createdAt", 0L),
                NbtSupport.getLong(tag, "dueAt", 0L),
                FineStatus.parse(NbtSupport.getString(tag, "status", "")),
                NbtSupport.getLong(tag, "paidAt", 0L),
                NbtSupport.getString(tag, "paidAtReceptionRef", ""),
                NbtSupport.getUuid(tag, "waivedBy"),
                NbtSupport.getString(tag, "waivedReason", ""),
                // The M4 fields are absent from a tag written before this build. Their defaults are
                // the truthful values for a fine that never carried them: no exile, no victim, no
                // collector and no self-payment record.
                NbtSupport.getInt(tag, "exileDays", 0),
                NbtSupport.getString(tag, "victim", ""),
                NbtSupport.getString(tag, "collectedBy", ""),
                NbtSupport.getBoolean(tag, "selfPaid", false),
                // A pre-M12 fine has no recipient UUID and no compensation stamp. It keeps the
                // old display-only behaviour instead of inventing a payee from the display name.
                NbtSupport.getUuid(tag, "victimUuid"),
                NbtSupport.getLong(tag, "compensationPaidAt", 0L));
    }
}
