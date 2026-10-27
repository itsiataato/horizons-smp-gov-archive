package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.records.BusinessRecord;

final class GovStartupCheckBusinessesPart3 {

    private GovStartupCheckBusinessesPart3() {
    }

    static void run(final GovStartupCheckBusinessesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final UUID dave = ctx.dave;
        final BusinessRecord ace = ctx.ace;
        final BusinessService.Actor registrar = ctx.registrar;
        final BusinessService.Actor executive = ctx.executive;
        final BusinessService.Actor aliceOwner = ctx.aliceOwner;
        final BusinessService.Actor bobOwner = ctx.bobOwner;
        final BusinessService.Actor clerk = ctx.clerk;
        final BusinessService.Actor citizen = ctx.citizen;

            // Suspension: executive authority, a required reason, and the status transitions.
            ctx.checks++;
            if (BusinessService.suspend(scratch, citizen, ace.id(), "StartupCheck nope", now).code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("a stranger suspended a business without business.manage");
            }
            ctx.checks++;
            if (BusinessService.suspend(scratch, clerk, ace.id(), "   ", now).code()
                    != ErrorCode.BAD_PAYLOAD) {
                failures.add("a suspension without a reason was accepted");
            }
            ctx.checks++;
            if (!BusinessService.suspend(scratch, clerk, ace.id(),
                    "StartupCheck unpaid wages", now).ok()) {
                failures.add("a business.manage holder could not suspend a business");
            }
            final BusinessRecord suspended = scratch.business(ace.id());
            ctx.checks++;
            if (suspended == null || !suspended.suspended()
                    || !"StartupCheck unpaid wages".equals(suspended.suspendReason())
                    || suspended.suspendedAt() != now
                    || !clerk.uuid().equals(suspended.suspendedBy())) {
                failures.add("the suspension did not store its status, reason, time and official");
            }
            ctx.checks++;
            if (BusinessService.suspend(scratch, clerk, ace.id(), "again", now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a second suspension of a suspended business was accepted");
            }

            // Transfer and close, including the closed-state freeze. Only the executive office may
            // move a CEO root; the registrar may not, and an unknown id is still BAD_BUSINESS.
            final BusinessRecord clean = BusinessService.find(scratch, "StartupCheck Clean Shop");
            ctx.checks++;
            if (BusinessService.transfer(scratch, registrar, clean.id(), bob,
                    "StartupCheck Business Bob", now).code() != ErrorCode.FORBIDDEN) {
                failures.add("a non-executive registrar transferred a business");
            }
            ctx.checks++;
            if (!BusinessService.transfer(scratch, executive, clean.id(), bob,
                    "StartupCheck Business Bob", now).ok()
                    || !bob.equals(scratch.business(clean.id()).ownerUuid())) {
                failures.add("the ownership transfer did not change the registered owner");
            }
            ctx.checks++;
            if (BusinessService.transfer(scratch, executive, clean.id(), bob,
                    "StartupCheck Business Bob", now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a transfer to the current owner was accepted");
            }
            ctx.checks++;
            if (BusinessService.transfer(scratch, citizen, clean.id(), carol,
                            "StartupCheck Business Carol", now).code() != ErrorCode.FORBIDDEN
                    || BusinessService.transfer(scratch, registrar, "StartupCheck No Business",
                            carol, "StartupCheck Business Carol", now).code()
                            != ErrorCode.BAD_BUSINESS) {
                failures.add("a transfer did not refuse a stranger or an unknown business");
            }
            ctx.checks++;
            if (!BusinessService.close(scratch, bobOwner, clean.id(), now).ok()
                    || !BusinessRecord.STATUS_CLOSED.equals(
                            scratch.business(clean.id()).status())) {
                failures.add("closing a business did not store the CLOSED status");
            }
            ctx.checks++;
            if (BusinessService.close(scratch, bobOwner, clean.id(), now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a second close of a closed business was accepted");
            }
            ctx.checks++;
            if (BusinessService.suspend(scratch, bobOwner, clean.id(), "StartupCheck late", now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a closed business was suspended");
            }
            ctx.checks++;
            if (BusinessService.transfer(scratch, executive, clean.id(), carol,
                    "StartupCheck Business Carol", now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a closed business changed hands");
            }
            ctx.checks++;
            if (BusinessService.addEmployee(scratch, bobOwner, clean.id(), dave,
                            "StartupCheck Business Dave", "worker", now).code()
                            != ErrorCode.INVALID_STATE
                    || BusinessService.link(scratch, bobOwner, clean.id(), "police").code()
                            != ErrorCode.INVALID_STATE) {
                failures.add("a closed business took staff or a job link");
            }
            ctx.checks++;
            if (BusinessService.setPayroll(scratch, bobOwner, clean.id(), "8", "").code()
                            != ErrorCode.INVALID_STATE
                    || BusinessService.recordPayment(scratch, bobOwner, clean.id(), "8", "", now).code()
                            != ErrorCode.INVALID_STATE) {
                failures.add("a closed business's payroll could still be changed");
            }

            // Pagination: enough rows for a second page, and a clamp for a page past the end.
            for (int i = 1; i <= 6; i++) {
                // Each registration needs a distinct owner now that a standing CEO contract owns
                // the one business-position slot; reusing the registrar would be refused.
                BusinessService.register(scratch, registrar,
                        "StartupCheck Page Shop " + i, "shop",
                        new UUID(0L, 9200L + i), "StartupCheck Page Owner " + i, now);
            }
            final Set<GovCapability> noCaps = EnumSet.noneOf(GovCapability.class);
            ctx.checks++;
            final BusinessService.Page carolPage = BusinessService.page(
                    scratch, carol, noCaps, BusinessKeys.FILTER_ALL, null, 0);
            if (carolPage.total() != 1
                    || !"StartupCheck Ace Shop".equals(
                            carolPage.rows().get(0).business().name())) {
                failures.add("an employee's business page was not scoped to their workplace");
            }
            ctx.checks++;
            final BusinessService.Page bobPage = BusinessService.page(
                    scratch, bob, noCaps, BusinessKeys.FILTER_ALL, null, 0);
            if (bobPage.total() != 1
                    || !"StartupCheck Clean Shop".equals(
                            bobPage.rows().get(0).business().name())) {
                failures.add("a citizen's business page was not scoped to their own business");
            }
            ctx.checks++;
            final BusinessService.Page allPage = BusinessService.page(
                    scratch, registrar.uuid(), registrar.capabilities(),
                    BusinessKeys.FILTER_ALL, null, 0);
            final BusinessService.Page lastPage = BusinessService.page(
                    scratch, registrar.uuid(), registrar.capabilities(),
                    BusinessKeys.FILTER_ALL, null, 5);
            if (allPage.total() != 9 || allPage.rows().size() != 8
                    || allPage.pages() != 2
                    || lastPage.page() != 1 || lastPage.rows().size() != 1) {
                failures.add("business pagination did not carry the true total, pages or page clamp");
            }
            // Counts are recomputed here, not carried from before the later mutations (tip t49).
            ctx.checks++;
            final int activeCount = BusinessService.page(scratch, registrar.uuid(),
                    registrar.capabilities(), BusinessKeys.FILTER_ACTIVE, null, 0).total();
            final int suspendedCount = BusinessService.page(scratch, registrar.uuid(),
                    registrar.capabilities(), BusinessKeys.FILTER_SUSPENDED, null, 0).total();
            final int closedCount = BusinessService.page(scratch, registrar.uuid(),
                    registrar.capabilities(), BusinessKeys.FILTER_CLOSED, null, 0).total();
            if (suspendedCount != 1 || closedCount != 1
                    || activeCount != allPage.total() - suspendedCount - closedCount) {
                failures.add("the business status filters do not partition the registry");
            }
            ctx.checks++;
            final BusinessService.Page alicePage = BusinessService.page(
                    scratch, registrar.uuid(), registrar.capabilities(),
                    BusinessKeys.FILTER_ALL, alice, 0);
            if (alicePage.total() != 2) {
                failures.add("the owner filter did not find exactly the owner's businesses");
            }


        ctx.suspended = suspended;
        ctx.noCaps = noCaps;
    }
}
