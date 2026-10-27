package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Runs the server's startup contract checks. Category implementations live beside this facade. */
public final class GovStartupCheck {
    public record Result(int checks, List<String> failures) {
        public Result {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public boolean ok() {
            return failures.isEmpty();
        }
    }

    private GovStartupCheck() {
    }

    public static Result run(final GovSavedData data) {
        return run(data, null);
    }

    public static Result run(final GovSavedData data, final MinecraftServer server) {
        final List<String> failures = new ArrayList<>();
        int checks = 0;
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "capabilities", () -> GovStartupCheckCapabilities.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "opcodes", () -> GovStartupCheckOpcodes.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "payloadGuard", () -> GovStartupCheckPayloadGuard.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "rateLimiter", () -> GovStartupCheckRateLimiter.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "sanitizer", () -> GovStartupCheckSanitizer.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "lawIndex", () -> GovStartupCheckLawIndex.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "seededJobs", () -> GovStartupCheckSeededJobs.run(data, failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "jobs", () -> GovStartupCheckJobs.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "systemDefaultJob", () -> GovStartupCheckSystemDefaultJob.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "people", () -> GovStartupCheckPeople.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "scopedNotes", () -> GovStartupCheckScopedNotes.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "fines", () -> GovStartupCheckFines.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "vehicles", () -> GovStartupCheckVehicles.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "businesses", () -> GovStartupCheckBusinesses.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "businessEmploymentModel",
                () -> GovStartupCheckBusinessEmploymentModel.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "businessEmploymentService",
                () -> GovStartupCheckBusinessEmploymentService.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "detention", () -> GovStartupCheckDetention.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "weapon", () -> WeaponStartupCheck.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "safety", () -> GovSafetyStartupCheck.run(data, failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "network", () -> GovStartupCheckNetwork.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "fields", () -> GovStartupCheckFields.run(failures));
        checks += GovGlobalGuard.guardStartupCheck(
                failures, "economy", () -> GovStartupCheckEconomy.run(data, server, failures));
        return new Result(checks, failures);
    }
}
