package com.horizonssmp.gov.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class GovThemedEditBox extends EditBox {

    private final Font font;
    private final String placeholder;
    private int selectionAnchor;
    private int[] prefixWidths = new int[] {0};
    private String measuredValue = "";

    GovThemedEditBox(
            final Font font, final int x, final int y, final int width, final int height,
            final Component hint) {
        super(font, x, y, width, height, Component.empty());
        this.font = font;
        this.placeholder = hint == null ? "" : hint.getString();
    }

    @Override
    public void setHighlightPos(final int position) {
        this.selectionAnchor = Math.max(0, Math.min(position, getValue().length()));
        super.setHighlightPos(this.selectionAnchor);
    }

    @Override
    public void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final float partialTick) {
        this.normaliseValue();
        final int cursor = getCursorPosition();
        final boolean selecting = cursor != this.selectionAnchor;
        if (selecting) {
            super.setHighlightPos(cursor);
            this.drawSelection(graphics, cursor);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (selecting) {
            super.setHighlightPos(this.selectionAnchor);
        } else if (getValue().isEmpty() && !isFocused() && !this.placeholder.isEmpty()) {
            GovText.clipped(graphics, this.font, this.placeholder, this.textX(), this.textY(),
                    getInnerWidth(), GovTheme.MUTED);
        }
    }

    private void normaliseValue() {
        final String value = getValue();
        if (value.equals(this.measuredValue)) {
            return;
        }
        this.measuredValue = value;
        this.prefixWidths = new int[value.length() + 1];
        for (int index = 1; index <= value.length(); index++) {
            this.prefixWidths[index] = this.font.width(value.substring(0, index));
        }
        final int cursor = getCursorPosition();
        final int anchor = this.selectionAnchor;
        super.setCursorPosition(0);
        super.setCursorPosition(cursor);
        super.setHighlightPos(anchor);
    }

    private void drawSelection(final GuiGraphics graphics, final int cursor) {
        final String value = getValue();
        final int textY = this.textY();
        if (this.font.width(value) > getInnerWidth()) {
            graphics.fill(getX(), textY, getX() + getWidth(), textY + this.font.lineHeight,
                    GovTheme.SELECTION_TINT);
            return;
        }
        final int start = Math.min(cursor, this.selectionAnchor);
        final int end = Math.min(Math.max(cursor, this.selectionAnchor), value.length());
        if (start >= end) {
            return;
        }
        final int x1 = this.textX() + this.prefixWidths[start];
        final int x2 = this.textX() + this.prefixWidths[end];
        if (x2 > x1) {
            graphics.fill(x1, textY, x2, textY + this.font.lineHeight,
                    GovTheme.SELECTION_TINT);
        }
    }

    private int textX() {
        return isBordered() ? getX() + 4 : getX();
    }

    private int textY() {
        return isBordered() ? getY() + (getHeight() - 8) / 2 : getY();
    }
}
