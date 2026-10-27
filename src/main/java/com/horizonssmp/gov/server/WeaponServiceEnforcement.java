package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovTime;
import com.horizonssmp.gov.common.TownCore;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.enums.PermitClass;
import com.horizonssmp.gov.common.enums.PermitStatus;
import com.horizonssmp.gov.common.enums.WeaponCategory;
import com.horizonssmp.gov.common.enums.WeaponClass;
import com.horizonssmp.gov.common.records.AreaPermitRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;


final class WeaponServiceEnforcement {

    // -------------------------------------------------------------------- checks

    /**
     * The enforcement decision for one interaction.
     *
     * <p>The caller supplies the classification and the subject; the inventory count and position
     * come from the gateway. A town-core list is passed explicitly so the check can be asserted
     * without changing server config. There is no path here that removes an item.
     */
    public static WeaponService.Decision evaluate(
            final GovSavedData data,
            final WeaponService.Subject subject,
            final WeaponClassifier.Classification classification,
            final WeaponService.Action action,
            final WeaponGateway gateway,
            final List<TownCore> townCores,
            final long now) {
        final WeaponGateway facts = gateway == null ? WeaponGateway.NONE : gateway;
        return evaluateAt(data, subject, classification, action, facts,
                facts.position(subject == null ? null : subject.uuid()), townCores, now);
    }

    /**
     * The same decision at an explicit position, for a placement or an explosion whose position is
     * the block or the blast center rather than the player's feet.
     */
    public static WeaponService.Decision evaluateAt(
            final GovSavedData data,
            final WeaponService.Subject subject,
            final WeaponClassifier.Classification classification,
            final WeaponService.Action action,
            final WeaponGateway gateway,
            final WeaponGateway.Position position,
            final List<TownCore> townCores,
            final long now) {
        if (classification == null || classification.weaponClass() == WeaponClass.NONE) {
            return WeaponService.Decision.allow();
        }
        if (data == null || subject == null || action == null) {
            return WeaponService.Decision.allow();
        }
        final WeaponGateway facts = gateway == null ? WeaponGateway.NONE : gateway;
        final WeaponGateway.Position where = position == null
                ? WeaponGateway.Position.none() : position;
        final boolean town = TownCore.anyContains(
                townCores, where.dimension(), where.x(), where.y(), where.z());
        final WeaponClass weaponClass = classification.enforcedClass();

        return switch (weaponClass) {
            case BLASTING -> evaluateTnt(data, subject, classification, action, facts,
                    where, town, now);
            case NUCLEAR -> evaluateMilitaryOrdance(data, subject, classification, action, facts,
                    where, town, now, true);
            case EXPLOSIVE -> evaluateMilitaryOrdance(data, subject, classification, action, facts,
                    where, town, now, false);
            case HEAVY -> evaluateHeavy(data, subject, classification, action, town, now);
            case LIGHT, SHOTGUN, RIFLE, PRECISION ->
                    evaluateFirearm(data, subject, classification, action, town, now);
            case UNCLASSIFIED -> evaluateHeavy(data, subject, classification, action, town, now);
            case NONE -> WeaponService.Decision.allow();
        };
    }

