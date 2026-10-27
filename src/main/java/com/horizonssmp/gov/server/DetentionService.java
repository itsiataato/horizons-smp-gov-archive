package com.horizonssmp.gov.server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.enums.LegalBasis;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.config.GovConfig;
import net.minecraft.server.level.ServerPlayer;
/**
 * Facade over the detention rules. Implementation lives in helper classes in this package.
 */
public final class DetentionService {
    public static final String ARTICLE_DETENTION = "9.12";
    private DetentionService() {
    }
    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {
        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
        public boolean can(final GovCapability capability) {
            return capability != null && capabilities.contains(capability);
        }
    }
    public record Outcome(boolean ok, ErrorCode code, String message, String detentionId) {
        public Outcome {
            message = message == null ? "" : message;
            detentionId = detentionId == null ? "" : detentionId;
        }
        public static Outcome ok(final String message, final String detentionId) {
            return new Outcome(true, null, message, detentionId);
        }
        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message, "");
        }
    }
    public static java.util.List<LocationRecord> allCells(final GovSavedData data) {
        return DetentionQueries.allCells(data);
    }
    public record Policy(
            int noWarrantMinutes,
            int warrantMinutes,
            boolean nativeCuffs,
            boolean nativeFallback,
            String pluginCommand,
            LocationRecord cell,
            double leashRadius) {
        public Policy {
            noWarrantMinutes = Math.max(1,
                    Math.min(noWarrantMinutes, JailKeys.HARD_MAX_NO_WARRANT_MINUTES));
            warrantMinutes = Math.max(1,
                    Math.min(warrantMinutes, JailKeys.HARD_MAX_WARRANT_MINUTES));
            pluginCommand = pluginCommand == null ? "" : pluginCommand.strip();
            cell = cell == null ? LocationRecord.NONE : cell;
            leashRadius = Math.max(1.0d, leashRadius);
        }
        public static Policy fromConfig() {
            final java.util.List<LocationRecord> cells = GovConfig.jailCells();
            final LocationRecord cell = cells.isEmpty() ? LocationRecord.NONE : cells.get(0);
            return new Policy(
                    GovConfig.maxDetentionMinutesNoWarrant(),
                    GovConfig.maxDetentionMinutesWithWarrant(),
                    GovConfig.nativeCuffs(),
                    GovConfig.nativeFallback(),
                    GovConfig.jailPluginCommand(),
                    cell,
                    GovConfig.jailCellLeashRadius());
        }
        public boolean cellConfigured() {
            return cell.isSet();
        }
        public JailAdapter.Kind adapterKind() {
            return JailAdapter.select(pluginCommand, nativeFallback, cellConfigured());
        }
        public JailAdapter adapter() {
            return JailAdapter.create(pluginCommand, nativeFallback, cellConfigured(), leashRadius);
        }
    }
    public record Row(DetentionRecord record) {
    }
    public record Page(List<Row> rows, int total, int activeTotal, int page, int pages) {
        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
    public static boolean canViewAll(final Set<GovCapability> capabilities) {
        return DetentionQueries.canViewAll(capabilities);
    }
    public static String noActiveDetentionMessage(final String name) {
        return DetentionQueries.noActiveDetentionMessage(name);
    }
    public static DetentionRecord find(final GovSavedData data, final String rawId) {
        return DetentionQueries.find(data, rawId);
    }
    public static DetentionRecord activeRecord(final GovSavedData data, final UUID targetUuid) {
        return DetentionQueries.activeRecord(data, targetUuid);
    }
    public static DetentionRecord latestRecord(final GovSavedData data, final UUID targetUuid) {
        return DetentionQueries.latestRecord(data, targetUuid);
    }
    public static DetentionRecord visibleRecord(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final UUID targetUuid) {
        return DetentionQueries.visibleRecord(data, viewerUuid, capabilities, targetUuid);
    }
    public static DetentionRecord visibleRecord(
            final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities,
            final String rawId) {
        return DetentionQueries.visibleRecord(data, viewerUuid, capabilities, rawId);
    }
    public static boolean isCuffed(
            final GovSavedData data, final UUID player, final long now, final boolean nativeCuffs) {
        return DetentionQueries.isCuffed(data, player, now, nativeCuffs);
    }
    public static boolean isNativeConfined(
            final GovSavedData data, final UUID player, final long now, final Policy policy) {
        return DetentionQueries.isNativeConfined(data, player, now, policy);
    }
    public static Outcome detain(
            final GovSavedData data,
            final Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawBasis,
            final String rawWarrantRef,
            final String rawMinutes,
            final String rawReason,
            final String rawConsent,
            final JailGateway gateway,
            final Policy policy,
            final long now) {
        return DetentionDetain.detain(data, actor, targetUuid, targetName, rawBasis, rawWarrantRef,
                rawMinutes, rawReason, rawConsent, gateway, policy, now);
    }
    public static Outcome putInCell(
            final GovSavedData data,
            final Actor actor,
            final UUID targetUuid,
            final String targetName,
            final int rawCell,
            final JailGateway gateway,
            final Policy policy,
            final long now) {
        return DetentionMutations.putInCell(data, actor, targetUuid, targetName, rawCell, gateway, policy, now);
    }
    public static Outcome release(
            final GovSavedData data,
            final Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawReason,
            final JailGateway gateway,
            final Policy policy,
            final long now) {
        return DetentionMutations.release(data, actor, targetUuid, targetName, rawReason, gateway, policy, now);
    }
    public static Outcome extend(
            final GovSavedData data,
            final Actor actor,
            final UUID targetUuid,
            final String targetName,
            final String rawMinutes,
            final String rawReason,
            final JailGateway gateway,
            final Policy policy,
            final long now) {
        return DetentionMutations.extend(data, actor, targetUuid, targetName, rawMinutes, rawReason, gateway, policy, now);
    }
    public static Outcome cuff(
            final GovSavedData data,
            final Actor actor,
            final UUID targetUuid,
            final String targetName,
            final JailGateway gateway,
            final Policy policy,
            final long now) {
        return DetentionMutations.cuff(data, actor, targetUuid, targetName, gateway, policy, now);
    }
    public static Outcome uncuff(
            final GovSavedData data,
            final Actor actor,
            final UUID targetUuid,
            final String targetName,
            final JailGateway gateway,
            final Policy policy,
            final long now) {
        return DetentionMutations.uncuff(data, actor, targetUuid, targetName, gateway, policy, now);
    }
    public static List<DetentionRecord> tick(
            final GovSavedData data, final JailGateway gateway, final Policy policy, final long now) {
        return DetentionTimer.tick(data, gateway, policy, now);
    }
    public static List<DetentionRecord> expireDue(
            final GovSavedData data, final JailGateway gateway, final Policy policy, final long now) {
        return DetentionTimer.expireDue(data, gateway, policy, now);
    }
    public static void reapplyOnJoin(
            final ServerPlayer player, final GovSavedData data, final JailGateway gateway,
            final Policy policy, final long now) {
        DetentionTimer.reapplyOnJoin(player, data, gateway, policy, now);
    }
    public static Outcome releasePrecheck(
            final GovSavedData data, final UUID targetUuid, final String targetName) {
        return DetentionTimer.releasePrecheck(data, targetUuid, targetName);
    }
    public static String canonicalFilter(final String raw) {
        return DetentionViews.canonicalFilter(raw);
    }
    public static Page page(
            final GovSavedData data,
            final UUID viewerUuid,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID targetFilter,
            final int rawPage,
            final long now) {
        return DetentionViews.page(data, viewerUuid, capabilities, rawFilter, targetFilter, rawPage, now);
    }
    public static Map<String, String> listFields(
            final GovSavedData data,
            final UUID viewerUuid,
            final String viewerName,
            final Set<GovCapability> capabilities,
            final String rawFilter,
            final UUID targetFilter,
            final int page,
            final List<String> onlineNames,
            final Policy policy,
            final long now) {
        return DetentionViews.listFields(data, viewerUuid, viewerName, capabilities, rawFilter, targetFilter, page, onlineNames, policy, now);
    }
    public static Map<String, String> detailFields(
            final GovSavedData data,
            final DetentionRecord record,
            final Set<GovCapability> capabilities,
            final UUID viewerUuid,
            final Policy policy,
            final long now) {
        return DetentionViews.detailFields(data, record, capabilities, viewerUuid, policy, now);
    }
    public static List<String> filters() {
        return DetentionViews.filters();
    }
    public static LegalBasis parseBasis(final String raw) {
        return DetentionSupport.parseBasis(raw);
    }
    public static String basisId(final LegalBasis basis) {
        return DetentionSupport.basisId(basis);
    }
    public static String basisLabel(final LegalBasis basis) {
        return DetentionSupport.basisLabel(basis);
    }
    public static boolean canExtend(final DetentionRecord record, final Policy policy) {
        return DetentionSupport.canExtend(record, policy);
    }
    public static String remainingText(final long remainingMs) {
        return DetentionSupport.remainingText(remainingMs);
    }
    public static String formatCell(final LocationRecord cell) {
        return DetentionSupport.formatCell(cell);
    }
}
