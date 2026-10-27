package com.horizonssmp.gov.client;

/** Headless suites for the element-level fit math. Runs via {@code main()}, no client needed. */
final class GovFitTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    void testRowsThatFit() {
        check(GovFit.rowsThatFit(100, 20, 7) == 5, "100/20 capped at 7 should be 5");
        check(GovFit.rowsThatFit(200, 20, 7) == 7, "200/20 should cap at 7");
        check(GovFit.rowsThatFit(19, 20, 7) == 0, "partial row should not count");
        check(GovFit.rowsThatFit(0, 20, 7) == 0, "zero space should give zero rows");
        check(GovFit.rowsThatFit(100, 0, 7) == 0, "zero row height should give zero rows");
        check(GovFit.rowsThatFit(100, 20, 0) == 0, "zero cap should give zero rows");
        check(GovFit.rowsThatFit(-5, 20, 7) == 0, "negative space should give zero rows");
    }

    void testFitsWithin() {
        check(GovFit.fitsWithin(10, 20, 10, 30), "exact full span should fit");
        check(!GovFit.fitsWithin(9, 20, 10, 30), "row above top should not fit");
        check(!GovFit.fitsWithin(11, 20, 10, 30), "row past bottom should not fit");
        check(!GovFit.fitsWithin(10, 21, 10, 30), "tall row should not fit");
        check(GovFit.fitsWithin(10, 0, 10, 30), "zero-height row at top should fit");
    }

    void testShrinkStep() {
        check(GovFit.shrinkStep(100, 120, 100) == 79, "100 fit, 20 over should give 79");
        check(GovFit.shrinkStep(5, 200, 100) == 0, "large overflow should floor at zero");
        final int first = GovFit.shrinkStep(100, 101, 100);
        check(first == 98, "1 over should step to 98, got " + first);
        check(first < 100, "shrink step must always move down");
    }

    public static void main(final String[] args) {
        final GovFitTest t = new GovFitTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {"testRowsThatFit", "testFitsWithin", "testShrinkStep"};
        final Runnable[] runs = {t::testRowsThatFit, t::testFitsWithin, t::testShrinkStep};
        for (int i = 0; i < names.length; i++) {
            try {
                runs[i].run();
                passed++;
                System.out.println("PASS " + names[i]);
            } catch (final Throwable e) {
                failed++;
                System.out.println("FAIL " + names[i] + ": " + e.getMessage());
            }
        }
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
