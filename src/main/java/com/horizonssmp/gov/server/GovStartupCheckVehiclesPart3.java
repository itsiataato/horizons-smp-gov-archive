package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.enums.ScanResult;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.PlateScanRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.config.GovConfig;

final class GovStartupCheckVehiclesPart3 {

    private GovStartupCheckVehiclesPart3() {
    }

    static void run(final GovStartupCheckVehiclesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final VehicleService.Actor citizen = ctx.citizen;
        final VehicleService.Actor registrar = ctx.registrar;
        final VehicleService.Actor scanner = ctx.scanner;
        final VehicleRecord heldCar = ctx.heldCar;
        final VehicleRecord releasedCar = ctx.releasedCar;
        final long afterWindow = now + LicenseRecord.POINT_WINDOW_MS + 1L;

            // Disposal: the 30-day mark is a flag, and the vehicle record is still there.
            ctx.checks++;
            if (!VehicleService.issueLicense(
                    scratch, registrar, carol, "StartupCheck Vehicle Carol", "car", now).ok()
                    || !VehicleService.register(scratch, registrar, carol,
                            "StartupCheck Vehicle Carol", "FLG-123", "car", true, now).ok()) {
                failures.add("could not set up the disposal vehicle");
            }
            final long disposalNow = now + 5_000_000L;
            ctx.checks++;
            if (!VehicleService.impound(scratch, registrar, "FLG123",
                    "StartupCheck blocking the road", com.horizonssmp.gov.common.records.LocationRecord.NONE, disposalNow).ok()) {
                failures.add("could not impound the disposal vehicle");
            }
            ctx.checks++;
            if (VehicleService.syncDisposalFlags(
                    scratch, disposalNow + VehicleService.DISPOSAL_AFTER_MS - 1L) != 0
                    || VehicleService.byPlate(scratch, "FLG123").status()
                            != VehicleStatus.IMPOUNDED) {
                failures.add("a vehicle was flagged for disposal before 30 in-game days");
            }
            ctx.checks++;
            final int flagged = VehicleService.syncDisposalFlags(
                    scratch, disposalNow + VehicleService.DISPOSAL_AFTER_MS);
            final VehicleRecord flaggedCar = VehicleService.byPlate(scratch, "FLG123");
            if (flagged != 1 || flaggedCar == null
                    || flaggedCar.status() != VehicleStatus.FLAGGED_FOR_DISPOSAL
                    || !flaggedCar.impound().flaggedForDisposal()
                    || flaggedCar.impound().disposalFlaggedAt() <= 0L) {
                failures.add("the 30-day disposal mark was not set, or the record was deleted");
            }
            ctx.checks++;
            if (VehicleService.syncDisposalFlags(
                    scratch, disposalNow + VehicleService.DISPOSAL_AFTER_MS + 1L) != 0) {
                failures.add("the disposal mark was not idempotent");
            }

            // Scans: registry only, no mutation of the vehicle, rate limiting is the dispatcher's
            // job and the capability check is the service's.
            ctx.checks++;
            final VehicleService.Scan unregistered = VehicleService.scan(
                    scratch, scanner, "ZZZ-999", VehicleService.REGISTRY_ONLY, now, false);
            if (!unregistered.ok()
                    || unregistered.view().result() != ScanResult.UNREGISTERED
                    || !unregistered.view().scanId().isEmpty()) {
                failures.add("an unregistered plate did not scan as UNREGISTERED");
            }
            ctx.checks++;
            if (VehicleService.scan(scratch, scanner, "", VehicleService.REGISTRY_ONLY,
                    now, false).code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a scan accepted a plate outside the law's format");
            }
            ctx.checks++;
            final VehicleService.Scan suspendedScan = VehicleService.scan(
                    scratch, scanner, "ABC-123", VehicleService.REGISTRY_ONLY, now, false);
            if (!suspendedScan.ok()
                    || suspendedScan.view().result() != ScanResult.LICENSE_INVALID
                    || !"SUSPENDED".equals(suspendedScan.view().licenseState())
                    || suspendedScan.view().licensePoints() != 14
                    || !"StartupCheck Vehicle Alice".equals(suspendedScan.view().ownerName())) {
                failures.add("a scan did not report the suspended owner's licence state");
            }
            ctx.checks++;
            if (VehicleService.scan(scratch, citizen, "ABC123", VehicleService.REGISTRY_ONLY,
                    now, false).code() != ErrorCode.FORBIDDEN) {
                failures.add("a scan did not refuse an actor without plate.scan");
            }
            ctx.checks++;
            final int scansBefore = scratch.plateScans().size();
            final VehicleService.Scan recorded = VehicleService.scan(
                    scratch, scanner, "ABC123", VehicleService.REGISTRY_ONLY, now, true);
            if (!recorded.ok() || recorded.view().scanId().isEmpty()
                    || scratch.plateScans().size() != scansBefore + 1) {
                failures.add("a player scan did not write a plate-scan record");
            }
            ctx.checks++;
            final VehicleService.Scan consoleScan = VehicleService.scan(
                    scratch, scanner, "ABC123", VehicleService.REGISTRY_ONLY, now, false);
            if (!consoleScan.ok() || !consoleScan.view().scanId().isEmpty()
                    || scratch.plateScans().size() != scansBefore + 1) {
                failures.add("a console scan wrote a record or lost its answer");
            }
            ctx.checks++;
            final Map<String, String> scanFields =
                    VehicleService.scanFields(suspendedScan.view());
            if (!"ABC123".equals(scanFields.get(VehicleKeys.SCAN_PLATE))
                    || !"LICENSE_INVALID".equals(scanFields.get(VehicleKeys.SCAN_RESULT))
                    || !"car".equals(scanFields.get(VehicleKeys.SCAN_CLASS))
                    || !"registry-only".equals(scanFields.get(VehicleKeys.SCAN_SOURCE))) {
                failures.add("the scan field block does not carry the registry answer");
            }
            ctx.checks++;
            final VehicleService.Scan validAgain = VehicleService.scan(
                    scratch, scanner, "ABC123", VehicleService.REGISTRY_ONLY, afterWindow, false);
            if (!validAgain.ok() || validAgain.view().result() != ScanResult.VALID) {
                failures.add("a scan after the points window did not return VALID");
            }
            ctx.checks++;
            final VehicleService.Scan flaggedScan = VehicleService.scan(
                    scratch, scanner, "FLG123", VehicleService.REGISTRY_ONLY,
                    disposalNow + VehicleService.DISPOSAL_AFTER_MS, false);
            if (!flaggedScan.ok() || flaggedScan.view().result() != ScanResult.DISPOSAL_FLAGGED) {
                failures.add("a scan of a flagged impound did not report DISPOSAL_FLAGGED");
            }

            // Registry reads: scope, filters, bounded page and the detail fee/impound fields.
            ctx.checks++;
            final VehicleService.Page bobPage = VehicleService.page(
                    scratch, bob, EnumSet.noneOf(GovCapability.class),
                    VehicleKeys.FILTER_ALL, null, 0, now);
            boolean bobOnly = true;
            for (final VehicleService.VehicleRow row : bobPage.rows()) {
                if (!bob.equals(row.vehicle().ownerUuid())) {
                    bobOnly = false;
                }
            }
            if (bobPage.total() != 1 || !bobOnly) {
                failures.add("a citizen's vehicle page was not scoped to their own registrations");
            }
            ctx.checks++;
            final VehicleService.Page allPage = VehicleService.page(
                    scratch, registrar.uuid(), registrar.capabilities(),
                    VehicleKeys.FILTER_ALL, null, 0, now);
            if (allPage.total() < 3 || allPage.rows().size() > VehicleKeys.PAGE_SIZE) {
                failures.add("the registry page did not carry the true total within the page cap");
            }
            ctx.checks++;
            final VehicleService.Page impoundedPage = VehicleService.page(
                    scratch, registrar.uuid(), registrar.capabilities(),
                    VehicleKeys.FILTER_IMPOUNDED, null, 0, now);
            if (impoundedPage.total() != 1) {
                failures.add("the impounded filter did not find exactly the held vehicle");
            }
            ctx.checks++;
            final VehicleService.Page flaggedPage = VehicleService.page(
                    scratch, registrar.uuid(), registrar.capabilities(),
                    VehicleKeys.FILTER_FLAGGED, null, 0, now);
            if (flaggedPage.total() != 1) {
                failures.add("the flagged filter did not find exactly the disposal mark");
            }
            ctx.checks++;
            final Map<String, String> listFields = VehicleService.listFields(
                    scratch, registrar.uuid(), registrar.name(), registrar.capabilities(),
                    VehicleKeys.FILTER_ALL, null, 0, List.of("StartupCheck Vehicle Alice"), now);
            if (!"true".equals(listFields.get(VehicleKeys.CAN_REGISTER))
                    || !"true".equals(listFields.get(VehicleKeys.CAN_MANAGE_LICENSES))
                    || !"true".equals(listFields.get(VehicleKeys.CAN_IMPOUND))
                    || !Long.toString(GovConfig.impoundReleaseFeeGold())
                            .equals(listFields.get(VehicleKeys.RELEASE_FEE))
                    || !"StartupCheck Vehicle Alice".equals(
                            listFields.get(VehicleKeys.ONLINE_PREFIX + "0"))) {
                failures.add("the vehicle list flags, fee or owner picker are wrong");
            }
            ctx.checks++;
            final Map<String, String> releasedDetail = VehicleService.detailFields(
                    scratch, releasedCar, registrar.capabilities(), now);
            if (!"false".equals(releasedDetail.get(VehicleKeys.IMPOUND_ACTIVE))
                    || !releasedDetail.get(VehicleKeys.RELEASED_AT).equals(Long.toString(now))
                    || !releasedDetail.get(VehicleKeys.LICENSE_STATE).equals("VALID")
                    || !releasedDetail.get(VehicleKeys.FEE)
                            .equals(Long.toString(heldCar.impound().releaseFeeGold()))) {
                failures.add("the released vehicle detail does not carry the release and licence facts");
            }
            ctx.checks++;
            final Map<String, String> flaggedDetail = VehicleService.detailFields(
                    scratch, flaggedCar, registrar.capabilities(),
                    disposalNow + VehicleService.DISPOSAL_AFTER_MS);
            if (!"true".equals(flaggedDetail.get(VehicleKeys.DISPOSAL))
                    || !"true".equals(flaggedDetail.get(VehicleKeys.IMPOUND_ACTIVE))
                    || flaggedDetail.get(VehicleKeys.IMPOUND_REASON).isBlank()) {
                failures.add("the flagged vehicle detail does not carry the disposal mark and reason");
            }


    }
}
