package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;

final class GovStartupCheckVehiclesContext {
    final List<String> failures;
    int checks;
    final long now = 1_800_000_000_000L;

    final GovSavedData scratch = new GovSavedData();
    final UUID alice = new UUID(0L, 5001L);
    final UUID bob = new UUID(0L, 5002L);
    final UUID carol = new UUID(0L, 5003L);
    final UUID dave = new UUID(0L, 5004L);

    final VehicleService.Actor registrar = new VehicleService.Actor(
            new UUID(0L, 5100L), "StartupCheck Vehicle Registrar",
            EnumSet.of(GovCapability.VEHICLE_REGISTER, GovCapability.VEHICLE_IMPOUND,
                    GovCapability.VEHICLE_RELEASE, GovCapability.PLATE_SCAN));
    final VehicleService.Actor scanner = new VehicleService.Actor(
            new UUID(0L, 5101L), "StartupCheck Vehicle Scanner",
            EnumSet.of(GovCapability.PLATE_SCAN));
    final VehicleService.Actor citizen = new VehicleService.Actor(
            alice, "StartupCheck Vehicle Alice", EnumSet.noneOf(GovCapability.class));

    VehicleRecord abc;
    LicenseRecord reissuedDave;
    VehicleRecord heldCar;
    GovStartupCheckVehiclesGateway gateway;
    VehicleRecord releasedCar;

    GovStartupCheckVehiclesContext(final List<String> failures) {
        this.failures = failures;
        scratch.seedIfNeeded(now, false);
        scratch.putPlayer(PlayerRecord.blank(alice, "StartupCheck Vehicle Alice", now));
        scratch.putPlayer(PlayerRecord.blank(bob, "StartupCheck Vehicle Bob", now));
        scratch.putPlayer(PlayerRecord.blank(carol, "StartupCheck Vehicle Carol", now));
        scratch.putPlayer(PlayerRecord.blank(dave, "StartupCheck Vehicle Dave", now));
        scratch.putPlayer(PlayerRecord.blank(registrar.uuid(), registrar.name(), now));
    }
}
