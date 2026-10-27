package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.BusinessRecord;

/**
 * The business registry rules. Implementation lives in helper classes in this package; this class
 * keeps the public API, constants and records.
 */
public final class BusinessService {

    public static final String BUSINESS_ID_PREFIX = "b-";
    public static final long MAX_PAYROLL_GOLD = BusinessKeys.MAX_PAYROLL_GOLD;
    public static final int MAX_EMPLOYEES = 24;

    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {
        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    public record Outcome(boolean ok, ErrorCode code, String message, String businessId) {
        public Outcome {
            message = message == null ? "" : message;
            businessId = businessId == null ? "" : businessId;
        }
        public static Outcome ok(final String message) { return new Outcome(true, null, message, ""); }
        public static Outcome ok(final String message, final String businessId) { return new Outcome(true, null, message, businessId); }
        public static Outcome refused(final ErrorCode code, final String message) { return new Outcome(false, code, message, ""); }
    }

    public record BusinessRow(BusinessRecord business, String ownerName, String jobName) {
    }

    public record Page(List<BusinessRow> rows, int total, int page, int pages) {
        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    private BusinessService() {
    }

    // lookups
    public static BusinessRecord find(final GovSavedData data, final String raw) { return BusinessLookups.find(data, raw); }
    public static BusinessRecord byName(final GovSavedData data, final String name) { return BusinessLookups.byName(data, name); }
    public static String noSuchBusinessMessage(final String raw) { return BusinessLookups.noSuchBusinessMessage(raw); }
    public static String ownerName(final GovSavedData data, final UUID uuid) { return BusinessLookups.ownerName(data, uuid); }
    public static String jobName(final GovSavedData data, final String jobId) { return BusinessLookups.jobName(data, jobId); }
    public static boolean canViewAll(final Set<GovCapability> capabilities) { return BusinessLookups.canViewAll(capabilities); }
    public static boolean canManage(final Actor actor) { return BusinessLookups.canManage(actor); }
    public static boolean isOwnerOrManager(final BusinessRecord business, final Actor actor) { return BusinessLookups.isOwnerOrManager(business, actor); }
    public static boolean isOwner(final BusinessRecord business, final Actor actor) { return BusinessLookups.isOwner(business, actor); }
    public static boolean visibleTo(final GovSavedData data, final UUID viewerUuid, final String businessId) { return BusinessLookups.visibleTo(data, viewerUuid, businessId); }

    // registration
    public static Outcome register(final GovSavedData data, final Actor actor, final String rawName, final String rawType, final UUID ownerUuid, final String enteredOwnerName, final long now) { return BusinessRegistration.register(data, actor, rawName, rawType, ownerUuid, enteredOwnerName, now); }
    public static Outcome transfer(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID newOwnerUuid, final String newOwnerName, final long now) { return BusinessRegistration.transfer(data, actor, rawBusiness, newOwnerUuid, newOwnerName, now); }

    // status
    public static Outcome suspend(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawReason, final long now) { return BusinessStatus.suspend(data, actor, rawBusiness, rawReason, now); }
    public static Outcome close(final GovSavedData data, final Actor actor, final String rawBusiness, final long now) { return BusinessStatus.close(data, actor, rawBusiness, now); }
    public static Outcome delete(final GovSavedData data, final Actor actor, final String rawBusiness, final long now) { return BusinessStatus.delete(data, actor, rawBusiness, now); }

    // staff
    public static Outcome addEmployee(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID employeeUuid, final String employeeName, final String rawRole, final long now) { return BusinessStaff.addEmployee(data, actor, rawBusiness, employeeUuid, employeeName, rawRole, now); }
    public static Outcome removeEmployee(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID employeeUuid) { return BusinessStaff.removeEmployee(data, actor, rawBusiness, employeeUuid); }

    // linked job
    public static Outcome link(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawJobId) { return BusinessLinks.link(data, actor, rawBusiness, rawJobId); }

    // payroll
    public static Outcome setPayroll(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawRate, final String rawSchedule) { return BusinessPayroll.setPayroll(data, actor, rawBusiness, rawRate, rawSchedule); }
    public static Outcome recordPayment(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawAmount, final String rawNote, final long now) { return BusinessPayroll.recordPayment(data, actor, rawBusiness, rawAmount, rawNote, now); }
    public static long parseAmount(final String raw) { return BusinessPayroll.parseAmount(raw); }

    // prechecks
    public static Outcome registerPrecheck(final GovSavedData data, final String rawName, final String rawType) { return BusinessPrechecks.registerPrecheck(data, rawName, rawType); }
    public static Outcome suspendPrecheck(final GovSavedData data, final String rawBusiness, final String rawReason) { return BusinessPrechecks.suspendPrecheck(data, rawBusiness, rawReason); }
    public static Outcome closePrecheck(final GovSavedData data, final String rawBusiness) { return BusinessPrechecks.closePrecheck(data, rawBusiness); }
    public static Outcome employeeAddPrecheck(final GovSavedData data, final String rawBusiness, final String rawRole) { return BusinessPrechecks.employeeAddPrecheck(data, rawBusiness, rawRole); }
    public static Outcome employeeRemovePrecheck(final GovSavedData data, final String rawBusiness) { return BusinessPrechecks.employeeRemovePrecheck(data, rawBusiness); }
    public static Outcome linkPrecheck(final GovSavedData data, final String rawBusiness, final String rawJobId) { return BusinessPrechecks.linkPrecheck(data, rawBusiness, rawJobId); }
    public static Outcome payrollSetPrecheck(final GovSavedData data, final String rawBusiness, final String rawRate) { return BusinessPrechecks.payrollSetPrecheck(data, rawBusiness, rawRate); }
    public static Outcome payrollRecordPrecheck(final GovSavedData data, final String rawBusiness, final String rawAmount) { return BusinessPrechecks.payrollRecordPrecheck(data, rawBusiness, rawAmount); }

    // reads
    public static String canonicalFilter(final String raw) { return BusinessReads.canonicalFilter(raw); }
    public static Page page(final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities, final String rawFilter, final UUID ownerFilter, final int rawPage) { return BusinessReads.page(data, viewerUuid, capabilities, rawFilter, ownerFilter, rawPage); }
    public static Map<String, String> listFields(final GovSavedData data, final UUID viewerUuid, final String viewerName, final Set<GovCapability> capabilities, final String rawFilter, final UUID ownerFilter, final int page, final List<String> onlineNames) { return BusinessReads.listFields(data, viewerUuid, viewerName, capabilities, rawFilter, ownerFilter, page, onlineNames); }
    public static Map<String, String> detailFields(final GovSavedData data, final BusinessRecord business, final Set<GovCapability> capabilities, final UUID viewerUuid, final Set<UUID> onlineUuids) { return BusinessReads.detailFields(data, business, capabilities, viewerUuid, onlineUuids); }
    public static Map<String, String> payrollInfoFields(final GovSavedData data, final BusinessRecord business, final Set<GovCapability> capabilities) { return BusinessReads.payrollInfoFields(data, business, capabilities); }
}