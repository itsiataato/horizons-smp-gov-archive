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

final class WeaponStartupCheckTnt {

    private WeaponStartupCheckTnt() {
    }

    static int checkTntDecisions(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_300_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6301L);
        final UUID erin = new UUID(0L, 6302L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        data.putPlayer(PlayerRecord.blank(erin, "StartupCheck Weapon Erin", now));
        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6300L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                        "StartupCheck Weapon Alice", "blasting", "", 0L, now).ok(),
                "could not issue the Blasting permit", failures);
        final WeaponService.Outcome area = WeaponService.createArea(
                data, registrar, "minecraft:overworld", "100", "64", "100", "10", "60",
                "StartupCheck TNT area", now);
        checks += WeaponStartupSupport.check(area.ok(), "could not create the TNT test area", failures);

        final WeaponClassifier.Classification tnt = WeaponClassifier.get().classifyId("minecraft:tnt");
        final WeaponStartupSupport.TestGateway gateway = new WeaponStartupSupport.TestGateway();
        gateway.position(alice, "minecraft:overworld", 100, 64, 100);
        gateway.position(erin, "minecraft:overworld", 100, 64, 100);
        gateway.add(alice, "minecraft:tnt", 16);
        gateway.add(erin, "minecraft:tnt", 2);
        final WeaponService.Subject aliceSubject = WeaponStartupSupport.subject(data, alice, EnumSet.noneOf(GovCapability.class));
        final WeaponService.Subject erinSubject = WeaponStartupSupport.subject(data, erin, EnumSet.noneOf(GovCapability.class));

        checks += WeaponStartupSupport.check(WeaponService.MAX_TNT == 16, "the TNT cap is not 16", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now).allowed(),
                "a permitted 16-TNT use inside an active area was denied", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, tnt,
                        WeaponService.Action.PLACE, gateway, List.of(), now).allowed(),
                "a permitted 16-TNT placement inside an active area was denied", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, erinSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "TNT use without a Blasting permit was allowed", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, erinSubject, tnt,
                        WeaponService.Action.PICKUP, gateway, List.of(), now).allowed(),
                "TNT pickup was hard-blocked", failures);
        gateway.add(alice, "minecraft:tnt", 17);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "TNT use with 17 carried was allowed", failures);
        gateway.add(alice, "minecraft:tnt", 16);
        gateway.position(alice, "minecraft:overworld", 200, 64, 200);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now).code()
                        == ErrorCode.FORBIDDEN,
                "TNT use outside every area permit was allowed", failures);
        final WeaponService.Outcome expiredArea = WeaponService.createArea(
                data, registrar, "minecraft:overworld", "300", "64", "300", "5", "1",
                "StartupCheck expired", now);
        gateway.position(erin, "minecraft:overworld", 300, 64, 300);
        gateway.add(erin, "minecraft:tnt", 2);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, erinSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now + 61_000L).code()
                        == ErrorCode.FORBIDDEN,
                "an expired area permit authorized TNT", failures);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, erinSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now + 30_000L).code()
                        == ErrorCode.FORBIDDEN,
                "TNT without a Blasting permit was allowed by an area", failures);
        final List<TownCore> town = List.of(new TownCore("minecraft:overworld",
                -200, 0, -200, 200, 320, 200));
        gateway.position(alice, "minecraft:overworld", 100, 64, 100);
        checks += WeaponStartupSupport.check(WeaponService.evaluate(data, aliceSubject, tnt,
                        WeaponService.Action.PLACE, gateway, town, now).code()
                        == ErrorCode.FORBIDDEN,
                "TNT placement in a configured town core was allowed", failures);
        checks += WeaponStartupSupport.check(!WeaponService.canDetonateTnt(data, aliceSubject,
                        "minecraft:overworld", 100, 64, 100, 17, now)
                        && WeaponService.canDetonateTnt(data, aliceSubject,
                                "minecraft:overworld", 100, 64, 100, 16, now),
                "the TNT detonation predicate does not enforce the 16-item cap", failures);
        checks += WeaponStartupSupport.check(!WeaponService.evaluate(data, erinSubject, tnt,
                        WeaponService.Action.USE, gateway, List.of(), now).message().isBlank(),
                "a TNT denial never has a message", failures);
        return checks;
    }

}
