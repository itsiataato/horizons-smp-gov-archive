package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import java.util.Set;
import com.horizonssmp.gov.config.GovConfig;

final class GovStartupCheckBusinessesContext {
    final List<String> failures;
    int checks;
    final long now = 1_800_000_000_000L;
    final boolean banking = GovConfig.bankEnabled();

    final GovSavedData scratch = new GovSavedData();
    final UUID alice = new UUID(0L, 5001L);
    final UUID bob = new UUID(0L, 5002L);
    final UUID carol = new UUID(0L, 5003L);
    final UUID dave = new UUID(0L, 5004L);

    final BusinessService.Actor registrar = new BusinessService.Actor(
            new UUID(0L, 5100L), "StartupCheck Business Registrar",
            EnumSet.of(GovCapability.BUSINESS_REGISTER, GovCapability.BUSINESS_MANAGE));
    final BusinessService.Actor executive = new BusinessService.Actor(
            new UUID(0L, 5101L), "StartupCheck Business Executive",
            EnumSet.allOf(GovCapability.class));
    final BusinessService.Actor clerk = new BusinessService.Actor(
            new UUID(0L, 5102L), "StartupCheck Business Clerk",
            EnumSet.of(GovCapability.BUSINESS_MANAGE,
                    GovCapability.BUSINESS_CLOSE_APPROVE));
    final BusinessService.Actor payrollViewer = new BusinessService.Actor(
            new UUID(0L, 5103L), "StartupCheck Business Payroll",
            EnumSet.of(GovCapability.PAYROLL_RUN));
    final BusinessService.Actor aliceOwner = new BusinessService.Actor(
            alice, "StartupCheck Business Alice", EnumSet.noneOf(GovCapability.class));
    final BusinessService.Actor bobOwner = new BusinessService.Actor(
            bob, "StartupCheck Business Bob",
            EnumSet.of(GovCapability.BUSINESS_MANAGE,
                    GovCapability.BUSINESS_CLOSE_APPROVE));
    final BusinessService.Actor citizen = new BusinessService.Actor(
            new UUID(0L, 5104L), "StartupCheck Citizen",
            EnumSet.noneOf(GovCapability.class));

    BusinessRecord ace;
    BusinessRecord fullShop;
    BusinessRecord suspended;
    Set<GovCapability> noCaps = EnumSet.noneOf(GovCapability.class);

    GovStartupCheckBusinessesContext(final List<String> failures) {
        this.failures = failures;
        scratch.seedIfNeeded(now, false);
        scratch.putPlayer(PlayerRecord.blank(alice, "StartupCheck Business Alice", now));
        scratch.putPlayer(PlayerRecord.blank(bob, "StartupCheck Business Bob", now));
        scratch.putPlayer(PlayerRecord.blank(carol, "StartupCheck Business Carol", now));
        scratch.putPlayer(PlayerRecord.blank(dave, "StartupCheck Business Dave", now));
        scratch.putPlayer(PlayerRecord.blank(registrar.uuid(), registrar.name(), now));
        scratch.putPlayer(PlayerRecord.blank(executive.uuid(), executive.name(), now));
        scratch.putPlayer(PlayerRecord.blank(clerk.uuid(), clerk.name(), now));
        scratch.putPlayer(PlayerRecord.blank(payrollViewer.uuid(), payrollViewer.name(), now));
    }
}
