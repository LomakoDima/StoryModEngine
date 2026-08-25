package com.dimalab.storymodengine.common.cinematic.state;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * The pose an {@code ActorTrack} evaluates to at one instant — plain data, no {@code Entity}
 * reference. Each channel is independently optional: an {@code ActorTrack} built without, say, a
 * position keyframe leaves {@link #position()} empty rather than snapping the bound entity to an
 * arbitrary default — {@code cinematic.client.ActorApplier} only touches the channels a mod author
 * actually animated, leaving everything else exactly as the entity's own (server-authoritative)
 * state already has it.
 */
public record ActorState(Optional<Vector3f> position, Optional<Quaternionf> rotation, Optional<Boolean> visible) {
}
