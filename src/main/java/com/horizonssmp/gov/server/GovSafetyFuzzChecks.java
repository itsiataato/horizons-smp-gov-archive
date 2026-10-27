package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.enums.BusinessType;
import com.horizonssmp.gov.common.enums.DetentionStatus;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.ImpoundReason;
import com.horizonssmp.gov.common.enums.LegalBasis;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.ScanResult;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.law.LawIndex;
import com.horizonssmp.gov.law.LawIndex;

final class GovSafetyFuzzChecks {

    private static final int MIN_CORPUS_CASES = 200;

    private GovSafetyFuzzChecks() {
    }

    /** A deterministic hostile-input corpus across every parser the dispatcher can reach. */
    static int checkFuzzCorpus(final List<String> failures) {
        int checks = 0;
        final List<String> corpus = List.of(
                "",
                " ",
                "\u0000",
                "\u0001\u0002\u0007\u001b",
                "line\nbreak\r\ttab",
                "\u00a7cRED\u00a7lBOLD\u00a7r",
                "caf\u00e9 \u2603 \ud83d\ude00",
                "\ud800",
                "\udfff",
                "x".repeat(20_000),
                "A".repeat(5_000),
                "../../etc/passwd",
                "..\\..\\windows\\system32",
                "%2e%2e%2f%2e%2e%2f",
                "a/b/c",
                "C:\\temp\\gov.json",
                "null",
                "NaN",
                "Infinity",
                "-Infinity",
                "-1",
                "2147483648",
                "9223372036854775808",
                "999999999999999999999999999999",
                "1e309",
                "0",
                "-0",
                "  spaced  ",
                "=",
                "\\",
                "a=b\nc=d",
                "\uFF0F\uFF0E\uFF0E\uFF0F",
                "DROP TABLE players;--",
                "\u202eRTL",
                "ABC-123",
                "ABC123",
                "9.11",
                "WARRANT",
                "police");

        for (final String value : corpus) {
            final String label = GovSafetyHelpers.label(value);
            checks += GovSafetyHelpers.runCase(failures, "GovFields.decode " + label,
                    () -> GovFields.decode(value));
            checks += GovSafetyHelpers.runCase(failures, "GovFields.encode " + label,
                    () -> GovFields.encode(Map.of("k", value)));
            checks += GovSafetyHelpers.runCase(failures, "Sanitizer.name " + label,
                    () -> Sanitizer.name(value));
            checks += GovSafetyHelpers.runCase(failures, "Sanitizer.text " + label,
                    () -> Sanitizer.text(value));
            checks += GovSafetyHelpers.runCase(failures, "Sanitizer.id " + label,
                    () -> Sanitizer.id(value));
            checks += GovSafetyHelpers.runCase(failures, "Sanitizer.multiLine " + label,
                    () -> Sanitizer.multiLine(value, 64));
            checks += GovSafetyHelpers.runCase(failures, "Sanitizer.payloadSummary " + label,
                    () -> Sanitizer.payloadSummary(Map.of("k", value)));
            checks += GovSafetyHelpers.runCase(failures, "OpCode.byName " + label,
                    () -> OpCode.byName(value));
            checks += GovSafetyHelpers.runCase(failures, "GovCapability.byId " + label,
                    () -> GovCapability.byId(value));
            checks += GovSafetyHelpers.runCase(failures, "GovExportSection.byId " + label,
                    () -> GovExportSection.byId(value));
            checks += GovSafetyHelpers.runCase(failures, "AuditOutcome.byId " + label,
                    () -> AuditOutcome.byId(value));
            checks += GovSafetyHelpers.runCase(failures, "AuditOutcome.parse " + label,
                    () -> AuditOutcome.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "LicenseClass.parse " + label,
                    () -> LicenseClass.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "PermitClass.parse " + label,
                    () -> PermitClass.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "WeaponClass.parse " + label,
                    () -> WeaponClass.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "WeaponCategory.parse " + label,
                    () -> WeaponCategory.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "BusinessType.parse " + label,
                    () -> BusinessType.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "BusinessRole.parse " + label,
                    () -> BusinessRole.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "FineBand.parse " + label,
                    () -> FineBand.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "LegalBasis.parse " + label,
                    () -> LegalBasis.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "DetentionStatus.parse " + label,
                    () -> DetentionStatus.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "VehicleStatus.parse " + label,
                    () -> VehicleStatus.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "ScanResult.parse " + label,
                    () -> ScanResult.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "ImpoundReason.parse " + label,
                    () -> ImpoundReason.parse(value));
            checks += GovSafetyHelpers.runCase(failures, "VehicleService.canonicalPlate " + label,
                    () -> VehicleService.canonicalPlate(value));
            checks += GovSafetyHelpers.runCase(failures, "VehicleService.parsePoints " + label,
                    () -> VehicleService.parsePoints(value));
            checks += GovSafetyHelpers.runCase(failures, "LawIndex.exists " + label,
                    () -> LawIndex.get().exists(value));
            checks += GovSafetyHelpers.runCase(failures, "PersonService.textPrecheck " + label,
                    () -> PersonService.textPrecheck(value));
            checks += GovSafetyHelpers.runCase(failures, "FineService.parseBand " + label,
                    () -> FineService.parseBand(value));
            checks += GovSafetyHelpers.runCase(failures, "DetentionService.parseBasis " + label,
                    () -> DetentionService.parseBasis(value));
            checks += GovSafetyHelpers.runCase(failures, "FineService.issuePrecheck " + label,
                    () -> FineService.issuePrecheck(
                            "BOGUS", "9.11", value, "", value, value));
        }

        // Value-bound cases that need a real data set and a real actor. The band is always bogus,
        // so every one of these is a refusal path and none can write a record.
        final long now = System.currentTimeMillis();
        final GovSavedData scratch = new GovSavedData();
        scratch.seedIfNeeded(now, false);
        final UUID actor = new UUID(0L, 21L);
        final UUID target = new UUID(0L, 22L);
        final FineService.Actor fineActor = new FineService.Actor(
                actor, "Fuzz", EnumSet.of(GovCapability.FINE_ISSUE));
        for (final String value : corpus) {
            checks += GovSafetyHelpers.runCase(failures, "FineService.issue bounds " + GovSafetyHelpers.label(value),
                    () -> FineService.issue(
                            scratch, fineActor, target, "Target", "BOGUS", "9.11",
                            value, value, value, value, now));
            checks += GovSafetyHelpers.runCase(failures, "WeaponService.issuePermit bounds " + GovSafetyHelpers.label(value),
                    () -> WeaponService.issuePermit(
                            scratch,
                            new WeaponService.Actor(
                                    actor, "Fuzz", EnumSet.of(GovCapability.WEAPON_REGISTER)),
                            target, "Target", value, value, 0L, now));
            checks += GovSafetyHelpers.runCase(failures, "DetentionService.detain bounds " + GovSafetyHelpers.label(value),
                    () -> DetentionService.detain(
                            scratch,
                            new DetentionService.Actor(
                                    actor, "Fuzz", EnumSet.of(GovCapability.JAIL_COMMIT)),
                            target, "Target", "consent", "", value, value, "true",
                            JailGateway.NONE, DetentionService.Policy.fromConfig(), now));
        }
        checks++;
        if (FineService.parseBand("not-a-band") != null) {
            failures.add("strict fine band parser accepted an unknown band");
        }
        checks++;
        if (DetentionService.parseBasis("not-a-basis") != null) {
            failures.add("strict detention basis parser accepted an unknown basis");
        }
        checks++;
        if (VehicleService.canonicalPlate("") != null) {
            failures.add("plate parser accepted a malformed plate");
        }
        checks++;
        if (VehicleService.canonicalPlate("ABC-123") == null) {
            failures.add("plate parser rejected a valid plate");
        }
        checks++;
        if (checks < MIN_CORPUS_CASES) {
            failures.add("hostile corpus is smaller than " + MIN_CORPUS_CASES + " cases");
        }
        return checks;
    }
}
