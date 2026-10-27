package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class GovAuditWidgets {
    static List<EditBox> createWidgets(final GovAuditPanel p, final Font font) {
        p.font = font;
        if (p.widgets.isEmpty()) {
            p.actorBox = box(font, "gui.gov.audit.filter.actor_hint");
            p.actionBox = box(font, "gui.gov.audit.filter.action_hint");
            p.targetBox = box(font, "gui.gov.audit.filter.target_hint");
            p.tokenBox = box(font, "gui.gov.audit.token_hint");
            p.widgets = List.of(p.actorBox, p.actionBox, p.targetBox, p.tokenBox);
        }
        if (p.bounds.w() > 0) {
            GovAuditGeometry.positionWidgets(p);
        }
        return p.widgets;
    }

    static EditBox box(final Font font, final String hintKey) {
        final EditBox box = GovWidgets.field(font, 0, 0, 10, 9,
                Component.translatable(hintKey));
        box.setMaxLength(64);
        box.setTextShadow(false);
        box.setVisible(false);
        return box;
    }

    static void syncWidgets(final GovAuditPanel p) {
        final boolean auditMode = p.active && p.mode == GovAuditPanel.Mode.AUDIT;
        final boolean adminMode = p.active && p.mode == GovAuditPanel.Mode.ADMIN
                && GovAuditCaches.adminControlsVisible(p);
        setVisible(p, p.actorBox, auditMode);
        setVisible(p, p.actionBox, auditMode);
        setVisible(p, p.targetBox, auditMode);
        setVisible(p, p.tokenBox, adminMode);
        if (!adminMode) {
            p.commitArmed = false;
            if (p.mode == GovAuditPanel.Mode.ADMIN) {
                p.exportArm = GovAuditPanel.ExportArm.NONE;
            }
        }
    }

    static void setVisible(final GovAuditPanel p, final EditBox box, final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && p.host.getFocused() == box) {
            p.host.setFocused(null);
        }
    }

    static boolean clearWidgetFocus(final GovAuditPanel p) {
        final EditBox[] boxes = {p.actorBox, p.actionBox, p.targetBox, p.tokenBox};
        boolean cleared = false;
        for (final EditBox box : boxes) {
            if (box != null && p.host.getFocused() == box) {
                p.host.setFocused(null);
                cleared = true;
            }
        }
        return cleared;
    }
}