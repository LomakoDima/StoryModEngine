package com.dimalab.storymodengine.client.math.example;

import com.dimalab.storymodengine.api.math.Numbers;
import com.dimalab.storymodengine.api.math.interp.Easing;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The UI half of the {@code math} foundation's usage examples: the same {@link Interpolators#FLOAT}
 * and {@link Easing} used to shape camera-path progress in {@link CameraPathCommand} here slide a
 * button in from off-screen with an overshoot-and-settle ({@link Easing#EASE_OUT_BACK}), driven by
 * {@code render}'s own {@code partialTick} rather than a fixed frame-rate-dependent step — open it
 * with {@code /storymodengine mathui} ({@link MathUiCommand}).
 */
public final class EasingDemoScreen extends Screen {

    private static final float ANIMATION_SECONDS = 0.6f;

    private long openedAtMillis;
    private int targetX;
    private Button slidingButton;

    public EasingDemoScreen() {
        super(Component.literal("StoryModEngine — Easing demo"));
    }

    @Override
    protected void init() {
        openedAtMillis = Util.getMillis();
        targetX = this.width / 2 - 100;
        int y = this.height / 2 - 10;
        slidingButton = addRenderableWidget(Button.builder(
                        Component.literal("Eased in with EASE_OUT_BACK"),
                        button -> onClose())
                .bounds(targetX, y, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float elapsedSeconds = (Util.getMillis() - openedAtMillis) / 1000f;
        float progress = Numbers.saturate(elapsedSeconds / ANIMATION_SECONDS);
        float eased = Easing.EASE_OUT_BACK.apply(progress);

        float startX = this.width;
        float x = Interpolators.FLOAT.interpolate(startX, (float) targetX, eased);
        slidingButton.setX(Math.round(x));

        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
