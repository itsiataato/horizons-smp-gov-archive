package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.config.GovConfig;

final class GovStartupCheckVehiclesPart2 {

    private GovStartupCheckVehiclesPart2() {
    }

    static void run(final GovStartupCheckVehiclesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID dave = ctx.dave;
        final VehicleService.Actor registrar = ctx.registrar;
        final VehicleService.Actor citizen = ctx.citizen;

            // The policy-off path: an owner between licences may still be recorded as owner.
            ctx.checks++;
            if (!VehicleService.register(
                    scratch, registrar, bob, "StartupCheck Vehicle Bob",
                    "XYZ-999", "car", false, now).ok()) {
                failures.add("registration with the licence policy off was refused");
            }
            ctx.checks++;
            if (VehicleService.transfer(
                    scratch, registrar, "XYZ999", bob, "StartupCheck Vehicle Bob", true, now).ok()) {
                failures.add("a transfer was accepted to an owner with no valid licence");
            }
            ctx.checks++;
            if (!VehicleService.issueLicense(
                    scratch, registrar, bob, "StartupCheck Vehicle Bob", "car", now).ok()) {
                failures.add("could not issue the second owner's licence: "
                        + VehicleService.noSuchLicenseMessage(scratch, bob, "bob", LicenseClass.CAR));
            }
            ctx.checks++;
            final VehicleService.Outcome transferred = VehicleService.transfer(
                    scratch, registrar, "XYZ-999", bob, "StartupCheck Vehicle Bob", true, now);
            final VehicleRecord bobCar = VehicleService.byPlate(scratch, "XYZ999");
            if (!transferred.ok() || bobCar == null || !bob.equals(bobCar.ownerUuid())) {
                failures.add("the ownership transfer did not change the registered owner");
            }

            // Points: 4 + 4 + 4 crosses twelve and suspends; the window is computed at read time.
            ctx.checks++;
            if (!VehicleService.assessPoints(scratch, registrar, alice,
                    "StartupCheck Vehicle Alice", "car", "4", "StartupCheck minor", now).ok()
                    || scratch.license(alice, LicenseClass.CAR).activePoints(now) != 4) {
                failures.add("the first point assessment did not add 4 points");
            }
            ctx.checks++;
            if (!VehicleService.assessPoints(scratch, registrar, alice,
                    "StartupCheck Vehicle Alice", "car", "4", "StartupCheck reckless", now).ok()
                    || scratch.license(alice, LicenseClass.CAR).activePoints(now) != 8) {
                failures.add("points did not accumulate to 8");
            }
            ctx.checks++;
            final VehicleService.Outcome suspended = VehicleService.assessPoints(
                    scratch, registrar, alice, "StartupCheck Vehicle Alice",
                    "car", "6", "StartupCheck impaired", now);
            final LicenseRecord suspendedLicense = scratch.license(alice, LicenseClass.CAR);
            if (!suspended.ok()
                    || suspendedLicense.stateAt(now) != LicenseStatus.SUSPENDED
                    || suspendedLicense.activePoints(now) != 14
                    || suspendedLicense.suspendedUntil() != now + VehicleService.SUSPENSION_MS) {
                failures.add("twelve points in the window did not suspend the licence for 7 days");
            }
            ctx.checks++;
            if (VehicleService.assessPoints(scratch, registrar, alice,
                    "StartupCheck Vehicle Alice", "car", "3", "StartupCheck bad points", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("an illegal point value was accepted");
            }
            ctx.checks++;
            if (VehicleService.assessPoints(scratch, registrar, alice,
                    "StartupCheck Vehicle Alice", "car", "2", "", now)
                    .code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a point assessment without a reason was accepted");
            }
            ctx.checks++;
            if (VehicleService.assessPoints(scratch, citizen, alice,
                    "StartupCheck Vehicle Alice", "car", "2", "StartupCheck forbidden", now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("a point assessment did not refuse an actor without vehicle.register");
            }

            ctx.checks++;
            final long atWindowEnd = now + LicenseRecord.POINT_WINDOW_MS;
            if (suspendedLicense.activePoints(atWindowEnd) != 14) {
                failures.add("points at exactly 30 in-game days were not counted");
            }
            ctx.checks++;
            final long afterWindow = atWindowEnd + 1L;
            if (suspendedLicense.activePoints(afterWindow) != 0
                    || suspendedLicense.stateAt(afterWindow) != LicenseStatus.VALID) {
                failures.add("points did not expire after 30 in-game days, or the suspension did not end");
            }

            // Revoke and reissue: the record stays, then a fresh licence starts with no points.
            ctx.checks++;
            final VehicleService.Outcome daveIssue = VehicleService.issueLicense(
                    scratch, registrar, dave, "StartupCheck Vehicle Dave", "truck", now);
            if (!daveIssue.ok() || scratch.license(dave, LicenseClass.TRUCK) == null) {
                failures.add("could not issue a Truck licence for the revoke round-trip");
            }
            ctx.checks++;
            if (VehicleService.revokeLicense(scratch, registrar, dave,
                    "StartupCheck Vehicle Dave", "truck", "", now).code() != ErrorCode.BAD_PAYLOAD) {
                failures.add("a revocation without a reason was accepted");
            }
            ctx.checks++;
            final VehicleService.Outcome revoked = VehicleService.revokeLicense(
                    scratch, registrar, dave, "StartupCheck Vehicle Dave", "truck",
                    "StartupCheck licence fraud", now);
            final LicenseRecord revokedDave = scratch.license(dave, LicenseClass.TRUCK);
            if (!revoked.ok() || revokedDave.stateAt(now) != LicenseStatus.REVOKED
                    || !"StartupCheck licence fraud".equals(revokedDave.revokeReason())) {
                failures.add("the revocation did not keep the record with its reason");
            }
            ctx.checks++;
            if (VehicleService.assessPoints(scratch, registrar, dave,
                    "StartupCheck Vehicle Dave", "truck", "2", "StartupCheck revoked", now).ok()) {
                failures.add("points were accepted on a revoked licence");
            }
            ctx.checks++;
            final VehicleService.Outcome reissued = VehicleService.issueLicense(
                    scratch, registrar, dave, "StartupCheck Vehicle Dave", "truck", afterWindow);
            final LicenseRecord reissuedDave = scratch.license(dave, LicenseClass.TRUCK);
            if (!reissued.ok() || reissuedDave.stateAt(afterWindow) != LicenseStatus.VALID
                    || !reissuedDave.points().isEmpty()) {
                failures.add("a revoked licence could not be reissued with a clean point history");
            }

            // Impound and release: tow only, fee copied onto the impound, gold removed once.
            ctx.checks++;
            final VehicleService.Outcome impounded = VehicleService.impound(
                    scratch, registrar, "xyz-999",
                    "StartupCheck no plate displayed", com.horizonssmp.gov.common.records.LocationRecord.NONE, now);
            final VehicleRecord heldCar = VehicleService.byPlate(scratch, "XYZ999");
            if (!impounded.ok() || heldCar == null
                    || heldCar.status() != VehicleStatus.IMPOUNDED
                    || !heldCar.impound().active()
                    || !heldCar.impound().reasonText().contains("plate")
                    || heldCar.impound().releaseFeeGold()
                            != GovConfig.impoundReleaseFeeGold()) {
                failures.add("the impound did not store IMPOUNDED with its reason and fee");
            }
            ctx.checks++;
            if (VehicleService.impound(scratch, registrar, "XYZ999",
                    "StartupCheck second tow", com.horizonssmp.gov.common.records.LocationRecord.NONE, now)
                    .code() != ErrorCode.INVALID_STATE) {
                failures.add("a second impound of a held vehicle was accepted");
            }
            ctx.checks++;
            if (VehicleService.impound(scratch, citizen, "ABC123",
                    "StartupCheck no authority", com.horizonssmp.gov.common.records.LocationRecord.NONE, now)
                    .code() != ErrorCode.FORBIDDEN) {
                failures.add("an impound did not refuse an actor without vehicle.impound");
            }

            final GovStartupCheckVehiclesGateway gateway = new GovStartupCheckVehiclesGateway();
            gateway.present(bob, true);
            gateway.atReception(registrar.uuid(), true);
            gateway.gold(bob, heldCar.impound().releaseFeeGold());
            final Optional<FineService.ReceptionPoint> reception = Optional.of(
                    new FineService.ReceptionPoint("minecraft:overworld", 0.0d, 64.0d, 0.0d, 8.0d));
            ctx.checks++;
            if (VehicleService.release(scratch, registrar, "XYZ999", gateway,
                    Optional.empty(), now).ok()) {
                failures.add("a release was accepted with no configured reception");
            }
            ctx.checks++;
            if (VehicleService.release(scratch, registrar, "XYZ999", gateway,
                    reception, now).ok()) {
                failures.add("a release was accepted with the owner away from the reception");
            }
            ctx.checks++;
            gateway.atReception(bob, true);
            gateway.gold(bob, Math.max(0L, heldCar.impound().releaseFeeGold() - 1L));
            if (VehicleService.release(scratch, registrar, "XYZ999", gateway,
                    reception, now).ok()) {
                failures.add("a release was accepted without the full fee");
            }
            ctx.checks++;
            if (VehicleService.byPlate(scratch, "XYZ999").status() != VehicleStatus.IMPOUNDED) {
                failures.add("a refused release changed the impound state");
            }
            ctx.checks++;
            gateway.gold(bob, heldCar.impound().releaseFeeGold());
            final VehicleService.Outcome released = VehicleService.release(
                    scratch, registrar, "XYZ999", gateway, reception, now);
            final VehicleRecord releasedCar = VehicleService.byPlate(scratch, "XYZ999");
            if (!released.ok() || releasedCar == null
                    || releasedCar.status() != VehicleStatus.VALID
                    || releasedCar.impound().active()
                    || releasedCar.impound().releasedAt() != now
                    || !registrar.uuid().equals(releasedCar.impound().releasedBy())
                    || gateway.goldOf(bob) != 0L) {
                failures.add("the release did not mark the vehicle VALID and record the fee payment");
            }
            ctx.checks++;
            if (VehicleService.release(scratch, registrar, "XYZ999", gateway,
                    reception, now).code() != ErrorCode.INVALID_STATE) {
                failures.add("a second release of the same vehicle was accepted");
            }
            ctx.checks++;
            if (VehicleService.release(scratch, citizen, "XYZ999", gateway,
                    reception, now).code() != ErrorCode.FORBIDDEN) {
                failures.add("a release did not refuse an actor without vehicle.release");
            }



        ctx.reissuedDave = reissuedDave;
        ctx.heldCar = heldCar;
        ctx.gateway = gateway;
        ctx.releasedCar = releasedCar;
    }
}
