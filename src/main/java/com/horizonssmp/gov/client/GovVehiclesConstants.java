package com.horizonssmp.gov.client;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.client.GovRect;

final class GovVehiclesConstants {

    private GovVehiclesConstants() {
    }

    static final int TAB_REGISTRY = 0;
    static final int TAB_SCAN = 1;
    static final int TAB_REGISTER = 2;
    static final int TAB_IMPOUND = 3;
    static final int TAB_DRIVERS_LICENSE = 4;
    static final int TAB_VEHICLE_LICENSES = 5;

    static final int ROW_TEXT = 9;
    static final int DETAIL_LINE = 10;
    static final long ARM_FLOOR_MS = 600L;
    static final long ARM_WINDOW_MS = 5000L;
    static final int RETRY_W = 52;
    static final int RETRY_H = 14;
    static final GovRect NONE = new GovRect(0, 0, 0, 0);
    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    static final String[] FILTERS = {
        VehicleKeys.FILTER_ALL, VehicleKeys.FILTER_VALID,
        VehicleKeys.FILTER_IMPOUNDED, VehicleKeys.FILTER_FLAGGED
    };
    static final String[] CLASSES = {"quad", "car", "truck", "helicopter", "plane"};
    static final String[] POINTS = {"2", "4", "6"};

    static final String[] LABEL_KEYS = {
        "gui.gov.previous", "gui.gov.next", "gui.gov.refresh",
        "gui.gov.vehicles.registry_title", "gui.gov.vehicles.scan_title",
        "gui.gov.vehicles.register_title", "gui.gov.vehicles.impound_title_short",
        "gui.gov.vehicles.license_title",
        "gui.gov.vehicles.filter.all", "gui.gov.vehicles.filter.valid",
        "gui.gov.vehicles.filter.impounded", "gui.gov.vehicles.filter.flagged",
        "gui.gov.vehicles.scan_button", "gui.gov.vehicles.scan_forbidden",
        "gui.gov.vehicles.scan_needs_plate", "gui.gov.vehicles.scan_error",
        "gui.gov.vehicles.scan_unregistered", "gui.gov.vehicles.scan_result",
        "gui.gov.vehicles.register_action", "gui.gov.vehicles.register_button",
        "gui.gov.vehicles.impound_action", "gui.gov.vehicles.impound_button",
        "gui.gov.vehicles.impound_confirm", "gui.gov.vehicles.impound_warning",
        "gui.gov.vehicles.release_button", "gui.gov.vehicles.confirm_release",
        "gui.gov.vehicles.release_rule",
        "gui.gov.vehicles.license_assess", "gui.gov.vehicles.license_issue",
        "gui.gov.vehicles.license_revoke", "gui.gov.vehicles.license_revoke_confirm",
        "gui.gov.vehicles.license_points_hint", "gui.gov.vehicles.license_warning",
        "gui.gov.vehicles.license_none",
        "gui.gov.vehicles.field.plate", "gui.gov.vehicles.field.owner",
        "gui.gov.vehicles.field.class", "gui.gov.vehicles.field.status",
        "gui.gov.vehicles.field.license", "gui.gov.vehicles.field.points",
        "gui.gov.vehicles.field.suspended", "gui.gov.vehicles.field.expires",
        "gui.gov.vehicles.field.reason", "gui.gov.vehicles.field.fee",
        "gui.gov.vehicles.field.impound", "gui.gov.vehicles.field.impound_reason",
        "gui.gov.vehicles.field.registered", "gui.gov.vehicles.field.disposal",
        "gui.gov.vehicles.field.impounded_at", "gui.gov.vehicles.field.impounded_by",
        "gui.gov.vehicles.field.released", "gui.gov.vehicles.field.location",
        "gui.gov.vehicles.loading", "gui.gov.vehicles.error", "gui.gov.vehicles.detail_error",
        "gui.gov.vehicles.count", "gui.gov.vehicles.showing", "gui.gov.vehicles.page",
        "gui.gov.vehicles.empty.all", "gui.gov.vehicles.empty.filter",
        "gui.gov.vehicles.empty.own", "gui.gov.vehicles.no_selection",
        "gui.gov.vehicles.no_owner", "gui.gov.vehicles.no_online",
        "gui.gov.vehicles.plate_required", "gui.gov.vehicles.reason_required",
        "gui.gov.vehicles.reception_missing", "gui.gov.vehicles.recorded",
        "gui.gov.vehicles.not_impounded", "gui.gov.vehicles.impounded_short",
        "gui.gov.vehicles.flagged_short", "gui.gov.vehicles.disposal_yes",
        "gui.gov.vehicles.disposal_no", "gui.gov.vehicles.released_at",
        "gui.gov.vehicles.impounded_at", "gui.gov.vehicles.never_impounded",
        "gui.gov.vehicles.class.quad", "gui.gov.vehicles.class.car",
        "gui.gov.vehicles.class.truck", "gui.gov.vehicles.class.helicopter",
        "gui.gov.vehicles.class.plane", "gui.gov.vehicles.state.active",
        "gui.gov.vehicles.state.impounded", "gui.gov.vehicles.state.disposed",
        "gui.gov.vehicles.state.flagged", "gui.gov.vehicles.state.valid",
        "gui.gov.vehicles.select_prompt",
        "gui.gov.vehicles.gold_ingots",
        "gui.gov.vehicles.scan_not_registered",
        "gui.gov.vehicles.register_rule",
        "gui.gov.vehicles.record_prefix", "gui.gov.vehicles.points_short",
        "gui.gov.vehicles.points_full", "gui.gov.card.retry"
    };
}
