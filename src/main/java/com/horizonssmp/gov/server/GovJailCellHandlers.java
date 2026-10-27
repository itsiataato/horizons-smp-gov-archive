package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.records.LocationRecord;

import net.minecraft.resources.ResourceLocation;

/**
 * The runtime jail-cell mutations: {@code JAIL_CELL_CREATE} and {@code JAIL_CELL_DELETE}.
 *
 * <p>Split from {@link GovJailHandlers} so that file stays under the 300-line rule; the
 * delegation methods there keep the registration surface stable. The logic mirrors the
 * {@code /gov jail new} and {@code /gov jail delete} command paths in
 * {@code GovJailMutationCommandHandlers}: the client resolves {@code here} into explicit
 * coordinates before sending, so the server only validates a non-blank name, parseable
 * integer coordinates and a non-blank dimension, then stores or removes the runtime cell.
 *
 * <p>Authorization is not decided here. The dispatcher has already checked the one capability
 * {@code GovCapabilities.requiredFor} names ({@code jail.commit} for both ops), exactly as for
 * the other jail mutations. Every method returns an {@link ActionResult} and never throws.
 */
final class GovJailCellHandlers {

    private GovJailCellHandlers() {
    }

    /**
     * {@code JAIL_CELL_CREATE}: registers a runtime jail cell from explicit coordinates.
     *
     * <p>A name that is already registered is a {@code NAME_TAKEN} refusal with the same message
     * semantics as the command path; anything malformed is {@code BAD_PAYLOAD}.
     */
    static ActionResult jailCellCreate(final GovActionDispatcher.ActionContext context) {
        final String name = context.payload(JailKeys.KEY_CELL_NAME).strip();
        if (name.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD, "Name a jail cell.");
        }
        final String dimension = context.payload(JailKeys.KEY_CELL_DIMENSION).strip();
        if (dimension.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.BAD_PAYLOAD,
                    "Name a dimension for the jail cell.");
        }
        if (ResourceLocation.tryParse(dimension) == null) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.BAD_PAYLOAD,
                    "Use a valid dimension for the jail cell: <namespace:path>.");
        }
        final int x;
        final int y;
        final int z;
        try {
            x = Integer.parseInt(context.payload(JailKeys.KEY_CELL_X).trim());
            y = Integer.parseInt(context.payload(JailKeys.KEY_CELL_Y).trim());
            z = Integer.parseInt(context.payload(JailKeys.KEY_CELL_Z).trim());
        } catch (final NumberFormatException e) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.BAD_PAYLOAD,
                    "Use integer block coordinates: <x,y,z>.");
        }
        final LocationRecord loc = new LocationRecord(dimension, x, y, z);
        if (!context.data().addRuntimeCell(name, loc)) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.NAME_TAKEN,
                    "A jail cell named '" + Sanitizer.name(name) + "' already exists.");
        }
        return ActionResult.success(
                context.request().reqId(),
                "Registered jail cell '" + Sanitizer.name(name) + "' at "
                        + loc.dimension() + ":" + loc.x() + "," + loc.y() + "," + loc.z() + ".");
    }

    /**
     * {@code JAIL_CELL_DELETE}: removes a runtime jail cell by name.
     *
     * <p>A name with no runtime cell is an {@code INVALID_STATE} refusal with the same message
     * semantics as the command path.
     */
    static ActionResult jailCellDelete(final GovActionDispatcher.ActionContext context) {
        final String name = context.payload(JailKeys.KEY_CELL_NAME).strip();
        if (name.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_PAYLOAD, "Name a jail cell.");
        }
        if (!context.data().removeRuntimeCell(name)) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.INVALID_STATE,
                    "No jail cell named '" + Sanitizer.name(name) + "' exists.");
        }
        return ActionResult.success(
                context.request().reqId(),
                "Deleted jail cell '" + Sanitizer.name(name) + "'.");
    }
}
