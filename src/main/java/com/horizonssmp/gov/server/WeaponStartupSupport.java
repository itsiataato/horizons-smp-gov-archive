package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.enums.DutyState;

final class WeaponStartupSupport {

    static final EnumSet<GovCapability> REGISTRAR_CAPS =
            EnumSet.of(GovCapability.WEAPON_REGISTER, GovCapability.WEAPON_VIEW);
    static final EnumSet<GovCapability> MILITARY_CAPS =
            EnumSet.of(GovCapability.WEAPON_REGISTER, GovCapability.MILITARY_AUTHORITY,
                    GovCapability.WEAPON_VIEW);

    private WeaponStartupSupport() {
    }

    static GovSavedData scratch(final long now) {
        final GovSavedData data = new GovSavedData();
        data.seedIfNeeded(now, false);
        return data;
    }

    static WeaponService.Subject subject(
            final GovSavedData data, final UUID uuid, final java.util.Set<GovCapability> capabilities) {
        final PlayerRecord record = data.player(uuid);
        final DutyState duty = record == null ? DutyState.N_A : record.duty();
        return subject(data, uuid, capabilities, duty);
    }

    static WeaponService.Subject subject(
            final GovSavedData data, final UUID uuid, final java.util.Set<GovCapability> capabilities,
            final DutyState duty) {
        final PlayerRecord stored = data.player(uuid);
        final PlayerRecord record = stored == null
                ? PlayerRecord.blank(uuid, "StartupCheck Weapon", 0L).withDuty(duty)
                : stored.withDuty(duty);
        return new WeaponService.Subject(uuid,
                record.lastName().isBlank() ? "StartupCheck Weapon" : record.lastName(),
                capabilities, record);
    }

    static int check(
            final boolean condition, final String failure, final List<String> failures) {
        if (!condition) {
            failures.add(failure);
        }
        return 1;
    }

    /** The in-memory inventory and position gateway; it never touches a real player. */
    static final class TestGateway implements WeaponGateway {

        private final Map<UUID, List<WeaponGateway.HeldStack>> inventories = new HashMap<>();
        private final Map<UUID, WeaponGateway.Position> positions = new HashMap<>();

        void add(final UUID player, final String itemId, final int count) {
            final List<WeaponGateway.HeldStack> stacks = new ArrayList<>(
                    inventories.getOrDefault(player, List.of()));
            stacks.removeIf(stack -> stack.itemId().equalsIgnoreCase(itemId));
            if (count > 0) {
                stacks.add(new WeaponGateway.HeldStack(itemId, count));
            }
            inventories.put(player, List.copyOf(stacks));
        }

        void position(final UUID player, final String dimension, final double x,
                final double y, final double z) {
            positions.put(player, new WeaponGateway.Position(dimension, x, y, z));
        }

        @Override
        public List<WeaponGateway.HeldStack> inventory(final UUID player) {
            return inventories.getOrDefault(player, List.of());
        }

        @Override
        public WeaponGateway.Position position(final UUID player) {
            return positions.getOrDefault(player, WeaponGateway.Position.none());
        }

        @Override
        public boolean present(final UUID player) {
            return positions.containsKey(player);
        }
    }
}
