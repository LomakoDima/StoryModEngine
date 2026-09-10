package com.dimalab.storymodengine.common.entity.ai;

import com.dimalab.storymodengine.common.entity.npc.ItemClaims;
import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * {@code npc_collect_items} — actively seeks the nearest matching dropped item within radius and walks
 * to it; actual pickup stays vanilla's own passive AABB-overlap mechanism (already wired via {@code
 * Mob.setCanPickUpLoot}, see {@code npc_set_pickup}'s own doc) rather than reimplementing inventory
 * insertion — this goal's whole job is closing the distance and reserving the target via {@link
 * ItemClaims} so two NPCs never converge on the same stack. Structurally a direct copy of {@link
 * NpcFollowGoal} (same repath-cooldown shape, same single-{@code Flag.MOVE} claim), with one addition:
 * releasing any held claim whenever the current target stops being valid or the goal itself stops.
 */
public final class NpcCollectItemsGoal extends Goal {

    private static final int REPATH_INTERVAL_TICKS = 20;

    private final NpcEntity npc;
    private int repathCooldown;
    /** The item entity this NPC currently owns a claim on, or {@code null} between targets. Purely this goal's own bookkeeping — nothing external needs to read or write it. */
    private UUID claimedItem;

    public NpcCollectItemsGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return npc.collectFilter() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return npc.collectFilter() != null;
    }

    @Override
    public void stop() {
        releaseClaim();
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (repathCooldown-- > 0) {
            return;
        }
        repathCooldown = REPATH_INTERVAL_TICKS;

        ServerLevel level = (ServerLevel) npc.level();
        ItemEntity target = currentClaimedItem(level);
        if (target == null) {
            target = claimNearest(level);
        }
        if (target != null) {
            npc.getNavigation().moveTo(target, 1.0D);
        }
    }

    /** The item behind {@link #claimedItem}, or {@code null} (releasing the stale claim first) once it's gone, dead, or emptied — vanilla's own passive pickup is what empties it. */
    private ItemEntity currentClaimedItem(ServerLevel level) {
        if (claimedItem == null) {
            return null;
        }
        Entity entity = level.getEntity(claimedItem);
        if (entity instanceof ItemEntity item && item.isAlive() && !item.getItem().isEmpty()) {
            return item;
        }
        releaseClaim();
        return null;
    }

    /** Scans for the nearest matching item within {@code npc.collectRadius()}, claiming candidates nearest-first until one succeeds (skipping any already claimed by another NPC — the actual reservation moment). */
    private ItemEntity claimNearest(ServerLevel level) {
        String filterId = npc.collectFilter();
        Item wanted = null;
        if (filterId != null && !filterId.isEmpty()) {
            wanted = ForgeRegistries.ITEMS.getValue(parseId(filterId));
            if (wanted == null) {
                // Unknown item id — nothing will ever match, no point scanning every cycle.
                return null;
            }
        }
        Item finalWanted = wanted;
        AABB box = npc.getBoundingBox().inflate(npc.collectRadius());
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty() && (finalWanted == null || e.getItem().is(finalWanted)));
        candidates.sort(Comparator.comparingDouble(npc::distanceToSqr));

        for (ItemEntity candidate : candidates) {
            if (ItemClaims.claim(level, candidate.getUUID(), npc.getUUID())) {
                claimedItem = candidate.getUUID();
                return candidate;
            }
        }
        return null;
    }

    private void releaseClaim() {
        if (claimedItem != null) {
            ItemClaims.release((ServerLevel) npc.level(), claimedItem, npc.getUUID());
            claimedItem = null;
        }
    }

    /** Same "bare id defaults to minecraft:" convention {@code NpcScriptCommands.id(...)} already uses. */
    private static ResourceLocation parseId(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }
}
