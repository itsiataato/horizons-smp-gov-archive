package com.horizonssmp.gov.server;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.common.records.LocationRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.VehicleRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovVehicleHandlers {

    private GovVehicleHandlers() {
    }

    // -------------------------------------------------------------- vehicles: reads

    /**
     * {@code VEH_LIST}: one page of the registry.
     *
     * <p>A viewer with a registry capability sees every vehicle and may filter by owner; a citizen
     * sees only their own. The online list is supplied for the owner picker, capped and with its
     * true count.
     */
    static ActionResult vehicleList(final GovActionDispatcher.ActionContext context) {
        UUID ownerFilter = null;
        if (VehicleService.canViewAll(context.capabilities())) {
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
        final Map<String, String> fields = VehicleService.listFields(
                context.data(),
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities(),
                context.payload(VehicleKeys.KEY_FILTER),
                ownerFilter,
                parsePage(context.payload(VehicleKeys.KEY_PAGE)),
                names,
                context.now());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code VEH_INFO}: one registry entry with licence, impound, fee and disposal detail. */
    static ActionResult vehicleInfo(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(VehicleKeys.KEY_PLATE);
        final VehicleRecord vehicle = VehicleService.find(context.data(), raw);
        if (vehicle == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_VEHICLE,
                    VehicleService.noSuchVehicleMessage(raw));
        }
        if (!VehicleService.canViewAll(context.capabilities())
                && (vehicle.ownerUuid() == null
                        || !vehicle.ownerUuid().equals(context.actor().getUUID()))) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.FORBIDDEN,
                    "You can only read the registry entry for your own vehicles.");
        }
        final Map<String, String> fields = VehicleService.detailFields(
                context.data(), vehicle, context.capabilities(), context.now());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    // ---------------------------------------------------------- vehicles: mutations

    /** {@code VEH_REGISTER}: registers a plate and class to the resolved owner. */
    static ActionResult vehicleRegister(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final VehicleService.Outcome outcome = VehicleService.register(
                context.data(),
                vehicleActor(context),
                target.uuid(),
                target.name(),
                context.payload(VehicleKeys.KEY_PLATE),
                context.payload(VehicleKeys.KEY_CLASS),
                GovConfig.requireLicenseToRegister(),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(
                            Sanitizer.name(context.actor().getName().getString())
                                    + " registered plate " + VehicleService.canonicalPlate(
                                            context.payload(VehicleKeys.KEY_PLATE))
                                    + " to you.")
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code VEH_EDIT}: transfers a plate to the resolved new owner. */
    static ActionResult vehicleEdit(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final VehicleService.Outcome outcome = VehicleService.transfer(
                context.data(),
                vehicleActor(context),
                context.payload(VehicleKeys.KEY_PLATE),
                target.uuid(),
                target.name(),
                GovConfig.requireLicenseToRegister(),
                context.now());
        return toResult(context, outcome);
    }

    /**
     * {@code VEH_IMPOUND}: marks a vehicle impounded with a required reason.
     *
     * <p>No location is invented: vanilla has no vehicle entity, and the officer's own position is
     * not the vehicle's position. The optional location field stays empty unless a future adapter
     * or a staff edit supplies one.
     */
    static ActionResult vehicleImpound(final GovActionDispatcher.ActionContext context) {
        final VehicleService.Outcome outcome = VehicleService.impound(
                context.data(),
                vehicleActor(context),
                context.payload(VehicleKeys.KEY_PLATE),
                context.payload(VehicleKeys.KEY_REASON),
                LocationRecord.NONE,
                context.now());
        return toResult(context, outcome);
    }

    /** {@code VEH_RELEASE}: releases at reception for the fee recorded on the impound. */
    static ActionResult vehicleRelease(final GovActionDispatcher.ActionContext context) {
        final VehicleService.Outcome outcome = VehicleService.release(
                context.data(),
                vehicleActor(context),
                context.payload(VehicleKeys.KEY_PLATE),
                FineGateway.live(context.actor().getServer()),
                FineService.configuredReception(),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code PLATE_SCAN}: a registry lookup; records the scan, never changes the vehicle. */
    static ActionResult plateScan(final GovActionDispatcher.ActionContext context) {
        final VehicleService.Scan scan = VehicleService.scan(
                context.data(),
                vehicleActor(context),
                context.payload(VehicleKeys.KEY_PLATE),
                VehicleService.REGISTRY_ONLY,
                context.now(),
                true);
        if (!scan.ok()) {
            return ActionResult.failure(
                    context.request().reqId(), scan.code(), scan.message());
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(VehicleService.scanFields(scan.view())));
    }

    // --------------------------------------------------------------- licences

    /** {@code LICENSE_ISSUE}: issues one class of licence to the resolved person. */
    static ActionResult licenseIssue(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final VehicleService.Outcome outcome = VehicleService.issueLicense(
                context.data(),
                vehicleActor(context),
                target.uuid(),
                target.name(),
                context.payload(LicenseKeys.KEY_CLASS),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.GRAY));
        }
        return toResult(context, outcome);
    }

    /** {@code LICENSE_REVOKE}: revokes a licence with a required reason. */
    static ActionResult licenseRevoke(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final VehicleService.Outcome outcome = VehicleService.revokeLicense(
                context.data(),
                vehicleActor(context),
                target.uuid(),
                target.name(),
                context.payload(LicenseKeys.KEY_CLASS),
                context.payload(LicenseKeys.KEY_REASON),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.RED));
        }
        return toResult(context, outcome);
    }

    /** {@code LICENSE_POINTS}: assesses 2, 4 or 6 points and suspends at twelve. */
    static ActionResult licensePoints(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final VehicleService.Outcome outcome = VehicleService.assessPoints(
                context.data(),
                vehicleActor(context),
                target.uuid(),
                target.name(),
                context.payload(LicenseKeys.KEY_CLASS),
                context.payload(LicenseKeys.KEY_POINTS),
                context.payload(LicenseKeys.KEY_REASON),
                context.now());
        if (outcome.ok() && target.online()) {
            target.player().sendSystemMessage(Component.literal(outcome.message())
                    .withStyle(ChatFormatting.YELLOW));
        }
        return toResult(context, outcome);
    }


}
