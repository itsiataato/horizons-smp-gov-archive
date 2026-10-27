package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

final class GovPeopleWidgets {
    static List<EditBox> createWidgets(final GovPeoplePanel p, final Font font) {
        p.font = font;
        if (p.widgets.isEmpty()) {
            p.searchBox = GovWidgets.field(font, 0, 0, 10, 9,
                    net.minecraft.network.chat.Component.translatable("gui.gov.people.search"));
            p.searchBox.setMaxLength(64);
            p.searchBox.setVisible(false);

            p.noteBox = GovWidgets.field(font, 0, 0, 10, 9,
                    net.minecraft.network.chat.Component.translatable("gui.gov.people.note_new"));
            p.noteBox.setMaxLength(com.horizonssmp.gov.common.NoteKeys.MAX_NOTE_CHARS);
            p.noteBox.setVisible(false);

            p.widgets = List.of(p.searchBox, p.noteBox);
        }
        if (p.bounds.w() > 0) {
            positionWidgets(p);
        }
        return p.widgets;
    }

    static EditBox createSearchBox(final GovPeoplePanel p, final Font font) {
        return createWidgets(p, font).get(0);
    }

    static EditBox createNoteBox(final GovPeoplePanel p, final Font font) {
        return createWidgets(p, font).get(1);
    }

    static void syncWidgets(final GovPeoplePanel p) {
        if (p.searchBox != null) {
            final boolean visible = p.active && p.mode == GovPeoplePanel.SUBTAB_SEARCH;
            if (visible != p.searchBoxVisible) {
                p.searchBoxVisible = visible;
                p.searchBox.setVisible(visible);
                if (!visible && p.host.getFocused() == p.searchBox) {
                    p.host.setFocused(null);
                }
            }
        }
        if (p.noteBox != null) {
            final boolean visible = p.active && p.mode == GovPeoplePanel.SUBTAB_NOTES
                    && p.canManageNotes && p.canPostNotes
                    && p.detailState == GovPeoplePanelModel.LoadState.READY
                    && !p.selectedUuid.isEmpty();
            if (visible != p.noteBoxVisible) {
                p.noteBoxVisible = visible;
                p.noteBox.setVisible(visible);
                if (!visible && p.host.getFocused() == p.noteBox) {
                    p.host.setFocused(null);
                }
            }
        }
    }

    static void clearWidgetFocus(final GovPeoplePanel p) {
        if (p.searchBox != null && p.host.getFocused() == p.searchBox) {
            p.host.setFocused(null);
        }
        if (p.noteBox != null && p.host.getFocused() == p.noteBox) {
            p.host.setFocused(null);
        }
    }

    static void positionWidgets(final GovPeoplePanel p) {
        if (p.searchBox != null) {
            p.searchBox.setX(p.searchFieldRect.x() + 4);
            p.searchBox.setY(p.searchFieldRect.y() + 4);
            p.searchBox.setWidth(Math.max(1, p.searchFieldRect.w() - 8));
        }
        if (p.noteBox != null) {
            p.noteBox.setX(p.noteFieldRect.x() + 4);
            p.noteBox.setY(p.noteFieldRect.y() + 4);
            p.noteBox.setWidth(Math.max(1, p.noteFieldRect.w() - 8));
        }
    }
}
