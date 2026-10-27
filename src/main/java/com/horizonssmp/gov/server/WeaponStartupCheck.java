package com.horizonssmp.gov.server;

import java.util.List;

/**
 * The M6 startup assertions: the classifier, permits, registry, explosion areas and every
 * enforcement decision.
 *
 * <p>A scratch data set, never the live one. Every name begins with {@code StartupCheck Weapon},
 * every serial begins with {@code STARTUP-}, and every UUID is from the reserved {@code 0x6000}
 * range, so anything that ever leaked into a log or a file identifies itself (tip t41). The live
 * inventory and position are behind {@link WeaponGateway}; the test gateway is an in-memory map,
 * so the whole decision set runs with no {@code ServerPlayer} (tip t36).
 *
 * <p>Called from {@link GovStartupCheck#run}; the returned number is added to the boot assertion
 * count. A failure names the property that broke, not the value that was expected, so the log line
 * is readable without reading this file.
 */
final class WeaponStartupCheck {

    private WeaponStartupCheck() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        try {
            checks += WeaponStartupCheckClassifier.checkClassifier(failures);
            checks += WeaponStartupCheckArticles.checkArticles(failures);
            checks += WeaponStartupCheckPermits.checkPermitMatrix(failures);
            checks += WeaponStartupCheckRegistry.checkRegistryMatrix(failures);
            checks += WeaponStartupCheckAreas.checkAreaMatrix(failures);
            checks += WeaponStartupCheckTnt.checkTntDecisions(failures);
            checks += WeaponStartupCheckOrdnance.checkOrdnanceDecisions(failures);
            checks += WeaponStartupCheckCarry.checkCarryDecisions(failures);
            checks += WeaponStartupCheckTownCores.checkTownCores(failures);
            checks += WeaponStartupCheckActionMatrix.checkActionMatrix(failures);
            checks += WeaponStartupCheckPersistence.checkPersistence(failures);
        } catch (final RuntimeException e) {
            checks++;
            failures.add("weapon rule check threw: " + e);
        }
        return checks;
    }
}
