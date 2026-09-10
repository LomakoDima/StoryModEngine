package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/**
 * {@code WaterAvoidingRandomStrollGoal}, minus the one case vanilla's own flag system can't see:
 * {@code npc_move_to} drives {@link NpcEntity}'s navigation directly rather than through a registered
 * {@code Goal}, so it holds no {@code Goal.Flag.MOVE} lock — nothing stops this goal's own dice roll
 * from firing mid-script-move and silently rerouting the NPC. {@link NpcFollowGoal} needs no equivalent
 * guard: it IS a real, higher-priority {@code Goal} with that flag, so vanilla's own mutual exclusion
 * already keeps this goal off the navigation while a follow is active.
 */
public class NpcRandomStrollGoal extends WaterAvoidingRandomStrollGoal {

    private final NpcEntity npc;

    public NpcRandomStrollGoal(NpcEntity npc, double speedModifier) {
        super(npc, speedModifier);
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        return !npc.isScriptNavigationActive() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !npc.isScriptNavigationActive() && super.canContinueToUse();
    }
}
