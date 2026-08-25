package com.dimalab.storymodengine.common.math.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.api.math.Numbers;
import com.dimalab.storymodengine.api.math.curve.ArcLengthTable;
import com.dimalab.storymodengine.api.math.curve.CatmullRomSpline;
import com.dimalab.storymodengine.api.math.curve.Curve;
import com.dimalab.storymodengine.api.math.interp.Easing;
import com.dimalab.storymodengine.api.math.transform.Transform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Drives an active {@link CatmullRomSpline}{@code <Transform>} for a player, one server tick at a
 * time — the "object animation" half of the camera-path example in {@link CameraPathCommand}. What
 * this class demonstrates, concretely:
 *
 * <ul>
 *   <li>a single {@code CatmullRomSpline<Transform>} carries position <em>and</em> rotation
 *       together — sampling it once per tick is both the "camera path" and the "transform
 *       interpolation" pieces of the example, not two separate systems;</li>
 *   <li>{@link ArcLengthTable}, built from a {@code Curve<Vector3f>} view of that same spline
 *       (see {@link CameraPathCommand}), converts the eased overall progress into constant-speed
 *       travel instead of speeding through tight turns;</li>
 *   <li>{@link Easing} shapes the overall progress, not the per-segment blend — the path itself
 *       stays constant-speed, the whole traversal eases in and out.</li>
 * </ul>
 *
 * <p>This moves the player by teleporting every tick, which is intentionally simple for an
 * example — it will fight actual WASD input, and doesn't attempt to reconcile with normal
 * movement/anti-cheat handling the way a real cutscene-camera system eventually should.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CameraPathAnimator {

    private static final Map<UUID, Animation> ACTIVE = new HashMap<>();

    private CameraPathAnimator() {
    }

    public static void start(ServerPlayer player, CatmullRomSpline<Transform> path, int durationTicks, Easing easing) {
        Curve<Vector3f> positionCurve = t -> path.sample(t).translation();
        ArcLengthTable arcLength = ArcLengthTable.build(positionCurve, 64);
        ACTIVE.put(player.getUUID(), new Animation(player, path, arcLength, easing, durationTicks));
    }

    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }

        Iterator<Animation> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Animation animation = it.next();
            animation.elapsedTicks++;

            float rawProgress = Numbers.saturate((float) animation.elapsedTicks / animation.durationTicks);
            float easedProgress = animation.easing.apply(rawProgress);
            float t = animation.arcLength.parameterAtFraction(easedProgress);
            Transform pose = animation.path.sample(t);

            float[] yawPitch = Angles.toYawPitch(pose.rotation());
            Vector3f pos = pose.translation();
            animation.player.teleportTo(animation.player.serverLevel(), pos.x, pos.y, pos.z, yawPitch[0], yawPitch[1]);

            if (rawProgress >= 1f) {
                animation.player.sendSystemMessage(Component.literal("[StoryModEngine] Camera path finished."));
                it.remove();
            }
        }
    }

    private static final class Animation {
        final ServerPlayer player;
        final CatmullRomSpline<Transform> path;
        final ArcLengthTable arcLength;
        final Easing easing;
        final int durationTicks;
        int elapsedTicks;

        Animation(ServerPlayer player, CatmullRomSpline<Transform> path, ArcLengthTable arcLength, Easing easing, int durationTicks) {
            this.player = player;
            this.path = path;
            this.arcLength = arcLength;
            this.easing = easing;
            this.durationTicks = durationTicks;
        }
    }
}
