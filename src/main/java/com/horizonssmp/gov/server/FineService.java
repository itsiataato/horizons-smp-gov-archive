package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.records.FineRecord;

/**
 * Facade over the fine rules. Implementation lives in helper classes in this package.
 */
public final class FineService {

    public static final long PETTY_GOLD = FineConstants.PETTY_GOLD;
    public static final long MISDEMEANOR_GOLD = FineConstants.MISDEMEANOR_GOLD;
    public static final long FELONY_GOLD = FineConstants.FELONY_GOLD;
    public static final long AGGRAVATED_GOLD = FineConstants.AGGRAVATED_GOLD;
    public static final long MAX_RESTITUTION_GOLD = FineConstants.MAX_RESTITUTION_GOLD;
    public static final long MAX_COMPLAINT_RESTITUTION_GOLD = FineConstants.MAX_COMPLAINT_RESTITUTION_GOLD;
    public static final int MIN_EXILE_DAYS = FineConstants.MIN_EXILE_DAYS;
    public static final int MAX_EXILE_DAYS = FineConstants.MAX_EXILE_DAYS;
    public static final String DEFAULT_ARTICLE = FineConstants.DEFAULT_ARTICLE;
    public static final String FINE_ID_PREFIX = FineConstants.FINE_ID_PREFIX;
    public static final String RECEIPT_PREFIX = FineConstants.RECEIPT_PREFIX;
    public static final long COMPLAINT_FINE_GOLD = FineConstants.COMPLAINT_FINE_GOLD;

    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {
        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    public record Outcome(boolean ok, ErrorCode code, String message, String fineId) {
        public Outcome {
            message = message == null ? "" : message;
            fineId = fineId == null ? "" : fineId;
        }
        public static Outcome ok(final String message) { return new Outcome(true, null, message, ""); }
        public static Outcome ok(final String message, final String fineId) { return new Outcome(true, null, message, fineId); }
        public static Outcome refused(final ErrorCode code, final String message) { return new Outcome(false, code, message, ""); }
    }

    public record ReceptionPoint(String dimension, double x, double y, double z, double radius) {
        public ReceptionPoint {
            dimension = dimension == null ? "" : dimension.trim();
            radius = Math.max(0.0d, radius);
        }
        public boolean contains(final String otherDimension, final double px, final double py, final double pz) {
            if (dimension.isEmpty() || otherDimension == null || !dimension.equals(otherDimension)) {
                return false;
            }
            final double dx = px - x;
            final double dy = py - y;
            final double dz = pz - z;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }
    }

    public interface Gateway {
        long goldIngots(UUID player);
        boolean removeGoldIngots(UUID player, long count);
        boolean addGoldIngots(UUID player, long count);
        boolean present(UUID player);
        boolean atReception(UUID player);
    }

    public record FineRow(FineRecord fine, String targetName) {
    }

    public record Page(List<FineRow> rows, int total, int page, int pages, String filter) {
        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
            filter = filter == null ? FineKeys.FILTER_ALL : filter;
        }
    }

    private FineService() {
    }

    public static long bandGold(final FineBand band) { return FineBands.bandGold(band); }
    public static FineBand parseBand(final String raw) { return FineBands.parseBand(raw); }
    public static String canonicalFilter(final String raw) { return FineReads.canonicalFilter(raw); }

    public static Optional<ReceptionPoint> configuredReception() { return FineReception.configuredReception(); }
    public static String receptionMissingMessage() { return FineReception.receptionMissingMessage(); }
    public static String notAtReceptionMessage() { return FineReception.notAtReceptionMessage(); }

    public static Outcome issue(
            final GovSavedData data, final Actor actor, final UUID targetUuid, final String targetName,
            final String bandText, final String articleText, final String restitutionText,
            final String victimText, final String exileText, final String rawReason, final long now) {
        return FineIssue.issue(data, actor, targetUuid, targetName, bandText, articleText, restitutionText, victimText, exileText, rawReason, now);
    }
    public static Outcome issuePrecheck(
            final String bandText, final String articleText, final String restitutionText,
            final String victimText, final String exileText, final String rawReason) {
        return FineIssue.issuePrecheck(bandText, articleText, restitutionText, victimText, exileText, rawReason);
    }
    public static Outcome issueComplaintFine(
            final GovSavedData data,
            final UUID targetUuid,
            final String targetDisplayName,
            final UUID issuerUuid,
            final String issuerDisplayName,
            final com.horizonssmp.gov.common.records.BusinessRecord business,
            final com.horizonssmp.gov.common.records.EmploymentContractRecord contract,
            final String rawReason,
            final long now) {
        return FineIssue.issueComplaintFine(data, targetUuid, targetDisplayName, issuerUuid, issuerDisplayName, business, contract, rawReason, now);
    }

    public static Outcome paySelf(
            final GovSavedData data, final Actor actor, final String rawFineId,
            final Gateway gateway, final Optional<ReceptionPoint> reception, final long now) {
        return FinePayment.paySelf(data, actor, rawFineId, gateway, reception, now);
    }
    public static Outcome collect(
            final GovSavedData data, final Actor actor, final String rawFineId,
            final UUID payerUuid, final String payerName, final Gateway gateway,
            final Optional<ReceptionPoint> reception, final long now) {
        return FinePayment.collect(data, actor, rawFineId, payerUuid, payerName, gateway, reception, now);
    }
    public static Outcome waive(
            final GovSavedData data, final Actor actor, final String rawFineId,
            final String rawReason, final long now) {
        return FineWaive.waive(data, actor, rawFineId, rawReason, now);
    }

    public static boolean canViewAll(final Set<GovCapability> capabilities) { return FineLookups.canViewAll(capabilities); }
    public static FineRecord visibleFine(
            final GovSavedData data, final UUID viewerUuid,
            final Set<GovCapability> capabilities, final String rawFineId) {
        return FineLookups.visibleFine(data, viewerUuid, capabilities, rawFineId);
    }
    public static Page page(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final String rawFilter, final UUID targetFilter, final int rawPage) {
        return FineReads.page(data, viewerUuid, capabilities, rawFilter, targetFilter, rawPage);
    }
    public static Map<String, String> listFields(
            final GovSavedData data, final Actor viewer, final Set<GovCapability> capabilities,
            final String rawFilter, final UUID targetFilter, final int rawPage,
            final List<String> onlineNames) {
        return FineReads.listFields(data, viewer, capabilities, rawFilter, targetFilter, rawPage, onlineNames);
    }
    public static Map<String, String> detailFields(
            final GovSavedData data, final FineRecord fine,
            final Set<GovCapability> capabilities, final UUID viewerUuid) {
        return FineReads.detailFields(data, fine, capabilities, viewerUuid);
    }

    public static Outcome finePrecheck(final GovSavedData data, final String rawFineId) { return FineLookups.finePrecheck(data, rawFineId); }
    public static Outcome payPrecheck(
            final GovSavedData data, final UUID payerUuid, final String rawFineId) { return FineLookups.payPrecheck(data, payerUuid, rawFineId); }
    public static Outcome waivePrecheck(
            final GovSavedData data, final String rawFineId, final String rawReason) { return FineLookups.waivePrecheck(data, rawFineId, rawReason); }
    public static String noSuchFineMessage(final String rawFineId) { return FineLookups.noSuchFineMessage(rawFineId); }
    public static FineRecord find(final GovSavedData data, final String rawFineId) { return FineLookups.find(data, rawFineId); }
    public static String targetName(final GovSavedData data, final UUID uuid) { return FineLookups.targetName(data, uuid); }
}
