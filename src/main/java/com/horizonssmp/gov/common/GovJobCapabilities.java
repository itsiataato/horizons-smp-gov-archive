package com.horizonssmp.gov.common;

import static com.horizonssmp.gov.common.GovCapabilities.*;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Helper: seeded job capability bundles and the E0a economy capability migration helpers.
 * Extracted from {@link GovCapabilities}; {@link GovCapabilities} delegates here.
 */
final class GovJobCapabilities {

    private GovJobCapabilities() {
    }

    public static Set<GovCapability> builtinJobCapabilities(final String jobId) {
        if (jobId == null) {
            return EnumSet.noneOf(GovCapability.class);
        }
        return switch (jobId) {
            case CITIZEN_JOB_ID -> EnumSet.of(
                    GovCapability.PLAYER_LOOKUP_SELF,
                    GovCapability.LAW_VIEW);
            case GOVWORKER_JOB_ID -> EnumSet.of(
                    GovCapability.PLAYER_LOOKUP,
                    GovCapability.LAW_VIEW,
                    GovCapability.JOB_ASSIGN,
                    GovCapability.BUSINESS_REGISTER,
                    GovCapability.BUSINESS_MANAGE,
                    GovCapability.VEHICLE_REGISTER,
                    GovCapability.VEHICLE_RELEASE,
                    GovCapability.WEAPON_REGISTER,
                    GovCapability.WEAPON_VIEW,
                    GovCapability.FINE_WAIVE,
                    GovCapability.BANK_MANAGE,
                    GovCapability.GOV_PAYROLL);
            case POLICE_JOB_ID -> EnumSet.of(
                    GovCapability.PLAYER_LOOKUP_SELF,
                    GovCapability.PLAYER_LOOKUP,
                    GovCapability.LAW_VIEW,
                    GovCapability.NOTE_WRITE,
                    GovCapability.NOTE_VIEW_ALL,
                    GovCapability.FINE_ISSUE,
                    GovCapability.FINE_VIEW_ALL,
                    GovCapability.SUSPECT_SET,
                    GovCapability.PLATE_SCAN,
                    GovCapability.VEHICLE_IMPOUND,
                    GovCapability.WEAPON_VIEW,
                    GovCapability.JAIL_COMMIT,
                    GovCapability.JAIL_RELEASE,
                    GovCapability.DUTY_TOGGLE,
                    GovCapability.POLICE_PAYROLL);
            case GOVEXECUTIVE_JOB_ID -> EnumSet.allOf(GovCapability.class);
            case JUDGE_JOB_ID -> EnumSet.of(
                    GovCapability.PLAYER_LOOKUP,
                    GovCapability.LAW_VIEW,
                    GovCapability.NOTE_WRITE,
                    GovCapability.NOTE_VIEW_ALL,
                    GovCapability.FINE_VIEW_ALL,
                    GovCapability.FINE_WAIVE,
                    GovCapability.AUDIT_VIEW);
            case BUILDER_JOB_ID -> EnumSet.of(
                    GovCapability.PLAYER_LOOKUP_SELF,
                    GovCapability.LAW_VIEW);
            default -> EnumSet.noneOf(GovCapability.class);
        };
    }

    public static Map<String, Set<GovCapability>> economyCapabilityMigrationSeed() {
        final Map<String, Set<GovCapability>> seed = new LinkedHashMap<>();
        seed.put(GOVEXECUTIVE_JOB_ID, Set.copyOf(EnumSet.of(
                GovCapability.BANK_MANAGE,
                GovCapability.GOV_PAYROLL,
                GovCapability.POLICE_PAYROLL)));
        seed.put(GOVWORKER_JOB_ID, Set.copyOf(EnumSet.of(
                GovCapability.BANK_MANAGE,
                GovCapability.GOV_PAYROLL)));
        seed.put(POLICE_JOB_ID, Set.copyOf(EnumSet.of(GovCapability.POLICE_PAYROLL)));
        return Collections.unmodifiableMap(seed);
    }

    public static Set<GovCapability> economyCapabilityAdditions(
            final String jobId, final Collection<GovCapability> current) {
        final Set<GovCapability> additions = EnumSet.noneOf(GovCapability.class);
        final Set<GovCapability> seeded = economyCapabilityMigrationSeed().get(jobId);
        if (seeded == null) {
            return additions;
        }
        for (final GovCapability capability : seeded) {
            if (current == null || !current.contains(capability)) {
                additions.add(capability);
            }
        }
        return additions;
    }
}
