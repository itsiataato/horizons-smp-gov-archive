package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.NbtSupport;

import net.minecraft.nbt.CompoundTag;

/**
 * TASK-003 (FR-2) unit test: {@code jailCells} NBT round-trip of runtime cells.
 *
 * <p>Acceptance criterion: save/load preserves runtime cells. The slice helpers below mirror the
 * production logic line-for-line ({@code GovSavedDataSave.java:51-57} for the write side,
 * {@code GovSavedDataLoad.java:59-68} for the read side) so this test exercises the exact tag
 * shape without SavedData/FML coupling (see tips/t08). The final test runs the real
 * {@code GovSavedDataSave.save}/{@code GovSavedDataLoad.load} pair headlessly.
 */
final class JailCellsNbtRoundTripTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Mirrors GovSavedDataSave jailCells write (lines 51-57). */
    private static List<CompoundTag> writeCellsSlice(final Map<String, LocationRecord> cells) {
        final List<CompoundTag> cellTags = new ArrayList<>();
        for (final Map.Entry<String, LocationRecord> entry : cells.entrySet()) {
            final CompoundTag cellTag = entry.getValue().write();
            cellTag.putString("name", entry.getKey());
            cellTags.add(cellTag);
        }
        return cellTags;
    }

    /** Mirrors GovSavedDataLoad jailCells read (lines 59-68). */
    private static Map<String, LocationRecord> readCellsSlice(final List<CompoundTag> tags) {
        final Map<String, LocationRecord> cells = new LinkedHashMap<>();
        for (final CompoundTag cellTag : tags) {
            final LocationRecord cell = LocationRecord.read(cellTag);
            if (cell != null && cell.isSet()) {
                final String name = cellTag.getString("name");
                final String key = name.isBlank() ? "cell_" + cells.size() : name;
                cells.put(key, cell);
            }
        }
        return cells;
    }

    void testSliceRoundTripPreservesCells() {
        final Map<String, LocationRecord> before = new LinkedHashMap<>();
        before.put("alpha", new LocationRecord(OVERWORLD, 100, 64, 200));
        before.put("beta", new LocationRecord(NETHER, -5, 70, 42));
        final CompoundTag root = new CompoundTag();
        NbtSupport.putCompoundList(root, "jailCells", writeCellsSlice(before));
        final Map<String, LocationRecord> after =
                readCellsSlice(NbtSupport.getCompoundList(root, "jailCells"));
        check(after.equals(before), "round trip should preserve names, dimensions and coords");
    }

    void testSliceEmptyRoundTrip() {
        final CompoundTag root = new CompoundTag();
        NbtSupport.putCompoundList(root, "jailCells", writeCellsSlice(Map.of()));
        final Map<String, LocationRecord> after =
                readCellsSlice(NbtSupport.getCompoundList(root, "jailCells"));
        check(after.isEmpty(), "empty cell map should round-trip to empty");
    }

    void testSliceBlankNameFallsBack() {
        final CompoundTag tag = new LocationRecord(OVERWORLD, 1, 2, 3).write();
        tag.putString("name", "");
        final CompoundTag root = new CompoundTag();
        NbtSupport.putCompoundList(root, "jailCells", List.of(tag));
        final Map<String, LocationRecord> after =
                readCellsSlice(NbtSupport.getCompoundList(root, "jailCells"));
        check(after.size() == 1 && after.containsKey("cell_0"),
                "blank name should fall back to 'cell_0'");
        check(after.get("cell_0").equals(new LocationRecord(OVERWORLD, 1, 2, 3)),
                "fallback cell should keep its coordinates");
    }

    void testSliceUnsetCellSkipped() {
        final CompoundTag unset = LocationRecord.NONE.write();
        unset.putString("name", "ghost");
        final CompoundTag kept = new LocationRecord(OVERWORLD, 9, 9, 9).write();
        kept.putString("name", "kept");
        final CompoundTag root = new CompoundTag();
        NbtSupport.putCompoundList(root, "jailCells", List.of(unset, kept));
        final Map<String, LocationRecord> after =
                readCellsSlice(NbtSupport.getCompoundList(root, "jailCells"));
        check(after.size() == 1 && after.containsKey("kept"),
                "unset cell should be skipped on load");
    }

    void testFullSavedDataRoundTrip() {
        final GovSavedData before = new GovSavedData();
        check(before.addRuntimeCell("alpha", new LocationRecord(OVERWORLD, 100, 64, 200)),
                "setup add alpha should succeed");
        check(before.addRuntimeCell("beta", new LocationRecord(NETHER, -5, 70, 42)),
                "setup add beta should succeed");
        final CompoundTag root = GovSavedDataSave.save(before, new CompoundTag(), null);
        check(root.contains("jailCells"), "saved tag should carry the jailCells root key");
        final GovSavedData after = GovSavedDataLoad.load(root, null);
        check(after.runtimeCells().equals(before.runtimeCells()),
                "load should restore every runtime cell");
        check(after.runtimeCells().get("alpha").equals(new LocationRecord(OVERWORLD, 100, 64, 200)),
                "alpha coordinates must survive save/load");
        check(after.runtimeCells().get("beta").equals(new LocationRecord(NETHER, -5, 70, 42)),
                "beta coordinates must survive save/load");
    }

    public static void main(final String[] args) {
        final JailCellsNbtRoundTripTest t = new JailCellsNbtRoundTripTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testSliceRoundTripPreservesCells", "testSliceEmptyRoundTrip",
                "testSliceBlankNameFallsBack", "testSliceUnsetCellSkipped",
                "testFullSavedDataRoundTrip"};
        final Runnable[] tests = {
                t::testSliceRoundTripPreservesCells, t::testSliceEmptyRoundTrip,
                t::testSliceBlankNameFallsBack, t::testSliceUnsetCellSkipped,
                t::testFullSavedDataRoundTrip};
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
