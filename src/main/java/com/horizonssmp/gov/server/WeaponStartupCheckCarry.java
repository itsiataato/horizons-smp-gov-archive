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

final class WeaponStartupCheckCarry {

    private WeaponStartupCheckCarry() {
    }

    static int checkCarryDecisions(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_500_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6501L);
        final UUID bob = new UUID(0L, 6502L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        data.putPlayer(PlayerRecord.blank(bob, "StartupCheck Weapon Bob", now)
                .withDuty(DutyState.ON_DUTY));
        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6500L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        final WeaponService.Actor military = new WeaponService.Actor(
                new UUID(0L, 6510L), "StartupCheck Weapon Military", WeaponStartupSupport.MILITARY_CAPS);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, military, bob,
                        "StartupCheck Weapon Bob", "g", "", 0L, now).ok()
                        && WeaponService.issuePermit(data, registrar, bob,
                                "StartupCheck Weapon Bob", "h", "", 0L, now).ok()
                        && WeaponService.issuePermit(data, registrar, bob,
                                "StartupCheck Weapon Bob", "r", "", 0L, now).ok(),
                "could not issue Bob's Class G, H and R permits", failures);

        final WeaponStartupSupport.TestGateway gateway = new WeaponStartupSupport.TestGateway();
        gateway.position(alice, "minecraft:overworld", 500, 64, 500);
        gateway.position(bob, "minecraft:overworld", 500, 64, 500);
        final WeaponService.Subject aliceSubject = WeaponStartupSupport.subject(data, alice,
                EnumSet.noneOf(GovCapability.class));
        final WeaponService.Subject bobSubject = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.MILITARY_CAPS);
        final Map<String, WeaponClassifier.Classification> classes = new LinkedHashMap<>();
        classes.put("l", WeaponClassifier.get().classifyId("tacz:startup_glock", List.of(), "pistol"));
        classes.put("s", WeaponClassifier.get().classifyId("tacz:startup_m870", List.of(), "shotgun"));
        classes.put("r", WeaponClassifier.get().classifyId("tacz:startup_m4a1", List.of(), "rifle"));
        classes.put("p", WeaponClassifier.get().classifyId("tacz:startup_awp", List.of(), "sniper"));
        final WeaponClassifier.Classification heavy =
                WeaponClassifier.get().classifyId("tacz:startup_m249", List.of(), "machine_gun");
        for (final Map.Entry<String, WeaponClassifier.Classification> entry : classes.entrySet()) {
            checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, entry.getValue(),
                            WeaponService.Action.USE, gateway, List.of(), now).code()
                            == ErrorCode.FORBIDDEN,
                    "civilian use of Class " + entry.getKey() + " without a permit was allowed",
                    failures);
            checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                            "StartupCheck Weapon Alice", entry.getKey(), "", 0L, now).ok(),
                    "could not issue Class " + entry.getKey() + " to Alice", failures);
            checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, entry.getValue(),
                            WeaponService.Action.USE, gateway, List.of(), now).allowed(),
                    "permitted use of Class " + entry.getKey() + " was denied", failures);
        }
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, classes.get("l"),
                        WeaponService.Action.PICKUP, gateway, List.of(), now).allowed(),
                "a civilian could not pick up a dropped Class L weapon", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, heavy,
                        WeaponService.Action.PICKUP, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "a civilian could pick up a Class H weapon", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobSubject, heavy,
                        WeaponService.Action.USE, gateway, List.of(), now).allowed(),
                "an on-duty Class G/H holder could not use a Class H weapon", failures);
        final WeaponService.Subject bobOff = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.MILITARY_CAPS, DutyState.OFF_DUTY);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobOff, heavy,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "off-duty use of a Class H weapon was allowed", failures);
        return checks;
    }

}
