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

final class WeaponStartupCheckRegistry {

    private WeaponStartupCheckRegistry() {
    }

    static int checkRegistryMatrix(final List<String> failures) {
        int checks = 0;
        final long now = 1_800_000_100_000L;
        final GovSavedData data = WeaponStartupSupport.scratch(now);
        final UUID alice = new UUID(0L, 6101L);
        final UUID bob = new UUID(0L, 6102L);
        final UUID carol = new UUID(0L, 6103L);
        final UUID dave = new UUID(0L, 6104L);
        data.putPlayer(PlayerRecord.blank(alice, "StartupCheck Weapon Alice", now));
        data.putPlayer(PlayerRecord.blank(bob, "StartupCheck Weapon Bob", now)
                .withDuty(DutyState.ON_DUTY));
        data.putPlayer(PlayerRecord.blank(carol, "StartupCheck Weapon Carol", now));
        data.putPlayer(PlayerRecord.blank(dave, "StartupCheck Weapon Dave", now));

        final WeaponService.Actor registrar = new WeaponService.Actor(
                new UUID(0L, 6100L), "StartupCheck Weapon Registrar", WeaponStartupSupport.REGISTRAR_CAPS);
        final WeaponService.Actor military = new WeaponService.Actor(
                new UUID(0L, 6110L), "StartupCheck Weapon Military", WeaponStartupSupport.MILITARY_CAPS);
        final WeaponService.Actor citizen = new WeaponService.Actor(
                alice, "StartupCheck Weapon Alice", EnumSet.noneOf(GovCapability.class));

        checks += WeaponStartupSupport.check(WeaponService.canonicalSerial("startup-6001")
                        .equals("STARTUP-6001")
                        && WeaponService.canonicalSerial("STARTUP6002") != null
                        && WeaponService.canonicalSerial("AB") == null
                        && WeaponService.canonicalSerial("A B C") == null,
                "serial canonicalisation is not uppercase and strict", failures);
        checks += WeaponStartupSupport.check(WeaponService.registrableCategory("l") == WeaponCategory.L
                        && WeaponService.registrableCategory("h") == WeaponCategory.H
                        && WeaponService.registrableCategory("unclassified") == null
                        && WeaponService.registrableCategory("spaceship") == null,
                "the registry class parser accepted a non-registry class", failures);

        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, alice,
                        "StartupCheck Weapon Alice", "l", "", 0L, now).ok(),
                "could not issue Alice's Class L permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, military, bob,
                        "StartupCheck Weapon Bob", "g", "", 0L, now).ok()
                        && WeaponService.issuePermit(data, registrar, bob,
                                "StartupCheck Weapon Bob", "h", "", 0L, now).ok(),
                "could not issue Bob's Class G and H permits", failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, citizen, alice,
                        "StartupCheck Weapon Alice", "l", "STARTUP-6001", "startupcheck:pistol", now)
                        .code() == ErrorCode.FORBIDDEN,
                "a weapon was registered without weapon.register", failures);
        final WeaponService.Outcome aliceWeapon = WeaponService.register(
                data, registrar, alice, "StartupCheck Weapon Alice", "l",
                "startup-6001", "startupcheck:pistol", now);
        final WeaponRecord pistol = WeaponService.bySerial(data, "startup-6001");
        checks += WeaponStartupSupport.check(aliceWeapon.ok() && pistol != null
                        && "STARTUP-6001".equals(pistol.serial())
                        && pistol.category() == WeaponCategory.L
                        && !pistol.militaryGrade()
                        && alice.equals(pistol.ownerUuid())
                        && !pistol.permitId().isBlank()
                        && pistol.id().startsWith(WeaponService.WEAPON_ID_PREFIX)
                        && pistol.active(),
                "registration did not store the canonical serial, class, owner and permit",
                failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, registrar, alice,
                        "StartupCheck Weapon Alice", "l", "startup-6001", "startupcheck:pistol", now)
                        .code() == ErrorCode.SERIAL_TAKEN,
                "a duplicate serial was not refused case-insensitively", failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, registrar, bob,
                        "StartupCheck Weapon Bob", "l", "STARTUP-6002", "startupcheck:pistol", now)
                        .code() == ErrorCode.INVALID_STATE,
                "a Class L weapon was registered to a person with no Class L permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, registrar, alice,
                        "StartupCheck Weapon Alice", "h", "STARTUP-6003", "startupcheck:mg", now)
                        .code() == ErrorCode.INVALID_STATE,
                "a Class H weapon was registered to a civilian without Class G/H", failures);
        final WeaponService.Outcome bobWeapon = WeaponService.register(
                data, registrar, bob, "StartupCheck Weapon Bob", "h",
                "STARTUP-6004", "startupcheck:mg", now);
        checks += WeaponStartupSupport.check(bobWeapon.ok()
                        && WeaponService.bySerial(data, "STARTUP-6004").militaryGrade()
                        && WeaponService.bySerial(data, "STARTUP-6004").category()
                                == WeaponCategory.H,
                "the military Class H registration did not carry the military flag", failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, registrar, alice,
                        "StartupCheck Weapon Alice", "d", "STARTUP-6005", "startupcheck:x", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a dealer permit class was accepted as a weapon class", failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, registrar, alice,
                        "StartupCheck Weapon Alice", "l", "AB", "startupcheck:x", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "an invalid serial was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.register(data, registrar, alice,
                        "StartupCheck Weapon Alice", "l", "STARTUP-6006", "  ", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a registration with no type was accepted", failures);

        checks += WeaponStartupSupport.check(WeaponService.transfer(data, registrar, "STARTUP-6001", bob,
                        "StartupCheck Weapon Bob", now).code() == ErrorCode.INVALID_STATE,
                "a weapon was transferred to an owner without the matching permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, bob,
                        "StartupCheck Weapon Bob", "l", "", 0L, now).ok(),
                "could not issue Bob's Class L permit for the transfer", failures);
        final WeaponService.Outcome transferred = WeaponService.transfer(
                data, registrar, "startup-6001", bob, "StartupCheck Weapon Bob", now);
        final WeaponRecord moved = WeaponService.bySerial(data, "STARTUP-6001");
        final WeaponRecord.TransferEntry last = moved.transfers().isEmpty()
                ? null : moved.transfers().get(moved.transfers().size() - 1);
        checks += WeaponStartupSupport.check(transferred.ok() && bob.equals(moved.ownerUuid())
                        && last != null && last.requestedAt() == now
                        && last.completedAt() == now && last.recordedWithinWindow()
                        && bob.equals(last.toUuid()) && alice.equals(last.fromUuid()),
                "the transfer did not store both stamps, the owners and the window flag", failures);
        checks += WeaponStartupSupport.check(WeaponService.transfer(data, registrar, "STARTUP-6001", carol,
                        "StartupCheck Weapon Carol",
                        now - WeaponService.TRANSFER_WINDOW_MS - 1L, now)
                        .code() == ErrorCode.INVALID_STATE,
                "a transfer recorded outside the one-day window was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.transfer(data, registrar, "STARTUP-6001", carol,
                        "StartupCheck Weapon Carol", now + 1L, now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a transfer with a future request stamp was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.revoke(data, registrar, "STARTUP-6001", "", now)
                        .code() == ErrorCode.BAD_PAYLOAD,
                "a weapon revocation without a reason was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.revoke(data, registrar, "STARTUP-6001",
                        "StartupCheck registry revocation", now).ok()
                        && WeaponService.bySerial(data, "STARTUP-6001").status()
                                .equals(WeaponRecord.STATUS_REVOKED),
                "the weapon revocation did not keep the record with its reason", failures);
        checks += WeaponStartupSupport.check(WeaponService.transfer(data, registrar, "STARTUP-6001", carol,
                        "StartupCheck Weapon Carol", now).code() == ErrorCode.INVALID_STATE,
                "a revoked weapon was transferred", failures);
        checks += WeaponStartupSupport.check(WeaponService.reportLoss(data, registrar, "STARTUP-6004",
                        "StartupCheck stolen").ok()
                        && WeaponRecord.STATUS_LOST.equals(
                                WeaponService.bySerial(data, "STARTUP-6004").status()),
                "a loss report did not mark the weapon LOST", failures);
        checks += WeaponStartupSupport.check(WeaponService.transfer(data, registrar, "STARTUP-6004", bob,
                        "StartupCheck Weapon Bob", now).code() == ErrorCode.INVALID_STATE,
                "a lost weapon was transferred", failures);

        final WeaponService.Actor dealerActor = new WeaponService.Actor(
                carol, "StartupCheck Weapon Carol", WeaponStartupSupport.REGISTRAR_CAPS);
        checks += WeaponStartupSupport.check(WeaponService.registerDealerSale(data, registrar, alice,
                        "StartupCheck Weapon Alice", "l", "STARTUP-6010", "startupcheck:dealer",
                        now, now).code() == ErrorCode.FORBIDDEN,
                "a dealer sale by a non-dealer was accepted", failures);
        checks += WeaponStartupSupport.check(WeaponService.issuePermit(data, registrar, carol,
                        "StartupCheck Weapon Carol", "d", "", 0L, now).ok(),
                "could not issue Carol's dealer permit", failures);
        checks += WeaponStartupSupport.check(WeaponService.registerDealerSale(data, dealerActor, alice,
                        "StartupCheck Weapon Alice", "l", "STARTUP-6011", "startupcheck:dealer",
                        now - 1_000L, now).ok(),
                "a dealer sale inside the one-day window was refused", failures);
        checks += WeaponStartupSupport.check(WeaponService.registerDealerSale(data, dealerActor, alice,
                        "StartupCheck Weapon Alice", "l", "STARTUP-6012", "startupcheck:dealer",
                        now - WeaponService.REGISTRATION_WINDOW_MS - 1L, now)
                        .code() == ErrorCode.INVALID_STATE,
                "a dealer sale registered outside the one-day window was accepted", failures);
        final WeaponRecord sale = WeaponService.bySerial(data, "STARTUP-6011");
        checks += WeaponStartupSupport.check(sale != null && sale.soldAt() == now - 1_000L
                        && "dealer-sale".equals(sale.source())
                        && carol.equals(sale.dealerUuid()),
                "the dealer sale did not record buyer, dealer and sale stamp", failures);

        final Map<String, String> listAll = WeaponService.listFields(
                data, registrar.uuid(), registrar.name(), WeaponStartupSupport.REGISTRAR_CAPS,
                WeaponKeys.FILTER_ALL, null, 0, List.of("StartupCheck Weapon Alice"));
        checks += WeaponStartupSupport.check("true".equals(listAll.get(WeaponKeys.CAN_REGISTER))
                        && "true".equals(listAll.get(WeaponKeys.CAN_VIEW_ALL))
                        && listAll.get(WeaponKeys.ROW_PREFIX + "0" + WeaponKeys.ROW_SERIAL) != null
                        && "1".equals(listAll.get(WeaponKeys.ONLINE_COUNT)),
                "the weapon list flags, rows or online picker are missing", failures);
        checks += WeaponStartupSupport.check(WeaponService.page(data, alice, EnumSet.noneOf(GovCapability.class),
                        WeaponKeys.FILTER_ALL, null, 0).rows().stream()
                        .allMatch(row -> alice.equals(row.weapon().ownerUuid())),
                "a citizen's weapon page leaked another owner's registration", failures);
        checks += WeaponStartupSupport.check(WeaponService.page(data, registrar.uuid(), WeaponStartupSupport.REGISTRAR_CAPS,
                        WeaponKeys.FILTER_REVOKED, null, 0).total() >= 1,
                "the revoked filter did not find the revoked registration", failures);
        checks += WeaponStartupSupport.check(WeaponService.page(data, registrar.uuid(), WeaponStartupSupport.REGISTRAR_CAPS,
                        WeaponKeys.FILTER_LOST, null, 0).total() == 1,
                "the lost filter did not find exactly the lost registration", failures);
        final Map<String, String> detail = WeaponService.detailFields(data, moved);
        checks += WeaponStartupSupport.check("STARTUP-6001".equals(detail.get(WeaponKeys.SERIAL))
                        && "1".equals(detail.get(WeaponKeys.TRANSFER_COUNT))
                        && detail.get(WeaponKeys.TRANSFER_PREFIX + "0"
                                + WeaponKeys.TRANSFER_IN_WINDOW) != null
                        && "false".equals(detail.get(WeaponKeys.MILITARY)),
                "the weapon detail does not carry its transfer or military facts", failures);
        checks += WeaponStartupSupport.check(WeaponService.registerPrecheck(data, "l", "STARTUP-6001", "x")
                        .code() == ErrorCode.SERIAL_TAKEN
                        && WeaponService.registerPrecheck(data, "d", "STARTUP-6020", "x")
                                .code() == ErrorCode.BAD_PAYLOAD
                        && WeaponService.registerPrecheck(data, "l", "AB", "x")
                                .code() == ErrorCode.BAD_PAYLOAD,
                "the registration pre-checks are wrong", failures);
        checks += WeaponStartupSupport.check(WeaponService.transferPrecheck(data, "STARTUP-6004")
                        .code() == ErrorCode.INVALID_STATE,
                "the transfer pre-check accepted a lost weapon", failures);
        return checks;
    }

}
