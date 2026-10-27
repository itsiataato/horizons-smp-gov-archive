package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.records.LocationRecord;

import net.minecraft.server.MinecraftServer;

/**
 * TASK-012 guard proof tests: {@code GovGlobalGuard} never lets a throw reach the server thread.
 *
 * <p>Acceptance criteria: a null-payload dispatch becomes an {@code INVALID_STATE} refusal (never a
 * throw); a throwing handler (including {@code Error}) becomes an {@code INVALID_STATE} refusal; a
 * throwing audit is warn-skipped with the handler result intact; one throwing startup check is
 * recorded and the rest still run; {@code GovSavedData.get} with a null server fails with an
 * actionable {@code IllegalStateException} (asserted message, not a crash); a junk dimension is a
 * {@code BAD_PAYLOAD} refusal via {@code GovJailCellHandlers}; deleting the middle cell leaves
 * dense rows (create a,b,c, delete b, rows 0,1 hold a,c).
 *
 * <p>Runs headless: an explicit {@code DetentionService.Policy} is passed (never null, so no
 * config read), the contexts carry a {@code null} actor/target, and no test touches
 * {@code FMLEnvironment} or the config spec (see tips/t08). Lives in the server package because
 * the cell handlers are package-private by design.
 */
final class GovGlobalGuardTest {

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

