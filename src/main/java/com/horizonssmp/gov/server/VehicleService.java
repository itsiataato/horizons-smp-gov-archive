package com.horizonssmp.gov.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.ImpoundReason;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.enums.ScanResult;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.config.GovConfig;

public final class VehicleService {

    public static final long DEFAULT_RELEASE_FEE_GOLD = 8L;
    public static final long SUSPENSION_MS = GovTime.days(7);
    public static final long DISPOSAL_AFTER_MS = GovTime.days(30);
    public static final String VEHICLE_ID_PREFIX = "v-";
    public static final String SCAN_ID_PREFIX = "s-";
    public static final String AUTO_SUSPENSION_REASON =
            "12 points within 30 in-game days (Article 6.09)";

    private VehicleService() {
    }

    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {
        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    public record Outcome(boolean ok, ErrorCode code, String message, String vehicleId) {
        public Outcome {
            message = message == null ? "" : message;
            vehicleId = vehicleId == null ? "" : vehicleId;
        }
        public static Outcome ok(final String message) {
            return new Outcome(true, null, message, "");
        }
        public static Outcome ok(final String message, final String vehicleId) {
            return new Outcome(true, null, message, vehicleId);
        }
        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message, "");
        }
    }

    public record VehicleRow(
            VehicleRecord vehicle,
            String ownerName,
            LicenseRecord license,
            LicenseStatus licenseState,
            int points) {
    }

    public record Page(List<VehicleRow> rows, int total, int page, int pages) {
        public Page {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record ScanView(
            String scanId,
            String plate,
            ScanResult result,
            String ownerName,
            String vehClass,
            String status,
            boolean impoundActive,
            String impoundReason,
            String licenseState,
            int licensePoints,
            long at,
            String source) {
        public ScanView {
            scanId = scanId == null ? "" : scanId;
            plate = plate == null ? "" : plate;
            result = result == null ? ScanResult.UNREGISTERED : result;
            ownerName = ownerName == null ? "" : ownerName;
            vehClass = vehClass == null ? "" : vehClass;
            status = status == null ? "" : status;
            impoundReason = impoundReason == null ? "" : impoundReason;
            licenseState = licenseState == null ? LicenseKeys.STATE_NONE : licenseState;
            source = source == null ? "" : source;
        }
    }

    public record Scan(boolean ok, ErrorCode code, String message, ScanView view) {
        public Scan {
            message = message == null ? "" : message;
        }
        public static Scan ok(final ScanView view) {
            return new Scan(true, null, "", view);
        }
        public static Scan refused(final ErrorCode code, final String message) {
            return new Scan(false, code, message, null);
        }
    }

    public interface VehicleAdapter {
        String source();
        Optional<Presence> presence(String canonicalPlate);
        record Presence(String description) {
            public Presence {
                description = description == null ? "" : description;
            }
        }
    }

    public static final VehicleAdapter REGISTRY_ONLY = new VehicleAdapter() {
        @Override
        public String source() {
            return "registry-only";
        }
        @Override
        public Optional<Presence> presence(final String canonicalPlate) {
            return Optional.empty();
        }
    };

    // plates
    public static String canonicalPlate(final String raw) { return VehiclePlates.canonicalPlate(raw); }
    public static String invalidPlateMessage(final String raw) { return VehiclePlates.invalidPlateMessage(raw); }
    public static VehicleRecord byPlate(final GovSavedData data, final String rawPlate) { return VehiclePlates.byPlate(data, rawPlate); }
    public static VehicleRecord find(final GovSavedData data, final String raw) { return VehiclePlates.find(data, raw); }
    public static String noSuchVehicleMessage(final String raw) { return VehiclePlates.noSuchVehicleMessage(raw); }

    // lookups
    public static String ownerName(final GovSavedData data, final UUID ownerUuid) { return VehicleLookups.ownerName(data, ownerUuid); }
    public static LicenseRecord licenseFor(final GovSavedData data, final UUID ownerUuid, final LicenseClass requested, final long now) { return VehicleLookups.licenseFor(data, ownerUuid, requested, now); }
    public static String licenseState(final GovSavedData data, final UUID ownerUuid, final LicenseClass licenseClass, final long now) { return VehicleLookups.licenseState(data, ownerUuid, licenseClass, now); }
    public static int licensePoints(final GovSavedData data, final UUID ownerUuid, final LicenseClass licenseClass, final long now) { return VehicleLookups.licensePoints(data, ownerUuid, licenseClass, now); }
    public static String summary(final GovSavedData data, final UUID playerUuid, final long now) { return VehicleLookups.summary(data, playerUuid, now); }
    public static int parsePoints(final String raw) { return VehicleLookups.parsePoints(raw); }

    // registration
    public static Outcome register(final GovSavedData data, final Actor actor, final UUID ownerUuid, final String enteredOwnerName, final String rawPlate, final String rawClass, final boolean requireLicense, final long now) {
        return VehicleRegistration.register(data, actor, ownerUuid, enteredOwnerName, rawPlate, rawClass, requireLicense, now);
    }
    public static Outcome transfer(final GovSavedData data, final Actor actor, final String rawPlate, final UUID newOwnerUuid, final String newOwnerName, final boolean requireLicense, final long now) {
        return VehicleRegistration.transfer(data, actor, rawPlate, newOwnerUuid, newOwnerName, requireLicense, now);
    }

    // licenses
    public static Outcome issueLicense(final GovSavedData data, final Actor actor, final UUID targetUuid, final String targetName, final String rawClass, final long now) {
        return VehicleLicenses.issueLicense(data, actor, targetUuid, targetName, rawClass, now);
    }
    public static Outcome revokeLicense(final GovSavedData data, final Actor actor, final UUID targetUuid, final String targetName, final String rawClass, final String rawReason, final long now) {
        return VehicleLicenses.revokeLicense(data, actor, targetUuid, targetName, rawClass, rawReason, now);
    }
    public static Outcome assessPoints(final GovSavedData data, final Actor actor, final UUID targetUuid, final String targetName, final String rawClass, final String rawPoints, final String rawReason, final long now) {
        return VehicleLicenses.assessPoints(data, actor, targetUuid, targetName, rawClass, rawPoints, rawReason, now);
    }
    public static Outcome licensePrecheck(final GovSavedData data, final UUID targetUuid, final String rawClass) {
        return VehicleLicenses.licensePrecheck(data, targetUuid, rawClass);
    }
    public static String noSuchLicenseMessage(final GovSavedData data, final UUID targetUuid, final String targetName, final LicenseClass licenseClass) {
        return VehicleLicenses.noSuchLicenseMessage(data, targetUuid, targetName, licenseClass);
    }

    // impound
    public static Outcome impound(final GovSavedData data, final Actor actor, final String rawPlate, final String rawReason, final LocationRecord location, final long now) {
        return VehicleImpound.impound(data, actor, rawPlate, rawReason, location, now);
    }
    public static Outcome release(final GovSavedData data, final Actor actor, final String rawPlate, final FineService.Gateway gateway, final Optional<FineService.ReceptionPoint> reception, final long now) {
        return VehicleImpound.release(data, actor, rawPlate, gateway, reception, now);
    }
    public static int syncDisposalFlags(final GovSavedData data, final long now) {
        return VehicleImpound.syncDisposalFlags(data, now);
    }

    // reads
    public static boolean canViewAll(final Set<GovCapability> capabilities) { return VehicleReads.canViewAll(capabilities); }
    public static Page page(final GovSavedData data, final UUID viewerUuid, final Set<GovCapability> capabilities, final String rawFilter, final UUID ownerFilter, final int rawPage, final long now) {
        return VehicleReads.page(data, viewerUuid, capabilities, rawFilter, ownerFilter, rawPage, now);
    }
    public static String canonicalFilter(final String raw) { return VehicleReads.canonicalFilter(raw); }
    public static Map<String, String> listFields(final GovSavedData data, final UUID viewerUuid, final String viewerName, final Set<GovCapability> capabilities, final String rawFilter, final UUID ownerFilter, final int page, final List<String> onlineNames, final long now) {
        return VehicleReads.listFields(data, viewerUuid, viewerName, capabilities, rawFilter, ownerFilter, page, onlineNames, now);
    }
    public static Map<String, String> detailFields(final GovSavedData data, final VehicleRecord vehicle, final Set<GovCapability> capabilities, final long now) {
        return VehicleReads.detailFields(data, vehicle, capabilities, now);
    }

    // scans
    public static Scan scan(final GovSavedData data, final Actor actor, final String rawPlate, final VehicleAdapter adapter, final long now, final boolean record) {
        return VehicleScans.scan(data, actor, rawPlate, adapter, now, record);
    }
    public static Map<String, String> scanFields(final ScanView view) { return VehicleScans.scanFields(view); }

    // prechecks
    public static Outcome platePrecheck(final String rawPlate) { return VehiclePrechecks.platePrecheck(rawPlate); }
    public static Outcome vehiclePrecheck(final GovSavedData data, final String rawPlate) { return VehiclePrechecks.vehiclePrecheck(data, rawPlate); }
    public static Outcome registerPrecheck(final GovSavedData data, final String rawPlate, final String rawClass) { return VehicleRegistration.registerPrecheck(data, rawPlate, rawClass); }
    public static Outcome impoundPrecheck(final GovSavedData data, final String rawPlate, final String rawReason) { return VehicleImpound.impoundPrecheck(data, rawPlate, rawReason); }
    public static Outcome releasePrecheck(final GovSavedData data, final String rawPlate) { return VehicleImpound.releasePrecheck(data, rawPlate); }
}