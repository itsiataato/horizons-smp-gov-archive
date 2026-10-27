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

final class WeaponStartupCheckArticles {

    private WeaponStartupCheckArticles() {
    }

    static int checkArticles(final List<String> failures) {
        int checks = 0;
        for (final String article : List.of(
                WeaponService.ARTICLE_EXPLOSIVES, WeaponService.ARTICLE_MILITARY,
                WeaponService.ARTICLE_TRANSFER, WeaponService.ARTICLE_SELF_DEFENSE,
                WeaponService.ARTICLE_SCHEDULE, WeaponService.ARTICLE_CARRY,
                WeaponService.ARTICLE_MINING, WeaponService.ARTICLE_REGISTRY,
                WeaponService.ARTICLE_PRIVACY)) {
            checks += WeaponStartupSupport.check(LawIndex.get().exists(article),
                    "weapon rules name law article " + article + ", which is not in the bundled law",
                    failures);
        }
        return checks;
    }

}
