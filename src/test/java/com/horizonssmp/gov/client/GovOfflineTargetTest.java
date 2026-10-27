package com.horizonssmp.gov.client;

import java.util.List;

/** Headless unit tests for GovOfflineTarget (TASK-023). Runs via {@code main()}. */
final class GovOfflineTargetTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(final boolean condition, final String name) {
        if (condition) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    public static void main(final String[] args) {
        check("".equals(GovOfflineTarget.clean(null)), "cleanNull");
        check("Alex".equals(GovOfflineTarget.clean("  Alex\t")), "cleanTrims");
        check(GovOfflineTarget.isBlank(null), "blankNull");
        check(GovOfflineTarget.isBlank("   "), "blankWhitespace");
        check(!GovOfflineTarget.isBlank("Sam"), "nonBlank");
        check("Alex".equals(GovOfflineTarget.orQuickPick("  Alex ", "Sam")),
                "typedWins");
        check("Sam".equals(GovOfflineTarget.orQuickPick("  ", "Sam")),
                "blankFallsBack");
        check("".equals(GovOfflineTarget.orQuickPick(null, null)), "bothBlank");
        check("n".equals(GovOfflineTarget.blankKey(true, "n", "t")), "blankKeyNoOnline");
        check("t".equals(GovOfflineTarget.blankKey(false, "n", "t")),
                "blankKeyNoTarget");
        final List<String> online = List.of("A", "B", "C");
        check("B".equals(GovOfflineTarget.stepQuickPick(online, "A", 1)), "stepFwd");
        check("C".equals(GovOfflineTarget.stepQuickPick(online, "A", -1)), "stepWrap");
        check("C".equals(GovOfflineTarget.stepQuickPick(online, "Zzz", 5)),
                "unknownRestartsHeadThenSteps");
        check("A".equals(GovOfflineTarget.stepQuickPick(online, "Zzz", 0)),
                "unknownRestartsHead");
        check(GovOfflineTarget.MAX_NAME == 16, "maxNameCeiling");
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            throw new AssertionError(failed + " failures");
        }
    }
}
