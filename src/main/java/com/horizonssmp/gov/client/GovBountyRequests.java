package com.horizonssmp.gov.client;

import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.BountyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;

import static com.horizonssmp.gov.client.GovTreasuryTypes.*;
import com.horizonssmp.gov.client.GovTreasuryTypes.RequestKind;

/**
 * The dashboard wire payloads for the bounty actions.
 *
 * <p>The maps built here are the client half of the contract whose server half lives in
 * {@code GovBountyHandlers}: {@code BOUNTY_PLACE} sends the target name plus the amount as text
 * (the dispatcher resolves the person, exactly like {@code PAY_PLAYER} resolves its target, so a
 * missing person is a {@code BAD_TARGET} refusal), {@code BOUNTY_LIST} sends the optional person
 * filter with an empty value listing every active bounty, and {@code BOUNTY_CANCEL} sends only
 * the stable bounty id. Every value crosses the wire as text and every semantic (escrow, caps,
 * owner-only cancel) is enforced server-side in {@code BountyService}; the panel only caches the
 * last answer. Builders return {@code null} when the inputs cannot form a valid payload, so the
 * panel can fail with its own message before anything is sent.
 */
final class GovBountyRequests {

    private GovBountyRequests() {
    }

    static Map<String, String> placePayload(final String target, final long amount) {
        final String clean = target == null ? "" : target.strip();
        if (clean.isEmpty() || amount <= 0L) {
            return null;
        }
        return Map.of(BountyKeys.KEY_TARGET, clean,
                BountyKeys.KEY_AMOUNT, Long.toString(amount));
    }

    static Map<String, String> listPayload() {
        return Map.of(BountyKeys.KEY_TARGET, "");
    }

    static Map<String, String> cancelPayload(final String bountyId) {
        final String clean = bountyId == null ? "" : bountyId.strip();
        if (clean.isEmpty()) {
            return null;
        }
        return Map.of(BountyKeys.KEY_BOUNTY_ID, clean);
    }

    static void requestList(final GovTreasuryPanel p) {
        if (p.pending.containsValue(RequestKind.BOUNTY_LIST)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BOUNTY_LIST);
        p.latestBountyReqId = reqId;
        p.bountyState = LoadState.LOADING;
        p.bountyError = "";
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.BOUNTY_LIST, listPayload(), reqId);
        GovTreasuryViews.rebuildViews(p);
    }

    static void submitPlace(final GovTreasuryPanel p) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        if (p.bountyState != LoadState.READY) {
            return;
        }
        final String target = GovBountyForm.target(p);
        if (target.isEmpty()) {
            GovTreasurySupport.setStatus(p,
                    GovTreasuryViews.label(p, "gui.gov.treasury.no_target"), true);
            GovTreasuryViews.rebuildViews(p);
            return;
        }
        if (target.equalsIgnoreCase(GovTreasurySupport.currentPlayerName())) {
            GovTreasurySupport.setStatus(p,
                    GovTreasuryViews.label(p, "gui.gov.treasury.bounty_self"), true);
            GovTreasuryViews.rebuildViews(p);
            return;
        }
        final long parsed = GovBountyForm.parseAmount(GovBountyForm.amountText(p));
        if (parsed == GovBountyForm.AMOUNT_INVALID) {
            final boolean sane = p.bountyMin > 0L && p.bountyMax >= p.bountyMin;
            GovTreasurySupport.setStatus(p, sane
                    ? GovTreasuryViews.trArgs("gui.gov.treasury.bounty_amount_invalid",
                            p.bountyMin, p.bountyMax)
                    : GovTreasuryViews.label(p, "gui.gov.treasury.error"), true);
            GovTreasuryViews.rebuildViews(p);
            return;
        }
        final long amount = GovBountyForm.clampAmount(parsed, p.bountyMin, p.bountyMax);
        final Map<String, String> payload = placePayload(target, amount);
        if (payload == null) {
            GovTreasurySupport.setStatus(p,
                    GovTreasuryViews.label(p, "gui.gov.treasury.error"), true);
            GovTreasuryViews.rebuildViews(p);
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BOUNTY_PLACE);
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.BOUNTY_PLACE, payload, reqId);
        GovTreasuryViews.rebuildViews(p);
    }

    static void submitCancel(final GovTreasuryPanel p, final String bountyId) {
        if (GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final Map<String, String> payload = cancelPayload(bountyId);
        if (payload == null) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BOUNTY_CANCEL);
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.BOUNTY_CANCEL, payload, reqId);
        GovTreasuryViews.rebuildViews(p);
    }
}
