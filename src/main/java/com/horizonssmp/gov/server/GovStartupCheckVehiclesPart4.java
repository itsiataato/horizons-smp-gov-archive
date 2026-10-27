package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.PersonKeys;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.LicenseStatus;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.VehicleRecord;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckVehiclesPart4 {

    private GovStartupCheckVehiclesPart4() {
    }

    static void run(final GovStartupCheckVehiclesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final UUID alice = ctx.alice;
        final UUID carol = ctx.carol;
        final VehicleRecord abc = ctx.abc;
        final LicenseRecord reissuedDave = ctx.reissuedDave;
        final GovStartupCheckVehiclesGateway gateway = ctx.gateway;
        final long afterWindow = now + LicenseRecord.POINT_WINDOW_MS + 1L;

            // The person card is the M5 integration point: it must show classes, states and points.
            ctx.checks++;
            final Map<String, String> card = PersonService.detail(
                    scratch, alice, "StartupCheck Vehicle Alice", false,
                    EnumSet.allOf(GovCapability.class), now);
            final String licenseText = card.getOrDefault(PersonKeys.LICENSE, "");
            if (!licenseText.contains("Car")
                    || !licenseText.contains("SUSPENDED")
                    || !licenseText.contains("14 pts")) {
                failures.add("the person card does not render the licence class, state and points");
            }
            ctx.checks++;
            final Map<String, String> laterCard = PersonService.detail(
                    scratch, alice, "StartupCheck Vehicle Alice", false,
                    EnumSet.allOf(GovCapability.class), afterWindow);
            if (!laterCard.getOrDefault(PersonKeys.LICENSE, "").contains("VALID")) {
                failures.add("the person card did not show the suspension ending at read time");
            }
            ctx.checks++;
            if (PersonService.detail(scratch, carol, "StartupCheck Vehicle Carol", false,
                    EnumSet.allOf(GovCapability.class), now)
                    .getOrDefault(PersonKeys.LICENSE, "").isBlank()) {
                failures.add("the person card lost its licence summary for a licensed person");
            }

            // Tolerant reads: an older or hand-edited tag must load, never throw. A licence with an
            // unknown class is the one case with no safe default and is skipped by the loader.
            ctx.checks++;
            final CompoundTag legacyVehicle = new CompoundTag();
            legacyVehicle.putString("id", "startup-check-vehicle-legacy");
            legacyVehicle.putString("plate", "LEG-123");
            legacyVehicle.putString("status", "NOT_A_STATUS");
            legacyVehicle.putString("vehClass", "spaceship");
            final VehicleRecord tolerantVehicle = VehicleRecord.read(legacyVehicle, null);
            if (tolerantVehicle.status() != VehicleStatus.VALID
                    || tolerantVehicle.impound() != ImpoundRecord.NONE
                    || !"LEG-123".equals(tolerantVehicle.plate())) {
                failures.add("a legacy vehicle tag did not load with tolerant defaults");
            }
            ctx.checks++;
            if (!abc.equals(VehicleRecord.read(abc.write(), null))) {
                failures.add("the vehicle record did not round-trip through its own encoding");
            }
            ctx.checks++;
            final CompoundTag unknownClassLicense = new CompoundTag();
            NbtSupport.putUuid(unknownClassLicense, "playerUuid", alice);
            unknownClassLicense.putString("licenseClass", "spaceship");
            if (LicenseRecord.read(unknownClassLicense, null) != null) {
                failures.add("a licence with an unknown class was loaded with a guessed class");
            }
            ctx.checks++;
            final CompoundTag legacyLicense = new CompoundTag();
            NbtSupport.putUuid(legacyLicense, "playerUuid", alice);
            legacyLicense.putString("licenseClass", "car");
            legacyLicense.putString("status", "NOT_A_STATUS");
            final LicenseRecord tolerantLicense = LicenseRecord.read(legacyLicense, null);
            if (tolerantLicense == null
                    || tolerantLicense.stateAt(now) != LicenseStatus.REVOKED
                    || tolerantLicense.activePoints(now) != 0
                    || !LicenseRecord.key(alice, LicenseClass.CAR).equals(tolerantLicense.id())) {
                failures.add("a legacy licence tag did not load with the safe revoked default");
            }
            ctx.checks++;
            if (!reissuedDave.equals(LicenseRecord.read(reissuedDave.write(), null))) {
                failures.add("the licence record did not round-trip through its own encoding");
            }

            // Persistence: the whole data set must write the new sections and read them back. A
            // field that is only correct in memory is invisible until the next restart.
            ctx.checks++;
            final CompoundTag saved = scratch.save(new CompoundTag(), null);
            final GovSavedData reloaded = GovSavedData.load(saved, null);
            if (!saved.contains("licenses") || !saved.contains("vehicles")
                    || reloaded.license(alice, LicenseClass.CAR) == null
                    || reloaded.vehicle(abc.id()) == null
                    || reloaded.licenses().size() != scratch.licenses().size()
                    || reloaded.vehicles().size() != scratch.vehicles().size()) {
                failures.add("the vehicle/licence sections did not survive a save and load");
            }

            // One audit entry per accepted mutation is the dispatcher's contract; the M5 service
            // builds no audit line itself. Assert the ops are flagged above and that a system
            // disposal mark names the vehicle so a reader can find it.
            ctx.checks++;
            boolean disposalAudit = false;
            for (final var entry : scratch.audit()) {
                if ("VEH_DISPOSAL_FLAGGED".equals(entry.action())
                        && entry.payload().contains("FLG123")) {
                    disposalAudit = true;
                    break;
                }
            }
            if (!disposalAudit) {
                failures.add("the disposal mark did not leave a system audit line naming the plate");
            }

    }
}
