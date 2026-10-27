package com.horizonssmp.gov.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovRect;
import com.horizonssmp.gov.client.GovVehiclesTypes.*;
import static com.horizonssmp.gov.client.GovVehiclesConstants.*;

abstract class GovVehiclesPanelAbstracts extends GovVehiclesPanelBase {

    GovVehiclesPanelAbstracts(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    abstract List<EditBox> createWidgets(final Font font);
    abstract EditBox box(final Font font, final int maxLength, final String hintKey);
    abstract void setActive(final boolean active);
    abstract void reset();
    abstract void setSubtab(final int index);
    abstract void layout(final int left, final int top, final int right, final int bottom, final Font font);
    abstract void computeListGeometry();
    abstract void computeColumns();
    abstract void computeScanGeometry();
    abstract void computeRegisterGeometry();
    abstract void computeImpoundGeometry();
    abstract void computeLicenseGeometry();
    abstract void positionFields();
    abstract void addField(final int tab, final GovRect labelRect, final GovRect fieldRect, final EditBox box, final String labelKey);
    abstract void layoutBox(final EditBox box, final GovRect rect);
    abstract GovRect rect(final int x, final int y, final int width, final int height);
    abstract void rebuildLabels();
    abstract String label(final String key);
    abstract String classText(final String id);
    abstract String classButtonLabel(final String id, final int width);
    abstract String statusText(final String id);
    abstract void rebuildAll();
    abstract void rebuildRows();
    abstract void rebuildDetail();
    abstract void addDetail(final String labelKey, final String value, final int color);
    abstract void addWrappedDetail(final String labelKey, final String value, final int valueW);
    abstract void rebuildScan();
    abstract void addScanLine(final String text, final int width);
    abstract void rebuildFormInfo();
    abstract void rebuildStatus();
    abstract int statusRectWidth();
    abstract void rebuildButtons();
    abstract void addRetryButtons();
    abstract void addListButtons();
    abstract void addButton(final GovRect rect, final String label, final String confirm, final Click click, final String value, final boolean enabled, final boolean primary);
    abstract void rebuildHits();
    abstract void requestListIfNeeded();
    abstract void requestList(final String filter, final int requestedPage);
    abstract void requestDetail(final String plate);
    abstract void requestScan(final String rawPlate);
    abstract void mutate(final OpCode op, final Map<String, String> payload);
    abstract boolean mutationPending();
    abstract void onActionResult(final GovActionResultPayload payload);
    abstract void applyList(final String delta);
    abstract void applyDetail(final String delta);
    abstract void selectVehicle(final String plate);
    abstract void setFilter(final String next);
    abstract void ensureVehicleSelection();
    abstract void cycleVehicle(final int delta);
    abstract void prefillImpoundPlate();
    abstract void syncWidgets();
    abstract void setVisible(final EditBox box, final boolean visible);
    abstract void unfocusAll();
    abstract void disarm();
    abstract boolean isArmed(final Click click, final long now);
    abstract boolean isArmedVisual(final Click click, final long now);
    abstract void setStatus(final String text, final boolean error);
    abstract void render(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawRegistry(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawList(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawTableHeader(final GuiGraphics graphics);
    abstract void drawSummary(final GuiGraphics graphics);
    abstract void drawDetail(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawBottomBar(final GuiGraphics graphics);
    abstract void drawScan(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawRegister(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawImpound(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawLicences(final GuiGraphics graphics, final int mouseX, final int mouseY, final boolean manage);
    abstract void drawFieldRows(final GuiGraphics graphics);
    abstract void drawButtons(final GuiGraphics graphics, final int mouseX, final int mouseY);
    abstract void drawText(final GuiGraphics graphics, final String text, final int x, final int y, final int width, final int color);
    abstract boolean mouseClicked(final double mouseX, final double mouseY, final int button);
    abstract boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY);
    abstract boolean fieldContains(final EditBox box, final double mouseX, final double mouseY);
    abstract boolean keyPressed(final int keyCode, final int scanCode, final int modifiers);
    abstract boolean charTyped(final char codePoint, final int modifiers);
    abstract void moveSelection(final int delta);
    abstract void changePage(final int delta);
    abstract void handleClick(final Button button);
    abstract void submitEnter();
    abstract boolean impoundActive();
    abstract boolean licenceOwnerKnown();
    abstract String defaultOwner();
    abstract void cycleOwner(final int delta);
    abstract void submitRegister();
    abstract String impoundConfirmText();
    abstract void submitImpound();
    abstract void releaseSelected();
    abstract void issueLicense();
    abstract void assessPoints();
    abstract void revokeLicense();
    abstract void clearMutationFields();
    abstract String scanText(final Map<String, String> fields);
    abstract String statusLabel(final VehicleRow row);
    abstract String detailStatus();
    abstract String licenceShort(final VehicleRow row);
    abstract String licenceText();
    abstract String impoundText();
    abstract String disposalText();

    static String tr(final String key) {
        return Component.translatable(key).getString();
    }

    static String trArgs(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    static String up(final String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    static String normalizePlate(final String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replace("-", "").replace(" ", "");
    }

    static String gold(final String raw) {
        return up(trArgs("gui.gov.vehicles.gold_ingots",
                raw == null || raw.isBlank() ? "0" : raw));
    }

    static String date(final long epochMillis) {
        return epochMillis <= 0L ? "-" : DATE.format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    static String dash(final String value) {
        return com.horizonssmp.gov.common.GovFields.dash(value);
    }

    static GovRect retryRect(final GovRect viewport) {
        if (viewport.w() <= 0 || viewport.h() <= 0) {
            return NONE;
        }
        final int width = Math.min(RETRY_W, viewport.w());
        final int height = Math.min(RETRY_H, viewport.h());
        return new GovRect(viewport.x() + (viewport.w() - width) / 2,
                viewport.bottom() - height, width, height);
    }

    static String fallbackError(final GovActionResultPayload payload, final String key) {
        return payload.errorText().isBlank()
                ? Component.translatable(key).getString()
                : payload.errorText();
    }

    static int intField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key);
    }

    static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }
}
