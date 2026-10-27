package com.horizonssmp.gov.client;

import com.horizonssmp.gov.Gov;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The rail icons.
 *
 * <p>The runtime assets are the original 16x16 PNGs recoloured from white to ink
 * {@code #1A1A1A}. The alpha channel is untouched, so the same silhouette that used to sit on a
 * dark dashboard now reads as a stamped mark on paper. The SVG sources are design material and
 * are deliberately not read at runtime.
 *
 * <p>Drawing scales the pose from the 16-pixel source to the requested size, translates to the
 * exact requested origin, and resets both the pose and the shader colour in a {@code finally}
 * block. Nothing about an icon draw is allowed to leak into the next widget.
 */
public final class GovIcons {

    /** The twelve section icons shipped in {@code assets/gov/textures/gui/icons}. */
    public enum GovIcon {
        DASHBOARD("dashboard"),
        LAW("law"),
        FINES("tickets"),
        VEHICLES("vehicles"),
        WEAPONS("weapons"),
        PEOPLE("people"),
        BUSINESSES("businesses"),
        DETENTION("jail"),
        JOBS("jobs"),
        NOTES("notes"),
        AUDIT("audit"),
        POLICE("police");

        private final String file;
        private final ResourceLocation location;

        GovIcon(final String file) {
            this.file = file;
            this.location = ResourceLocation.fromNamespaceAndPath(
                    Gov.MODID, "textures/gui/icons/" + file + ".png");
        }

        /** The runtime texture path for this icon. */
        public ResourceLocation location() {
            return this.location;
        }

        /** The file stem, without extension or directory. */
        public String file() {
            return this.file;
        }
    }

    private GovIcons() {
    }

    /** Draws an icon at its full ink strength. */
    public static void draw(
            final GuiGraphics graphics, final GovIcon icon, final int x, final int y,
            final int size) {
        draw(graphics, icon, x, y, size, 1.0F);
    }

    /** Draws an icon at a reduced alpha for a disabled control. */
    public static void drawDisabled(
            final GuiGraphics graphics, final GovIcon icon, final int x, final int y,
            final int size) {
        draw(graphics, icon, x, y, size, 0.35F);
    }

    /**
     * Draws an icon with an explicit alpha multiplier.
     *
     * <p>The shader colour is reset in a {@code finally} block even if the blit throws. GUI code
     * shares one shader colour with every later fill and text draw, so a leaked alpha would dim
     * the rest of the frame.
     */
    public static void draw(
            final GuiGraphics graphics, final GovIcon icon, final int x, final int y,
            final int size, final float alpha) {
        if (graphics == null || icon == null || size <= 0) {
            return;
        }
        final float safeAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
        final float scale = (float) size / 16.0F;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.setColor(1.0F, 1.0F, 1.0F, safeAlpha);
            graphics.blit(icon.location(), 0, 0, 0, 0, 16, 16, 16, 16);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
        }
    }
}
