package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.BusinessPermission;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.BusinessComplaintRecord;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

import com.horizonssmp.gov.server.BusinessEmploymentService.*;
import static com.horizonssmp.gov.server.BusinessEmploymentService.REAL_DAY_MS;
import static com.horizonssmp.gov.server.BusinessEmploymentService.MAX_PAYROLL_DAYS_PER_RUN;
import static com.horizonssmp.gov.server.BusinessEmploymentService.COMPLAINT_THRESHOLD;
import static com.horizonssmp.gov.server.BusinessEmploymentService.COMPLAINT_FINE_GOLD;
import static com.horizonssmp.gov.server.BusinessEmploymentService.POSITION_ID_PREFIX;
import static com.horizonssmp.gov.server.BusinessEmploymentService.CONTRACT_ID_PREFIX;
import com.horizonssmp.gov.server.BusinessEmploymentAccess.Access;

/**
 * Helper for {@link BusinessEmploymentService}: BusinessEmploymentComplaints.
 */
public final class BusinessEmploymentComplaints {

    private BusinessEmploymentComplaints() {
    }

    /**
     * Files one CEO complaint against an active employee.
     *
     * <p>Only the owner may file. The 24-hour cooldown is per owner, business and employee, so a
     * third complaint necessarily represents conduct on three separate days. Every complaint is
     * upheld when filed: this build has no separate adjudication action, and the product rule makes
     * the third one the automatic threshold. On the third, the contract is terminated and the fine
     * service creates a 64-gold AGGRAVATED fine whose restitution equals the wages actually paid
     * on that contract, credited to the CEO.
     */
    public static Outcome fileComplaint(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final UUID employeeUuid,
            final String employeeName,
            final String rawReason,
            final long now) {
        final BusinessRecord found = BusinessService.find(data, rawBusiness);
        if (found == null) {
            return Outcome.refused(
                    ErrorCode.BAD_BUSINESS, BusinessService.noSuchBusinessMessage(rawBusiness));
        }
        final BusinessRecord business = BusinessEmploymentIds.prepared(data, found, now);
        if (actor == null || actor.uuid() == null
                || !actor.uuid().equals(business.ownerUuid())) {
            return Outcome.refused(
                    ErrorCode.FORBIDDEN, "Only the CEO who owns the business may file a complaint.");
        }
        if (employeeUuid == null) {
            return Outcome.refused(ErrorCode.BAD_TARGET, "No employee to complain about.");
        }
        if (employeeUuid.equals(business.ownerUuid())) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "The CEO cannot complain against themselves.");
        }
        if (business.closed()) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE, "Business '" + business.name() + "' is closed.");
        }
        if (business.complaints().size() >= BusinessKeys.MAX_COMPLAINTS) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    "Business '" + business.name() + "' already has the maximum of "
                            + BusinessKeys.MAX_COMPLAINTS + " complaints.");
        }
        EmploymentContractRecord active = null;
        for (final EmploymentContractRecord contract : business.contracts()) {
            if (contract.status() == EmploymentStatus.ACTIVE
                    && employeeUuid.equals(contract.employeeUuid())) {
                if (active == null || (active.oneTime() && !contract.oneTime())) {
                    active = contract;
                }
                if (!active.oneTime()) {
                    break;
                }
            }
        }
        if (active == null) {
            return Outcome.refused(
                    ErrorCode.INVALID_STATE,
                    BusinessService.ownerName(data, employeeUuid)
                            + " has no active contract in '" + business.name() + "'.");
        }
        final String reason = Sanitizer.text(rawReason);
        if (reason.isBlank()) {
            return Outcome.refused(
                    ErrorCode.BAD_PAYLOAD,
                    "A complaint needs a reason of at most " + Sanitizer.MAX_TEXT + " characters.");
        }
        for (final BusinessComplaintRecord complaint : business.complaints()) {
            if (employeeUuid.equals(complaint.employeeUuid())
                    && actor.uuid().equals(complaint.filedByUuid())) {
                if (now < complaint.createdAt()
                        || now - complaint.createdAt() < BusinessKeys.COMPLAINT_COOLDOWN_MS) {
                    return Outcome.refused(
                            ErrorCode.RATE_LIMITED,
                            "One complaint per employee per 24 real hours. The next one may be filed"
                                    + " after the cooldown.");
                }
            }
        }
        int upheld = 0;
        for (final BusinessComplaintRecord complaint : business.complaints()) {
            if (complaint.upheld()
                    && employeeUuid.equals(complaint.employeeUuid())
                    && complaint.createdAt() >= active.startedAt()) {
                upheld++;
            }
        }
        final boolean threshold = upheld + 1 >= COMPLAINT_THRESHOLD;
        String fineId = "";
        EmploymentContractRecord terminated = null;
        if (threshold) {
            final FineService.Outcome fine = FineService.issueComplaintFine(
                    data, employeeUuid, employeeName, actor.uuid(), actor.name(),
                    business, active, reason, now);
            if (!fine.ok()) {
                return Outcome.refused(fine.code(), fine.message());
            }
            fineId = fine.fineId();
            terminated = active.withStatus(
                    EmploymentStatus.TERMINATED, now, "three upheld complaints");
        }
        final BusinessComplaintRecord complaint = new BusinessComplaintRecord(
                BusinessEmploymentIds.newComplaintId(business), employeeUuid, actor.uuid(), reason, now, true, fineId);
        final List<BusinessComplaintRecord> complaints = new ArrayList<>(business.complaints());
        complaints.add(complaint);
        BusinessRecord updated = business.withComplaints(complaints);
        if (terminated != null) {
            updated = updated.withContracts(BusinessEmploymentTree.replaceContract(updated, terminated));
        }
        data.putBusiness(updated);
        final String employee = BusinessService.ownerName(data, employeeUuid);
        HistoryService.appendAll(data,
                List.of(employeeUuid, actor.uuid()),
                HistoryKind.COMPLAINT_FILED,
                "Complaint " + complaint.id() + " was filed against " + employee + " at '"
                        + business.name() + "': " + reason,
                actor.uuid(), actor.name(), now, complaint.id());
        if (terminated != null) {
            HistoryService.appendAll(data,
                    List.of(employeeUuid, actor.uuid()),
                    HistoryKind.COMPLAINT_UPHELD,
                    "Third upheld complaint: the contract at '" + business.name()
                            + "' was terminated and fine " + fineId + " was issued.",
                    actor.uuid(), actor.name(), now, complaint.id());
            HistoryService.appendAll(data,
                    List.of(employeeUuid, actor.uuid()),
                    HistoryKind.CONTRACT_FIRED,
                    "Contract at '" + business.name() + "' ended after three upheld complaints.",
                    actor.uuid(), actor.name(), now, terminated.id());
        }
        return Outcome.ok(
                "filed complaint " + complaint.id() + " against " + employee + " at '"
                        + business.name() + "'"
                        + (threshold ? "; contract terminated and fine " + fineId + " issued."
                                : "."));
    }

    /** One page of complaints for a business, or the viewer's own across every business. */

    /** One page of complaints for a business, or the viewer's own across every business. */
    public static ComplaintPage complaintPage(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final int rawPage) {
        final List<ComplaintRow> all = new ArrayList<>();
        if (data != null && actor != null && actor.uuid() != null) {
            if (rawBusiness == null || rawBusiness.isBlank()) {
                for (final BusinessRecord business : data.businesses().values()) {
                    for (final BusinessComplaintRecord complaint : business.complaints()) {
                        if (actor.uuid().equals(complaint.employeeUuid())) {
                            all.add(new ComplaintRow(complaint,
                                    BusinessService.ownerName(data, complaint.employeeUuid())));
                        }
                    }
                }
            } else {
                final BusinessRecord business = BusinessService.find(data, rawBusiness);
                if (business != null) {
                    final Access access = BusinessEmploymentAccess.access(business, actor);
                    final boolean allRows = access != null
                            && (access.owner() || access.government());
                    for (final BusinessComplaintRecord complaint : business.complaints()) {
                        if (allRows || actor.uuid().equals(complaint.employeeUuid())) {
                            all.add(new ComplaintRow(complaint,
                                    BusinessService.ownerName(data, complaint.employeeUuid())));
                        }
                    }
                }
            }
        }
        all.sort(Comparator
                .comparingLong((ComplaintRow row) -> row.complaint().createdAt()).reversed()
                .thenComparing(row -> row.complaint().id()));
        final int total = all.size();
        final int pages = Math.max(1, (total + BusinessKeys.EMPLOYMENT_PAGE_SIZE - 1)
                / BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<ComplaintRow> rows = new ArrayList<>(BusinessKeys.EMPLOYMENT_PAGE_SIZE);
        final int start = page * BusinessKeys.EMPLOYMENT_PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + BusinessKeys.EMPLOYMENT_PAGE_SIZE); i++) {
            rows.add(all.get(i));
        }
        return new ComplaintPage(rows, total, page, pages);
    }

    // -------------------------------------------------------------- close/rename

    /** Requests a soft close. The status does not change until a government official decides. */

    /** The {@code BIZ_COMPLAINT_LIST} answer. */
    public static Map<String, String> complaintFields(
            final GovSavedData data,
            final Actor actor,
            final String rawBusiness,
            final int rawPage) {
        final ComplaintPage result = BusinessEmploymentComplaints.complaintPage(data, actor, rawBusiness, rawPage);
        final Map<String, String> fields = new LinkedHashMap<>();
        final BusinessRecord business = rawBusiness == null || rawBusiness.isBlank()
                ? null : BusinessService.find(data, rawBusiness);
        fields.put(BusinessKeys.KEY_BUSINESS, business == null ? "" : business.id());
        fields.put(BusinessKeys.NAME, business == null ? "" : business.name());
        fields.put(BusinessKeys.COMPLAINT_COUNT, Integer.toString(result.total()));
        fields.put(BusinessKeys.PAGE, Integer.toString(result.page()));
        fields.put(BusinessKeys.PAGES, Integer.toString(result.pages()));
        for (int i = 0; i < result.rows().size(); i++) {
            final ComplaintRow row = result.rows().get(i);
            final BusinessComplaintRecord complaint = row.complaint();
            final String prefix = BusinessKeys.COMPLAINT_PREFIX + i;
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_ID, complaint.id());
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_EMPLOYEE, row.employeeName());
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_EMPLOYEE_UUID,
                    complaint.employeeUuid().toString());
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_REASON, complaint.reason());
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_AT,
                    Long.toString(complaint.createdAt()));
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_UPHELD,
                    Boolean.toString(complaint.upheld()));
            fields.put(prefix + BusinessKeys.COMPLAINT_ROW_FINE, complaint.fineId());
        }
        return fields;
    }

    // ------------------------------------------------------------------ helpers

    /** The contract a payload names, restricted to one business when the payload supplied one. */

}