    public static WeaponService.Decision evaluateTnt(
            final GovSavedData data, final WeaponService.Subject subject,
            final WeaponClassifier.Classification classification, final WeaponService.Action action,
            final WeaponGateway gateway, final WeaponGateway.Position position,
            final boolean town, final long now) {
        if (action == WeaponService.Action.PICKUP) {
            // TNT is the one explosive a citizen may pick up. The permit and count are checked at
            // use/placement, exactly as the locked ruling states.
            return WeaponService.Decision.allow();
        }
        if (WeaponServicePermits.blastingPermit(data, subject.uuid(), now) == null) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "TNT needs a valid Class B Blasting permit (Article " + WeaponService.ARTICLE_EXPLOSIVES + ").",
                    denials(action, classification, "no Blasting permit"));
        }
        final int carried = tntCount(gateway, subject.uuid());
        if (carried > WeaponService.MAX_TNT) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "You carry " + carried + " TNT; a Blasting permit allows at most " + WeaponService.MAX_TNT
                            + " (Article " + WeaponService.ARTICLE_EXPLOSIVES + ").",
                    denials(action, classification, "over the 16 TNT cap: " + carried));
        }
        if (town && (action == WeaponService.Action.USE || action == WeaponService.Action.PLACE
                || action == WeaponService.Action.IGNITE || action == WeaponService.Action.EXPLODE)) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "No TNT storage or detonation in a town core (Article " + WeaponService.ARTICLE_EXPLOSIVES
                            + ").",
                    denials(action, classification, "TNT in a town core"));
        }
        if (action == WeaponService.Action.USE || action == WeaponService.Action.PLACE
                || action == WeaponService.Action.IGNITE || action == WeaponService.Action.EXPLODE) {
            if (WeaponServiceAreas.activeAreaAt(data, position.dimension(), position.x(), position.y(), position.z(),
                    now) == null) {
                return WeaponService.Decision.deny(
                        ErrorCode.FORBIDDEN,
                        "No active explosion-area permit covers this position (Article "
                                + WeaponService.ARTICLE_EXPLOSIVES + ").",
                        denials(action, classification, "no active explosion-area permit"));
            }
        }
        return WeaponService.Decision.allow();
    }

    public static WeaponService.Decision evaluateMilitaryOrdance(
            final GovSavedData data, final WeaponService.Subject subject,
            final WeaponClassifier.Classification classification, final WeaponService.Action action,
            final WeaponGateway gateway, final WeaponGateway.Position position,
            final boolean town, final long now, final boolean nuclear) {
        final String what = nuclear
                ? "Uranium and nuclear material are military-grade"
                : "Explosives are military-grade";
        if (!WeaponServicePermits.militaryAuthority(data, subject, now)) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    what + ": only a Class G duty holder with military authority, on duty, may hold"
                            + " or use them (Article " + (nuclear ? WeaponService.ARTICLE_MILITARY : WeaponService.ARTICLE_EXPLOSIVES)
                            + ").",
                    denials(action, classification, nuclear
                            ? "nuclear material without military authority"
                            : "explosive without military authority"));
        }
        if (town) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "No explosives or nuclear material in a town core (Article "
                            + WeaponService.ARTICLE_EXPLOSIVES + ").",
                    denials(action, classification, "military ordnance in a town core"));
        }
        if (action == WeaponService.Action.USE || action == WeaponService.Action.PLACE
                || action == WeaponService.Action.ATTACK || action == WeaponService.Action.IGNITE || action == WeaponService.Action.EXPLODE) {
            if (WeaponServiceAreas.activeAreaAt(data, position.dimension(), position.x(), position.y(), position.z(),
                    now) == null) {
                return WeaponService.Decision.deny(
                        ErrorCode.FORBIDDEN,
                        "No active explosion-area permit covers this position (Article "
                                + WeaponService.ARTICLE_EXPLOSIVES + ").",
                        denials(action, classification, "no active explosion-area permit"));
            }
        }
        return WeaponService.Decision.allow();
    }

    public static WeaponService.Decision evaluateHeavy(
            final GovSavedData data, final WeaponService.Subject subject,
            final WeaponClassifier.Classification classification, final WeaponService.Action action,
            final boolean town, final long now) {
        if (action == WeaponService.Action.PICKUP && !WeaponServicePermits.militaryAuthority(data, subject, now)) {
            // Military-grade ordnance is illegal to hold, not only to fire; unlike a civilian
            // firearm, the pickup is denied. No item is removed from anyone.
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "Class H is military-grade; only a Class G duty holder with military authority,"
                            + " on duty, may hold it (Article " + WeaponService.ARTICLE_MILITARY + ").",
                    denials(action, classification, "Class H pickup without military authority"));
        }
        if (!WeaponServicePermits.onDutyClassG(data, subject, now)) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "Class H needs a valid Class G government duty permit and on-duty status"
                            + " (Article " + WeaponService.ARTICLE_MILITARY + ").",
                    denials(action, classification, "Class H without on-duty Class G"));
        }
        if (WeaponServicePermits.activePermit(data, subject.uuid(), PermitClass.H, now) == null) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "Class H needs a valid Class H permit (Article " + WeaponService.ARTICLE_SCHEDULE + ").",
                    denials(action, classification, "Class H without a Class H permit"));
        }
        // In a town core an on-duty Class G officer is the one permitted carrier.
        return WeaponService.Decision.allow();
    }

    public static WeaponService.Decision evaluateFirearm(
            final GovSavedData data, final WeaponService.Subject subject,
            final WeaponClassifier.Classification classification, final WeaponService.Action action,
            final boolean town, final long now) {
        if (action == WeaponService.Action.PICKUP) {
            // The locked ruling puts the boundary at use, attack and place, not at looting. A
            // dropped weapon can be picked up and handed in; it still cannot be used without a
            // permit.
            return WeaponService.Decision.allow();
        }
        final PermitClass required = classification.enforcedClass().permitClass();
        if (required != null && WeaponServicePermits.activePermit(data, subject.uuid(), required, now) == null) {
            return WeaponService.Decision.deny(
                    ErrorCode.FORBIDDEN,
                    "Carrying or using a " + classification.enforcedClass().label()
                            + " needs a valid " + required.label() + " permit (Article "
                            + WeaponService.ARTICLE_CARRY + ").",
                    denials(action, classification, "no " + required.id() + " permit"));
        }
        if (town) {
            final boolean concealedClassL = classification.enforcedClass() == WeaponClass.LIGHT
                    && WeaponServicePermits.activePermit(data, subject.uuid(), PermitClass.L, now) != null;
            if (!concealedClassL && !WeaponServicePermits.onDutyClassG(data, subject, now)) {
                return WeaponService.Decision.deny(
                        ErrorCode.FORBIDDEN,
                        "In a town core only a Class L handgun with a Class L permit, or an on-duty"
                                + " Class G officer, may carry (Article " + WeaponService.ARTICLE_CARRY + ").",
                        denials(action, classification, "town core carry"));
            }
        }
        return WeaponService.Decision.allow();
    }

    public static String denials(
            final WeaponService.Action action, final WeaponClassifier.Classification classification,
            final String reason) {
        return action.name() + " " + classification.itemId() + " ("
                + classification.enforcedClass().id() + "): " + reason;
    }

    /** The TNT carried, summed over every item id the classifier marks as TNT. */
    public static int tntCount(final WeaponGateway gateway, final UUID player) {
        if (gateway == null || player == null) {
            return 0;
        }
        int total = 0;
        for (final String itemId : WeaponClassifier.get().tntItems()) {
            total += gateway.countItem(player, itemId);
        }
        return total;
    }

    /** The positive TNT decision, useful to a caller that wants no message on success. */
    public static boolean canDetonateTnt(
            final GovSavedData data, final WeaponService.Subject subject, final String dimension,
            final double x, final double y, final double z, final int carriedTnt, final long now) {
        return subject != null
                && WeaponServicePermits.blastingPermit(data, subject.uuid(), now) != null
                && carriedTnt <= WeaponService.MAX_TNT
                && WeaponServiceAreas.activeAreaAt(data, dimension, x, y, z, now) != null;
    }

}
