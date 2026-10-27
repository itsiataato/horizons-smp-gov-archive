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

final class WeaponStartupCheckTownCores {

    private WeaponStartupCheckTownCores() {
    }

    static int checkTownCores(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_600_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6601L);
        final UUID bob = new UUID(0L, 6602L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        data.putPlayer(PlayerRecord.blank(bob, "StartupCheck Weapon Bob", now)
                .withDuty(DutyState.ON_DUTY));
        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6600L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        final WeaponService.Actor military = new WeaponService.Actor(
                new UUID(0L, 6610L), "StartupCheck Weapon Military", WeaponStartupSupport.MILITARY_CAPS);
        WeaponService.issuePermit(data, registrar, alice, "StartupCheck Weapon Alice",
                "l", "", 0L, now);
        WeaponService.issuePermit(data, registrar, alice, "StartupCheck Weapon Alice",
                "r", "", 0L, now);
        WeaponService.issuePermit(data, military, bob, "StartupCheck Weapon Bob",
                "g", "", 0L, now);
        WeaponService.issuePermit(data, registrar, bob, "StartupCheck Weapon Bob",
                "r", "", 0L, now);

        final TownCore core = new TownCore("minecraft:overworld",
                -100, 0, -100, 100, 320, 100);
        checks += WeaponStartupSupport.check(core.contains("minecraft:overworld", 0, 64, 0)
                        && core.contains("minecraft:overworld", 100, 64, 100)
                        && !core.contains("minecraft:overworld", 101, 64, 100)
                        && !core.contains("minecraft:the_nether", 0, 64, 0),
                "the town-core box is not inclusive and dimension-scoped", failures);
        checks += WeaponStartupSupport.check(TownCore.parse("minecraft:overworld:-10,0,-10:10,64,10").isPresent()
                        && TownCore.parse("overworld:0,0,0:1,1,1").isEmpty()
                        && TownCore.parse("minecraft:overworld:0,0").isEmpty()
                        && TownCore.parse("").isEmpty(),
                "the town-core parser accepted a malformed region", failures);
        final List<TownCore> cores = List.of(core);
        final WeaponStartupSupport.TestGateway gateway = new WeaponStartupSupport.TestGateway();
        gateway.position(alice, "minecraft:overworld", 0, 64, 0);
        gateway.position(bob, "minecraft:overworld", 0, 64, 0);
        final WeaponService.Subject aliceSubject = WeaponStartupSupport.subject(data, alice,
                EnumSet.noneOf(GovCapability.class));
        final WeaponService.Subject bobSubject = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.MILITARY_CAPS);
        final WeaponClassifier.Classification pistol =
                WeaponClassifier.get().classifyId("tacz:startup_glock", List.of(), "pistol");
        final WeaponClassifier.Classification rifle =
                WeaponClassifier.get().classifyId("tacz:startup_m4a1", List.of(), "rifle");
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, pistol,
                        WeaponService.Action.USE, gateway, cores, now).allowed(),
                "a Class L permit holder was denied a handgun in a town core", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, rifle,
                        WeaponService.Action.USE, gateway, cores, now).code()
                        == ErrorCode.FORBIDDEN,
                "a Class R rifle was allowed in a town core", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobSubject, rifle,
                        WeaponService.Action.USE, gateway, cores, now).allowed(),
                "an on-duty Class G officer was denied a rifle in a town core", failures);
        gateway.position(alice, "minecraft:overworld", 500, 64, 500);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, rifle,
                        WeaponService.Action.USE, gateway, cores, now).allowed(),
                "a rifle outside every town core was denied", failures);
        checks += WeaponStartupSupport.check(!GovConfig.townCoresConfigured()
                        || !GovConfig.townCoreRegions().isEmpty(),
                "the configured town-core list disagrees with its own flag", failures);
        checks += WeaponStartupSupport.check(TownCore.anyContains(cores, "minecraft:overworld", 0, 64, 0)
                        && !TownCore.anyContains(List.of(), "minecraft:overworld", 0, 64, 0),
                "the town-core any-match helper is wrong", failures);
        return checks;
    }

}
