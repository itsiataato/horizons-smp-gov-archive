package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.List;

import com.horizonssmp.gov.common.NoteKeys;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

class GovNotesWidgets extends GovNotesUtil {
    GovNotesWidgets(final GovDashboardScreen host) {
        super(host);
    }

    protected List<EditBox> createWidgets(final Font font) {
        this.font = font;
        if (this.targetBox == null) {
            this.targetBox = GovWidgets.field(font, 0, 0, 10, 9, this.targetHint);
            this.targetBox.setMaxLength(36);
            this.targetBox.setVisible(false);
            this.targetBox.setResponder(this::onTargetChanged);

            this.textBox = GovWidgets.field(font, 0, 0, 10, 9, this.noteHint);
            this.textBox.setMaxLength(NoteKeys.MAX_NOTE_CHARS);
            this.textBox.setVisible(false);
            this.textBox.setResponder(this::onTextChanged);

            this.widgets = List.of(this.targetBox, this.textBox);
        }
        syncWidgets();
        return this.widgets;
    }

    protected void onTargetChanged(final String value) {
        this.hasTarget = value != null && !value.isBlank();
        final String typed = value == null ? "" : value.strip();
        String expected = "";
        if (this.mode == 2) {
            final BusinessState state = this.bizStates.get(this.selectedBizId);
            expected = state == null ? "" : state.target;
        } else {
            final View view = currentView();
            expected = view == null ? "" : view.target;
        }
        this.targetDirty = !typed.equals(expected);
    }

    protected void onTextChanged(final String value) {
        this.hasText = value != null && !value.isBlank();
    }

    protected void clearFocus() {
        if (this.host.getFocused() == this.targetBox || this.host.getFocused() == this.textBox) {
            this.host.setFocused(null);
        }
    }

    protected boolean blurVisibleField() {
        final boolean targetFocused = this.targetBox != null && this.targetBox.isVisible()
                && this.host.getFocused() == this.targetBox;
        final boolean textFocused = this.textBox != null && this.textBox.isVisible()
                && this.host.getFocused() == this.textBox;
        if (targetFocused || textFocused) {
            this.host.setFocused(null);
            return true;
        }
        return false;
    }
}
