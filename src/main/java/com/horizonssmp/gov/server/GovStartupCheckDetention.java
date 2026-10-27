package com.horizonssmp.gov.server;

import java.util.List;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.DetentionStatus;
import com.horizonssmp.gov.common.records.DetentionRecord;

/** Compact M8 contract check; detailed detention behavior is covered by the safety checks. */
final class GovStartupCheckDetention {
    private GovStartupCheckDetention() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        checks++;
        if (!DetentionRecord.ID_PREFIX.equals("d-") || JailKeys.PAGE_SIZE != 8
                || JailKeys.HARD_MAX_NO_WARRANT_MINUTES != 10
                || DetentionStatus.values().length != 3) {
            failures.add("a detention constant is not the documented value");
        }
        final OpCode[] reads = {OpCode.JAIL_LIST, OpCode.JAIL_INFO};
        for (final OpCode op : reads) {
            checks++;
            if (!GovActionDispatcher.isRegistered(op)
                    || GovCapabilities.requiredFor(op) != GovCapability.PLAYER_LOOKUP_SELF
                    || op.audited()) {
                failures.add("jail read action contract is invalid: " + op);
            }
        }
        final OpCode[] mutations = {OpCode.JAIL_COMMIT, OpCode.JAIL_RELEASE,
                OpCode.JAIL_EXTEND, OpCode.CUFF, OpCode.UNCUFF,
                OpCode.JAIL_CELL_CREATE, OpCode.JAIL_CELL_DELETE};
        for (final OpCode op : mutations) {
            checks++;
            if (!GovActionDispatcher.isRegistered(op) || !op.audited()) {
                failures.add("jail mutation action contract is invalid: " + op);
            }
        }
        return checks;
    }
}
