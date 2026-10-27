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

final class WeaponStartupCheckPermits {

    private WeaponStartupCheckPermits() {
    }

    static int checkPermitMatrix(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_000_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6001L);
        final UUID bob = new UUID(0L, 6002L);
        final UUID carol = new UUID(0L, 6003L);
        final UUID dave = new UUID(0L, 6004L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        data.putPlayer(PlayerRecord.blank(bob, "StartupCheck Weapon Bob", now)
                .withDuty(DutyState.ON_DUTY));
        data.putPlayer(PlayerRecord.blank(carol, "StartupCheck Weapon Carol", now));
        data.putPlayer(PlayerRecord.blank(dave, "StartupCheck Weapon Dave", now));

        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6000L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        final WeaponService.Actor military = new WeaponService.Actor(
                new UUID(0L, 6010L), "StartupCheck Weapon Military", WeaponStartupSupport.MILITARY_CAPS);
        final WeaponService.Actor citizen = new WeaponService.Actor(
                alice, "StartupCheck Weapon Alice", EnumSet.noneOf(GovCapability.class));

        for (final PermitClass permitClass : PermitClass.values()) {
            checks += WeaponStartupSupport.check(PermitClass.parse(permitClass.id()) == permitClass,
                    "permit class id does not round-trip: " + permitClass.id(), failures);
        }
        checks += WeaponStartupSupport.check(PermitClass.parse("BLASTING") == PermitClass.BLASTING
                        && PermitClass.parse("mining") == PermitClass.MINING
                        && PermitClass.parse("nonsense") == null,
                "the permit class parser is not strict and alias-tolerant", failures);

        final long seasonEnd = now + 50_000L;
        final WeaponService.Outcome aliceL = WeaponService.issuePermit(
                data, registrar, alice, "StartupCheck Weapon Alice", "l",
                "StartupCheck carry", seasonEnd, now);
        final PermitRecord aliceLPermit = data.weaponPermit(alice, PermitClass.L);
        checks += WeaponStartupSupport.check(aliceL.ok() && aliceLPermit != null
                        && aliceLPermit.stateAt(now) == PermitStatus.VALID
                        && aliceLPermit.expiresAt() == seasonEnd
                        && registrar.uuid().equals(aliceLPermit.issuedBy())
                        && "StartupCheck Weapon Alice".equals(aliceLPermit.holderName())
                        && "StartupCheck carry".equals(aliceLPermit.note()),
                "the Class L permit issue did not store its holder, issuer, expiry and note",
                failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                        "StartupCheck Weapon Alice", "l", "", seasonEnd, now)
                        .code() == ErrorCode.INVALID_STATE,
                "a second live Class L permit was issued", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, citizen, bob,
                        "StartupCheck Weapon Bob", "l", "", 0L, now)
                        .code() == ErrorCode.FORBIDDEN,
                "a permit was issued without weapon.register", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, bob,
                        "StartupCheck Weapon Bob", "g", "", 0L, now)
                        .code() == ErrorCode.FORBIDDEN,
                "Class G was issued without military.authority", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, military, bob,
                        "StartupCheck Weapon Bob", "g", "", 0L, now).ok(),
                "a military actor could not issue Class G", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, carol,
                        "StartupCheck Weapon Carol", "h", "", 0L, now)
                        .code() == ErrorCode.INVALID_STATE,
                "Class H was issued to a person without a Class G permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, military, carol,
                        "StartupCheck Weapon Carol", "g", "", 0L, now).ok()
                        && WeaponService.issuePermit(data, registrar, carol,
                                "StartupCheck Weapon Carol", "h", "", 0L, now).ok(),
                "Class G then Class H could not be issued", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                        "StartupCheck Weapon Alice", "h", "", 0L, now)
                        .code() == ErrorCode.INVALID_STATE,
                "Class H was issued to a civilian without Class G", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                        "StartupCheck Weapon Alice", "blasting", "", 0L, now).ok()
                        && WeaponService.issuePermit(data, registrar, alice,
                                "StartupCheck Weapon Alice", "mining", "", 0L, now).ok()
                        && WeaponService.issuePermit(data, registrar, bob,
                                "StartupCheck Weapon Bob", "d", "", 0L, now).ok(),
                "the Blasting, mining or dealer permit could not be issued", failures);
        checks += WeaponStartupSupport.check(WeaponService.blastingPermit(data, alice, now) != null
                        && data.weaponPermit(alice, PermitClass.MINING) != null
                        && data.weaponPermit(bob, PermitClass.D) != null,
                "the Blasting, mining or dealer permit was not stored", failures);

        checks += WeaponStartupSupport.check(aliceLPermit.stateAt(seasonEnd) == PermitStatus.EXPIRED
                        && !aliceLPermit.validAt(seasonEnd)
                        && aliceLPermit.stateAt(seasonEnd - 1L) == PermitStatus.VALID,
                "permit expiry is not the stored season-end instant", failures);

        final long daveSeasonEnd = now + GovTime.days(30);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, dave,
                        "StartupCheck Weapon Dave", "l", "", daveSeasonEnd, now).ok(),
                "could not issue Dave's permit for the suspension test", failures);
        final WeaponService.Outcome suspended = WeaponService.suspendPermit(
                data, registrar, dave, "StartupCheck Weapon Dave", "l", "60",
                "StartupCheck suspension", now);
        final PermitRecord suspendedPermit = data.weaponPermit(dave, PermitClass.L);
        checks += WeaponStartupSupport.check(suspended.ok() && suspendedPermit != null
                        && suspendedPermit.stateAt(now) == PermitStatus.SUSPENDED
                        && !suspendedPermit.validAt(now)
                        && suspendedPermit.stateAt(now + 60L * 60_000L + 1L) == PermitStatus.VALID
                        && "StartupCheck suspension".equals(suspendedPermit.suspendReason()),
                "permit suspension did not lapse at read time or keep its reason", failures);
        checks += WeaponStartupSupport.check(WeaponService.suspendPermit(data, registrar, dave,
                        "StartupCheck Weapon Dave", "l", "0", "x", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a zero-minute suspension was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.revokePermit(data, registrar, dave,
                        "StartupCheck Weapon Dave", "l", "", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a permit revocation without a reason was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.revokePermit(data, registrar, dave,
                        "StartupCheck Weapon Dave", "l", "StartupCheck revocation", now).ok()
                        && data.weaponPermit(dave, PermitClass.L).stateAt(now)
                                == PermitStatus.REVOKED,
                "permit revocation did not keep the record with its reason", failures);
        checks += WeaponStartupSupport.check(WeaponService.suspendPermit(data, registrar, dave,
                        "StartupCheck Weapon Dave", "l", "60", "again", now)
                        .code() == ErrorCode.INVALID_STATE,
                "a revoked permit was suspended", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, dave,
                        "StartupCheck Weapon Dave", "l", "", daveSeasonEnd, now + 1L).ok(),
                "a revoked permit could not be reissued", failures);
        checks += WeaponStartupSupport.check(WeaponService.revokePermit(data, registrar, new UUID(0L, 6999L),
                        "StartupCheck Ghost", "l", "nope", now).code() == ErrorCode.BAD_PERMIT,
                "revoking an unknown permit did not report BAD_PERMIT", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                        "StartupCheck Weapon Alice", "nonsense", "", 0L, now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "an unknown permit class was accepted", failures);

        final PermitRecord roundTrip = data.weaponPermit(carol, PermitClass.H);
        checks += WeaponStartupSupport.check(roundTrip != null
                        && roundTrip.equals(PermitRecord.read(roundTrip.write(), null)),
                "the permit record did not round-trip through its own encoding", failures);
        final CompoundTag legacy = new CompoundTag();
        NbtSupport.putUuid(legacy, "holderUuid", alice);
        legacy.putString("permitClass", "spaceship");
        checks += WeaponStartupSupport.check(PermitRecord.read(legacy, null) == null,
                "a permit with an unknown class was loaded with a guessed class", failures);
        final CompoundTag legacyValid = new CompoundTag();
        NbtSupport.putUuid(legacyValid, "holderUuid", alice);
        legacyValid.putString("permitClass", "l");
        legacyValid.putString("status", "NOT_A_STATUS");
        final PermitRecord tolerant = PermitRecord.read(legacyValid, null);
        checks += WeaponStartupSupport.check(tolerant != null
                        && tolerant.stateAt(now) == PermitStatus.REVOKED
                        && tolerant.expiresAt() == 0L,
                "a legacy permit tag did not load with the safe revoked default", failures);

        final WeaponService.Subject bobSubject = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.MILITARY_CAPS);
        checks += WeaponStartupSupport.check(WeaponService.militaryAuthority(data, bobSubject, now),
                "an on-duty Class G holder with military.authority was not recognized", failures);
        final WeaponService.Subject bobOff = WeaponStartupSupport.subject(data, bob,
                WeaponStartupSupport.MILITARY_CAPS, DutyState.OFF_DUTY);
        checks += WeaponStartupSupport.check(!WeaponService.militaryAuthority(data, bobOff, now),
                "an off-duty Class G holder was recognized as military authority", failures);
        final WeaponService.Subject bobNoCap = WeaponStartupSupport.subject(data, bob, WeaponStartupSupport.REGISTRAR_CAPS);
        checks += WeaponStartupSupport.check(!WeaponService.militaryAuthority(data, bobNoCap, now),
                "a Class G holder without military.authority was recognized", failures);

        final Map<String, String> permitFields = WeaponService.permitListFields(
                data, carol, "StartupCheck Weapon Carol", WeaponStartupSupport.REGISTRAR_CAPS, 0, now);
        checks += WeaponStartupSupport.check(Integer.parseInt(permitFields.getOrDefault(WeaponKeys.PERMIT_TOTAL, "0")) >= 2
                        && permitFields.get(WeaponKeys.PERMIT_PREFIX + "0"
                                + WeaponKeys.PERMIT_CLASS) != null
                        && "true".equals(permitFields.get(WeaponKeys.PERMIT_PREFIX + "0"
                                + WeaponKeys.PERMIT_MILITARY)),
                "the permit list field block is missing its first row or military flag", failures);
        return checks;
    }

}
