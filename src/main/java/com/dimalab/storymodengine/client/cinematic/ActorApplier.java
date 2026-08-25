package com.dimalab.storymodengine.client.cinematic;

import com.dimalab.storymodengine.common.cinematic.state.ActorState;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.api.math.Angles;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Applies an evaluated {@link ActorState} to the real, already-ticking {@link Entity} an {@code
 * ActorBinding} resolved — client-side visual override only (see {@code ARCHITECTURE.md}'s known
 * limitations: this never touches the server-authoritative copy, so it's cosmetic and temporary by
 * nature, not a substitute for real entity movement). Unlike {@link CameraRig}'s marker, this
 * entity is a normal, regularly-ticked one, so its own built-in previous/current pose
 * interpolation already smooths what {@link Entity#setYRot}/{@link Entity#setXRot} set here —
 * no extra {@code TickValue} needed on this side.
 */
final class ActorApplier {

    private ActorApplier() {
    }

    static void apply(Entity entity, ActorState state) {
        if (entity == null) {
            // Safe by design: an unresolved binding (entity despawned, never spawned, wrong id)
            // simply skips this tick's apply rather than crashing — logged at debug since this
            // runs up to 20x/sec and a binding can legitimately stay unresolved for a whole
            // cutscene (e.g. an actor that only appears partway through).
            EngineLog.channel("Cinematic").debug("ActorApplier: binding did not resolve to an entity, skipping this tick");
            return;
        }
        state.position().ifPresent(pos -> setPosition(entity, pos));
        state.rotation().ifPresent(rot -> setRotation(entity, rot));
        state.visible().ifPresent(visible -> entity.setInvisible(!visible));
    }

    private static void setPosition(Entity entity, Vector3f pos) {
        entity.setPosRaw(pos.x, pos.y, pos.z);
    }

    private static void setRotation(Entity entity, Quaternionf rotation) {
        float[] yawPitch = Angles.toYawPitch(rotation);
        entity.setYRot(yawPitch[0]);
        entity.setXRot(yawPitch[1]);
        entity.setYHeadRot(yawPitch[0]);
    }
}
