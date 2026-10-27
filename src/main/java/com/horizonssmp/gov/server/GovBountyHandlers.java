package com.horizonssmp.gov.server;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.BountyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.config.GovConfig;

/**
 * The bounty actions: {@code BOUNTY_PLACE}, {@code BOUNTY_LIST} and {@code BOUNTY_CANCEL}.
 *
 * <p>Split from {@link GovHandlers} so that file stays under the 300-line rule. The logic mirrors
 * the {@code /gov bounty place|list|cancel} command paths in {@code GovBountyCommandHandlers}:
 * placing escrows the poster's physical gold into the government account under the configured
 * amount bounds and per-target/per-poster caps, listing shows the active bounties with an
 * optional person filter, and cancelling refunds the escrow to the poster's account. Every
 * semantic lives in {@link BountyService}; the handlers only classify its answer into an
 * {@link ActionResult} and encode the list block.
 *
 * <p>Authorization is not decided here. The dispatcher has already checked the one capability
 * {@code GovCapabilities.requiredFor} names ({@code player.lookup.self} for all three, because
 * placing spends the poster's own gold and cancelling is owner-only by the service), exactly as
 * for {@code FINE_PAY} and {@code PAY_PLAYER}. Every method returns an {@link ActionResult} and
 * never throws: a service refusal is a refusal, never an exception.
 */
public final class GovBountyHandlers {

    private GovBountyHandlers() {
    }

