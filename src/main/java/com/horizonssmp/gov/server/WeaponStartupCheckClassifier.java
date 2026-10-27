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

final class WeaponStartupCheckClassifier {

    private WeaponStartupCheckClassifier() {
    }

    static int checkClassifier(final List<String> failures) {
        int checks = 0;
        final WeaponClassifier classifier = WeaponClassifier.get();
        checks += WeaponStartupSupport.check(classifier.version() >= 1, "classifier version is not positive", failures);
        checks += WeaponStartupSupport.check(!classifier.itemClasses().isEmpty(),
                "classifier has no exact item mapping", failures);
        checks += WeaponStartupSupport.check(classifier.itemClasses().get("minecraft:tnt") == WeaponClass.BLASTING,
                "minecraft:tnt is not mapped to BLASTING", failures);
        checks += WeaponStartupSupport.check(classifier.tagClasses().get("gov:explosive") == WeaponClass.EXPLOSIVE,
                "gov:explosive tag is not mapped to EXPLOSIVE", failures);
        checks += WeaponStartupSupport.check(classifier.tagClasses().get("gov:nuclear") == WeaponClass.NUCLEAR,
                "gov:nuclear tag is not mapped to NUCLEAR", failures);
        checks += WeaponStartupSupport.check(classifier.tagClasses().get("gov:weapon_like") == WeaponClass.HEAVY,
                "gov:weapon_like tag is not mapped to Class H", failures);
        checks += WeaponStartupSupport.check(!classifier.taczNamespaces().isEmpty(),
                "classifier has no TACZ namespace", failures);
        checks += WeaponStartupSupport.check(classifier.nuclearTokens().contains("uranium")
                        && classifier.nuclearTokens().contains("plutonium")
                        && classifier.nuclearTokens().contains("nuclear"),
                "nuclear heuristic tokens are incomplete", failures);
        checks += WeaponStartupSupport.check(classifier.explosiveTokens().contains("tnt")
                        && classifier.explosiveTokens().contains("dynamite")
                        && classifier.explosiveTokens().contains("mining_charge")
                        && classifier.explosiveTokens().contains("grenade"),
                "explosive heuristic tokens are incomplete", failures);
        checks += WeaponStartupSupport.check(classifier.tntItems().contains("minecraft:tnt")
                        && classifier.isTnt("MINECRAFT:TNT")
                        && classifier.tntClassification().weaponClass() == WeaponClass.BLASTING,
                "the TNT item list is missing or does not classify", failures);
        checks += WeaponStartupSupport.check(classifier.ignitionItems().contains("minecraft:flint_and_steel")
                        && classifier.ignitionItems().contains("minecraft:fire_charge")
                        && classifier.isIgnitionItem("minecraft:FLINT_AND_STEEL"),
                "the ignition item list is missing or case-sensitive", failures);

        final Map<String, WeaponClass> categories = new LinkedHashMap<>();
        categories.put("pistol", WeaponClass.LIGHT);
        categories.put("handgun", WeaponClass.LIGHT);
        categories.put("shotgun", WeaponClass.SHOTGUN);
        categories.put("rifle", WeaponClass.RIFLE);
        categories.put("smg", WeaponClass.RIFLE);
        categories.put("sniper", WeaponClass.PRECISION);
        categories.put("marksman", WeaponClass.PRECISION);
        categories.put("machine_gun", WeaponClass.HEAVY);
        categories.put("rpg", WeaponClass.HEAVY);
        categories.put("launcher", WeaponClass.HEAVY);
        categories.put("grenade", WeaponClass.HEAVY);
        categories.put("explosive", WeaponClass.HEAVY);
        for (final Map.Entry<String, WeaponClass> entry : categories.entrySet()) {
            checks += WeaponStartupSupport.check(classifier.taczCategories().get(entry.getKey()) == entry.getValue(),
                    "TACZ category '" + entry.getKey() + "' does not map to " + entry.getValue(),
                    failures);
        }

        checks += WeaponStartupSupport.check(classifier.classifyId("minecraft:stone").weaponClass() == WeaponClass.NONE,
                "an ordinary block was classified as a weapon", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("minecraft:diamond_sword").weaponClass()
                        == WeaponClass.NONE,
                "an ordinary tool was classified as a weapon", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("minecraft:granite").weaponClass() == WeaponClass.NONE,
                "granite matched the grenade token", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:firework_rocket").weaponClass()
                        == WeaponClass.NONE,
                "a firework rocket was classified as an explosive", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("minecraft:tnt").weaponClass() == WeaponClass.BLASTING
                        && "json".equals(classifier.classifyId("minecraft:tnt").source()),
                "the exact TNT mapping did not win", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:uranium_ore").weaponClass()
                        == WeaponClass.NUCLEAR,
                "a uranium id did not classify as NUCLEAR", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:enriched_uranium_ingot").weaponClass()
                        == WeaponClass.NUCLEAR,
                "an enriched uranium id did not classify as NUCLEAR", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:plutonium_ingot").weaponClass()
                        == WeaponClass.NUCLEAR,
                "a plutonium id did not classify as NUCLEAR", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:dynamite").weaponClass()
                        == WeaponClass.EXPLOSIVE,
                "a dynamite id did not classify as EXPLOSIVE", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:mining_charge").weaponClass()
                        == WeaponClass.EXPLOSIVE,
                "a mining charge did not classify as EXPLOSIVE", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:tagged_explosive",
                        List.of("gov:explosive")).weaponClass() == WeaponClass.EXPLOSIVE,
                "an item tag did not classify as EXPLOSIVE", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:tagged_nuclear",
                        List.of("gov:nuclear")).weaponClass() == WeaponClass.NUCLEAR,
                "an item tag did not classify as NUCLEAR", failures);
        checks += WeaponStartupSupport.check(classifier.classifyId("startupcheck:tagged_weapon",
                        List.of("gov:weapon_like")).weaponClass() == WeaponClass.HEAVY,
                "an item tag did not classify as Class H", failures);

        final WeaponClassifier.Classification pistol =
                classifier.classifyId("tacz:startup_glock", List.of(), "pistol");
        final WeaponClassifier.Classification shotgun =
                classifier.classifyId("tacz:startup_m870", List.of(), "shotgun");
        final WeaponClassifier.Classification rifle =
                classifier.classifyId("tacz:startup_m4a1", List.of(), "rifle");
        final WeaponClassifier.Classification precision =
                classifier.classifyId("tacz:startup_awp", List.of(), "sniper");
        final WeaponClassifier.Classification heavy =
                classifier.classifyId("tacz:startup_m249", List.of(), "machine_gun");
        final WeaponClassifier.Classification unknownTacz =
                classifier.classifyId("tacz:startup_unlisted", List.of(), null);
        checks += WeaponStartupSupport.check(pistol.weaponClass() == WeaponClass.LIGHT
                        && "tacz".equals(pistol.source()),
                "the TACZ pistol category did not map to Class L", failures);
        checks += WeaponStartupSupport.check(shotgun.weaponClass() == WeaponClass.SHOTGUN,
                "the TACZ shotgun category did not map to Class S", failures);
        checks += WeaponStartupSupport.check(rifle.weaponClass() == WeaponClass.RIFLE,
                "the TACZ rifle category did not map to Class R", failures);
        checks += WeaponStartupSupport.check(precision.weaponClass() == WeaponClass.PRECISION,
                "the TACZ sniper category did not map to Class P", failures);
        checks += WeaponStartupSupport.check(heavy.weaponClass() == WeaponClass.HEAVY,
                "the TACZ machine-gun category did not map to Class H", failures);
        checks += WeaponStartupSupport.check(unknownTacz.weaponClass() == WeaponClass.UNCLASSIFIED
                        && unknownTacz.enforcedClass() == WeaponClass.HEAVY
                        && unknownTacz.permitClass() == PermitClass.H,
                "an unlisted TACZ weapon did not normalize to Class H", failures);

        final Set<WeaponClass> seen = new HashSet<>();
        seen.add(classifier.classifyId("minecraft:stone").weaponClass());
        seen.add(classifier.classifyId("minecraft:tnt").weaponClass());
        seen.add(classifier.classifyId("startupcheck:uranium").weaponClass());
        seen.add(classifier.classifyId("startupcheck:dynamite").weaponClass());
        seen.add(classifier.classifyId("startupcheck:tagged_weapon",
                List.of("gov:weapon_like")).weaponClass());
        seen.add(pistol.weaponClass());
        seen.add(shotgun.weaponClass());
        seen.add(rifle.weaponClass());
        seen.add(precision.weaponClass());
        seen.add(heavy.weaponClass());
        seen.add(unknownTacz.weaponClass());
        for (final WeaponClass expected : WeaponClass.values()) {
            if (expected == WeaponClass.NONE || expected == WeaponClass.UNCLASSIFIED) {
                continue;
            }
            checks += WeaponStartupSupport.check(seen.contains(expected),
                    "no classifier input produced " + expected, failures);
        }
        checks += WeaponStartupSupport.check(seen.contains(WeaponClass.NONE),
                "no classifier input produced NONE", failures);
        checks += WeaponStartupSupport.check(seen.contains(WeaponClass.UNCLASSIFIED),
                "no classifier input produced UNCLASSIFIED", failures);

        checks += WeaponStartupSupport.check(throwsOnMalformedJson(), "malformed classifier JSON was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponClassifier.fromJson("{\"version\":1}")
                        .classifyId("startupcheck:anything").weaponClass() == WeaponClass.NONE,
                "an empty classifier mapping did not fall back to NONE", failures);
        final Map<String, String> fields = WeaponService.classificationFields(heavy);
        checks += WeaponStartupSupport.check(fields != null
                        && "H".equals(fields.get(WeaponKeys.ITEM_CLASS))
                        && "h".equals(fields.get(WeaponKeys.ITEM_PERMIT_CLASS))
                        && "true".equals(fields.get(WeaponKeys.ITEM_MILITARY)),
                "the classification field block does not carry Class H facts", failures);
        return checks;
    }
    static boolean throwsOnMalformedJson() {
        try {
            WeaponClassifier.fromJson("{\"itemClasses\":");
            return false;
        } catch (final RuntimeException e) {
            return true;
        }
    }

}
