package com.horizonssmp.gov.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * TASK-003 (FR-3) integration test: detain assigns the closest free runtime cell.
 *
 * <p>Acceptance criteria: the first detain lands in the closest cell and the second detain of a
 * different player lands in the next free cell. Runs headless through a stub {@code JailGateway}:
 * officer and targets stand within 8 blocks in the overworld, two runtime cells are registered on
 * a scratch {@code GovSavedData}, and the policy selects the native fallback (see tips/t08).
 *
 * <p>Headless risk: {@code detain} reaches {@code GovConfig.jailCells()} via
 * {@code DetentionQueries.allCells}. {@code GovConfigCore.read} returns the default (empty list)
 * when the spec is not loaded, so only the runtime cells apply; if NeoForge class init ever needs
 * a bootstrapped game this test fails loudly instead of passing vacuously.
 */
final class DetentionDetainCellsIntegrationTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final long NOW = 1_700_000_000_000L;

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal stub: fixed positions, teleport/message recording, everything else inert. */
    private static final class StubGateway implements JailGateway {
        private final Map<UUID, Position> positions = new HashMap<>();
        private final Map<UUID, LocationRecord> teleports = new HashMap<>();

        void place(final UUID player, final Position position) {
            positions.put(player, position);
        }

        @Override
        public boolean present(final UUID player) {
            return positions.containsKey(player);
        }

        @Override
        public Position position(final UUID player) {
            return positions.getOrDefault(player, Position.none());
        }

        @Override
        public boolean teleport(final UUID player, final LocationRecord location) {
            if (location == null || !location.isSet()) {
                return false;
            }
            teleports.put(player, location);
            return true;
        }

        @Override
        public void applyCuffs(final UUID player, final boolean cuffed) {
        }

        @Override
        public void applyConfinement(final UUID player, final boolean confined) {
        }

        @Override
        public void message(final UUID player, final String message) {
        }

        @Override
        public boolean command(final String command) {
            return false;
        }
    }

    private static DetentionService.Policy policy() {
        return new DetentionService.Policy(10, 140, false, true, "", LocationRecord.NONE, 12.0d);
    }

    private static DetentionService.Actor officer(final UUID uuid) {
        return new DetentionService.Actor(uuid, "Officer", Set.of(GovCapability.JAIL_COMMIT));
    }

    private static GovSavedData twoCellData(
            final LocationRecord near, final LocationRecord far) {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("near", near), "setup: near cell should register");
        check(data.addRuntimeCell("far", far), "setup: far cell should register");
        return data;
    }

    private static DetentionRecord detain(
            final GovSavedData data,
            final UUID officerUuid,
            final UUID targetUuid,
            final StubGateway gateway,
            final DetentionService.Policy policy,
            final long now) {
        final DetentionService.Outcome outcome = DetentionService.detain(
                data, officer(officerUuid), targetUuid, "Target",
                "violent", "", "", "saw them break the peace", "", gateway, policy, now);
        check(outcome.ok(), "detain should succeed, got: " + outcome.message());
        final DetentionRecord record = data.detention(outcome.detentionId());
        check(record != null, "detained record should be stored");
        return record;
    }

    void testFirstDetainGetsClosestCell() {
        final LocationRecord near = new LocationRecord(OVERWORLD, 10, 64, 0);
        final LocationRecord far = new LocationRecord(OVERWORLD, 100, 64, 0);
        final GovSavedData data = twoCellData(near, far);
        final UUID officerUuid = UUID.randomUUID();
        final UUID target = UUID.randomUUID();
        final StubGateway gateway = new StubGateway();
        gateway.place(officerUuid, new JailGateway.Position(OVERWORLD, 0.0d, 64.0d, 0.0d));
        gateway.place(target, new JailGateway.Position(OVERWORLD, 1.0d, 64.0d, 0.0d));

        final DetentionRecord record = detain(data, officerUuid, target, gateway, policy(), NOW);
        check(record.cell().equals(near),
                "first detain should take the closest cell, got " + record.cell());
        check(DetentionRecord.ADAPTER_NATIVE.equals(record.jailAdapter()),
                "native fallback should be recorded, got " + record.jailAdapter());
        check(gateway.teleports.get(target).equals(near),
                "native confine should teleport to the assigned cell");
    }

    void testSecondDetainGetsNextFreeCell() {
        final LocationRecord near = new LocationRecord(OVERWORLD, 10, 64, 0);
        final LocationRecord far = new LocationRecord(OVERWORLD, 100, 64, 0);
        final GovSavedData data = twoCellData(near, far);
        final UUID officerUuid = UUID.randomUUID();
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();
        final StubGateway gateway = new StubGateway();
        gateway.place(officerUuid, new JailGateway.Position(OVERWORLD, 0.0d, 64.0d, 0.0d));
        gateway.place(first, new JailGateway.Position(OVERWORLD, 1.0d, 64.0d, 0.0d));
        gateway.place(second, new JailGateway.Position(OVERWORLD, 2.0d, 64.0d, 0.0d));
        final DetentionService.Policy policy = policy();

        final DetentionRecord firstRecord = detain(data, officerUuid, first, gateway, policy, NOW);
        check(firstRecord.cell().equals(near), "first detain should take the near cell");
        final DetentionRecord secondRecord =
                detain(data, officerUuid, second, gateway, policy, NOW + 1_000L);
        check(secondRecord.cell().equals(far),
                "second detain should skip the occupied cell, got " + secondRecord.cell());
    }

    void testDetainRefusedBeyondEightBlocks() {
        final LocationRecord near = new LocationRecord(OVERWORLD, 10, 64, 0);
        final GovSavedData data = twoCellData(near, new LocationRecord(OVERWORLD, 100, 64, 0));
        final UUID officerUuid = UUID.randomUUID();
        final UUID target = UUID.randomUUID();
        final StubGateway gateway = new StubGateway();
        gateway.place(officerUuid, new JailGateway.Position(OVERWORLD, 0.0d, 64.0d, 0.0d));
        gateway.place(target, new JailGateway.Position(OVERWORLD, 50.0d, 64.0d, 0.0d));

        final DetentionService.Outcome outcome = DetentionService.detain(
                data, officer(officerUuid), target, "Target",
                "violent", "", "", "saw them break the peace", "", gateway, policy(), NOW);
        check(!outcome.ok(), "detain beyond 8 blocks should be refused");
    }

    void testDetainRefusedWithoutReason() {
        final LocationRecord near = new LocationRecord(OVERWORLD, 10, 64, 0);
        final GovSavedData data = twoCellData(near, new LocationRecord(OVERWORLD, 100, 64, 0));
        final UUID officerUuid = UUID.randomUUID();
        final UUID target = UUID.randomUUID();
        final StubGateway gateway = new StubGateway();
        gateway.place(officerUuid, new JailGateway.Position(OVERWORLD, 0.0d, 64.0d, 0.0d));
        gateway.place(target, new JailGateway.Position(OVERWORLD, 1.0d, 64.0d, 0.0d));

        final DetentionService.Outcome outcome = DetentionService.detain(
                data, officer(officerUuid), target, "Target",
                "violent", "", "", "   ", "", gateway, policy(), NOW);
        check(!outcome.ok(), "detain without a reason should be refused");
    }

    public static void main(final String[] args) {
        final DetentionDetainCellsIntegrationTest t = new DetentionDetainCellsIntegrationTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testFirstDetainGetsClosestCell", "testSecondDetainGetsNextFreeCell",
                "testDetainRefusedBeyondEightBlocks", "testDetainRefusedWithoutReason"};
        final Runnable[] tests = {
                t::testFirstDetainGetsClosestCell, t::testSecondDetainGetsNextFreeCell,
                t::testDetainRefusedBeyondEightBlocks, t::testDetainRefusedWithoutReason};
        for (int i = 0; i < names.length; i++) {
            try {
                tests[i].run();
                passed++;
                System.out.println("PASS " + names[i]);
            } catch (final Throwable e) {
                failed++;
                System.out.println("FAIL " + names[i] + ": " + e);
            }
        }
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
