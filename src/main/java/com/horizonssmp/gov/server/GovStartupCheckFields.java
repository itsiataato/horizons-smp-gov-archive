package com.horizonssmp.gov.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.records.EconomyMeta;

final class GovStartupCheckFields {

    private GovStartupCheckFields() {
    }

    static int run(final List<String> failures) {
        int checks = 0;

        final Map<String, String> original = new LinkedHashMap<>();
        original.put("name", "Steve");
        original.put("note", "has = and \\ and a\nnewline in it");
        original.put("empty", "");
        original.put("unicode", "caf\u00e9 \u00a7c");
        checks++;
        if (!original.equals(GovFields.decode(GovFields.encode(original)))) {
            failures.add("field block did not round-trip through its own encoding");
        }

        checks++;
        if (!GovFields.decode("garbage\nno_equals_here\n=blank_key").isEmpty()) {
            failures.add("field block accepted a malformed line");
        }

        checks++;
        if (!GovFields.decode(null).isEmpty() || !GovFields.decode("").isEmpty()) {
            failures.add("field block rejected empty input");
        }

        checks++;
        if (!"1".equals(GovFields.decode("k=1\nk=2").get("k"))) {
            failures.add("field block let a later duplicate key win");
        }

        checks++;
        if (GovFields.encode(Map.of("big", "x".repeat(GovFields.MAX_ENCODED * 2)))
                .length() > GovFields.MAX_ENCODED) {
            failures.add("field block exceeded its documented length");
        }
        return checks;
    }

    /**
     * The persisted economy must be self-consistent and every configured entry point must be usable.
     *
     * <p>The two singleton accounts are checked separately because their type and status are part
     * of the authority model, not just data. The account sweep proves the shape of every record and
     * the ledger sweep proves the posting rules that a service could otherwise break silently. The
     * conservation check is the identity {@link EconomyMeta} documents: every account balance plus
     * contract escrow equals the physical gold that ever entered the world.
     */

}
