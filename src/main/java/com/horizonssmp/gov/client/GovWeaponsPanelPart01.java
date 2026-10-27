package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovWeaponsTypes.*;
import static com.horizonssmp.gov.client.GovWeaponsConstants.*;

abstract class GovWeaponsPanelPart01 extends GovWeaponsPanelBase {
    GovWeaponsPanelPart01(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void setVisible(final EditBox box, final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && this.host.getFocused() == box) {
            this.host.setFocused(null);
        }
    }

    // -------------------------------------------------------------------- render

    EditBox box(final Font font, final int maxLength, final String hintKey) {
        final EditBox box = GovWidgets.field(font, 0, 0, 10, 9,
                Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setVisible(false);
        return box;
    }

    /** Keeps an optional leading minus and digits, so a coordinate cannot become text. */

    void setValue(final EditBox box, final String value) {
        if (box != null) {
            box.setValue(value == null ? "" : value);
        }
    }

    static void signedOnly(final EditBox box) {
        if (box == null) {
            return;
        }
        box.setResponder(value -> {
            final String raw = value == null ? "" : value;
            final boolean negative = raw.startsWith("-");
            final String digits = raw.replaceAll("[^0-9]", "");
            final String next = negative ? "-" + digits : digits;
            if (!next.equals(raw)) {
                box.setValue(next);
            }
        });
    }

    static void digitsOnly(final EditBox box) {
        if (box == null) {
            return;
        }
        box.setResponder(value -> {
            final String raw = value == null ? "" : value;
            final String digits = raw.replaceAll("[^0-9]", "");
            if (!digits.equals(raw)) {
                box.setValue(digits);
            }
        });
    }

    /** Selects Registry (0), Permits (1), Areas (2) or Classifier (3). */

    List<EditBox> createWidgets(final Font font) {
        this.font = font;
        if (!this.widgets.isEmpty()) {
            return this.widgets;
        }
        this.classifyBox = box(font, 160, "gui.gov.weapons.classify_hint");
        this.registerSerialBox = box(font, 32, "gui.gov.weapons.serial_hint");
        this.registerTypeBox = box(font, 128, "gui.gov.weapons.type_hint");
        this.ownerBox = box(font, 32, "gui.gov.weapons.player_hint");
        this.transferOwnerBox = box(font, 32, "gui.gov.weapons.player_hint");
        this.permitTargetBox = box(font, 32, "gui.gov.weapons.player_hint");
        this.issueTargetBox = box(font, 32, "gui.gov.weapons.player_hint");
        this.reasonBox = box(font, 256, "gui.gov.weapons.reason_hint");
        this.dimBox = box(font, 64, "gui.gov.weapons.dimension_hint");
        this.xBox = box(font, 10, "gui.gov.weapons.x_hint");
        this.yBox = box(font, 10, "gui.gov.weapons.y_hint");
        this.zBox = box(font, 10, "gui.gov.weapons.z_hint");
        this.radiusBox = box(font, 8, "gui.gov.weapons.radius_hint");
        this.minutesBox = box(font, 8, "gui.gov.weapons.minutes_hint");
        this.areaMinutesBox = box(font, 8, "gui.gov.weapons.minutes_hint");
        this.areaReasonBox = box(font, 256, "gui.gov.weapons.reason_hint");
        signedOnly(this.xBox);
        signedOnly(this.yBox);
        signedOnly(this.zBox);
        digitsOnly(this.radiusBox);
        digitsOnly(this.minutesBox);
        digitsOnly(this.areaMinutesBox);
        this.widgets = List.of(this.classifyBox, this.registerSerialBox, this.registerTypeBox,
                this.ownerBox, this.transferOwnerBox, this.permitTargetBox, this.issueTargetBox,
                this.reasonBox, this.dimBox, this.xBox, this.yBox, this.zBox, this.radiusBox,
                this.minutesBox, this.areaMinutesBox, this.areaReasonBox);
        return this.widgets;
    }

    void syncWidgets() {
        final boolean form = this.active;
        setVisible(this.classifyBox, form && this.subtab == 3 && this.modal == Modal.NONE);
        setVisible(this.permitTargetBox, form && this.subtab == 1 && this.modal == Modal.NONE);
        setVisible(this.registerSerialBox, form && this.modal == Modal.REGISTER);
        setVisible(this.registerTypeBox, form && this.modal == Modal.REGISTER);
        setVisible(this.ownerBox, form && this.modal == Modal.REGISTER);
        setVisible(this.transferOwnerBox, form && this.modal == Modal.TRANSFER);
        setVisible(this.issueTargetBox, form && this.modal == Modal.PERMIT_ISSUE);
        setVisible(this.reasonBox, form && (this.modal == Modal.REVOKE
                || this.modal == Modal.LOSS || this.modal == Modal.PERMIT_REVOKE
                || this.modal == Modal.PERMIT_SUSPEND || this.modal == Modal.AREA_REVOKE));
        setVisible(this.minutesBox, form && this.modal == Modal.PERMIT_SUSPEND);
        setVisible(this.areaMinutesBox, form && this.modal == Modal.AREA_CREATE);
        setVisible(this.dimBox, form && this.modal == Modal.AREA_CREATE);
        setVisible(this.xBox, form && this.modal == Modal.AREA_CREATE);
        setVisible(this.yBox, form && this.modal == Modal.AREA_CREATE);
        setVisible(this.zBox, form && this.modal == Modal.AREA_CREATE);
        setVisible(this.radiusBox, form && this.modal == Modal.AREA_CREATE);
        setVisible(this.areaReasonBox, form && this.modal == Modal.AREA_CREATE);
    }

    GovRect rect(final int x, final int y, final int w, final int h) {
        return new GovRect(Math.max(0, x), Math.max(0, y), Math.max(0, w), Math.max(0, h))
                .clamp(this.bounds);
    }

    void addButton(final int x, final int y, final int w, final int h,
            final Click click, final String value, final String label, final boolean enabled,
            final boolean primary, final boolean selected) {
        if (w <= 0 || h <= 0) {
            return;
        }
        final GovRect rect = rect(x, y, w, h);
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        this.buttons.add(new Button(rect, click, value, label, enabled, primary, selected));
    }

    AreaRow selectedArea() {
        for (final AreaRow row : this.areaRows) {
            if (row.id().equals(this.selectedAreaId)) {
                return row;
            }
        }
        return null;
    }

    PermitRow selectedPermit() {
        for (final PermitRow row : this.permitRows) {
            if (row.id().equals(this.selectedPermitId)) {
                return row;
            }
        }
        return null;
    }

    boolean mutationPending() {
        return this.pending.containsValue(RequestKind.MUTATE);
    }

    void addDetailActions(final GovRect detail) {
        final int y = detail.bottom() - 17;
        final int gap = 3;
        final int available = Math.max(0, detail.w() - 8 - gap * 2);
        final int width = available / 3;
        if (width <= 0) {
            return;
        }
        int x = detail.x() + 4;
        addButton(x, y, width, 14, Click.TRANSFER_OPEN, "", this.buttonTransfer,
                this.canRegister && !mutationPending(), false, false);
        x += width + gap;
        addButton(x, y, width, 14, Click.REVOKE_OPEN, "", this.buttonRevoke,
                this.canRegister && !mutationPending(), false, false);
        x += width + gap;
        addButton(x, y, width, 14, Click.LOSS_OPEN, "", this.buttonLoss,
                this.canRegister && !mutationPending(), false, false);
    }

    GovRect detailBodyViewport(final GovRect detail, final int actionH) {
        if (detail.w() <= 0 || detail.h() <= 0) {
            return EMPTY;
        }
        final int top = detail.y() + 16;
        final int bottom = detail.bottom() - actionH - 2;
        return rect(detail.x(), top, detail.w(), Math.max(0, bottom - top));
    }

    /** A retry sits bottom-centre of the viewport that failed, never outside it. */

    void addRetryButtons() {
        if (this.listState == LoadState.ERROR
                && this.listBodies[this.subtab].h() >= RETRY_H + 24
                && this.listRetryRect.w() > 0) {
            addButton(this.listRetryRect.x(), this.listRetryRect.y(), this.listRetryRect.w(),
                    this.listRetryRect.h(), Click.RETRY, "list", this.buttonRetry, true, false,
                    false);
        }
        if (this.subtab == 0 && this.detailState == LoadState.ERROR
                && this.detailBodyViewport(this.details[0], 18).h() >= RETRY_H + 18
                && this.detailRetryRect.w() > 0) {
            addButton(this.detailRetryRect.x(), this.detailRetryRect.y(), this.detailRetryRect.w(),
                    this.detailRetryRect.h(), Click.RETRY, "detail", this.buttonRetry, true, false,
                    false);
        }
    }

    int currentPages() {
        return switch (this.subtab) {
            case 0 -> this.weaponPages;
            case 1 -> this.permitPages;
            default -> this.areaPages;
        };
    }

    // -------------------------------------------------------------------- modal

    int currentPage() {
        return switch (this.subtab) {
            case 0 -> this.weaponPage;
            case 1 -> this.permitPage;
            default -> this.areaPage;
        };
    }

    void addPageButtons(final GovRect bar) {
        final int gap = 3;
        final int buttonW = Math.min(54, Math.max(0, bar.w() / 3));
        final int y = bar.y() + 1;
        final int h = Math.max(0, bar.h() - 2);
        addButton(bar.x() + 3, y, buttonW, h, Click.PREV, "", this.buttonPrev,
                currentPage() > 0 && !mutationPending(), false, false);
        addButton(bar.x() + 3 + buttonW + gap, y, buttonW, h, Click.NEXT, "",
                this.buttonNext, currentPage() < currentPages() - 1 && !mutationPending(),
                false, false);
        final int refreshW = GovMetrics.span(bar.w(), bar.w() / 5, 44, 60);
        addButton(bar.right() - refreshW - 3, y, refreshW, h, Click.REFRESH, "",
                this.buttonRefresh, !mutationPending(), false, false);
    }

}
