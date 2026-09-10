package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.common.capabilities.Capabilities;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Reservation for {@code npc_collect_items}'s {@code NpcCollectItemsGoal} — mirrors HollowEngine's own
 * {@code NpcItemClaims} semantics (studied from their real source, not their code): claiming an
 * already-unclaimed-or-self-owned item succeeds idempotently, claiming someone else's fails, and a
 * release only ever removes an entry the releaser itself still owns, so a stale/superseded release can
 * never undo a newer claimant's ownership.
 */
public final class ItemClaims {

    private ItemClaims() {
    }

    /** True if {@code item} was unclaimed or already claimed by {@code npc} (now claimed by {@code npc} either way); false if owned by a different NPC. */
    public static boolean claim(ServerLevel level, UUID item, UUID npc) {
        NpcItemClaims claims = Capabilities.get(level, ModNpcCapabilities.ITEM_CLAIMS);
        if (claims == null) {
            return true;
        }
        UUID owner = claims.claimedBy.get(item);
        if (owner != null && !owner.equals(npc)) {
            return false;
        }
        claims.claimedBy.put(item, npc);
        Capabilities.markDirty(level, ModNpcCapabilities.ITEM_CLAIMS);
        return true;
    }

    /** No-op unless {@code item} is currently claimed by exactly {@code npc}. */
    public static void release(ServerLevel level, UUID item, UUID npc) {
        NpcItemClaims claims = Capabilities.get(level, ModNpcCapabilities.ITEM_CLAIMS);
        if (claims != null && npc.equals(claims.claimedBy.get(item))) {
            claims.claimedBy.remove(item);
            Capabilities.markDirty(level, ModNpcCapabilities.ITEM_CLAIMS);
        }
    }
}
