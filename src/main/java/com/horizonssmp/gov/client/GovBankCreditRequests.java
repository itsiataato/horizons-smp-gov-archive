package com.horizonssmp.gov.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;
import com.horizonssmp.gov.server.GovTargets;

import com.horizonssmp.gov.client.GovTreasuryTypes.RequestKind;

/**
 * The dashboard wire payload for the balances-tab bank credit.
 *
 * <p>The map built here is the client half of the contract whose server half lives in
 * {@code EconomyBankHandlers.bankCredit}: the target as a name under {@code target} (with
 * {@code account} accepted as an alias, so {@code me} or blank credits the actor), the amount
 * as text, and the reason as text. Every value crosses the wire verbatim — there is no
 * client-side validation — so {@code FORBIDDEN}, {@code BAD_TARGET}, {@code BAD_AMOUNT} and
 * the short-reason {@code BAD_PAYLOAD} refusals all surface from the server through the shared
 * status line. No nonce: the handler consumes none.
 */
final class GovBankCreditRequests {

    private GovBankCreditRequests() {
    }

    static Map<String, String> creditPayload(final GovBankCreditForm form) {
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(GovTargets.KEY_TARGET, form.targetBox.getValue());
        payload.put(EconomyKeys.KEY_AMOUNT, form.amountBox.getValue());
        payload.put(EconomyKeys.KEY_REASON, form.reasonBox.getValue());
        return payload;
    }

    static void submit(final GovTreasuryPanel p) {
        if (!p.canCredit || GovTreasurySupport.mutationPending(p)) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        p.pending.put(reqId, RequestKind.BANK_CREDIT);
        p.status = "";
        p.statusIsError = false;
        GovNetwork.sendRequest(OpCode.BANK_CREDIT, creditPayload(p.credit), reqId);
        GovTreasuryViews.rebuildViews(p);
    }
}
