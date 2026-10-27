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

final class WeaponStartupCheckOrdnance {

    private WeaponStartupCheckOrdnance() {
    }

    static int checkOrdnanceDecisions(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_400_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6401L);
        final UUID bob = new UUID(0L, 6402L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        data.putPlayer(PlayerRecord.blank(bob, "StartupCheck Weapon Bob", now)
                .withDuty(DutyState.ON_DUTY));
        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6400L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        final WeaponService.Actor military = new WeaponService.Actor(
                new UUID(0L, 6410L), "StartupCheck Weapon Military", WeaponStartupSupport.MILITARY_CAPS);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, military, bob,
                        "StartupCheck Weapon Bob", "g", "", 0L, now).ok(),
                "could not issue the Class G permit for the ordnance check", failures);
        checks += WeaponStartupSupport.check(WeaponService.createArea(data, registrar, "minecraft:overworld",
                        "100", "64", "100", "20", "60", "StartupCheck ordnance", now).ok(),
                "could not create the ordnance test area", failures);

        final WeaponClassifier.Classification uranium =
                WeaponClassifier.get().classifyId("startupcheck:uranium_ore");
        final WeaponClassifier.Classification explosive =
                WeaponClassifier.get().classifyId("startupcheck:mining_charge");
        final WeaponStartupSupport.TestGateway gateway = new WeaponStartupSupport.TestGateway();
        gateway.position(alice, "minecraft:overworld", 100, 64, 100);
        gateway.position(bob, "minecraft:overworld", 100, 64, 100);
        gateway.add(alice, "startupcheck:uranium_ore", 3);
        gateway.add(alice, "startupcheck:mining_charge", 2);
        final WeaponService.Subject aliceSubject = WeaponStartupSupport.subject(data, alice,
                EnumSet.noneOf(GovCapability.class));
        final WeaponService.Subject bobSubject = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.MILITARY_CAPS);

        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, uranium,
                        WeaponService.Action.PICKUP, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "a civilian could pick up uranium", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, uranium,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "a civilian could use uranium", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, explosive,
                        WeaponService.Action.PICKUP, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "a civilian could pick up an explosive", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, explosive,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "a civilian could use an explosive", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobSubject, uranium,
                        WeaponService.Action.USE, gateway, List.of(), now).allowed(),
                "an on-duty military authority could not use uranium inside an area", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobSubject, explosive,
                        WeaponService.Action.USE, gateway, List.of(), now).allowed(),
                "an on-duty military authority could not use an explosive inside an area", failures);
        gateway.position(bob, "minecraft:overworld", 400, 64, 400);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobSubject, uranium,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "military uranium use outside every area was allowed", failures);
        gateway.position(bob, "minecraft:overworld", 100, 64, 100);
        final WeaponService.Subject bobOff = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.MILITARY_CAPS, DutyState.OFF_DUTY);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobOff, uranium,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "off-duty military uranium use was allowed", failures);
        final WeaponService.Subject bobNoCap = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.REGISTRAR_CAPS);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, bobNoCap, uranium,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "uranium use without military.authority was allowed", failures);
        return checks;
    }

}
