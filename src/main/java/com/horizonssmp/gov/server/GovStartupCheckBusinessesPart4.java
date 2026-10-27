package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Set;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PayrollInfo;
import java.util.Map;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckBusinessesPart4 {

    private GovStartupCheckBusinessesPart4() {
    }

    static void run(final GovStartupCheckBusinessesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final boolean banking = ctx.banking;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final BusinessRecord ace = ctx.ace;
        final BusinessRecord fullShop = ctx.fullShop;
        final BusinessRecord suspended = ctx.suspended;
        final BusinessService.Actor aliceOwner = ctx.aliceOwner;
        final BusinessService.Actor registrar = ctx.registrar;
        final BusinessService.Actor payrollViewer = ctx.payrollViewer;
        final Set<GovCapability> noCaps = ctx.noCaps;

            // The wire blocks: viewer flags, bounded staff list, linked job name and payroll note.
            ctx.checks++;
            final Map<String, String> listFields = BusinessService.listFields(
                    scratch, registrar.uuid(), registrar.name(), registrar.capabilities(),
                    BusinessKeys.FILTER_ALL, null, 0, List.of("StartupCheck Business Alice"));
            if (!"true".equals(listFields.get(BusinessKeys.CAN_REGISTER))
                    || !"true".equals(listFields.get(BusinessKeys.CAN_MANAGE))
                    || !"false".equals(listFields.get(BusinessKeys.CAN_PAYROLL))
                    || !"64".equals(listFields.get(BusinessKeys.PAYROLL_CAP_GOLD))
                    || !"9".equals(listFields.get(BusinessKeys.TOTAL))
                    || !"2".equals(listFields.get(BusinessKeys.PAGES))
                    || !"StartupCheck Business Alice".equals(
                            listFields.get(BusinessKeys.ONLINE_PREFIX + "0"))) {
                failures.add("the business list flags, cap or picker are wrong");
            }
            ctx.checks++;
            final Map<String, String> payrollList = BusinessService.listFields(
                    scratch, payrollViewer.uuid(), payrollViewer.name(),
                    payrollViewer.capabilities(), BusinessKeys.FILTER_SUSPENDED, null, 0,
                    List.of());
            if (!"true".equals(payrollList.get(BusinessKeys.CAN_PAYROLL))
                    || !"1".equals(payrollList.get(BusinessKeys.TOTAL))) {
                failures.add("the payroll viewer's flags or suspended filter are wrong");
            }
            ctx.checks++;
            final String payrollRate = banking ? "0" : "8";
            final String payrollSchedule = banking ? "" : "on the first day";
            final String payrollCount = banking ? "0" : "2";
            final String payrollLastAmount = banking ? "0" : "8";
            final String payrollLastBy = banking ? "" : "StartupCheck Business Clerk";
            final Map<String, String> aceDetail = BusinessService.detailFields(
                    scratch, scratch.business(ace.id()), registrar.capabilities(),
                    alice, Set.of());
            if (!"StartupCheck Ace Shop".equals(aceDetail.get(BusinessKeys.NAME))
                    || !"StartupCheck Business Alice".equals(aceDetail.get(BusinessKeys.OWNER))
                    || !"SUSPENDED".equals(aceDetail.get(BusinessKeys.STATUS))
                    || !"shop".equals(aceDetail.get(BusinessKeys.TYPE))
                    || !"govworker".equals(aceDetail.get(BusinessKeys.JOB))
                    || !"Government Worker".equals(aceDetail.get(BusinessKeys.JOB_NAME))
                    || !"1".equals(aceDetail.get(BusinessKeys.EMPLOYEE_COUNT))
                    || !"cashier".equals(
                            aceDetail.get(BusinessKeys.EMPLOYEE_PREFIX + "0"
                                    + BusinessKeys.EMPLOYEE_ROLE))
                    || !"false".equals(aceDetail.get(BusinessKeys.EMPLOYEE_PREFIX + "0"
                            + BusinessKeys.EMPLOYEE_ONLINE))
                    || !payrollRate.equals(aceDetail.get(BusinessKeys.PAYROLL_RATE))
                    || !payrollSchedule.equals(
                            aceDetail.get(BusinessKeys.PAYROLL_SCHEDULE))
                    || !payrollCount.equals(aceDetail.get(BusinessKeys.PAYROLL_COUNT))
                    || !payrollLastAmount.equals(
                            aceDetail.get(BusinessKeys.PAYROLL_LAST_AMOUNT))
                    || !payrollLastBy.equals(
                            aceDetail.get(BusinessKeys.PAYROLL_LAST_BY))
                    || !"StartupCheck unpaid wages".equals(
                            aceDetail.get(BusinessKeys.SUSPEND_REASON))
                    || !"true".equals(aceDetail.get(BusinessKeys.CAN_MANAGE_THIS))) {
                failures.add("the business detail block does not carry the card facts");
            }
            ctx.checks++;
            final Map<String, String> aliceDetail = BusinessService.detailFields(
                    scratch, scratch.business(ace.id()), noCaps, alice, Set.of());
            final Map<String, String> strangerDetail = BusinessService.detailFields(
                    scratch, scratch.business(ace.id()), noCaps, bob, Set.of());
            if (!"true".equals(aliceDetail.get(BusinessKeys.CAN_EDIT_THIS))
                    || !"false".equals(aliceDetail.get(BusinessKeys.CAN_MANAGE_THIS))
                    || !"false".equals(strangerDetail.get(BusinessKeys.CAN_EDIT_THIS))) {
                failures.add("the business owner/manager control flags are wrong");
            }
            ctx.checks++;
            final Map<String, String> fullDetail = BusinessService.detailFields(
                    scratch, fullShop, registrar.capabilities(), registrar.uuid(), Set.of());
            if (!"24".equals(fullDetail.get(BusinessKeys.EMPLOYEE_COUNT))
                    || !fullDetail.containsKey(BusinessKeys.EMPLOYEE_PREFIX + "11"
                            + BusinessKeys.EMPLOYEE_ROLE)
                    || fullDetail.containsKey(BusinessKeys.EMPLOYEE_PREFIX + "12"
                            + BusinessKeys.EMPLOYEE_ROLE)) {
                failures.add("the detail staff list is not bounded at the documented row count");
            }
            ctx.checks++;
            final Map<String, String> payrollFields = BusinessService.payrollInfoFields(
                    scratch, scratch.business(ace.id()), payrollViewer.capabilities());
            if (!payrollRate.equals(payrollFields.get(BusinessKeys.PAYROLL_RATE))
                    || !payrollCount.equals(payrollFields.get(BusinessKeys.PAYROLL_COUNT))
                    || !payrollLastAmount.equals(
                            payrollFields.get(BusinessKeys.PAYROLL_LAST_AMOUNT))
                    || !payrollLastBy.equals(payrollFields.get(BusinessKeys.PAYROLL_LAST_BY))) {
                failures.add("the payroll-only block does not carry the recorded facts");
            }
            ctx.checks++;
            if (!BusinessService.visibleTo(scratch, alice, ace.id())
                    || !BusinessService.visibleTo(scratch, carol, ace.id())
                    || BusinessService.visibleTo(scratch, bob, ace.id())
                    || BusinessService.visibleTo(scratch, null, ace.id())) {
                failures.add("the business read visibility rule is wrong for owner, staff or stranger");
            }

            // Tolerant reads: the legacy REVOKED status is CLOSED, an unknown status never becomes
            // ACTIVE, and an employee entry without a UUID is skipped instead of failing the load.
            ctx.checks++;
            final CompoundTag legacy = new CompoundTag();
            legacy.putString("id", "startup-check-business-legacy");
            legacy.putString("name", "StartupCheck Legacy Shop");
            legacy.putString("status", "revoked");
            final BusinessRecord legacyRecord = BusinessRecord.read(legacy, null);
            if (!legacyRecord.closed()
                    || !BusinessRecord.STATUS_CLOSED.equals(legacyRecord.status())
                    || legacyRecord.employees().size() != 0
                    || !PayrollInfo.NONE.equals(legacyRecord.payroll())) {
                failures.add("a legacy REVOKED business tag did not read as CLOSED");
            }
            ctx.checks++;
            final CompoundTag unknownStatus = new CompoundTag();
            unknownStatus.putString("id", "startup-check-business-paused");
            unknownStatus.putString("status", "PAUSED");
            final BusinessRecord paused = BusinessRecord.read(unknownStatus, null);
            if (paused.active() || !"PAUSED".equals(paused.status())) {
                failures.add("an unknown stored business status was treated as active");
            }
            ctx.checks++;
            final CompoundTag staffTag = new CompoundTag();
            staffTag.putString("id", "startup-check-business-staff");
            final CompoundTag noUuid = new CompoundTag();
            noUuid.putString("role", "worker");
            final CompoundTag withUuid = new CompoundTag();
            NbtSupport.putUuid(withUuid, "uuid", carol);
            withUuid.putString("role", "spaceship");
            NbtSupport.putCompoundList(staffTag, "employees", List.of(noUuid, withUuid));
            final BusinessRecord tolerantStaff = BusinessRecord.read(staffTag, null);
            if (tolerantStaff.employees().size() != 1
                    || !"spaceship".equals(tolerantStaff.employees().get(0).role())
                    || !tolerantStaff.payroll().empty()
                    || !tolerantStaff.name().isEmpty()) {
                failures.add("a tolerant business tag did not skip the unkeyed staff entry");
            }
            ctx.checks++;
            final BusinessRecord aceNow = scratch.business(ace.id());
            final CompoundTag aceTag = aceNow.write();
            if (!aceNow.equals(BusinessRecord.read(aceTag, null))
                    || aceTag.contains("balanceGold")
                    || aceTag.getCompound("payroll").contains("balance")
                    || BusinessKeys.MAX_PAYROLL_GOLD != 64L) {
                failures.add("the business record did not round-trip, or a balance field exists");
            }

            // Persistence: the section appears in the file and every M7 field survives a reload.
            ctx.checks++;
            final CompoundTag saved = scratch.save(new CompoundTag(), null);
            final GovSavedData reloaded = GovSavedData.load(saved, null);
            final BusinessRecord reloadedAce = BusinessService.find(
                    reloaded, "StartupCheck Ace Shop");
            if (!saved.contains("businesses")
                    || reloaded.businesses().size() != scratch.businesses().size()
                    || reloadedAce == null
                    || !"SUSPENDED".equals(reloadedAce.status())
                    || reloadedAce.employees().size() != 1
                    || reloadedAce.payroll().paymentsRecorded() != (banking ? 0 : 2)
                    || !"govworker".equals(reloadedAce.linkedJobId())
                    || !"StartupCheck unpaid wages".equals(reloadedAce.suspendReason())) {
                failures.add("the business section did not survive a save and load");
            }
            ctx.checks++;
            if (!GovSavedData.load(new CompoundTag(), null).isDirty()) {
                failures.add("a file without the businesses key was not marked dirty for the next save");
            }

    }
}
