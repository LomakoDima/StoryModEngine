package com.dimalab.storymodengine.common.entity.behavior;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

/**
 * "This entity watches whatever living thing is nearest" — the generic, any-{@code Mob} equivalent of
 * {@code NpcLookAtGoal}'s default nearest-visible-{@code LivingEntity} behavior, for a mob that has no
 * goal of its own this engine can add to it. Deliberately simpler than {@code NpcLookAtGoal}: no
 * scripted fixed-point/fixed-player override, no combat-target priority, no body-turn-past-head-limit
 * follow-through — just the on/off default watch, matching the "follow + look-at only, first pass"
 * scope this was built for.
 */
public final class LookAtBehavior implements EntityCapability {

    public boolean enabled = false;
    public float range = 16.0F;
}
