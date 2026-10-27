package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * TASK-003 follow-up unit test: runtime-cell enumeration in {@code JAIL_LIST}.
 *
 * <p>Acceptance criteria: {@code DetentionViews.listFields} emits {@code cellCount} as the true
 * total (uncapped, {@code "0"} when data is null) plus {@code cell.&lt;i&gt;.name/.dimension/.x/.y/.z}
 * rows in insertion order, capped at {@link JailKeys#MAX_LISTED_CELLS}. Names are sanitized via
 * {@code Sanitizer.name}; the dimension is verbatim; rows stay dense with no gaps after a
 * middle delete (the backend null-skip in {@code DetentionViews} stays as defense for nulls that
 * are unreachable via {@code addRuntimeCell} and NBT load, both of which validate). A
 * create-to-list-to-delete round trip through {@code GovJailCellHandlers} proves the row
 * keys appear and vanish.
 *
 * <p>Runs headless: an explicit {@code DetentionService.Policy} is passed (never null, so no
 * config read for the policy), and the remaining config touch ({@code GovConfig.jailCells()},
 * empty by default outside a loaded spec) plus {@code new GovSavedData()} need no FML/Minecraft
 * runtime (see tips/t08). Lives in the server package because the handlers and the personnel
 * store map are package-private by design.
 */
final class JailCellListEnumerationTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final long NOW = 1_700_000_000_000L;

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static DetentionService.Policy policy() {
        return new DetentionService.Policy(10, 140, false, true, "", LocationRecord.NONE, 12.0d);
    }

    private static Map<String, String> list(final GovSavedData data) {
        return DetentionViews.listFields(
                data, null, "Viewer", Set.of(), "active", null, 0, List.of(), policy(), NOW);
    }

    private static String cellKey(final int index, final String field) {
        return JailKeys.CELL_ROW_PREFIX + index + field;
    }

    private static int cellRowKeyCount(final Map<String, String> fields) {
        int count = 0;
        for (final String key : fields.keySet()) {
            if (key.startsWith(JailKeys.CELL_ROW_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    private static GovActionDispatcher.ActionContext createCtx(
            final Map<String, String> payload, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.JAIL_CELL_CREATE, payload, "req-create"),
                null, data, null, Set.of(), null, 0L);
    }

    private static GovActionDispatcher.ActionContext deleteCtx(
            final String name, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.JAIL_CELL_DELETE,
                        Map.of(JailKeys.KEY_CELL_NAME, name), "req-delete"),
                null, data, null, Set.of(), null, 0L);
    }

    void testNullDataGivesZeroCountAndNoCellRows() {
        final Map<String, String> fields = list(null);
        check("0".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "null data should give cellCount \"0\", got " + fields.get(JailKeys.CELL_LIST_COUNT));
        check(cellRowKeyCount(fields) == 0, "null data should emit no cell.* row keys");
    }

    void testInsertionOrderPreserved() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup: alpha should register");
        check(data.addRuntimeCell("beta", new LocationRecord(OVERWORLD, 2, 65, 0)),
                "setup: beta should register");
        check(data.addRuntimeCell("gamma", new LocationRecord("minecraft:the_nether", 3, 66, 0)),
                "setup: gamma should register");
        final Map<String, String> fields = list(data);
        check("3".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "three cells should give cellCount \"3\"");
        check("alpha".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_NAME))),
                "cell.0 should be alpha");
        check("beta".equals(fields.get(cellKey(1, JailKeys.CELL_ROW_NAME))),
                "cell.1 should be beta");
        check("gamma".equals(fields.get(cellKey(2, JailKeys.CELL_ROW_NAME))),
                "cell.2 should be gamma");
        check("1".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_X)))
                && "64".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_Y)))
                && "0".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_Z))),
                "cell.0 should carry alpha's coordinates");
        check(fields.get(cellKey(3, JailKeys.CELL_ROW_NAME)) == null,
                "no cell.3 row should exist for three cells");
    }

    void testCapKeepsTrueTotal() {
        final GovSavedData data = new GovSavedData();
        for (int i = 0; i < 70; i++) {
            final String name = "cell-" + (i < 10 ? "0" + i : Integer.toString(i));
            check(data.addRuntimeCell(name, new LocationRecord(OVERWORLD, i, 64, i)),
                    "setup: " + name + " should register");
        }
        final Map<String, String> fields = list(data);
        check("70".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "cellCount should be the true total \"70\", got "
                        + fields.get(JailKeys.CELL_LIST_COUNT));
        check("cell-63".equals(fields.get(cellKey(63, JailKeys.CELL_ROW_NAME))),
                "cell.63 should be the last listed row");
        check(fields.get(cellKey(64, JailKeys.CELL_ROW_NAME)) == null,
                "cell.64.name must be absent above the 64-row cap");
        check(fields.get(cellKey(64, JailKeys.CELL_ROW_DIMENSION)) == null
                && fields.get(cellKey(64, JailKeys.CELL_ROW_X)) == null,
                "no cell.64 row fields of any kind should be emitted");
        check(cellRowKeyCount(fields) == 64 * 5,
                "exactly 64 rows x 5 fields of cell.* keys expected, got "
                        + cellRowKeyCount(fields));
    }

    void testNameSanitizationApplied() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("\u00a7aRed  Cell", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup: formatted name should register");
        check(data.addRuntimeCell("tab\tname", new LocationRecord(OVERWORLD, 2, 64, 0)),
                "setup: tabbed name should register");
        final Map<String, String> fields = list(data);
        check("Red Cell".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_NAME))),
                "formatting code should be stripped and spaces collapsed, got '"
                        + fields.get(cellKey(0, JailKeys.CELL_ROW_NAME)) + "'");
        check("tab name".equals(fields.get(cellKey(1, JailKeys.CELL_ROW_NAME))),
                "tab should become a single space, got '"
                        + fields.get(cellKey(1, JailKeys.CELL_ROW_NAME)) + "'");
    }

    void testDimensionVerbatim() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("home", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup: home should register");
        check(data.addRuntimeCell("custom", new LocationRecord("Custom:My_Dim-09", 2, 64, 0)),
                "setup: custom should register");
        final Map<String, String> fields = list(data);
        check(OVERWORLD.equals(fields.get(cellKey(0, JailKeys.CELL_ROW_DIMENSION))),
                "minecraft:overworld must survive verbatim, got '"
                        + fields.get(cellKey(0, JailKeys.CELL_ROW_DIMENSION)) + "'");
        check("Custom:My_Dim-09".equals(fields.get(cellKey(1, JailKeys.CELL_ROW_DIMENSION))),
                "mixed-case dimension must not be lowercased, got '"
                        + fields.get(cellKey(1, JailKeys.CELL_ROW_DIMENSION)) + "'");
    }

    void testDeleteMiddleKeepsDenseIndex() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup: alpha should register");
        check(data.addRuntimeCell("beta", new LocationRecord(OVERWORLD, 2, 64, 0)),
                "setup: beta should register");
        check(data.addRuntimeCell("gamma", new LocationRecord(OVERWORLD, 3, 64, 0)),
                "setup: gamma should register");
        check(data.removeRuntimeCell("beta"), "setup: beta delete should succeed");
        final Map<String, String> fields = list(data);
        check("alpha".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_NAME))),
                "cell.0 should be alpha after the middle delete");
        check("gamma".equals(fields.get(cellKey(1, JailKeys.CELL_ROW_NAME))),
                "rows must stay dense with no gap: cell.1 should be gamma");
        check(fields.get(cellKey(2, JailKeys.CELL_ROW_NAME)) == null,
                "no cell.2 row should exist for two cells");
        check("2".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "cellCount must reflect the store (\"2\"), got "
                        + fields.get(JailKeys.CELL_LIST_COUNT));
    }

    void testCreateListDeleteRoundTrip() {
        final GovSavedData data = new GovSavedData();
        final ActionResult created = GovJailCellHandlers.jailCellCreate(createCtx(Map.of(
                JailKeys.KEY_CELL_NAME, "roundtrip",
                JailKeys.KEY_CELL_X, "100",
                JailKeys.KEY_CELL_Y, "64",
                JailKeys.KEY_CELL_Z, "200",
                JailKeys.KEY_CELL_DIMENSION, OVERWORLD), data));
        check(created.ok(), "create should succeed: " + created.errorText());
        Map<String, String> fields = list(data);
        check("1".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "after create cellCount should be \"1\"");
        check("roundtrip".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_NAME))),
                "after create cell.0.name should be roundtrip");
        check(OVERWORLD.equals(fields.get(cellKey(0, JailKeys.CELL_ROW_DIMENSION)))
                && "100".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_X)))
                && "64".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_Y)))
                && "200".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_Z))),
                "after create cell.0 should carry the created coordinates");
        final ActionResult deleted =
                GovJailCellHandlers.jailCellDelete(deleteCtx("roundtrip", data));
        check(deleted.ok(), "delete should succeed: " + deleted.errorText());
        fields = list(data);
        check("0".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "after delete cellCount should be \"0\"");
        check(cellRowKeyCount(fields) == 0, "after delete no cell.* row keys should remain");
    }

    public static void main(final String[] args) {
        final JailCellListEnumerationTest t = new JailCellListEnumerationTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testNullDataGivesZeroCountAndNoCellRows", "testInsertionOrderPreserved",
                "testCapKeepsTrueTotal", "testNameSanitizationApplied",
                "testDimensionVerbatim", "testDeleteMiddleKeepsDenseIndex",
                "testCreateListDeleteRoundTrip"};
        final Runnable[] tests = {
                t::testNullDataGivesZeroCountAndNoCellRows, t::testInsertionOrderPreserved,
                t::testCapKeepsTrueTotal, t::testNameSanitizationApplied,
                t::testDimensionVerbatim, t::testDeleteMiddleKeepsDenseIndex,
                t::testCreateListDeleteRoundTrip};
        for (int i = 0; i < names.length; i++) {
            try {
                tests[i].run();
                passed++;
                System.out.println("PASS " + names[i]);
            } catch (final Throwable e) {
                failed++;
                System.out.println("FAIL " + names[i] + ": " + e);
                e.printStackTrace(System.out);
            }
        }
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
