package com.dimalab.storymodengine.client.dialogue;

import com.dimalab.storymodengine.common.dialogue.DialogueLine;
import com.dimalab.storymodengine.api.dialogue.DialogueMode;
import com.dimalab.storymodengine.common.dialogue.DialogueSpeaker;
import com.dimalab.storymodengine.common.dialogue.DialogueSpeakerRegistry;
import com.dimalab.storymodengine.common.dialogue.DialogueWindowAnimation;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.math.interp.Easing;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The actual draw calls for the {@link DialogueWindow} overlay — deliberately separate from {@link
 * DialogueWindowState} (which owns *what* is showing and *when* it started) and from input handling
 * (there is none here; this class never reads mouse/keyboard). Broken into the named steps a
 * cinematic HUD needs: {@link #renderBackground}, {@link #renderAvatar}, {@link #renderText}
 * (speaker + message drawn as one inline {@code [Name]: text} run — see below for why), all called
 * from {@link #render}. {@code renderChoices}/{@code choicesHeight} are unrelated to any of that —
 * they're {@link DialogueScreen}'s own drawing helpers, kept here unchanged since choices weren't in
 * scope to restructure this round. There is no "reveal complete" indicator — removed on request;
 * a mod author who wants one can watch {@code state.typewriter().isComplete()} themselves.
 *
 * <p>The box is **content-hugging**: {@link Font#split} against a relative wrap *cap* (from {@link
 * DialogueLayout#resolveWidth}, never an absolute pixel width) gives the wrapped line list, and the
 * box is sized to the actually-measured widest line and line count — not always stretched to the
 * cap, and not guessed. Measured from the line's *full* text (not just what the typewriter has
 * revealed so far), so the box is a stable size throughout the reveal instead of visibly resizing.
 * Name and message render as one composed {@code Component} — {@code [Name]: message} — the same
 * shape a reference implementation this style was matched against uses, rather than the name
 * occupying its own reserved line above the text.
 */
final class DialogueWindowRenderer {

    private static final int AVATAR_SIZE = 24;
    private static final int ICON_GAP = 4;
    private static final float WHISPER_TEXT_SCALE = 0.85f;
    private static final int SLIDE_DISTANCE_EXTRA = 12;

    private DialogueWindowRenderer() {
    }

    static void render(GuiGraphics graphics, Font font, DialogueWindowState state, int screenWidth, int screenHeight, long nowMillis) {
        DialogueLine line = state.line();
        if (line == null) {
            return;
        }
        float rawVisibility = state.visibility(nowMillis);
        if (rawVisibility <= 0f && state.isHiding()) {
            return;
        }
        // Eased, not linear — a linear fade/slide reads as robotic. EASE_OUT on the way in (settles
        // gently), EASE_IN on the way out (accelerates away) — the standard entrance/exit pairing.
        float visibility = state.isHiding() ? Easing.EASE_IN_CUBIC.apply(rawVisibility) : Easing.EASE_OUT_CUBIC.apply(rawVisibility);

        DialogueStyle style = state.style();
        DialogueLayout layout = style.layout();
        DialogueMode mode = line.mode();
        float textScale = mode == DialogueMode.WHISPER ? WHISPER_TEXT_SCALE : 1f;

        DialogueSpeaker speaker = DialogueSpeakerRegistry.get(line.speaker());
        ResourceLocation portrait = resolvePortrait(line, speaker);
        String displayName = speaker != null ? speaker.displayName() : line.speaker();
        int nameColor = resolveNameColor(line, speaker, style);
        boolean hasSpeakerName = displayName != null && !displayName.isEmpty();

        int avatarSize = portrait != null ? AVATAR_SIZE : 0;
        int iconReservedWidth = portrait != null ? avatarSize + ICON_GAP : 0;

        int wrapCap = layout.resolveWidth(screenWidth) - 2 * layout.padding() - iconReservedWidth;
        int wrapWidthUnscaled = Math.max(1, Math.round(wrapCap / textScale));

        // Measured from the *full* line text, for a stable box size across the whole typewriter
        // reveal (see class doc) — the actually-drawn component (built again below from just the
        // revealed prefix) reuses the identical wrap width, so wrapping stays consistent as text fills in.
        Component fullMessage = buildInlineMessage(hasSpeakerName ? displayName : null, nameColor, line.text(), mode, style.textColor());
        List<FormattedCharSequence> fullyWrapped = font.split(fullMessage, wrapWidthUnscaled);
        int measuredTextWidth = fullyWrapped.stream().mapToInt(font::width).max().orElse(0);
        int textBlockWidth = Math.round(measuredTextWidth * textScale);
        int textBlockHeight = Math.round(Math.max(1, fullyWrapped.size()) * font.lineHeight * textScale);

        int contentWidth = textBlockWidth + iconReservedWidth;
        int boxWidth = Math.min(layout.resolveWidth(screenWidth), contentWidth + 2 * layout.padding());
        int boxHeight = Math.max(textBlockHeight, avatarSize) + 2 * layout.padding();

        int boxX = layout.resolveX(screenWidth, boxWidth);
        int boxY = layout.resolveY(screenHeight, boxHeight);

        DialogueWindowAnimation animation = state.animation();
        float alphaMultiplier = animation == DialogueWindowAnimation.FADE ? visibility : 1f;
        int slideOffsetY = animation == DialogueWindowAnimation.SLIDE
                ? Math.round((1f - visibility) * (boxHeight + SLIDE_DISTANCE_EXTRA))
                : 0;

        // try/finally around the pushed pose: an exception from any render* step below must never
        // leave the PoseStack permanently unbalanced for every frame after this one.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0, slideOffsetY, 0);

            renderBackground(graphics, style, boxX, boxY, boxWidth, boxHeight, alphaMultiplier);

            int textX = boxX + layout.padding();
            if (portrait != null) {
                int avatarY = boxY + (boxHeight - avatarSize) / 2;
                renderAvatar(graphics, portrait, textX, avatarY, avatarSize, alphaMultiplier);
                textX += avatarSize + ICON_GAP;
            }
            // Centered against the *box*, not top-padded — with no avatar this reduces to exactly
            // boxY + padding (box height is text height + 2*padding, so centering == top-aligning),
            // but with an avatar taller than the text block this keeps the text vertically centered
            // on the icon instead of hugging the top of it.
            int textY = boxY + (boxHeight - textBlockHeight) / 2;

            Component visibleMessage = buildInlineMessage(
                    hasSpeakerName ? displayName : null, nameColor, state.typewriter().visibleText(), mode, style.textColor());
            renderText(graphics, font, visibleMessage, textX, textY, wrapWidthUnscaled, textScale, alphaMultiplier);
        } finally {
            pose.popPose();
        }
    }

    /** {@code [Name]: message} as one composed {@code Component} — name/brackets in {@code nameColor} (not bold, on request), message in {@code textColor} with the mode's italic/bold flag, so word-wrap treats the whole thing as a single run instead of two independently-wrapped pieces. */
    private static Component buildInlineMessage(String displayName, int nameColor, String text, DialogueMode mode, int textColor) {
        MutableComponent result = Component.literal("");
        if (displayName != null) {
            result.append(Component.literal("[" + displayName + "]: ").withStyle(Style.EMPTY.withColor(nameColor)));
        }
        Style messageStyle = Style.EMPTY
                .withColor(textColor)
                .withItalic(mode == DialogueMode.THOUGHT || mode == DialogueMode.MUTTER)
                .withBold(mode == DialogueMode.SHOUT);
        result.append(Component.literal(text).withStyle(messageStyle));
        return result;
    }

    private static void renderBackground(GuiGraphics graphics, DialogueStyle style, int x, int y, int width, int height, float alphaMultiplier) {
        // True rounded corners aren't practical with GuiGraphics#fill alone (an axis-aligned rect
        // fill, no vertex/stencil shaping) — re-filling a corner pixel with a translucent color only
        // composites *more* opaque there, it can't punch transparency into what's already drawn.
        graphics.fill(x, y, x + width, y + height, scaleAlpha(style.backgroundColor(), alphaMultiplier));
        // No border by default (DialogueStyle's constants use a fully transparent borderColor,
        // matching the flat, borderless look this was tuned against) — still drawn, so a custom
        // style with a real border color/alpha keeps working.
        graphics.fill(x, y, x + width, y + 1, scaleAlpha(style.borderColor(), alphaMultiplier));
    }

    private static void renderAvatar(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, float alphaMultiplier) {
        // try/finally, not just try: an exception mid-blit must never leave the shader color
        // permanently dimmed for every subsequent draw call this frame.
        RenderSystem.setShaderColor(1f, 1f, 1f, clamp01(alphaMultiplier));
        try {
            graphics.blit(texture, x, y, 0, 0, size, size, size, size);
        } catch (RuntimeException e) {
            EngineLog.channel("Dialogue").error("Failed to render avatar {}", texture, e);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static void renderText(GuiGraphics graphics, Font font, Component message, int x, int y, int wrapWidth, float scale, float alphaMultiplier) {
        int alpha = Math.round(255 * clamp01(alphaMultiplier)) << 24;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1f);
            // The message's own per-segment Style colors (name vs. text) carry the actual RGB;
            // this color argument only ever contributes the alpha byte here since drawWordWrap
            // takes styled FormattedText components as-is — 0x00FFFFFF is a harmless no-op on RGB.
            graphics.drawWordWrap(font, message, 0, 0, wrapWidth, alpha | 0x00FFFFFF);
        } catch (RuntimeException e) {
            EngineLog.channel("Dialogue").error("Failed to render dialogue line text", e);
        }
        pose.popPose();
    }

    private static ResourceLocation resolvePortrait(DialogueLine line, DialogueSpeaker speaker) {
        if (line.portrait() != null) {
            return ResourceLocation.tryParse(line.portrait());
        }
        return speaker != null ? speaker.portrait() : null;
    }

    /** {@code line}'s own override, else the registered {@link DialogueSpeaker}'s, else the style default — same precedence as {@link #resolvePortrait}. */
    private static int resolveNameColor(DialogueLine line, DialogueSpeaker speaker, DialogueStyle style) {
        if (line.nameColor() != null) {
            return line.nameColor();
        }
        return speaker != null ? speaker.nameColor() : style.speakerColor();
    }

    private static int scaleAlpha(int argbColor, float multiplier) {
        int alpha = (argbColor >>> 24) & 0xFF;
        int scaledAlpha = Math.round(alpha * clamp01(multiplier));
        return (scaledAlpha << 24) | (argbColor & 0x00FFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    // --- DialogueScreen's own drawing helpers (choices) — unchanged from before this round ---

    static void renderChoices(GuiGraphics graphics, Font font, DialogueStyle style, int x, int y, int width,
                               List<String> choiceTexts, int selectedIndex) {
        int rowHeight = font.lineHeight + 6;
        int totalHeight = rowHeight * choiceTexts.size() + 8;
        graphics.fill(x, y, x + width, y + totalHeight, style.backgroundColor());
        graphics.fill(x, y, x + width, y + 1, style.borderColor());

        int rowY = y + 4;
        for (int i = 0; i < choiceTexts.size(); i++) {
            int color = i == selectedIndex ? style.selectedChoiceColor() : style.choiceColor();
            graphics.drawString(font, choiceTexts.get(i), x + 10, rowY, color);
            rowY += rowHeight;
        }
    }

    static int choicesHeight(Font font, int choiceCount) {
        return (font.lineHeight + 6) * choiceCount + 8;
    }
}