    private static GovActionDispatcher.ActionContext createCtx(
            final Map<String, String> payload, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.JAIL_CELL_CREATE, payload, "req-create"),
                null, data, null, Set.of(), null, 0L);
    }

    void testNullPayloadDispatchBecomesRefusal() {
        final ActionResult pre = GovGlobalGuard.guardPreHandle(
                OpCode.JAIL_CELL_CREATE, "req-null", () -> {
                    throw new NullPointerException("simulated null payload");
                });
        check(pre != null, "pre-handle guard must return a refusal, never null");
        check(!pre.ok() && pre.errorCode() == ErrorCode.INVALID_STATE,
                "null-payload pre-handle must refuse INVALID_STATE, got " + pre);
        check("req-null".equals(pre.reqId()), "refusal must carry the request id");
        final ActionResult nullResult = GovGlobalGuard.guardHandler(
                OpCode.JAIL_CELL_CREATE, "actor", "req-null2", () -> null);
        check(nullResult != null && !nullResult.ok()
                        && nullResult.errorCode() == ErrorCode.INVALID_STATE,
                "null handler result must refuse INVALID_STATE, got " + nullResult);
        check("req-null2".equals(nullResult.reqId()), "null-result refusal must carry the request id");
    }

    void testThrowingHandlerBecomesInvalidState() {
        final ActionResult runtime = GovGlobalGuard.guardHandler(
                OpCode.JAIL_CELL_CREATE, "actor", "req-throw", () -> {
                    throw new IllegalStateException("boom");
                });
        check(!runtime.ok() && runtime.errorCode() == ErrorCode.INVALID_STATE,
                "throwing handler must refuse INVALID_STATE, got " + runtime);
        check("req-throw".equals(runtime.reqId()), "refusal must carry the request id");
        final ActionResult error = GovGlobalGuard.guardHandler(
                OpCode.JAIL_CELL_CREATE, "actor", "req-error", () -> {
                    throw new AssertionError("boom-error");
                });
        check(!error.ok() && error.errorCode() == ErrorCode.INVALID_STATE,
                "throwing Error must refuse INVALID_STATE, got " + error);
    }

    void testThrowingAuditSkippedResultIntact() {
        final ActionResult result = ActionResult.success("req-audit", "cell registered");
        boolean thrown = false;
        try {
            GovGlobalGuard.guardAudit(() -> {
                throw new IllegalStateException("audit boom");
            });
        } catch (final Throwable t) {
            thrown = true;
        }
        check(!thrown, "a throwing audit must be warn-skipped, never propagated");
        check(result.ok() && "cell registered".equals(result.delta()),
                "the handler result must stay intact after the audit throw");
    }

    void testFailingStartupCheckRecordsRestRun() {
        final List<String> failures = new ArrayList<>();
        final int first = GovGlobalGuard.guardStartupCheck(failures, "ok-one", () -> 3);
        final int broken = GovGlobalGuard.guardStartupCheck(failures, "boom", () -> {
            throw new IllegalStateException("boom");
        });
        final int second = GovGlobalGuard.guardStartupCheck(failures, "ok-two", () -> 5);
        check(first == 3, "first check must return 3, got " + first);
        check(broken == 0, "throwing check must record 0, got " + broken);
        check(second == 5, "checks after a failure must still run, got " + second);
        check(failures.size() == 1 && failures.get(0).contains("boom"),
                "exactly the throwing check must be recorded, got " + failures);
    }

    void testSavedDataNullOverworldActionableIse() {
        try {
            GovSavedData.get((MinecraftServer) null);
            check(false, "GovSavedData.get(null server) must throw, not return or crash");
        } catch (final IllegalStateException e) {
            check(e.getMessage() != null && e.getMessage().contains("saved data unavailable"),
                    "ISE must be actionable ('saved data unavailable'), got: " + e.getMessage());
            check(e.getMessage().contains("not ready"),
                    "ISE must say the world is not ready, got: " + e.getMessage());
        } catch (final Throwable t) {
            check(false, "null server must give an actionable ISE, not " + t);
        }
        try {
            GovGlobalGuard.guardSavedData(null);
            check(false, "guardSavedData(null) must throw, not return or crash");
        } catch (final IllegalStateException e) {
            check(e.getMessage() != null && e.getMessage().contains("not ready"),
                    "guard ISE must say the world is not ready, got: " + e.getMessage());
        } catch (final Throwable t) {
            check(false, "guardSavedData(null) must give an actionable ISE, not " + t);
        }
    }

    void testJunkDimensionIsBadPayload() {
        final GovSavedData data = new GovSavedData();
        final String[] junks = {"::::junk", "not a dimension!!!"};
        for (int i = 0; i < junks.length; i++) {
            final ActionResult result = GovJailCellHandlers.jailCellCreate(createCtx(Map.of(
                    JailKeys.KEY_CELL_NAME, "junk-" + i,
                    JailKeys.KEY_CELL_X, "1",
                    JailKeys.KEY_CELL_Y, "64",
                    JailKeys.KEY_CELL_Z, "0",
                    JailKeys.KEY_CELL_DIMENSION, junks[i]), data));
            check(!result.ok() && result.errorCode() == ErrorCode.BAD_PAYLOAD,
                    "dimension '" + junks[i] + "' must be BAD_PAYLOAD, got " + result);
        }
        check(data.runtimeCells().isEmpty(), "no cell should be stored after junk dimensions");
    }

    void testDeleteMiddleKeepsDenseIndex() {
        final GovSavedData data = new GovSavedData();
        check(data.addRuntimeCell("cell-a", new LocationRecord(OVERWORLD, 1, 64, 0)),
                "setup: cell-a should register");
        check(data.addRuntimeCell("cell-b", new LocationRecord(OVERWORLD, 2, 64, 0)),
                "setup: cell-b should register");
        check(data.addRuntimeCell("cell-c", new LocationRecord(OVERWORLD, 3, 64, 0)),
                "setup: cell-c should register");
        check(data.removeRuntimeCell("cell-b"), "setup: cell-b delete should succeed");
        final Map<String, String> fields = list(data);
        check("2".equals(fields.get(JailKeys.CELL_LIST_COUNT)),
                "cellCount must reflect the store (\"2\"), got "
                        + fields.get(JailKeys.CELL_LIST_COUNT));
        check("cell-a".equals(fields.get(cellKey(0, JailKeys.CELL_ROW_NAME))),
                "cell.0 should be cell-a after the middle delete");
        check("cell-c".equals(fields.get(cellKey(1, JailKeys.CELL_ROW_NAME))),
                "rows must stay dense with no gap: cell.1 should be cell-c");
        check(fields.get(cellKey(2, JailKeys.CELL_ROW_NAME)) == null,
                "no cell.2 row should exist for two cells");
    }

    public static void main(final String[] args) {
        final GovGlobalGuardTest t = new GovGlobalGuardTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testNullPayloadDispatchBecomesRefusal", "testThrowingHandlerBecomesInvalidState",
                "testThrowingAuditSkippedResultIntact", "testFailingStartupCheckRecordsRestRun",
                "testSavedDataNullOverworldActionableIse", "testJunkDimensionIsBadPayload",
                "testDeleteMiddleKeepsDenseIndex"};
        final Runnable[] tests = {
                t::testNullPayloadDispatchBecomesRefusal, t::testThrowingHandlerBecomesInvalidState,
                t::testThrowingAuditSkippedResultIntact, t::testFailingStartupCheckRecordsRestRun,
                t::testSavedDataNullOverworldActionableIse, t::testJunkDimensionIsBadPayload,
                t::testDeleteMiddleKeepsDenseIndex};
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
