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

final class WeaponStartupCheckActionMatrix {

    private WeaponStartupCheckActionMatrix() {
    }

    static int checkActionMatrix(final List<String> failures) {
        int checks = 0;
        checks += WeaponStartupSupport.check(GovCapabilities.requiredFor(OpCode.WPN_LIST)
                        == GovCapability.PLAYER_LOOKUP_SELF
                        && GovCapabilities.requiredFor(OpCode.WPN_INFO)
                                == GovCapability.PLAYER_LOOKUP_SELF
                        && GovCapabilities.requiredFor(OpCode.WPN_CLASSIFY)
                                == GovCapability.PLAYER_LOOKUP_SELF
                        && GovCapabilities.requiredFor(OpCode.WPN_PERMIT_LIST)
                                == GovCapability.PLAYER_LOOKUP_SELF
                        && GovCapabilities.requiredFor(OpCode.AREA_PERMIT_LIST)
                                == GovCapability.PLAYER_LOOKUP_SELF,
                "an M6 read is not reachable by a baseline player", failures);
        checks += WeaponStartupSupport.check(GovCapabilities.requiredFor(OpCode.WPN_REGISTER)
                        == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.WPN_TRANSFER)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.WPN_REVOKE)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.WPN_REPORT_LOSS)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.WPN_PERMIT_ISSUE)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.WPN_PERMIT_REVOKE)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.WPN_PERMIT_SUSPEND)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.AREA_PERMIT_CREATE)
                                == GovCapability.WEAPON_REGISTER
                        && GovCapabilities.requiredFor(OpCode.AREA_PERMIT_REVOKE)
                                == GovCapability.WEAPON_REGISTER,
                "an M6 mutation does not require weapon.register", failures);
        for (final OpCode op : List.of(OpCode.WPN_LIST, OpCode.WPN_INFO,
                OpCode.WPN_CLASSIFY, OpCode.WPN_PERMIT_LIST, OpCode.AREA_PERMIT_LIST)) {
            checks += WeaponStartupSupport.check(!op.audited(), "M6 read " + op + " is marked audited", failures);
        }
        for (final OpCode op : List.of(OpCode.WPN_REGISTER, OpCode.WPN_TRANSFER,
                OpCode.WPN_REVOKE, OpCode.WPN_REPORT_LOSS, OpCode.WPN_PERMIT_ISSUE,
                OpCode.WPN_PERMIT_REVOKE, OpCode.WPN_PERMIT_SUSPEND,
                OpCode.AREA_PERMIT_CREATE, OpCode.AREA_PERMIT_REVOKE)) {
            checks += WeaponStartupSupport.check(op.audited(), "M6 mutation " + op + " is not marked audited", failures);
            checks += WeaponStartupSupport.check(GovActionDispatcher.isRegistered(op),
                    "M6 mutation " + op + " has no handler", failures);
        }
        for (final OpCode op : List.of(OpCode.WPN_LIST, OpCode.WPN_INFO,
                OpCode.WPN_CLASSIFY, OpCode.WPN_PERMIT_LIST, OpCode.AREA_PERMIT_LIST)) {
            checks += WeaponStartupSupport.check(GovActionDispatcher.isRegistered(op),
                    "M6 read " + op + " has no handler", failures);
        }
        checks += WeaponStartupSupport.check(GovCapability.MILITARY_AUTHORITY.id().equals("military.authority"),
                "the military authority capability id changed", failures);
        checks += WeaponStartupSupport.check(GovCapabilities.builtinJobCapabilities(
                        GovCapabilities.GOVEXECUTIVE_JOB_ID)
                        .contains(GovCapability.MILITARY_AUTHORITY),
                "the executive seed lost military.authority", failures);
        checks += WeaponStartupSupport.check(WeaponService.MAX_TNT == 16
                        && WeaponService.TRANSFER_WINDOW_MS == GovTime.days(1)
                        && WeaponService.REGISTRATION_WINDOW_MS == GovTime.days(1),
                "a weapon law constant is not the documented value", failures);
        return checks;
    }

}
