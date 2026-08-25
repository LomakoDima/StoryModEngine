package com.dimalab.storymodengine.common.cinematic.binding;

import com.dimalab.storymodengine.common.cinematic.CutsceneContext;
import com.dimalab.storymodengine.common.cinematic.actor.ActorBinding;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A world-space point the camera should look at — resolved fresh through {@link CutsceneContext}
 * every time it's needed, never a stored {@link Entity}, so it stays safe if the target despawns:
 * {@link #resolve} simply returns {@code null} (logged at {@code debug}, not {@code warn} — this
 * can be checked up to once per render frame, so anything louder would spam), and {@code
 * cinematic.client.ClientCutscenePlayer} falls back to the shot's own keyframed rotation for that
 * frame when that happens. Coexists with manually-authored rotation keyframes on purpose — a shot
 * only uses this when one is actually set (see {@code CameraTrack}).
 */
public final class LookAt implements Binding<Vector3f> {

    private final Binding<Vector3f> delegate;

    private LookAt(Binding<Vector3f> delegate) {
        this.delegate = delegate;
    }

    /** Looks at a fixed world position for the whole shot. */
    public static LookAt position(Vector3f position) {
        return new LookAt(context -> position);
    }

    /** Looks at the eye position of whichever entity the actor binding {@code bindingName} currently resolves to. */
    public static LookAt entity(String bindingName) {
        ActorBinding actor = ActorBinding.named(bindingName);
        return new LookAt(context -> {
            Entity entity = actor.resolve(context.level(), context);
            if (entity == null) {
                EngineLog.channel("Cinematic").debug("LookAt: binding '{}' did not resolve to an entity", bindingName);
                return null;
            }
            Vec3 eye = entity.getEyePosition();
            return new Vector3f((float) eye.x, (float) eye.y, (float) eye.z);
        });
    }

    @Override
    public Vector3f resolve(CutsceneContext context) {
        return delegate.resolve(context);
    }
}
