package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * {@code npc_break_block} — walks the NPC to a script-chosen block, then breaks it through the real
 * vanilla destroy-progress pipeline: a cached {@link NpcEntity#fakePlayer()} driven through
 * {@code ServerPlayerGameMode.handleBlockBreakAction}, the same mechanism HollowEngine's own
 * {@code startDestroyBlock} uses (verified against its decompiled/Kotlin source this session). Unlike
 * {@code npc_destroy_block} (instant, no tool check, kept unchanged for scripts that want that), this
 * respects real per-tick break speed and, when {@link NpcEntity#destroyBlockRequireTool()} is set,
 * refuses to even start on the wrong tool — a pre-flight gate only, never threaded into the break
 * itself, exactly like HollowEngine's own {@code requireCorrectTool}.
 *
 * <p>Structurally close to {@link NpcCollectItemsGoal}'s shape ({@code canUse()}/{@code
 * canContinueToUse()} both gated on a nullable target field on the NPC), but claims {@code Flag.LOOK}
 * too, not just {@code Flag.MOVE} — without it, {@link NpcLookAtGoal} keeps running concurrently and
 * fights this goal over {@code LookControl.setLookAt} every tick (one call aiming at the block, the
 * other at the nearest player), which is what "the NPC dances/twitches instead of holding still and
 * mining" turned out to be: two goals sharing the head/body with neither one winning consistently.
 */
public final class NpcDestroyBlockGoal extends Goal {

    /** Vanilla's own default block-interaction reach for a non-creative player. */
    private static final double REACH = 4.5D;
    /**
     * Real vanilla mining sound is entirely client-local prediction (verified: no {@code playSound}/
     * {@code SoundEvent} call anywhere in the decompiled {@code ServerPlayerGameMode}) — a real player
     * hears their own hit sound, but nobody else does, ever, for anyone's mining. That's fine for a
     * player at a keyboard; it means a server-driven NPC with no real client attached produces total
     * silence for its whole multi-second break unless something explicitly broadcasts a sound, which
     * is what this interval is for — a periodic real {@code level.playSound} using the block's own
     * {@code SoundType}, not a made-up fixed sound.
     */
    private static final int HIT_SOUND_INTERVAL_TICKS = 4;

    private final NpcEntity npc;
    private boolean breaking;
    private int breakTicks;

    public NpcDestroyBlockGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.destroyBlockTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return npc.destroyBlockTarget() != null;
    }

    @Override
    public void stop() {
        abort();
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos pos = npc.destroyBlockTarget();
        if (pos == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        if (npc.distanceToSqr(center) > REACH * REACH) {
            breaking = false;
            npc.setBreakingBlockAnim(false);
            npc.getNavigation().moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(center.x, center.y, center.z);

        ServerLevel level = (ServerLevel) npc.level();
        ServerPlayer fake = npc.fakePlayer();
        Direction face = Direction.getNearest(npc.getX() - center.x, npc.getEyeY() - center.y, npc.getZ() - center.z);

        if (!breaking) {
            BlockState state = level.getBlockState(pos);
            if (state.getDestroyProgress(fake, level, pos) <= 0.0F) {
                EngineLog.channel("Npc").warn("npc_break_block: block at {} can't be broken by anything", pos);
                finish();
                return;
            }
            if (npc.destroyBlockRequireTool() && !fake.hasCorrectToolForDrops(state)) {
                EngineLog.channel("Npc").warn("npc_break_block: wrong tool for block at {} and requireCorrectTool is true", pos);
                finish();
                return;
            }
            fake.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, face, level.getMaxBuildHeight(), 0);
            breaking = true;
            breakTicks = 0;
            npc.setBreakingBlockAnim(true);
            return;
        }

        breakTicks++;
        fake.swing(InteractionHand.MAIN_HAND);
        if (breakTicks % HIT_SOUND_INTERVAL_TICKS == 0) {
            SoundType soundType = level.getBlockState(pos).getSoundType();
            level.playSound(null, pos, soundType.getHitSound(), SoundSource.BLOCKS, soundType.getVolume() * 0.5F, soundType.getPitch() * 0.8F);
        }
        // The actual bug behind "one microscopic crack and nothing else": ServerPlayerGameMode's own
        // completion check (in its STOP_DESTROY_BLOCK branch) compares against ITS OWN internal
        // gameTicks counter, which normally advances because the server ticks every real ServerPlayer
        // automatically once per tick (ServerPlayer.doTick() -> gameMode.tick()) — a FakePlayer is
        // never added to that list, so nothing was ever calling this, gameTicks stayed frozen at
        // whatever it was when START_DESTROY_BLOCK fired, and STOP_DESTROY_BLOCK's own progress
        // calculation (destroyProgress * (gameTicks - destroyProgressStart + 1)) was permanently ~1
        // tick's worth — never enough to actually destroy anything, matching the single crack update
        // handleBlockBreakAction's START branch itself sends once and then never again. Calling this
        // manually, exactly once per goal tick, is what HollowEngine's own startDestroyBlock does too.
        fake.gameMode.tick();
        BlockState current = level.getBlockState(pos);
        if (current.isAir()) {
            // Already gone by some other means (a second command, an explosion, ...) — nothing left to finish.
            breaking = false;
            finish();
            return;
        }
        float progress = current.getDestroyProgress(fake, level, pos) * breakTicks;
        if (progress >= 1.0F) {
            fake.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, face, level.getMaxBuildHeight(), 0);
            breaking = false;
            finish();
        }
    }

    /** Only sends a real abort packet when a break was actually in flight — an externally-forced stop (another script command taking the movement channel) mid-break, not the normal finish path, which already cleared {@link #breaking} itself. */
    private void abort() {
        if (!breaking) {
            return;
        }
        BlockPos pos = npc.destroyBlockTarget();
        if (pos != null) {
            ServerLevel level = (ServerLevel) npc.level();
            npc.fakePlayer().gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, level.getMaxBuildHeight(), 0);
        }
        breaking = false;
        finish();
    }

    private void finish() {
        npc.setBreakingBlockAnim(false);
        npc.setDestroyBlockTarget(null);
    }
}
