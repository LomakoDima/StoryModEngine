package com.dimalab.storymodengine.client.dialogue;

/**
 * Where and how wide the dialogue box is — entirely relative, resolved against the live screen
 * size at render time (never a stored pixel {@code x}/{@code y}), so it's correct at any resolution
 * or GUI Scale by construction. {@link #DEFAULT} is bottom-center at up to 50% of screen width
 * (the box itself is content-hugging — see {@code DialogueWindowRenderer} — so this is a cap, not a
 * fixed width); {@code margin = 46} clears the hotbar (22px) and the health/food/armor row above it
 * (up to 39px, per {@code Gui.renderPlayerHealth}) with a few px of breathing room, rather than
 * sitting flush against the bottom edge (a plain distance-from-bottom-edge like a reference
 * {@code screenHeight - 100} constant would give, except resolved as a genuine bottom-edge margin
 * that stays correct as content height varies). Deliberately does *not* also clear the held-item-name
 * tooltip (which can reach ~59-73px) — that popup is transient (a few seconds after switching hotbar
 * slots) rather than a fixture like the health row, and reserving permanent space for it left a
 * visibly empty gap under the box the rest of the time.
 */
public record DialogueLayout(
        HorizontalAnchor horizontalAnchor,
        VerticalAnchor verticalAnchor,
        float maxWidthFraction,
        int margin,
        int padding,
        int offsetX,
        int offsetY
) {
    public static final DialogueLayout DEFAULT = new DialogueLayout(
            HorizontalAnchor.CENTER, VerticalAnchor.BOTTOM, 0.5f, 50, 8, 0, 0);

    /** The box width for a given screen width — {@code maxWidthFraction} of the screen, never wider than the screen minus its margins. */
    public int resolveWidth(int screenWidth) {
        int maxWidth = Math.round(screenWidth * maxWidthFraction);
        return Math.min(maxWidth, Math.max(0, screenWidth - 2 * margin));
    }

    public int resolveX(int screenWidth, int boxWidth) {
        int base = switch (horizontalAnchor) {
            case LEFT -> margin;
            case CENTER -> (screenWidth - boxWidth) / 2;
            case RIGHT -> screenWidth - boxWidth - margin;
        };
        return base + offsetX;
    }

    public int resolveY(int screenHeight, int boxHeight) {
        int base = switch (verticalAnchor) {
            case TOP -> margin;
            case CENTER -> (screenHeight - boxHeight) / 2;
            case BOTTOM -> screenHeight - boxHeight - margin;
        };
        return base + offsetY;
    }
}
