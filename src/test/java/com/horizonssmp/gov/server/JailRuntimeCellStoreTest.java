package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * TASK-003 (FR-2, FR-3) unit test: {@code GovPersonnelStore.addRuntimeCell/removeRuntimeCell}.
 *
 * <p>Acceptance criterion: duplicate add returns {@code false}; deleting a missing cell returns
 * {@code false}. Runs headless: the store plus {@code new GovSavedData()} touch only NBT data
 * classes and {@code SavedData.setDirty()}, never FML/Minecraft client code (see tips/t08).
 */
final class JailRuntimeCellStoreTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static LocationRecord cell(final int x, final int y, final int z) {
        return new LocationRecord(OVERWORLD, x, y, z);
    }

    void testAddValidCell() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", cell(100, 64, 200)), "addRuntimeCell should accept a valid cell");
        check(data.runtimeCells().containsKey("alpha"), "runtimeCells should contain 'alpha'");
        check(data.runtimeCells().get("alpha").equals(cell(100, 64, 200)),
                "stored cell should keep its coordinates");
    }

    void testAddDuplicateReturnsFalse() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", cell(100, 64, 200)), "first add should succeed");
        check(!data.addRuntimeCell("alpha", cell(1, 2, 3)), "duplicate add should return false");
        check(data.runtimeCells().get("alpha").equals(cell(100, 64, 200)),
                "duplicate add must not overwrite the original cell");
    }

    void testAddBlankNameReturnsFalse() {
        final GovSavedData data = new GovSavedData();
        check(!data.addRuntimeCell(null, cell(1, 2, 3)), "null name should return false");
        check(!data.addRuntimeCell("", cell(1, 2, 3)), "empty name should return false");
        check(!data.addRuntimeCell("   ", cell(1, 2, 3)), "blank name should return false");
        check(data.runtimeCells().isEmpty(), "no cell should be stored after blank-name adds");
    }

    void testAddNullOrUnsetCellReturnsFalse() {
        final GovSavedData data = new GovSavedData();
        check(!data.addRuntimeCell("null-cell", null), "null cell should return false");
        check(!data.addRuntimeCell("unset-cell", LocationRecord.NONE),
                "unset cell should return false");
        check(data.runtimeCells().isEmpty(), "no cell should be stored after invalid adds");
    }

    void testRemoveExistingReturnsTrue() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", cell(100, 64, 200)), "setup add should succeed");
        check(data.removeRuntimeCell("alpha"), "removing an existing cell should return true");
        check(!data.runtimeCells().containsKey("alpha"), "cell should be gone after removal");
    }

    void testRemoveMissingReturnsFalse() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", cell(100, 64, 200)), "setup add should succeed");
        check(!data.removeRuntimeCell("nope"), "removing a missing cell should return false");
        check(data.runtimeCells().containsKey("alpha"), "existing cell must survive a missed delete");
    }

    void testRemoveBlankReturnsFalse() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", cell(100, 64, 200)), "setup add should succeed");
        check(!data.removeRuntimeCell(null), "null name should return false");
        check(!data.removeRuntimeCell(""), "empty name should return false");
        check(!data.removeRuntimeCell("   "), "blank name should return false");
        check(data.runtimeCells().containsKey("alpha"), "existing cell must survive blank deletes");
    }

    void testRemoveThenReAdd() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", cell(100, 64, 200)), "setup add should succeed");
        check(data.removeRuntimeCell("alpha"), "remove should succeed");
        check(data.addRuntimeCell("alpha", cell(7, 8, 9)), "re-add after delete should succeed");
        check(data.runtimeCells().get("alpha").equals(cell(7, 8, 9)),
                "re-added cell should carry the new coordinates");
    }

    public static void main(final String[] args) {
        final JailRuntimeCellStoreTest t = new JailRuntimeCellStoreTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testAddValidCell", "testAddDuplicateReturnsFalse", "testAddBlankNameReturnsFalse",
                "testAddNullOrUnsetCellReturnsFalse", "testRemoveExistingReturnsTrue",
                "testRemoveMissingReturnsFalse", "testRemoveBlankReturnsFalse",
                "testRemoveThenReAdd"};
        final Runnable[] tests = {
                t::testAddValidCell, t::testAddDuplicateReturnsFalse, t::testAddBlankNameReturnsFalse,
                t::testAddNullOrUnsetCellReturnsFalse, t::testRemoveExistingReturnsTrue,
                t::testRemoveMissingReturnsFalse, t::testRemoveBlankReturnsFalse,
                t::testRemoveThenReAdd};
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