    /**
     * {@code BOUNTY_PLACE}: places a bounty on the resolved person, escrowing the gold.
     *
     * <p>The dispatcher resolved the person target from the payload, so a missing target is a
     * {@code BAD_TARGET} refusal. An unreadable amount parses to {@code -1}, which the service
     * refuses with its range message, so a hand-made payload gets {@code BAD_PAYLOAD} rather
     * than a silent default.
     */
    static ActionResult bountyPlace(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final long amount = GovFields.parseLong(context.payload(BountyKeys.KEY_AMOUNT), -1L);
        final String result = BountyService.place(
                context.actor(),
                target.online() ? target.player() : null,
                target.uuid(),
                target.name(),
                amount);
        if (result != null && result.startsWith("Bounty of ")) {
            return ActionResult.success(context.request().reqId(), result);
        }
        if (result == null || result.isBlank()) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.INVALID_STATE);
        }
        return ActionResult.failure(context.request().reqId(), placeCode(result), result);
    }

    /** Classifies a {@link BountyService#place} refusal into the client-branchable code. */
    private static ErrorCode placeCode(final String result) {
        if (result.startsWith("Bounty amount must be between ")) {
            return ErrorCode.BAD_PAYLOAD;
        }
        if (result.startsWith("That target already has the maximum ")
                || result.startsWith("You already have the maximum ")) {
            return ErrorCode.LIMIT_EXCEEDED;
        }
        if (result.startsWith("You do not have enough gold ingots.")
                || result.startsWith("Could not remove gold from your inventory.")) {
            return ErrorCode.INSUFFICIENT_ITEMS;
        }
        return ErrorCode.INVALID_STATE;
    }

    /**
     * {@code BOUNTY_LIST}: the active bounties, newest first, with an optional person filter.
     *
     * <p>A named filter that resolves to nobody is a {@code BAD_TARGET} refusal, exactly as the
     * command path refuses an unknown name. The answer carries the true total in
     * {@code bounties} and at most {@link BountyKeys#MAX_LISTED_BOUNTIES} dense rows, plus the
     * place-form bounds and the online picker names for the place form.
     */
    static ActionResult bountyList(final GovActionDispatcher.ActionContext context) {
        UUID filter = null;
        final String raw = context.payload(BountyKeys.KEY_TARGET).strip();
        if (!raw.isEmpty()) {
            final GovTargets.Resolved resolved =
                    GovTargets.resolve(context.actor().getServer(), context.data(), raw);
            if (resolved == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            filter = resolved.uuid();
        }
        final List<BountyRecord> matching = BountyService.listActive(context.data(), filter);
        matching.sort(Comparator.comparingLong(BountyRecord::createdAt).reversed()
                .thenComparing(BountyRecord::id));

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BountyKeys.TOTAL, Integer.toString(matching.size()));
        fields.put(BountyKeys.MIN_AMOUNT, Integer.toString(GovConfig.BOUNTY_MIN.get()));
        fields.put(BountyKeys.MAX_AMOUNT, Integer.toString(GovConfig.BOUNTY_MAX.get()));
        fields.put(BountyKeys.PER_TARGET_CAP,
                Integer.toString(GovConfig.BOUNTY_PER_TARGET_CAP.get()));
        fields.put(BountyKeys.PER_POSTER_CAP,
                Integer.toString(GovConfig.BOUNTY_PER_POSTER_CAP.get()));
        fields.put(BountyKeys.VIEWER_UUID, context.actor().getUUID().toString());

        final int rows = Math.min(matching.size(), BountyKeys.MAX_LISTED_BOUNTIES);
        for (int i = 0; i < rows; i++) {
            writeRow(fields, i, matching.get(i), context.actor().getUUID());
        }

        final List<String> names =
                GovHandlerSupport.onlineNames(context.actor().getServer());
        fields.put(BountyKeys.ONLINE_COUNT, Integer.toString(names.size()));
        for (int i = 0; i < names.size() && i < BountyKeys.MAX_LISTED_ONLINE; i++) {
            fields.put(BountyKeys.ONLINE_PREFIX + i, Sanitizer.name(names.get(i)));
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** Writes one dense bounty row: {@code bounty.<n>.<field>}. */
    private static void writeRow(
            final Map<String, String> fields, final int index,
            final BountyRecord bounty, final UUID viewerUuid) {
        final String prefix = BountyKeys.ROW_PREFIX + index + ".";
        fields.put(prefix + BountyKeys.ROW_ID, bounty.id());
        fields.put(prefix + BountyKeys.ROW_TARGET, bounty.targetName());
        fields.put(prefix + BountyKeys.ROW_TARGET_UUID,
                bounty.targetUuid() == null ? "" : bounty.targetUuid().toString());
        fields.put(prefix + BountyKeys.ROW_POSTER, bounty.posterName());
        fields.put(prefix + BountyKeys.ROW_POSTER_UUID,
                bounty.posterUuid() == null ? "" : bounty.posterUuid().toString());
        fields.put(prefix + BountyKeys.ROW_AMOUNT, Long.toString(bounty.amount()));
        fields.put(prefix + BountyKeys.ROW_CREATED, Long.toString(bounty.createdAt()));
        fields.put(prefix + BountyKeys.ROW_EXPIRES, Long.toString(bounty.expiresAt()));
        fields.put(prefix + BountyKeys.ROW_MINE,
                Boolean.toString(viewerUuid != null && viewerUuid.equals(bounty.posterUuid())));
    }

    /**
     * {@code BOUNTY_CANCEL}: cancels one of the actor's own bounties and refunds the escrow.
     *
     * <p>A bounty that is not active is an {@code INVALID_STATE} refusal; one posted by someone
     * else is {@code FORBIDDEN}. The owner-only rule lives in {@link BountyService}, exactly as
     * the command path enforces it.
     */
    static ActionResult bountyCancel(final GovActionDispatcher.ActionContext context) {
        final String bountyId = context.payload(BountyKeys.KEY_BOUNTY_ID).strip();
        final String result = BountyService.cancel(context.actor(), bountyId);
        if ("Bounty cancelled and refunded.".equals(result)) {
            return ActionResult.success(context.request().reqId(), result);
        }
        if (result == null || result.isBlank()) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.INVALID_STATE);
        }
        if ("You can only cancel your own bounties.".equals(result)) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.FORBIDDEN, result);
        }
        return ActionResult.failure(context.request().reqId(), ErrorCode.INVALID_STATE, result);
    }
}
