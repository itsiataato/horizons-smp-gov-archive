package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class GovFinesWidgets {

    static List<EditBox> createWidgets(final GovFinesPanel p, final Font font) {
        p.font = font;
        if (!p.widgets.isEmpty()) {
            return p.widgets;
        }
        p.articleBox = box(font, 16, "gui.gov.fines.article_hint", "9.11");
        p.restitutionBox = box(font, 6, "gui.gov.fines.restitution_hint", "0");
        p.victimBox = box(font, 64, "gui.gov.fines.victim_hint", "");
        p.exileBox = box(font, 2, "gui.gov.fines.exile_hint", "7");
        p.issueReasonBox = box(font, 200, "gui.gov.fines.reason_hint", "");
        p.waiveReasonBox = box(font, 200, "gui.gov.fines.reason_hint", "");
        digitsOnly(p.restitutionBox);
        digitsOnly(p.exileBox);
        p.widgets = List.of(p.articleBox, p.restitutionBox, p.victimBox,
                p.exileBox, p.issueReasonBox, p.waiveReasonBox);
        return p.widgets;
    }

    private static EditBox box(final Font font, final int maxLength, final String hintKey,
            final String initial) {
        final EditBox box = GovWidgets.field(font, 0, 0, 10, ROW_TEXT,
                Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setValue(initial);
        box.setVisible(false);
        return box;
    }

    static void digitsOnly(final EditBox box) {
        if (box == null) {
            return;
        }
        box.setResponder(value -> {
            final String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
            if (!digits.equals(value)) {
                box.setValue(digits);
            }
        });
    }
}