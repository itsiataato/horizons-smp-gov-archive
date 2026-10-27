package com.horizonssmp.gov.server;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.WeaponRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovWeaponHandlers {

    private GovWeaponHandlers() {
    }

    // --------------------------------------------------------------- weapons: reads

    /**
     * {@code WPN_LIST}: one page of the weapon registry.
     *
     * <p>A viewer with {@code weapon.view} or {@code weapon.register} sees every registration and
     * may filter by owner; a citizen sees only their own. The online list is supplied for the
     * owner picker, capped and with its true count.
     */
    static ActionResult weaponList(final GovActionDispatcher.ActionContext context) {
        UUID ownerFilter = null;
        if (WeaponService.canViewAll(context.capabilities())) {
            final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
            if (!rawTarget.isEmpty() && !"all".equalsIgnoreCase(rawTarget)) {
                final GovTargets.Resolved target =
                        GovTargets.resolve(context.actor().getServer(), context.data(), rawTarget);
                if (target == null) {
                    return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
                }
                ownerFilter = target.uuid();
            }
        }
        final List<String> names = onlineNames(context.actor().getServer());
        final Map<String, String> fields = WeaponService.listFields(
                context.data(),
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities(),
                context.payload(WeaponKeys.KEY_FILTER),
                ownerFilter,
                parsePage(context.payload(WeaponKeys.KEY_PAGE)),
                names);
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code WPN_INFO}: one registration with its permit reference and transfer history. */
    static ActionResult weaponInfo(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(WeaponKeys.KEY_SERIAL);
        final WeaponRecord weapon = WeaponService.find(context.data(), raw);
        if (weapon == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_WEAPON,
                    WeaponService.noSuchWeaponMessage(raw));
        }
        if (!WeaponService.canViewAll(context.capabilities())
                && (weapon.ownerUuid() == null
                        || !weapon.ownerUuid().equals(context.actor().getUUID()))) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "You can only read the registry entry for your own weapons.");
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(WeaponService.detailFields(context.data(), weapon)));
    }

    /** {@code WPN_CLASSIFY}: the server's classification of an item id or the held item. */
    static ActionResult weaponClassify(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(WeaponKeys.KEY_ITEM_ID).strip();
        final WeaponClassifier.Classification classification;
        if (raw.isEmpty()) {
            classification = WeaponClassifier.get().classify(context.actor().getMainHandItem());
        } else {
            classification = WeaponService.classify(raw, WeaponClassifier.get());
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(WeaponService.classificationFields(classification)));
    }

    /**
     * {@code WPN_PERMIT_LIST}: one person's permits. A viewer without a registry capability may
     * read only their own; the console names the person in the command or sends their own target.
     */
    static ActionResult weaponPermitList(final GovActionDispatcher.ActionContext context) {
        final String rawTarget = context.payload(GovTargets.KEY_TARGET).strip();
        final UUID holder;
        final String holderName;
        if (rawTarget.isEmpty()) {
            holder = context.actor().getUUID();
            holderName = context.actor().getName().getString();
        } else {
            final GovTargets.Resolved target =
                    GovTargets.resolve(context.actor().getServer(), context.data(), rawTarget);
            if (target == null) {
                return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
            }
            if (!WeaponService.canViewAll(context.capabilities())
                    && !target.uuid().equals(context.actor().getUUID())) {
                return ActionResult.failure(
                        context.request().reqId(), ErrorCode.FORBIDDEN,
                        "You can only read your own permits.");
            }
            holder = target.uuid();
            holderName = target.name();
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(WeaponService.permitListFields(
                        context.data(), holder, holderName, context.capabilities(),
                        parsePage(context.payload(WeaponKeys.KEY_PAGE)), context.now())));
    }

    // ----------------------------------------------------------- weapons: mutations

    /** {@code WPN_REGISTER}: registers a firearm to the resolved owner. */
    static ActionResult weaponRegister(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final WeaponService.Outcome outcome = WeaponService.register(
                context.data(),
                weaponActor(context),
                target.uuid(),
                target.name(),
                context.payload(WeaponKeys.KEY_CLASS),
                context.payload(WeaponKeys.KEY_SERIAL),
                context.payload(WeaponKeys.KEY_TYPE),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code WPN_TRANSFER}: transfers a serial to the resolved new owner. */
    static ActionResult weaponTransfer(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final WeaponService.Outcome outcome = WeaponService.transfer(
                context.data(),
                weaponActor(context),
                context.payload(WeaponKeys.KEY_SERIAL),
                target.uuid(),
                target.name(),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code WPN_REVOKE}: revokes a registration with a required reason. */
    static ActionResult weaponRevoke(final GovActionDispatcher.ActionContext context) {
        final WeaponService.Outcome outcome = WeaponService.revoke(
                context.data(),
                weaponActor(context),
                context.payload(WeaponKeys.KEY_SERIAL),
                context.payload(WeaponKeys.KEY_REASON),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code WPN_REPORT_LOSS}: records a weapon lost or stolen. */
    static ActionResult weaponReportLoss(final GovActionDispatcher.ActionContext context) {
        final WeaponService.Outcome outcome = WeaponService.reportLoss(
                context.data(),
                weaponActor(context),
                context.payload(WeaponKeys.KEY_SERIAL),
                context.payload(WeaponKeys.KEY_REASON));
        return toResult(context, outcome);
    }

    /** {@code WPN_PERMIT_ISSUE}: issues a permit, including BLASTING, mining and military H. */
    static ActionResult weaponPermitIssue(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final WeaponService.Outcome outcome = WeaponService.issuePermit(
                context.data(),
                weaponActor(context),
                target.uuid(),
                target.name(),
                context.payload(WeaponKeys.KEY_PERMIT_CLASS),
                context.payload(WeaponKeys.KEY_REASON),
                GovConfig.seasonEndMillis(),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code WPN_PERMIT_REVOKE}: revokes a permit with a recorded reason. */
    static ActionResult weaponPermitRevoke(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final WeaponService.Outcome outcome = WeaponService.revokePermit(
                context.data(),
                weaponActor(context),
                target.uuid(),
                target.name(),
                context.payload(WeaponKeys.KEY_PERMIT_CLASS),
                context.payload(WeaponKeys.KEY_REASON),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.RED));
        }
        return toResult(context, outcome);
    }

    /** {@code WPN_PERMIT_SUSPEND}: suspends a permit for the stated minutes. */
    static ActionResult weaponPermitSuspend(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final WeaponService.Outcome outcome = WeaponService.suspendPermit(
                context.data(),
                weaponActor(context),
                target.uuid(),
                target.name(),
                context.payload(WeaponKeys.KEY_PERMIT_CLASS),
                context.payload(WeaponKeys.KEY_MINUTES),
                context.payload(WeaponKeys.KEY_REASON),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.YELLOW));
        }
        return toResult(context, outcome);
    }


}
