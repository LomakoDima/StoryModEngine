package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * {@code npc_place_block} — walks the NPC to a script-chosen position, then places whatever block item
 * is actually in its main hand at that moment (checked here, not passed in as an id — see the
 * command's own doc for why). Placing itself is a single real {@code Item.useOn(UseOnContext)} call
 * through {@link NpcEntity#fakePlayer()} (no vanilla multi-tick mechanic behind placement the way
 * breaking has one), so unlike {@link NpcDestroyBlockGoal} there is no "channel" phase — arriving in
 * reach, swinging, and placing all happen the same tick, matching how a real player's right-click
 * plays the identical arm swing for placing as for attacking.
 *
 * <p>Claims {@code Flag.LOOK} alongside {@code Flag.MOVE} for the same reason {@link
 * NpcDestroyBlockGoal} does: without it, {@link NpcLookAtGoal} fights this goal over {@code
 * LookControl.setLookAt} every tick.
 */
public final class NpcPlaceBlockGoal extends Goal {

    /** Vanilla's own default block-interaction reach for a non-creative player. */
    private static final double REACH = 4.5D;

    private final NpcEntity npc;

    public NpcPlaceBlockGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.placeBlockTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return npc.placeBlockTarget() != null;
    }

    @Override
    public void stop() {
        npc.setPlaceBlockTarget(null);
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos pos = npc.placeBlockTarget();
        if (pos == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        if (npc.distanceToSqr(center) > REACH * REACH) {
            npc.getNavigation().moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(center.x, center.y, center.z);

        ItemStack held = npc.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem)) {
            EngineLog.channel("Npc").warn("npc_place_block: npc isn't holding a placeable block (give it one with npc_give_main_hand first)");
            npc.setPlaceBlockTarget(null);
            return;
        }

        Direction facing = npc.placeBlockFacing();
        ServerPlayer fake = npc.fakePlayer();
        fake.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
        BlockHitResult hit = new BlockHitResult(center, facing, pos, false);
        InteractionResult result = held.copy().useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND, hit));

        npc.swing(InteractionHand.MAIN_HAND);
        if (result.consumesAction()) {
            ItemStack remaining = held.copy();
            remaining.shrink(1);
            npc.setItemSlot(EquipmentSlot.MAINHAND, remaining);
        } else {
            EngineLog.channel("Npc").warn("npc_place_block: placement at {} was rejected (occupied or unsupported)", pos);
        }
        npc.setPlaceBlockTarget(null);
    }
}
