package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessEmployee;
import com.horizonssmp.gov.common.records.PayrollInfo;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.records.BusinessRecord;

final class GovStartupCheckBusinessesPart2 {

    private GovStartupCheckBusinessesPart2() {
    }

    static void run(final GovStartupCheckBusinessesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final boolean banking = ctx.banking;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID carol = ctx.carol;
        final UUID dave = ctx.dave;
        final BusinessRecord ace = ctx.ace;
        final BusinessService.Actor aliceOwner = ctx.aliceOwner;
        final BusinessService.Actor citizen = ctx.citizen;
        final BusinessService.Actor payrollViewer = ctx.payrollViewer;
        final BusinessService.Actor bobOwner = ctx.bobOwner;
        final BusinessService.Actor clerk = ctx.clerk;
        final BusinessService.Actor registrar = ctx.registrar;

            // Staff: the owner may staff without an office; a stranger may not; the role is a
            // validated label and a duplicate is refused.
            ctx.checks++;
            if (!BusinessService.addEmployee(scratch, aliceOwner, ace.id(), carol,
                    "StartupCheck Business Carol", "cashier", now).ok()) {
                failures.add("the owner could not add an employee to their own business");
            }
            final BusinessRecord aceStaffed = scratch.business(ace.id());
            ctx.checks++;
            if (aceStaffed == null || aceStaffed.employees().size() != 1
                    || !carol.equals(aceStaffed.employees().get(0).uuid())
                    || !"cashier".equals(aceStaffed.employees().get(0).role())
                    || aceStaffed.employees().get(0).since() != now) {
                failures.add("the employee entry did not store the uuid, role and join time");
            }
            ctx.checks++;
            if (BusinessService.addEmployee(scratch, aliceOwner, ace.id(), carol,
                    "StartupCheck Business Carol", "worker", now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a duplicate employee entry was accepted");
            }
            ctx.checks++;
            if (BusinessService.addEmployee(scratch, aliceOwner, ace.id(), dave,
                    "StartupCheck Business Dave", "spaceship", now).code()
                    != ErrorCode.BAD_PAYLOAD) {
                failures.add("an unknown employee role was accepted");
            }
            ctx.checks++;
            if (BusinessService.addEmployee(scratch, bobOwner, ace.id(), dave,
                    "StartupCheck Business Dave", "worker", now).code() != ErrorCode.FORBIDDEN) {
                failures.add("a stranger added an employee without business.manage");
            }
            ctx.checks++;
            if (!BusinessService.addEmployee(scratch, aliceOwner, ace.id(), dave,
                    "StartupCheck Business Dave", "worker", now).ok()) {
                failures.add("the owner could not staff their own business");
            }
            ctx.checks++;
            if (!BusinessService.removeEmployee(scratch, aliceOwner, ace.id(), dave).ok()
                    || scratch.business(ace.id()).employees().size() != 1) {
                failures.add("the owner could not remove an employee");
            }
            ctx.checks++;
            if (BusinessService.addEmployee(scratch, clerk, ace.id(), dave,
                    "StartupCheck Business Dave", "worker", now).code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("a business.manage holder was allowed to staff another's business");
            }
            ctx.checks++;
            if (BusinessService.removeEmployee(scratch, aliceOwner, ace.id(), dave).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("removing an absent employee was accepted");
            }

            final List<BusinessEmployee> fullStaff = new ArrayList<>();
            for (int i = 0; i < BusinessService.MAX_EMPLOYEES; i++) {
                fullStaff.add(new BusinessEmployee(new UUID(0L, 9000L + i), "worker", now));
            }
            final BusinessRecord fullShop = new BusinessRecord(
                    "b-startupcheckfull", "StartupCheck Full Shop", alice, "shop",
                    BusinessRecord.STATUS_ACTIVE, now, registrar.uuid(), "", fullStaff,
                    PayrollInfo.NONE, "", "", 0L, null);
            scratch.putBusiness(fullShop);
            ctx.checks++;
            if (BusinessService.addEmployee(scratch, aliceOwner, fullShop.id(),
                    new UUID(0L, 9100L), "StartupCheck Business Extra", "worker", now).code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("a business accepted more staff entries than the documented bound");
            }

            // Linked job: validated at write time, unlinkable, and it grants nothing by itself.
            ctx.checks++;
            if (!BusinessService.link(scratch, aliceOwner, ace.id(), "police").ok()
                    || !"police".equals(scratch.business(ace.id()).linkedJobId())) {
                failures.add("linking a business to an existing job failed");
            }
            ctx.checks++;
            if (BusinessService.link(scratch, aliceOwner, ace.id(), "police").code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("linking the same job twice was accepted");
            }
            ctx.checks++;
            if (BusinessService.link(scratch, aliceOwner, ace.id(), "nosuchjob").code()
                    != ErrorCode.BAD_JOB) {
                failures.add("linking a business to an unknown job was accepted");
            }
            ctx.checks++;
            if (GovCapabilities.effective(scratch.player(carol), scratch::job)
                            .contains(GovCapability.FINE_ISSUE)
                    || GovCapabilities.effective(scratch.player(alice), scratch::job)
                            .contains(GovCapability.FINE_ISSUE)) {
                failures.add("a linked job granted a capability through the business card");
            }
            ctx.checks++;
            if (!BusinessService.link(scratch, aliceOwner, ace.id(), "").ok()
                    || !scratch.business(ace.id()).linkedJobId().isEmpty()) {
                failures.add("unlinking a job failed or left the id behind");
            }
            ctx.checks++;
            if (BusinessService.link(scratch, aliceOwner, ace.id(), "").code()
                    != ErrorCode.INVALID_STATE) {
                failures.add("unlinking an unlinked business was accepted");
            }
            ctx.checks++;
            if (BusinessService.link(scratch, citizen, ace.id(), "police").code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("a stranger linked a job without business.manage");
            }
            ctx.checks++;
            if (!BusinessService.link(scratch, aliceOwner, ace.id(), "govworker").ok()
                    || !"Government Worker".equals(
                            BusinessService.jobName(scratch, "govworker"))) {
                failures.add("the second link or its job display name is wrong");
            }

            // Payroll: a stated rate and schedule, bounded, then a recorded physical payment. When
            // banking is enabled the legacy note is retired, so only the refusal is asserted.
            if (banking) {
                ctx.checks++;
                if (BusinessService.setPayroll(scratch, clerk, ace.id(), "8", "").code()
                                != ErrorCode.INVALID_STATE
                        || BusinessService.recordPayment(scratch, clerk, ace.id(), "16",
                                "StartupCheck first payment", now).code()
                                != ErrorCode.INVALID_STATE) {
                    failures.add("banking did not retire the legacy payroll note");
                }
            } else {
                ctx.checks++;
                if (!BusinessService.setPayroll(scratch, aliceOwner, ace.id(), "16",
                        "paid at dusk each in-game day").ok()
                        || scratch.business(ace.id()).payroll().ratePerDayGold() != 16L
                        || !"paid at dusk each in-game day".equals(
                                scratch.business(ace.id()).payroll().scheduleNote())) {
                    failures.add("setting the payroll rate or schedule note failed");
                }
                ctx.checks++;
                if (BusinessService.setPayroll(scratch, aliceOwner, ace.id(), "-1", "").code()
                                != ErrorCode.BAD_PAYLOAD
                        || BusinessService.setPayroll(scratch, aliceOwner, ace.id(), "65", "").code()
                                != ErrorCode.BAD_PAYLOAD
                        || BusinessService.setPayroll(scratch, aliceOwner, ace.id(), "abc", "").code()
                                != ErrorCode.BAD_PAYLOAD) {
                    failures.add("an out-of-bounds or unreadable payroll rate was accepted");
                }
                ctx.checks++;
                if (BusinessService.setPayroll(scratch, citizen, ace.id(), "8", "").code()
                        != ErrorCode.FORBIDDEN) {
                    failures.add("a stranger set a payroll rate without business.manage");
                }
                ctx.checks++;
                if (!BusinessService.setPayroll(scratch, aliceOwner, ace.id(), "8", "on the first day")
                        .ok()) {
                    failures.add("the owner could not set the payroll rate");
                }
                ctx.checks++;
                if (BusinessService.setPayroll(scratch, clerk, ace.id(), "8", "on the first day")
                        .code() != ErrorCode.FORBIDDEN) {
                    failures.add("a business.manage holder was allowed to set another's payroll");
                }
                ctx.checks++;
                if (!BusinessService.recordPayment(scratch, clerk, ace.id(), "16",
                        "StartupCheck first payment", now).ok()) {
                    failures.add("recording a physical payroll payment failed");
                }
                final PayrollInfo firstPayment = scratch.business(ace.id()).payroll();
                ctx.checks++;
                if (firstPayment.paymentsRecorded() != 1
                        || firstPayment.lastPaidAt() != now
                        || !clerk.uuid().equals(firstPayment.lastPaidBy())
                        || firstPayment.lastPaidAmountGold() != 16L
                        || !"StartupCheck first payment".equals(firstPayment.lastPaymentNote())) {
                    failures.add(
                            "the recorded payment did not store its amount, time, official or note");
                }
                ctx.checks++;
                if (!BusinessService.recordPayment(scratch, clerk, ace.id(), "8",
                        "StartupCheck second payment", now + 1_000L).ok()
                        || scratch.business(ace.id()).payroll().paymentsRecorded() != 2
                        || scratch.business(ace.id()).payroll().lastPaidAmountGold() != 8L) {
                    failures.add(
                            "the second recorded payment did not replace the last one and count twice");
                }
                ctx.checks++;
                if (BusinessService.recordPayment(scratch, aliceOwner, ace.id(), "8", "", now).code()
                                != ErrorCode.FORBIDDEN
                        || BusinessService.recordPayment(
                                scratch, clerk, ace.id(), "0", "", now).code()
                                != ErrorCode.BAD_PAYLOAD
                        || BusinessService.recordPayment(
                                scratch, clerk, ace.id(), "65", "", now).code()
                                != ErrorCode.BAD_PAYLOAD) {
                    failures.add(
                            "a payment was accepted from the owner, at zero, or above the bound");
                }
            }


        ctx.fullShop = fullShop;
    }
}
