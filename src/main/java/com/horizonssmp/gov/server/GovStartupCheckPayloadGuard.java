package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.horizonssmp.gov.common.ErrorCode;

final class GovStartupCheckPayloadGuard {

    private GovStartupCheckPayloadGuard() {
    }

    static int run(final List<String> failures) {
        int checks = 0;

        checks++;
        if (GovActionDispatcher.checkPayload(null) != null) {
            failures.add("payload guard rejected an empty payload");
        }
        checks++;
        if (GovActionDispatcher.checkPayload(Map.of()) != null) {
            failures.add("payload guard rejected an empty map");
        }

        final Map<String, String> atLimit = new LinkedHashMap<>();
        for (int i = 0; i < GovActionDispatcher.MAX_PAYLOAD_KEYS; i++) {
            atLimit.put("k" + i, "v");
        }
        checks++;
        if (GovActionDispatcher.checkPayload(atLimit) != null) {
            failures.add("payload guard rejected a payload at the key limit");
        }

        final Map<String, String> overLimit = new LinkedHashMap<>(atLimit);
        overLimit.put("oneTooMany", "v");
        checks++;
        if (GovActionDispatcher.checkPayload(overLimit) != ErrorCode.BAD_PAYLOAD) {
            failures.add("payload guard accepted more than " + GovActionDispatcher.MAX_PAYLOAD_KEYS + " keys");
        }

        final Map<String, String> tooLong = new LinkedHashMap<>();
        tooLong.put("blob", "x".repeat(GovActionDispatcher.MAX_PAYLOAD_CHARS + 1));
        checks++;
        if (GovActionDispatcher.checkPayload(tooLong) != ErrorCode.BAD_PAYLOAD) {
            failures.add("payload guard accepted more than "
                    + GovActionDispatcher.MAX_PAYLOAD_CHARS + " characters");
        }

        final Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("target", null);
        checks++;
        if (GovActionDispatcher.checkPayload(nullValue) != ErrorCode.BAD_PAYLOAD) {
            failures.add("payload guard accepted a null value");
        }
        return checks;
    }

    /** The limiter must allow exactly the configured allowance and then refuse. */

}
