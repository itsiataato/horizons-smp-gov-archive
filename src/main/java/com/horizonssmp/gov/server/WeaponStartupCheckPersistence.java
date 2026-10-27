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

final class WeaponStartupCheckPersistence {

    private WeaponStartupCheckPersistence() {
    }

    static int checkPersistence(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_700_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6701L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6700L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        WeaponService.issuePermit(data, registrar, alice, "StartupCheck Weapon Alice",
                "l", "", 0L, now);
        WeaponService.register(data, registrar, alice, "StartupCheck Weapon Alice", "l",
                "STARTUP-6701", "startupcheck:pistol", now);
        WeaponService.createArea(data, registrar, "minecraft:overworld", "0", "64", "0",
                "8", "30", "StartupCheck persistence", now);
        final CompoundTag saved = data.save(new CompoundTag(), null);
        final GovSavedData reloaded = GovSavedData.load(saved, null);
        checks += WeaponStartupSupport.check(saved.contains("weaponPermits") && saved.contains("areaPermits")
                        && reloaded.weaponPermit(alice, PermitClass.L) != null
                        && reloaded.areaPermits().size() == 1
                        && reloaded.weapons().size() == data.weapons().size()
                        && reloaded.weaponPermits().size() == data.weaponPermits().size(),
                "the permit or area sections did not survive a save and load", failures);
        checks += WeaponStartupSupport.check(reloaded.schemaVersion() == 2,
                "the government data schema version changed", failures);
        return checks;
    }

}
