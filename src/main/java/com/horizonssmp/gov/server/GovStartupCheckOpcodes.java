package com.horizonssmp.gov.server;

import java.util.List;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;

final class GovStartupCheckOpcodes {

    private GovStartupCheckOpcodes() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        for (final OpCode op : OpCode.values()) {
            checks++;
            final GovCapability required = GovCapabilities.requiredFor(op);
            if (required == null) {
                failures.add("opcode has no required capability: " + op);
            }
        }
        return checks;
    }

    /** The payload envelope must accept exactly what it documents. */

}
