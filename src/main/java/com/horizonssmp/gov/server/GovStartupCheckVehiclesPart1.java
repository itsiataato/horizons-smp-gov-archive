package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;

final class GovStartupCheckVehiclesPart1 {

    private GovStartupCheckVehiclesPart1() {
    }

    static void run(final GovStartupCheckVehiclesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final VehicleService.Actor registrar = ctx.registrar;
        final VehicleService.Actor citizen = ctx.citizen;

            // Plate shape and canonicalisation: the law accepts both spellings, the registry stores
            // one. This is the property that stops ABC-123 and ABC123 becoming two vehicles.
            ctx.checks++;
            if (!"ABC123".equals(VehicleService.canonicalPlate("ABC-123"))
                    || !"ABC123".equals(VehicleService.canonicalPlate("abc123"))
                    || !VehicleService.canonicalPlate("ABC-123")
                            .equals(VehicleService.canonicalPlate("ABC123"))) {
                failures.add("plate canonicalisation does not make ABC-123 and ABC123 the same plate");
            }
            ctx.checks++;
            if (!"ABC1234".equals(VehicleService.canonicalPlate("abc-1234"))
                    || !"ABC123".equals(VehicleService.canonicalPlate("  ABC123  "))) {
                failures.add("a valid 4-digit or padded plate was not canonicalised");
            }
            final String[] invalidPlates = {"", null, "ABCDEFGHIJKLMN"};
            boolean allInvalid = true;
            for (final String plate : invalidPlates) {
                if (VehicleService.canonicalPlate(plate) != null) {
                    allInvalid = false;
                    failures.add("an invalid plate was accepted: " + plate);
                    break;
                }
            }
            ctx.checks++;
            if (!allInvalid) {
                // The loop already named the value; this keeps the assertion count stable.
            }

            ctx.checks++;
            if (VehicleService.DEFAULT_RELEASE_FEE_GOLD != 8L
                    || VehicleService.SUSPENSION_MS != GovTime.days(7)
                    || VehicleService.DISPOSAL_AFTER_MS != GovTime.days(30)
                    || LicenseRecord.POINT_WINDOW_MS != GovTime.days(30)) {
                failures.add("a vehicle law constant is not the documented value");
            }

            // The action matrix: every M5 action has a handler and the capability this build
            // documents. Reads are not audited; every mutation is.
            ctx.checks++;
            if (!GovActionDispatcher.isRegistered(OpCode.VEH_LIST)
                    || !GovActionDispatcher.isRegistered(OpCode.VEH_INFO)
                    || !GovActionDispatcher.isRegistered(OpCode.VEH_REGISTER)
                    || !GovActionDispatcher.isRegistered(OpCode.VEH_EDIT)
                    || !GovActionDispatcher.isRegistered(OpCode.VEH_IMPOUND)
                    || !GovActionDispatcher.isRegistered(OpCode.VEH_RELEASE)
                    || !GovActionDispatcher.isRegistered(OpCode.PLATE_SCAN)
                    || !GovActionDispatcher.isRegistered(OpCode.LICENSE_ISSUE)
                    || !GovActionDispatcher.isRegistered(OpCode.LICENSE_REVOKE)
                    || !GovActionDispatcher.isRegistered(OpCode.LICENSE_POINTS)) {
                failures.add("an M5 action has no handler registered");
            }
            ctx.checks++;
            if (GovCapabilities.requiredFor(OpCode.VEH_LIST) != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.VEH_INFO) != GovCapability.PLAYER_LOOKUP_SELF
                    || GovCapabilities.requiredFor(OpCode.VEH_REGISTER) != GovCapability.VEHICLE_REGISTER
                    || GovCapabilities.requiredFor(OpCode.VEH_EDIT) != GovCapability.VEHICLE_REGISTER
                    || GovCapabilities.requiredFor(OpCode.VEH_IMPOUND) != GovCapability.VEHICLE_IMPOUND
                    || GovCapabilities.requiredFor(OpCode.VEH_RELEASE) != GovCapability.VEHICLE_RELEASE
                    || GovCapabilities.requiredFor(OpCode.PLATE_SCAN) != GovCapability.PLATE_SCAN
                    || GovCapabilities.requiredFor(OpCode.LICENSE_ISSUE) != GovCapability.VEHICLE_REGISTER
                    || GovCapabilities.requiredFor(OpCode.LICENSE_REVOKE) != GovCapability.VEHICLE_REGISTER
                    || GovCapabilities.requiredFor(OpCode.LICENSE_POINTS) != GovCapability.VEHICLE_REGISTER) {
                failures.add("an M5 action does not require the documented capability");
            }
            ctx.checks++;
            if (OpCode.VEH_LIST.audited() || OpCode.VEH_INFO.audited()) {
                failures.add("a vehicle read op is marked as audited");
            }
            ctx.checks++;
            if (!OpCode.VEH_REGISTER.audited() || !OpCode.VEH_EDIT.audited()
                    || !OpCode.VEH_IMPOUND.audited() || !OpCode.VEH_RELEASE.audited()
                    || !OpCode.PLATE_SCAN.audited() || !OpCode.LICENSE_ISSUE.audited()
                    || !OpCode.LICENSE_REVOKE.audited() || !OpCode.LICENSE_POINTS.audited()) {
                failures.add("an M5 mutation op is not marked as audited");
            }
            ctx.checks++;
            if (VehicleService.canViewAll(EnumSet.noneOf(GovCapability.class))
                    || !VehicleService.canViewAll(EnumSet.of(GovCapability.PLATE_SCAN))
                    || !VehicleService.canViewAll(EnumSet.of(GovCapability.VEHICLE_REGISTER))
                    || !VehicleService.canViewAll(EnumSet.of(GovCapability.VEHICLE_IMPOUND))
                    || !VehicleService.canViewAll(EnumSet.of(GovCapability.VEHICLE_RELEASE))) {
                failures.add("the vehicle read scope does not follow the registry capabilities");
            }
            ctx.checks++;
            if (!VehicleKeys.FILTER_IMPOUNDED.equals(VehicleService.canonicalFilter("IMPOUNDED"))
                    || !VehicleKeys.FILTER_FLAGGED.equals(VehicleService.canonicalFilter(" flagged "))
                    || !VehicleKeys.FILTER_ALL.equals(VehicleService.canonicalFilter("nonsense"))) {
                failures.add("the vehicle filter parser is not tolerant and canonical");
            }

