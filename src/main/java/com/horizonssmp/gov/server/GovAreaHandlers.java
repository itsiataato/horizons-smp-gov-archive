package com.horizonssmp.gov.server;


import java.util.List;
import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.common.records.PermitRecord;


import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovAreaHandlers {

    private GovAreaHandlers() {
    }

    // --------------------------------------------------------- explosion areas

    /** {@code AREA_PERMIT_LIST}: the public Article 8.12 explosion-area registry. */
    static ActionResult areaPermitList(final GovActionDispatcher.ActionContext context) {
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(WeaponService.areaListFields(
                        context.data(), context.capabilities(),
                        parsePage(context.payload(WeaponKeys.KEY_PAGE)), context.now())));
    }

    /** {@code AREA_PERMIT_CREATE}: issues a bounded, time-limited explosion area. */
    static ActionResult areaPermitCreate(final GovActionDispatcher.ActionContext context) {
        final WeaponService.Outcome outcome = WeaponService.createArea(
                context.data(),
                weaponActor(context),
                context.payload(WeaponKeys.KEY_DIMENSION),
                context.payload(WeaponKeys.KEY_X),
                context.payload(WeaponKeys.KEY_Y),
                context.payload(WeaponKeys.KEY_Z),
                context.payload(WeaponKeys.KEY_RADIUS),
                context.payload(WeaponKeys.KEY_VALID_MINUTES),
                context.payload(WeaponKeys.KEY_REASON),
                context.now());
        return toResult(context, outcome);
    }

    /** {@code AREA_PERMIT_REVOKE}: cancels an explosion-area permit with a reason. */
    static ActionResult areaPermitRevoke(final GovActionDispatcher.ActionContext context) {
        final WeaponService.Outcome outcome = WeaponService.revokeArea(
                context.data(),
                weaponActor(context),
                context.payload(WeaponKeys.KEY_AREA_ID),
                context.payload(WeaponKeys.KEY_REASON),
                context.now());
        return toResult(context, outcome);
    }


}
