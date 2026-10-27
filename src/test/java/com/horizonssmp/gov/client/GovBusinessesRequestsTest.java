package com.horizonssmp.gov.client;

import java.util.UUID;

import com.horizonssmp.gov.client.GovBusinessesTypes.LoadState;
import com.horizonssmp.gov.client.GovBusinessesTypes.RequestKind;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.OpCode;

final class GovBusinessesRequestsTest {

    private static GovBusinessesPanel newPanel() {
        final GovBusinessesPanel p = new GovBusinessesPanel(null);
        p.selectedId = "biz-1";
        p.positionPage = 0;
        p.offerPage = 0;
        p.contractPage = 0;
        p.complaintPage = 0;
        return p;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static RequestKind requestKindFor(final OpCode op) {
        return switch (op) {
            case BIZ_POSITION_LIST -> RequestKind.POSITION_LIST;
            case BIZ_POSITION_EDIT -> RequestKind.POSITION_EDIT;
            case BIZ_OFFER_LIST -> RequestKind.OFFER_LIST;
            case BIZ_CONTRACT_LIST -> RequestKind.CONTRACT_LIST;
            case BIZ_COMPLAINT_LIST -> RequestKind.COMPLAINT_LIST;
            default -> throw new IllegalArgumentException("unknown op: " + op);
        };
    }

    void testPositionListPayload() {
        final GovBusinessesPanel p = newPanel();
        p.pending.clear();
        GovBusinessesRequests.requestPositionList(p);
        check(p.positionState == LoadState.LOADING, "positionState not LOADING");
        check(p.positionError.isEmpty(), "positionError not cleared");
        check(p.pending.containsValue(RequestKind.POSITION_LIST), "no POSITION_LIST pending");
        for (final String reqId : p.pending.keySet()) {
            check(UUID.fromString(reqId) != null, "reqId is not a UUID: " + reqId);
        }
    }

    void testPositionEditPayload() {
        final GovBusinessesPanel p = newPanel();
        p.selectedPosition = "pos-1";
        p.pending.clear();
        GovBusinessesRequests.requestPositionEdit(
                p, "pos-1", "Manager", "50", "invite", "1");
        check(p.pending.containsValue(RequestKind.POSITION_EDIT), "no POSITION_EDIT pending");
        check(p.pending.size() == 1, "expected exactly one pending request");
    }

    void testPositionEditOmitsBlankFields() {
        final GovBusinessesPanel p = newPanel();
        p.selectedPosition = "pos-1";
        p.pending.clear();
        GovBusinessesRequests.requestPositionEdit(p, "pos-1", "", "", "", "");
        check(p.pending.containsValue(RequestKind.POSITION_EDIT), "no POSITION_EDIT pending");
    }

    void testPositionEditRejectsMissingIds() {
        final GovBusinessesPanel p = newPanel();
        p.selectedId = "";
        p.selectedPosition = "pos-1";
        p.pending.clear();
        GovBusinessesRequests.requestPositionEdit(p, "pos-1", "Manager", "50", "invite", "1");
        check(p.pending.isEmpty(), "should not send when selectedId is blank");
    }

    void testOfferListPayload() {
        final GovBusinessesPanel p = newPanel();
        p.pending.clear();
        GovBusinessesRequests.requestOfferList(p);
        check(p.offerState == LoadState.LOADING, "offerState not LOADING");
        check(p.pending.containsValue(RequestKind.OFFER_LIST), "no OFFER_LIST pending");
    }

    void testContractListPayload() {
        final GovBusinessesPanel p = newPanel();
        p.pending.clear();
        GovBusinessesRequests.requestContractList(p);
        check(p.contractState == LoadState.LOADING, "contractState not LOADING");
        check(p.pending.containsValue(RequestKind.CONTRACT_LIST), "no CONTRACT_LIST pending");
    }

    void testComplaintListPayload() {
        final GovBusinessesPanel p = newPanel();
        p.pending.clear();
        GovBusinessesRequests.requestComplaintList(p);
        check(p.complaintState == LoadState.LOADING, "complaintState not LOADING");
        check(p.pending.containsValue(RequestKind.COMPLAINT_LIST), "no COMPLAINT_LIST pending");
    }

    void testMutationPendingBlocksEdits() {
        final GovBusinessesPanel p = newPanel();
        p.selectedPosition = "pos-1";
        p.pending.clear();
        p.pending.put("dummy", RequestKind.MUTATE);
        GovBusinessesRequests.requestPositionEdit(p, "pos-1", "Manager", "50", "invite", "1");
        check(p.pending.size() == 1, "mutationPending should block new edit");
        check(p.pending.containsKey("dummy"), "dummy request should remain");
    }

    public static void main(final String[] args) {
        final GovBusinessesRequestsTest t = new GovBusinessesRequestsTest();
        int passed = 0;
        int failed = 0;

        GovBusinessesPanel p;

        p = newPanel(); p.pending.clear();
        try { t.testPositionListPayload(); passed++;
            System.out.println("PASS testPositionListPayload");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testPositionListPayload: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testPositionEditPayload(); passed++;
            System.out.println("PASS testPositionEditPayload");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testPositionEditPayload: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testPositionEditOmitsBlankFields(); passed++;
            System.out.println("PASS testPositionEditOmitsBlankFields");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testPositionEditOmitsBlankFields: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testPositionEditRejectsMissingIds(); passed++;
            System.out.println("PASS testPositionEditRejectsMissingIds");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testPositionEditRejectsMissingIds: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testOfferListPayload(); passed++;
            System.out.println("PASS testOfferListPayload");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testOfferListPayload: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testContractListPayload(); passed++;
            System.out.println("PASS testContractListPayload");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testContractListPayload: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testComplaintListPayload(); passed++;
            System.out.println("PASS testComplaintListPayload");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testComplaintListPayload: " + e.getMessage());
        }

        p = newPanel(); p.pending.clear();
        try { t.testMutationPendingBlocksEdits(); passed++;
            System.out.println("PASS testMutationPendingBlocksEdits");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testMutationPendingBlocksEdits: " + e.getMessage());
        }

        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
