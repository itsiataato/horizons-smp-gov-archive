package com.horizonssmp.gov.client;

/**
 * An immutable pixel rectangle used by every Gov surface.
 *
 * <p>Sizes are validated as non-negative at construction, so a rendering path never has to
 * defend against a rectangle that reports a negative width or height. A zero-sized rectangle is
 * legal and means "nothing to draw"; callers should still guard text drawing because a zero
 * rectangle has no area to clip against.
 */
public record GovRect(int x, int y, int w, int h) {

    public GovRect {
        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("GovRect must not have negative size: " + w + "x" + h);
        }
    }

    /** The first pixel to the right of this rectangle. */
    public int right() {
        return this.x + this.w;
    }

    /** The first pixel below this rectangle. */
    public int bottom() {
        return this.y + this.h;
    }

    /** True when the point is inside the half-open rectangle. */
    public boolean contains(final double pointX, final double pointY) {
        return this.w > 0 && this.h > 0
                && pointX >= this.x && pointX < this.right()
                && pointY >= this.y && pointY < this.bottom();
    }

    /** True when the integer point is inside the half-open rectangle. */
    public boolean contains(final int pointX, final int pointY) {
        return contains((double) pointX, (double) pointY);
    }

    /** A rectangle inset by the same amount on every side. */
    public GovRect inset(final int amount) {
        return inset(amount, amount);
    }

    /**
     * A rectangle inset horizontally and vertically.
     *
     * <p>Insets are shrink-only: a negative value is treated as zero, and an inset larger than
     * half the rectangle collapses it to an empty rectangle at the centre. The result therefore
     * always stays inside the original bounds, which is what the "everything fits" rule needs.
     */
    public GovRect inset(final int horizontal, final int vertical) {
        final int dx = Math.min(Math.max(0, horizontal), this.w / 2);
        final int dy = Math.min(Math.max(0, vertical), this.h / 2);
        return new GovRect(this.x + dx, this.y + dy, this.w - dx * 2, this.h - dy * 2);
    }

    /** The same origin and height with a new width. */
    public GovRect withWidth(final int width) {
        return new GovRect(this.x, this.y, Math.max(0, width), this.h);
    }

    /** The same origin and width with a new height. */
    public GovRect withHeight(final int height) {
        return new GovRect(this.x, this.y, this.w, Math.max(0, height));
    }

    /** The same size moved by the given offset. */
    public GovRect translate(final int dx, final int dy) {
        return new GovRect(this.x + dx, this.y + dy, this.w, this.h);
    }

    /**
     * Moves and shrinks this rectangle until it lies inside the container.
     *
     * <p>This is the last line of defence for the "everything fits" rule: a caller may compute a
     * width from a minimum that is larger than the available space, and this helper turns that
     * into a clipped rectangle rather than a negative one. A zero-width container yields a
     * zero-width result at the container origin.
     */
    public GovRect clamp(final GovRect container) {
        if (container == null) {
            return this;
        }
        final int width = Math.min(this.w, container.w);
        final int height = Math.min(this.h, container.h);
        final int maxX = container.x + container.w - width;
        final int maxY = container.y + container.h - height;
        final int clampedX = Math.max(container.x, Math.min(maxX, this.x));
        final int clampedY = Math.max(container.y, Math.min(maxY, this.y));
        return new GovRect(clampedX, clampedY, width, height);
    }
}
