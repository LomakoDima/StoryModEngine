package com.dimalab.storymodengine.common.model.rig;

import java.util.List;

/**
 * One joint of a rig applied to an otherwise flat model.
 *
 * @param name    what procedural animation addresses this joint by
 * @param parent  the bone this one hangs off, or {@code null} for the rig root
 * @param anchor  the name of a source node whose own origin <i>is</i> this joint's pivot. Taking the
 *                pivot from real geometry rather than hardcoding coordinates is what lets the same
 *                rig description survive the model being re-exported at a different scale or offset.
 * @param members source node names that move with this joint. A name listed here claims the next
 *                not-yet-claimed node with that name, in bone declaration order — which is how a
 *                model containing two nodes with the same name (a common export slip) can still be
 *                split correctly between two bones.
 */
public record RigBone(String name, String parent, String anchor, List<String> members) {

    public static RigBone of(String name, String parent, String anchor, String... members) {
        return new RigBone(name, parent, anchor, List.of(members));
    }
}
