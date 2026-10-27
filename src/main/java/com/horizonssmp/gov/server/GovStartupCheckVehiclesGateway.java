package com.horizonssmp.gov.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

    final class GovStartupCheckVehiclesGateway implements FineService.Gateway {

        private final Map<UUID, Long> gold = new HashMap<>();
        private final Set<UUID> present = new HashSet<>();
        private final Set<UUID> atReception = new HashSet<>();

        void gold(final UUID player, final long amount) {
            gold.put(player, Math.max(0L, amount));
        }

        long goldOf(final UUID player) {
            return gold.getOrDefault(player, 0L);
        }

        void present(final UUID player, final boolean value) {
            if (value) {
                present.add(player);
            } else {
                present.remove(player);
            }
        }

        void atReception(final UUID player, final boolean value) {
            if (value) {
                atReception.add(player);
            } else {
                atReception.remove(player);
            }
        }

        @Override
        public long goldIngots(final UUID player) {
            return goldOf(player);
        }

        @Override
        public boolean removeGoldIngots(final UUID player, final long count) {
            final long carried = goldOf(player);
            if (count < 0L || carried < count) {
                return false;
            }
            gold.put(player, carried - count);
            return true;
        }

        @Override
        public boolean addGoldIngots(final UUID player, final long count) {
            if (count < 0L) {
                return false;
            }
            final long carried = goldOf(player);
            gold.put(player, carried > Long.MAX_VALUE - count ? Long.MAX_VALUE : carried + count);
            return true;
        }

        @Override
        public boolean present(final UUID player) {
            return present.contains(player);
        }

        @Override
        public boolean atReception(final UUID player) {
            return atReception.contains(player);
        }
    }

    /**
     * The in-memory gateway the startup check uses.
     *
     * <p>It observes the same contract as {@link FineGateway}: a count, an all-or-nothing removal,
     * presence and reception. It never touches a real inventory, which is the point of the
     * boundary; the live-only half is stated in the report.
     */

