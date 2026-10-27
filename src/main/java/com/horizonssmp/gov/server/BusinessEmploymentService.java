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

public final class BusinessEmploymentService {

    public static final long REAL_DAY_MS = 24L * 60L * 60L * 1000L;

    public static final int MAX_PAYROLL_DAYS_PER_RUN = 7;

    public static final int COMPLAINT_THRESHOLD = 3;

    public static final long COMPLAINT_FINE_GOLD = FineService.AGGRAVATED_GOLD;

    public static final String POSITION_ID_PREFIX = "pos-";

    public static final String CONTRACT_ID_PREFIX = "c-";

    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {

        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }

        public boolean can(final GovCapability capability) {
            return capability != null && capabilities.contains(capability);
        }
    }

    public record Outcome(boolean ok, ErrorCode code, String message) {

        public Outcome {
            message = message == null ? "" : message;
        }

        public static Outcome ok(final String message) {
            return new Outcome(true, null, message);
        }

        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message);
        }
    }

    public record PositionRow(BusinessPositionRecord position, int depth, boolean canEdit) {
    }

    public record PositionPage(List<PositionRow> rows, int total, int page, int pages) {

        public PositionPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record OfferRow(EmploymentContractRecord contract, String positionTitle) {
    }

    public record OfferPage(List<OfferRow> rows, int total, int page, int pages) {

        public OfferPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record ContractRow(EmploymentContractRecord contract, String positionTitle) {
    }

    public record ContractPage(List<ContractRow> rows, int total, int page, int pages) {

        public ContractPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record ComplaintRow(BusinessComplaintRecord complaint, String employeeName) {
    }

    public record ComplaintPage(List<ComplaintRow> rows, int total, int page, int pages) {

        public ComplaintPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record ContractRef(BusinessRecord business, EmploymentContractRecord contract) {
    }


    private BusinessEmploymentService() {
    }

    // Delegated implementations (behavior-preserving facades)

    public static BusinessRecord standingRole(final GovSavedData data, final UUID playerUuid) {
        return BusinessEmploymentIds.standingRole(data, playerUuid);
    }

    public static boolean canTakeStandingRole(final GovSavedData data, final UUID playerUuid) {
        return BusinessEmploymentIds.canTakeStandingRole(data, playerUuid);
    }

    public static boolean isStrictDescendant(final BusinessRecord business, final String positionId, final String ancestorId) {
        return BusinessEmploymentAccess.isStrictDescendant(business, positionId, ancestorId);
    }

    public static int depthOf(final BusinessRecord business, final String positionId) {
        return BusinessEmploymentAccess.depthOf(business, positionId);
    }

    public static boolean isCeoPosition(final BusinessPositionRecord position) {
        return BusinessEmploymentAccess.isCeoPosition(position);
    }

    public static boolean canView(final BusinessRecord business, final Actor actor, final Set<GovCapability> capabilities) {
        return BusinessEmploymentAccess.canView(business, actor, capabilities);
    }

    public static Outcome createPosition(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawTitle, final String rawParent, final String rawWage, final String rawPermissions, final String rawOrder, final long now) {
        return BusinessEmploymentPositionCreate.createPosition(data, actor, rawBusiness, rawTitle, rawParent, rawWage, rawPermissions, rawOrder, now);
    }

    public static Outcome editPosition(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawPosition, final String rawTitle, final String rawWage, final String rawPermissions, final String rawOrder, final long now) {
        return BusinessEmploymentPositionEdit.editPosition(data, actor, rawBusiness, rawPosition, rawTitle, rawWage, rawPermissions, rawOrder, now);
    }

    public static Outcome deletePosition(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawPosition, final long now) {
        return BusinessEmploymentPositionDelete.deletePosition(data, actor, rawBusiness, rawPosition, now);
    }

    public static Outcome movePosition(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawPosition, final String rawParent, final long now) {
        return BusinessEmploymentPositionMove.movePosition(data, actor, rawBusiness, rawPosition, rawParent, now);
    }

    public static Outcome sendOffer(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID employeeUuid, final String employeeName, final String rawPosition, final String rawWage, final String rawOneTime, final long now) {
        return BusinessEmploymentOfferSend.sendOffer(data, actor, rawBusiness, employeeUuid, employeeName, rawPosition, rawWage, rawOneTime, now);
    }

    public static OfferPage offerPage(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID employeeFilter, final int rawPage) {
        return BusinessEmploymentOfferReads.offerPage(data, actor, rawBusiness, employeeFilter, rawPage);
    }

    public static Outcome respondOffer(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawOffer, final boolean accept, final long now) {
        return BusinessEmploymentOfferRespond.respondOffer(data, actor, rawBusiness, rawOffer, accept, now);
    }

    public static Outcome endContract(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawContract, final String decision, final String rawReason, final long now) {
        return BusinessEmploymentContractMutations.endContract(data, actor, rawBusiness, rawContract, decision, rawReason, now);
    }

    public static Outcome promoteContract(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawContract, final String rawPosition, final long now) {
        return BusinessEmploymentContractMutations.promoteContract(data, actor, rawBusiness, rawContract, rawPosition, now);
    }

    public static ContractPage contractPage(final GovSavedData data, final Actor actor, final String rawBusiness, final int rawPage) {
        return BusinessEmploymentContractReads.contractPage(data, actor, rawBusiness, rawPage);
    }

    public static Outcome runPayroll(final GovSavedData data, final Actor actor, final String rawBusiness, final long now) {
        return BusinessEmploymentPayroll.runPayroll(data, actor, rawBusiness, now);
    }

    public static Outcome fileComplaint(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID employeeUuid, final String employeeName, final String rawReason, final long now) {
        return BusinessEmploymentComplaints.fileComplaint(data, actor, rawBusiness, employeeUuid, employeeName, rawReason, now);
    }

    public static ComplaintPage complaintPage(final GovSavedData data, final Actor actor, final String rawBusiness, final int rawPage) {
        return BusinessEmploymentComplaints.complaintPage(data, actor, rawBusiness, rawPage);
    }

    public static Outcome requestClose(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawReason, final long now) {
        return BusinessEmploymentClose.requestClose(data, actor, rawBusiness, rawReason, now);
    }

    public static Outcome approveClose(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawReason, final long now) {
        return BusinessEmploymentClose.approveClose(data, actor, rawBusiness, rawReason, now);
    }

    public static Outcome rejectClose(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawReason, final long now) {
        return BusinessEmploymentClose.rejectClose(data, actor, rawBusiness, rawReason, now);
    }

    public static Outcome closeDirect(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawReason, final long now) {
        return BusinessEmploymentClose.closeDirect(data, actor, rawBusiness, rawReason, now);
    }

    public static Outcome rename(final GovSavedData data, final Actor actor, final String rawBusiness, final String rawName, final long now) {
        return BusinessEmploymentRenameTransfer.rename(data, actor, rawBusiness, rawName, now);
    }

    public static Outcome transferOwnership(final GovSavedData data, final Actor actor, final BusinessRecord found, final UUID newOwnerUuid, final String newOwnerName, final long now) {
        return BusinessEmploymentRenameTransfer.transferOwnership(data, actor, found, newOwnerUuid, newOwnerName, now);
    }

    public static PositionPage positionPage(final GovSavedData data, final BusinessRecord rawBusiness, final Actor actor, final Set<GovCapability> capabilities, final long now, final int rawPage) {
        return BusinessEmploymentPositionReads.positionPage(data, rawBusiness, actor, capabilities, now, rawPage);
    }

    public static Map<String, String> positionFields(final GovSavedData data, final BusinessRecord business, final Actor actor, final Set<GovCapability> capabilities, final long now, final int rawPage) {
        return BusinessEmploymentPositionReads.positionFields(data, business, actor, capabilities, now, rawPage);
    }

    public static Map<String, String> offerFields(final GovSavedData data, final Actor actor, final String rawBusiness, final UUID employeeFilter, final int rawPage) {
        return BusinessEmploymentOfferReads.offerFields(data, actor, rawBusiness, employeeFilter, rawPage);
    }

    public static Map<String, String> contractFields(final GovSavedData data, final Actor actor, final String rawBusiness, final int rawPage) {
        return BusinessEmploymentContractReads.contractFields(data, actor, rawBusiness, rawPage);
    }

    public static Map<String, String> complaintFields(final GovSavedData data, final Actor actor, final String rawBusiness, final int rawPage) {
        return BusinessEmploymentComplaints.complaintFields(data, actor, rawBusiness, rawPage);
    }

    public static ContractRef findContract(final GovSavedData data, final String rawBusiness, final String rawContract) {
        return BusinessEmploymentContractReads.findContract(data, rawBusiness, rawContract);
    }

    public static String positionTitle(final BusinessRecord business, final EmploymentContractRecord contract) {
        return BusinessEmploymentPositionReads.positionTitle(business, contract);
    }

    public static Long parseWage(final String raw, final long fallback) {
        return BusinessEmploymentIds.parseWage(raw, fallback);
    }

    public static Integer parseOrder(final String raw, final int fallback) {
        return BusinessEmploymentIds.parseOrder(raw, fallback);
    }

    public static Set<BusinessPermission> parsePermissionsStrict(final String raw) {
        return BusinessEmploymentIds.parsePermissionsStrict(raw);
    }

    public static Boolean parseOneTime(final String raw) {
        return BusinessEmploymentIds.parseOneTime(raw);
    }

    public static boolean hasAnyActiveContract(final GovSavedData data, final UUID playerUuid) {
        return BusinessEmploymentContractReads.hasAnyActiveContract(data, playerUuid);
    }

    public static Map<String, String> positionPermissionMap(final BusinessRecord business, final EmploymentContractRecord contract) {
        return BusinessEmploymentPositionReads.positionPermissionMap(business, contract);
    }

}
