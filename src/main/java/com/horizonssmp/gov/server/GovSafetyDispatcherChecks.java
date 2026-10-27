package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionRequestPayload;
import com.horizonssmp.gov.network.GovActionResultPayload;

final class GovSafetyDispatcherChecks {

    private GovSafetyDispatcherChecks() {
    }

    /** The dispatcher envelope: unknown op, unknown field, bad codec, oversized payload. */
    static int checkDispatcherBoundary(final List<String> failures) {
        int checks = 0;

        checks++;
        if (OpCode.byName("NOPE_FROM_A_NEWER_BUILD").isPresent()) {
            failures.add("unknown op name was accepted");
        }
        checks++;
        if (new GovActionRequestPayload(
                "NOPE_FROM_A_NEWER_BUILD", Map.of(), "r-1").opCode().isPresent()) {
            failures.add("unknown op survived the request payload decoder");
        }
        checks++;
        if (new GovActionResultPayload(
                "r-2", false, "CODE_FROM_A_NEWER_BUILD", "x", "")
                .errorCodeValue().isPresent()) {
            failures.add("unknown error code survived the result payload decoder");
        }

        final ActionRequest request =
                ActionRequest.of(
                        OpCode.NOTE_ADD, Map.of("unknownField", "x", "target", "Steve"));
        checks++;
        if (!"fallback".equals(request.get("missing", "fallback"))) {
            failures.add("unknown payload field did not fall back safely");
        }
        checks++;
        if (!"Steve".equals(request.get("target", ""))) {
            failures.add("known payload field was lost next to an unknown one");
        }
        checks++;
        if (GovActionDispatcher.checkPayload(Map.of("unknownField", "x")) != null) {
            failures.add("payload guard rejected an unknown but bounded field");
        }
        checks++;
        if (GovActionDispatcher.checkPayload(Map.of("blob", "x".repeat(
                GovActionDispatcher.MAX_PAYLOAD_CHARS + 1))) != ErrorCode.BAD_PAYLOAD) {
            failures.add("payload guard accepted an oversized value");
        }
        checks++;
        final Map<String, String> tooMany = new LinkedHashMap<>();
        for (int i = 0; i <= GovActionDispatcher.MAX_PAYLOAD_KEYS; i++) {
            tooMany.put("k" + i, "v");
        }
        if (GovActionDispatcher.checkPayload(tooMany) != ErrorCode.BAD_PAYLOAD) {
            failures.add("payload guard accepted too many keys");
        }
        checks++;
        if (GovFields.decode("garbage\nno_equals\n=blank").size() != 0) {
            failures.add("field block accepted a malformed line");
        }
        checks++;
        if (GovFields.decode("k=1\nk=2").get("k") == null
                || !"1".equals(GovFields.decode("k=1\nk=2").get("k"))) {
            failures.add("field block duplicate-key rule changed");
        }
        return checks;
    }
}