            // Registration policy: with the default policy on, no licence means no registration.
            final VehicleService.Outcome noLicense = VehicleService.register(
                    scratch, registrar, alice, "StartupCheck Vehicle Alice",
                    "ABC-123", "car", true, now);
            ctx.checks++;
            if (noLicense.ok() || noLicense.code() != ErrorCode.INVALID_STATE
                    || !scratch.vehicles().isEmpty()) {
                failures.add("registration did not refuse an owner without a licence");
            }
            ctx.checks++;
            if (VehicleService.register(
                    scratch, citizen, alice, "StartupCheck Vehicle Alice",
                    "ABC-123", "car", false, now).code() != ErrorCode.FORBIDDEN) {
                failures.add("registration did not refuse an actor without vehicle.register");
            }

            // Licence issue round-trip, and the refusal of a second live licence.
            final VehicleService.Outcome aliceCar = VehicleService.issueLicense(
                    scratch, registrar, alice, "StartupCheck Vehicle Alice", "car", now);
            ctx.checks++;
            final LicenseRecord aliceLicense = scratch.license(alice, LicenseClass.CAR);
            if (!aliceCar.ok() || aliceLicense == null
                    || aliceLicense.stateAt(now) != LicenseStatus.VALID
                    || aliceLicense.activePoints(now) != 0
                    || !registrar.uuid().equals(aliceLicense.issuedBy())) {
                failures.add("the licence issue round-trip did not produce a valid Car licence");
            }
            ctx.checks++;
            if (VehicleService.issueLicense(
                    scratch, registrar, alice, "StartupCheck Vehicle Alice", "CAR", now).ok()) {
                failures.add("a second live licence was issued over an existing one");
            }
            ctx.checks++;
            if (VehicleService.issueLicense(
                    scratch, citizen, bob, "StartupCheck Vehicle Bob", "car", now).code()
                    != ErrorCode.FORBIDDEN) {
                failures.add("licence issue did not refuse an actor without vehicle.register");
            }

            // Register the canonical plate; ABC-123 must resolve to the same row as ABC123.
            final VehicleService.Outcome registered = VehicleService.register(
                    scratch, registrar, alice, "StartupCheck Vehicle Alice",
                    "ABC-123", "Car", true, now);
            final VehicleRecord abc = VehicleService.byPlate(scratch, "ABC123");
            ctx.checks++;
            if (!registered.ok() || abc == null
                    || !"ABC123".equals(abc.plate())
                    || !"car".equals(abc.vehClass())
                    || !alice.equals(abc.ownerUuid())
                    || abc.status() != VehicleStatus.VALID
                    || !abc.id().startsWith(VehicleService.VEHICLE_ID_PREFIX)) {
                failures.add("registration did not store the canonical plate and class");
            }
            ctx.checks++;
            if (VehicleService.byPlate(scratch, "ABC-123") != abc
                    || VehicleService.byPlate(scratch, "abc-123") != abc) {
                failures.add("a plate lookup did not treat ABC-123 and ABC123 as the same vehicle");
            }
            ctx.checks++;
            if (VehicleService.register(
                    scratch, registrar, bob, "StartupCheck Vehicle Bob",
                    "ABC123", "car", false, now).code() != ErrorCode.PLATE_TAKEN) {
                failures.add("a duplicate plate was not refused with PLATE_TAKEN");
            }



        ctx.abc = abc;
    }
}
