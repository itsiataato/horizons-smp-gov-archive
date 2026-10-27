package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.TownCore;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.PermitStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.law.LawIndex;

import net.minecraft.nbt.CompoundTag;

final class WeaponStartupCheckAreas {

    private WeaponStartupCheckAreas() {
    }

    static int checkAreaMatrix(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_200_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6200L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        final WeaponService.Actor citizen = new WeaponService.Actor(
                new UUID(0L, 6201L), "StartupCheck Weapon Citizen",
                EnumSet.noneOf(GovCapability.class));

        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("minecraft:overworld",
                        "0", "64", "0", "16", "60", "StartupCheck") == null,
                "a valid area pre-check was refused", failures);
        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("overworld",
                        "0", "64", "0", "16", "60", "StartupCheck")
                        .code() == ErrorCode.BAD_PAYLOAD,
                "an invalid dimension passed the area pre-check", failures);
        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("minecraft:overworld",
                        "0", "64", "0", "0", "60", "StartupCheck")
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a zero radius passed the area pre-check", failures);
        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("minecraft:overworld",
                        "0", "64", "0", Integer.toString(WeaponService.MAX_AREA_RADIUS + 1),
                        "60", "StartupCheck").code() == ErrorCode.BAD_PAYLOAD,
                "an over-large radius passed the area pre-check", failures);
        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("minecraft:overworld",
                        "0", "64", "0", "16", "0", "StartupCheck")
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a zero-minute area passed the area pre-check", failures);
        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("minecraft:overworld",
                        "0", "64", "0", "16",
                        Long.toString(WeaponService.MAX_AREA_MINUTES + 1L), "StartupCheck")
                        .code() == ErrorCode.BAD_PAYLOAD,
                "an over-long area passed the area pre-check", failures);
        checks += WeaponStartupSupport.check(WeaponService.areaCreatePrecheck("minecraft:overworld",
                        "0", "64", "0", "16", "60", "  ")
                        .code() == ErrorCode.BAD_PAYLOAD,
                "an area with no reason passed the pre-check", failures);
        checks += WeaponStartupSupport.check(WeaponService.createArea(data, citizen, "minecraft:overworld",
                        "0", "64", "0", "16", "60", "StartupCheck", now)
                        .code() == ErrorCode.FORBIDDEN,
                "an area permit was issued without weapon.register", failures);

        final WeaponService.Outcome created = WeaponService.createArea(
                data, registrar, "Minecraft:Overworld", "100", "64", "100", "10", "60",
                "StartupCheck blasting", now);
        final AreaPermitRecord area = created.ok() ? data.areaPermit(created.id()) : null;
        checks += WeaponStartupSupport.check(created.ok() && area != null
                        && area.id().startsWith(AreaPermitRecord.ID_PREFIX)
                        && "minecraft:overworld".equals(area.dimension())
                        && area.centerX() == 100 && area.centerY() == 64 && area.centerZ() == 100
                        && area.radius() == 10
                        && area.minX() == 90 && area.maxX() == 110
                        && area.minY() == 54 && area.maxY() == 74
                        && area.minZ() == 90 && area.maxZ() == 110
                        && area.validFrom() == now
                        && area.validUntil() == now + 60L * 60_000L
                        && area.activeAt(now),
                "the explosion-area permit did not store its canonical geometry and window",
                failures);
        checks += WeaponStartupSupport.check(area.contains("minecraft:overworld", 100, 64, 100)
                        && area.contains("minecraft:overworld", 110, 64, 100)
                        && !area.contains("minecraft:overworld", 111, 64, 100)
                        && !area.contains("minecraft:the_nether", 100, 64, 100),
                "the area geometry is not an inclusive bounded box in one dimension", failures);
        checks += WeaponStartupSupport.check(area.covers("minecraft:overworld", 100, 64, 100, now)
                        && !area.covers("minecraft:overworld", 100, 64, 100,
                                now + 60L * 60_000L + 1L)
                        && AreaPermitRecord.STATUS_EXPIRED.equals(
                                area.stateAt(now + 60L * 60_000L + 1L)),
                "the area active window is not read-time computed", failures);
        checks += WeaponStartupSupport.check(WeaponService.activeAreaAt(data, "minecraft:overworld",
                        105, 64, 100, now) != null
                        && WeaponService.activeAreaAt(data, "minecraft:overworld",
                                200, 64, 200, now) == null,
                "the active-area lookup did not find the covering permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.createArea(data, registrar, "minecraft:overworld",
                        "100", "64", "100", "5", "60", "StartupCheck overlap", now).ok(),
                "overlapping area permits were refused", failures);
        checks += WeaponStartupSupport.check(WeaponService.revokeArea(data, registrar, created.id(), "", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "an area revocation without a reason was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.revokeArea(data, registrar, created.id(),
                        "StartupCheck cancelled", now).ok()
                        && AreaPermitRecord.STATUS_REVOKED.equals(
                                data.areaPermit(created.id()).stateAt(now))
                        && WeaponService.activeAreaAt(data, "minecraft:overworld",
                                100, 64, 100, now) != null,
                "the area revocation did not keep the record or the overlapping permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.revokeArea(data, registrar, "a-00000000", "x", now)
                        .code() == ErrorCode.BAD_AREA_PERMIT,
                "revoking an unknown area permit did not report BAD_AREA_PERMIT", failures);

        final Map<String, String> fields = WeaponService.areaListFields(
                data, WeaponStartupSupport.REGISTRAR_CAPS, 0, now);
        checks += WeaponStartupSupport.check(Integer.parseInt(fields.getOrDefault(WeaponKeys.AREA_TOTAL, "0")) >= 2
                        && fields.get(WeaponKeys.AREA_PREFIX + "0" + WeaponKeys.AREA_ID) != null
                        && fields.get(WeaponKeys.AREA_PREFIX + "0" + WeaponKeys.AREA_BOX) != null,
                "the area list field block is missing its rows", failures);
        checks += WeaponStartupSupport.check(area.equals(AreaPermitRecord.read(area.write(), null)),
                "the area permit did not round-trip through its own encoding", failures);
        final CompoundTag legacy = new CompoundTag();
        legacy.putString("id", "a-00000000");
        checks += WeaponStartupSupport.check(AreaPermitRecord.read(legacy, null) == null,
                "an area permit without a dimension was loaded", failures);
        return checks;
    }

}
