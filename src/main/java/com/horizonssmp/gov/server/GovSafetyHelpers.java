package com.horizonssmp.gov.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.enums.BusinessRole;
import com.horizonssmp.gov.common.enums.BusinessType;
import com.horizonssmp.gov.common.enums.DetentionStatus;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.ImpoundReason;
import com.horizonssmp.gov.common.enums.LegalBasis;
import com.horizonssmp.gov.common.enums.LicenseClass;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.ScanResult;
import com.horizonssmp.gov.common.enums.VehicleStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.GoldAmount;
import com.horizonssmp.gov.common.records.ImpoundRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.LicenseRecord;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.common.records.PayrollInfo;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.law.LawIndex;

import net.minecraft.nbt.CompoundTag;

final class GovSafetyHelpers {

    private GovSafetyHelpers() {
    }

    static int runCase(final List<String> failures, final String label, final Runnable action) {
        try {
            action.run();
        } catch (final Throwable t) {
            failures.add("hostile input threw at " + label + ": " + t);
        }
        return 1;
    }

    static String label(final String value) {
        if (value == null) {
            return "<null>";
        }
        final String single = value.replaceAll("[\\r\\n\\t\\u0000-\\u001f]", "?");
        return "[" + (single.length() <= 24 ? single : single.substring(0, 24) + "...")
                + "/" + value.length() + "]";
    }

    static boolean hasLoneSurrogate(final String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    static Path scratchDirectory(final List<String> failures, final String name) {
        try {
            final Path base = Path.of(System.getProperty("user.dir"))
                    .resolve("build").resolve("gov-selfcheck");
            Files.createDirectories(base);
            return Files.createTempDirectory(base, "m9-" + name + "-");
        } catch (final IOException | RuntimeException e) {
            failures.add("could not create the M9 scratch directory: " + e);
            return null;
        }
    }

    static GovSavedData seasonFixture(final long now) {
        final GovSavedData data = new GovSavedData();
        data.seedIfNeeded(now, false);
        final UUID player = new UUID(0L, 53L);
        final UUID officer = new UUID(0L, 54L);
        final UUID issuer = new UUID(0L, 51L);
        PlayerRecord record = PlayerRecord.blank(player, "SeasonPlayer", now)
                .withJob("police", "Officer", now, issuer, true)
                .withDuty(DutyState.ON_DUTY);
        record = new PlayerRecord(
                record.uuid(), record.lastName(), record.firstSeen(), record.lastSeen(),
                record.primaryJobId(), record.jobAssignedAt(), record.jobAssignedBy(),
                record.rank(), EnumSet.of(GovCapability.AUDIT_VIEW), record.suspect(),
                record.duty(), record.employment(), record.jail(), record.jobDecided());
        data.putPlayer(record);
        data.putNote(new NoteRecord(
                "n-season", player, officer, "Officer", "season note", now, 0L));
        data.putFine(new FineRecord(
                "f-season", player, officer, "9.11", FineBand.PETTY, GoldAmount.of(8L),
                GoldAmount.ZERO, "season fine", now, now + 60_000L,
                com.horizonssmp.gov.common.enums.FineStatus.UNPAID,
                0L, "", null, "", 0, "", "", false));
        data.putLicense(LicenseRecord.issued(player, LicenseClass.CAR, issuer, now));
        data.putVehicle(new VehicleRecord(
                "v-season", "ABC-123", player, "Make", "Model", "Black", "VIN-1", "C",
                now, issuer, 0L, VehicleStatus.VALID, ImpoundRecord.NONE));
        data.putWeapon(new WeaponRecord(
                "w-season", "SER-1", "Rifle", "", WeaponCategory.R, false,
                null, 0L, player, null, "", now, issuer, 0L,
                WeaponRecord.STATUS_ACTIVE, "Rifle", "", 0L, List.of()));
        data.putWeaponPermit(PermitRecord.issued(
                player, "SeasonPlayer", PermitClass.L, issuer, now, 0L, ""));
        data.putAreaPermit(AreaPermitRecord.create(
                "a-season", "minecraft:overworld", 0, 64, 0, 16,
                now, now + 60_000L, issuer, now, "season area"));
        data.putDetention(new DetentionRecord(
                "d-season", player, "SeasonPlayer", officer, "Officer",
                LegalBasis.CONSENT, "season detention", "", now, 10,
                0L, null, "", "", DetentionStatus.ACTIVE,
                LocationRecord.NONE, LocationRecord.NONE, false,
                DetentionRecord.ADAPTER_NONE, 0, 0L, null, "", ""));
        data.putBusiness(new BusinessRecord(
                "b-season", "Season Business", player, "SHOP", BusinessRecord.STATUS_ACTIVE,
                now, issuer, "", List.of(), PayrollInfo.NONE, "", "", 0L, null));
        return data;
    }
}
