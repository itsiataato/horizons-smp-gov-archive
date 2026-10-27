package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

final class GovStartupCheckFinesContext {
    final List<String> failures;
    int checks;
    final long now = System.currentTimeMillis();

    final FineService.ReceptionPoint receptionPoint =
            new FineService.ReceptionPoint("minecraft:overworld", 100.0d, 64.0d, 100.0d, 5.0d);

    final GovSavedData scratch = new GovSavedData();
    final UUID alice = new UUID(0L, 4001L);
    final UUID bob = new UUID(0L, 4002L);
    final UUID carol = new UUID(0L, 4003L);

    final FineService.Actor officer = new FineService.Actor(
            new UUID(0L, 4100L), "StartupCheck Fine Officer",
            EnumSet.of(GovCapability.FINE_ISSUE, GovCapability.FINE_WAIVE,
                    GovCapability.FINE_VIEW_ALL));
    final FineService.Actor citizen = new FineService.Actor(
            alice, "StartupCheck Fine Alice", EnumSet.noneOf(GovCapability.class));

    FineRecord aggravatedFine;
    GovSavedData pay;
    FineRecord collectedFine;

    GovStartupCheckFinesContext(final List<String> failures) {
        this.failures = failures;
        scratch.seedIfNeeded(now, false);
        scratch.putPlayer(PlayerRecord.blank(alice, "StartupCheck Fine Alice", now));
        scratch.putPlayer(PlayerRecord.blank(bob, "StartupCheck Fine Bob", now));
        scratch.putPlayer(PlayerRecord.blank(officer.uuid(), officer.name(), now));
    }
}
