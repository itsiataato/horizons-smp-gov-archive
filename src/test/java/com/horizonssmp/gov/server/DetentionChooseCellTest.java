package com.horizonssmp.gov.server;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import com.horizonssmp.gov.common.enums.DetentionStatus;
import com.horizonssmp.gov.common.enums.LegalBasis;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * TASK-003 (FR-3) unit test: {@code DetentionDetain.chooseCell} closest-free-cell selection.
 *
 * <p>Acceptance criteria: returns the closest free cell, prefers the officer's dimension, falls
 * back cross-dimension, returns {@code NONE} when all cells are occupied. {@code chooseCell} is
 * private, so these tests invoke it reflectively; the detain-level integration lives in
 * {@code DetentionDetainCellsIntegrationTest}. Runs headless (pure records + NBT, see tips/t08).
 */
final class DetentionChooseCellTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final long NOW = 1_700_000_000_000L;

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Reflective call into the private DetentionDetain.chooseCell. */
    private static LocationRecord chooseCell(
            final List<LocationRecord> cells,
            final GovSavedData data,
            final JailGateway.Position officer,
            final long now) throws Exception {
        final Method method = DetentionDetain.class.getDeclaredMethod(
                "chooseCell", List.class, GovSavedData.class, JailGateway.Position.class, long.class);
        method.setAccessible(true);
        return (LocationRecord) method.invoke(null, cells, data, officer, now);
    }

    private static JailGateway.Position officerAt(final String dimension, final double x) {
        return new JailGateway.Position(dimension, x, 64.0d, 0.0d);
    }

    private static DetentionRecord activeDetention(
            final String id, final UUID target, final LocationRecord cell, final long now) {
        return new DetentionRecord(
                id, target, "Target", UUID.randomUUID(), "Officer", LegalBasis.VIOLENT_OFFENSE,
                "probe", "", now, 10, 0L, null, "", "", DetentionStatus.ACTIVE,
                cell, LocationRecord.NONE, false, DetentionRecord.ADAPTER_NATIVE,
                0, 0L, null, "", "");
    }

    void testClosestUnoccupied() throws Exception {
        final List<LocationRecord> cells = List.of(
                new LocationRecord(OVERWORLD, 100, 64, 0),
                new LocationRecord(OVERWORLD, 10, 64, 0),
                new LocationRecord(OVERWORLD, 30, 64, 0));
        final LocationRecord picked = chooseCell(cells, null, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.equals(new LocationRecord(OVERWORLD, 10, 64, 0)),
                "should pick the closest unoccupied cell, got " + picked);
    }

    void testPrefersOfficerDimension() throws Exception {
        final List<LocationRecord> cells = List.of(
                new LocationRecord(NETHER, 1, 64, 0),
                new LocationRecord(OVERWORLD, 1000, 64, 0));
        final LocationRecord picked = chooseCell(cells, null, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.dimension().equals(OVERWORLD),
                "should prefer the officer dimension over a nearer cross-dimension cell");
    }

    void testFallsBackCrossDimension() throws Exception {
        final List<LocationRecord> cells = List.of(
                new LocationRecord(NETHER, 50, 64, 50),
                new LocationRecord(NETHER, 5, 64, 5));
        final LocationRecord picked = chooseCell(cells, null, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.equals(new LocationRecord(NETHER, 5, 64, 5)),
                "should fall back to the closest cross-dimension cell, got " + picked);
    }

    void testNoneWhenAllOccupied() throws Exception {
        final LocationRecord near = new LocationRecord(OVERWORLD, 10, 64, 0);
        final LocationRecord far = new LocationRecord(OVERWORLD, 100, 64, 0);
        final GovSavedData data = new GovSavedData();
        data.putDetention(activeDetention("d-aaaa1111", UUID.randomUUID(), near, NOW));
        data.putDetention(activeDetention("d-bbbb2222", UUID.randomUUID(), far, NOW));
        final LocationRecord picked =
                chooseCell(List.of(near, far), data, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.equals(LocationRecord.NONE), "all occupied should return NONE, got " + picked);
    }

    void testOccupiedSkipsToNextFree() throws Exception {
        final LocationRecord near = new LocationRecord(OVERWORLD, 10, 64, 0);
        final LocationRecord far = new LocationRecord(OVERWORLD, 100, 64, 0);
        final GovSavedData data = new GovSavedData();
        data.putDetention(activeDetention("d-aaaa1111", UUID.randomUUID(), near, NOW));
        final LocationRecord picked =
                chooseCell(List.of(near, far), data, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.equals(far), "should skip the occupied cell, got " + picked);
    }

    void testExpiredDetentionDoesNotOccupy() throws Exception {
        final LocationRecord cell = new LocationRecord(OVERWORLD, 10, 64, 0);
        final GovSavedData data = new GovSavedData();
        // Started 10 minutes ago with a 10-minute term: expired at NOW, so the cell is free.
        data.putDetention(activeDetention("d-cccc3333", UUID.randomUUID(), cell, NOW - 600_000L));
        final LocationRecord picked =
                chooseCell(List.of(cell), data, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.equals(cell), "an expired detention should not occupy its cell");
    }

    void testEmptyAndUnsetCells() throws Exception {
        check(chooseCell(List.of(), null, officerAt(OVERWORLD, 0.0d), NOW)
                        .equals(LocationRecord.NONE),
                "empty cell list should return NONE");
        final LocationRecord real = new LocationRecord(OVERWORLD, 3, 64, 0);
        final LocationRecord picked = chooseCell(
                java.util.Arrays.asList(LocationRecord.NONE, null, real),
                null, officerAt(OVERWORLD, 0.0d), NOW);
        check(picked.equals(real), "unset and null entries should be skipped, got " + picked);
    }

    public static void main(final String[] args) {
        final DetentionChooseCellTest t = new DetentionChooseCellTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testClosestUnoccupied", "testPrefersOfficerDimension",
                "testFallsBackCrossDimension", "testNoneWhenAllOccupied",
                "testOccupiedSkipsToNextFree", "testExpiredDetentionDoesNotOccupy",
                "testEmptyAndUnsetCells"};
        final ThrowingRunnable[] tests = {
                t::testClosestUnoccupied, t::testPrefersOfficerDimension,
                t::testFallsBackCrossDimension, t::testNoneWhenAllOccupied,
                t::testOccupiedSkipsToNextFree, t::testExpiredDetentionDoesNotOccupy,
                t::testEmptyAndUnsetCells};
        for (int i = 0; i < names.length; i++) {
            try {
                tests[i].run();
                passed++;
                System.out.println("PASS " + names[i]);
            } catch (final Throwable e) {
                failed++;
                final Throwable cause = e instanceof java.lang.reflect.InvocationTargetException
                        ? e.getCause() : e;
                System.out.println("FAIL " + names[i] + ": " + cause);
            }
        }
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
