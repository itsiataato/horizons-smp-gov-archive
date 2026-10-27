package com.horizonssmp.gov.server;

import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * TASK-002/TASK-003 (FR-1, FR-4) unit test: {@code GovJailCellHandlers} validation.
 *
 * <p>Acceptance criteria: blank name and non-integer coords are {@code BAD_PAYLOAD}; a duplicate
 * create is {@code NAME_TAKEN}; deleting a missing cell is {@code INVALID_STATE}. The handlers
 * only touch {@code payload/request/data}, so the {@code ActionContext} carries a {@code null}
 * actor and target and runs headless (see tips/t08). This class lives in the server package
 * because the handlers are package-private by design.
 */
final class JailCellHandlerValidationTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
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

    private static Map<String, String> createPayload(
            final String name, final String x, final String y, final String z,
            final String dimension) {
        return Map.of(
                JailKeys.KEY_CELL_NAME, name,
                JailKeys.KEY_CELL_X, x,
                JailKeys.KEY_CELL_Y, y,
                JailKeys.KEY_CELL_Z, z,
                JailKeys.KEY_CELL_DIMENSION, dimension);
    }

    void testCreateBlankNameIsBadPayload() {
        final GovSavedData data = new GovSavedData();
        final ActionResult empty = GovJailCellHandlers.jailCellCreate(
                createCtx(createPayload("", "1", "2", "3", OVERWORLD), data));
        check(!empty.ok() && empty.errorCode() == ErrorCode.BAD_PAYLOAD,
                "empty name should be BAD_PAYLOAD");
        final ActionResult blank = GovJailCellHandlers.jailCellCreate(
                createCtx(createPayload("   ", "1", "2", "3", OVERWORLD), data));
        check(!blank.ok() && blank.errorCode() == ErrorCode.BAD_PAYLOAD,
                "blank name should be BAD_PAYLOAD");
    }

    void testCreateNonIntegerCoordsIsBadPayload() {
        final GovSavedData data = new GovSavedData();
        for (final String bad : new String[] {"twelve", "10.5", "", "0x10"}) {
            final ActionResult result = GovJailCellHandlers.jailCellCreate(
                    createCtx(createPayload("cell-" + bad, bad, "64", "0", OVERWORLD), data));
            check(!result.ok() && result.errorCode() == ErrorCode.BAD_PAYLOAD,
                    "x='" + bad + "' should be BAD_PAYLOAD");
        }
        check(data.runtimeCells().isEmpty(), "no cell should be stored after bad coords");
    }

    void testCreateBlankDimensionIsBadPayload() {
        final GovSavedData data = new GovSavedData();
        final ActionResult result = GovJailCellHandlers.jailCellCreate(
                createCtx(createPayload("alpha", "1", "64", "0", "  "), data));
        check(!result.ok() && result.errorCode() == ErrorCode.BAD_PAYLOAD,
                "blank dimension should be BAD_PAYLOAD");
    }

    void testCreateDuplicateIsNameTaken() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup add should succeed");
        final ActionResult result = GovJailCellHandlers.jailCellCreate(
                createCtx(createPayload("alpha", "9",  "9", "9", OVERWORLD), data));
        check(!result.ok() && result.errorCode() == ErrorCode.NAME_TAKEN,
                "duplicate create should be NAME_TAKEN, got " + result.errorCode());
    }

    void testCreateValidStoresCell() {
        final GovSavedData data = new GovSavedData();
        final ActionResult result = GovJailCellHandlers.jailCellCreate(
                createCtx(createPayload("alpha", "100", "64", "200", OVERWORLD), data));
        check(result.ok(), "valid create should succeed: " + result.errorText());
        check(data.runtimeCells().get("alpha")
                        .equals(new LocationRecord(OVERWORLD, 100, 64, 200)),
                "stored cell should carry the exact coordinates and dimension");
    }

    void testDeleteMissingIsInvalidState() {
        final GovSavedData data = new GovSavedData();
        final ActionResult result =
                GovJailCellHandlers.jailCellDelete(deleteCtx("ghost", data));
        check(!result.ok() && result.errorCode() == ErrorCode.INVALID_STATE,
                "deleting a missing cell should be INVALID_STATE");
    }

    void testDeleteBlankIsBadPayload() {
        final GovSavedData data = new GovSavedData();
        final ActionResult result =
                GovJailCellHandlers.jailCellDelete(deleteCtx("   ", data));
        check(!result.ok() && result.errorCode() == ErrorCode.BAD_PAYLOAD,
                "blank delete name should be BAD_PAYLOAD");
    }

    void testDeleteExistingRemovesCell() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("alpha", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup add should succeed");
        final ActionResult result =
                GovJailCellHandlers.jailCellDelete(deleteCtx("alpha", data));
        check(result.ok(), "valid delete should succeed: " + result.errorText());
        check(!data.runtimeCells().containsKey("alpha"), "cell should be gone after delete");
    }

    public static void main(final String[] args) {
        final JailCellHandlerValidationTest t = new JailCellHandlerValidationTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testCreateBlankNameIsBadPayload", "testCreateNonIntegerCoordsIsBadPayload",
                "testCreateBlankDimensionIsBadPayload", "testCreateDuplicateIsNameTaken",
                "testCreateValidStoresCell", "testDeleteMissingIsInvalidState",
                "testDeleteBlankIsBadPayload", "testDeleteExistingRemovesCell"};
        final Runnable[] tests = {
                t::testCreateBlankNameIsBadPayload, t::testCreateNonIntegerCoordsIsBadPayload,
                t::testCreateBlankDimensionIsBadPayload, t::testCreateDuplicateIsNameTaken,
                t::testCreateValidStoresCell, t::testDeleteMissingIsInvalidState,
                t::testDeleteBlankIsBadPayload, t::testDeleteExistingRemovesCell};
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
