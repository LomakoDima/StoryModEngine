package com.dimalab.storymodengine.common.flow.wait;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.flow.FlowContext;
import com.dimalab.storymodengine.common.flow.Result;
import com.dimalab.storymodengine.common.flow.Task;

/**
 * {@code wait_persistent <name> <ticks>} — the restart-safe sibling to plain {@code wait <ticks>}
 * (confirmed not restart-safe: {@code NodeSnapshot} only ever captures a node's lifecycle state, never
 * arbitrary per-node data like a countdown, so {@code restore()} re-{@code start()}s a {@code RUNNING}
 * wait node from its full duration). Mirrors HollowEngine's real {@code waitPersistent}: store an
 * absolute deadline once, compare against it every tick — a restart rebuilds a fresh {@link Task} (see
 * {@code TaskAction.onStart}) whose very first tick finds the deadline already persisted in {@link
 * PersistentWaitData} and resumes correctly, no restart-detection code needed anywhere.
 */
public final class PersistentWaitTask implements Task {

    private final String name;
    private final int ticks;

    public PersistentWaitTask(String name, int ticks) {
        this.name = name;
        this.ticks = ticks;
    }

    @Override
    public Result tick(FlowContext context) {
        PersistentWaitData data = Capabilities.get(context.player(), PersistentWaitCapabilities.PERSISTENT_WAIT);
        if (data == null) {
            // The player's capabilities were already invalidated for this tick — observed exactly at
            // disconnect (Forge detaches capabilities before the entity is fully removed). Not a real
            // failure: either the flow gets torn down along with the disconnect and this task is never
            // ticked again, or the player reconnects and capabilities are attached fresh next tick.
            return Result.RUNNING;
        }
        long now = context.player().serverLevel().getGameTime();
        Long deadline = data.deadlines.get(name);
        if (deadline == null) {
            deadline = now + ticks;
            data.deadlines.put(name, deadline);
            Capabilities.markDirty(context.player(), PersistentWaitCapabilities.PERSISTENT_WAIT);
        }
        if (now >= deadline) {
            data.deadlines.remove(name);
            Capabilities.markDirty(context.player(), PersistentWaitCapabilities.PERSISTENT_WAIT);
            return Result.SUCCESS;
        }
        return Result.RUNNING;
    }
}
