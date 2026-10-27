package com.horizonssmp.gov.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovRect;

final class GovVehiclesTypes {

    private GovVehiclesTypes() {
    }

    enum RequestKind {
        LIST, DETAIL, SCAN, MUTATE
    }

    enum LoadState {
        NONE, LOADING, READY, ERROR
    }

    enum Click {
        FILTER, ROW, PAGE, REFRESH, RETRY_LIST, RETRY_DETAIL,
        SCAN, OWNER_PREV, OWNER_NEXT, REGISTER_CLASS, REGISTER_SUBMIT,
        IMPOUND_SUBMIT, RELEASE,
        LICENSE_PREV, LICENSE_NEXT, LICENSE_POINTS, LICENSE_ISSUE,
        LICENSE_ASSESS, LICENSE_REVOKE
    }

    record VehicleRow(
            String id, String plate, String vehicleClass, String owner, String ownerUuid,
            String status, boolean impound, String impoundReason, String license,
            int points, boolean disposal) {
    }

    record VehicleRowView(
            String id, String plate, String owner, String vehicleClass, String status,
            String licence, Component statusBold) {
    }

    record Hit(GovRect rect, Click click, String value) {

        boolean contains(final double mouseX, final double mouseY) {
            return this.rect.contains(mouseX, mouseY);
        }
    }

    record Button(
            GovRect rect, String label, String confirm, Click click, String value,
            boolean enabled, boolean primary) {
    }

    record FieldRow(
            int tab, GovRect labelRect, GovRect fieldRect, EditBox box, String label) {
    }

    record DetailLine(
            String label, String value, FormattedCharSequence wrapped, int color) {
    }
}
