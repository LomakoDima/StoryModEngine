package com.dimalab.storymodengine.common.raycast;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A fluent, mutable raycast builder — what {@link Raycast#from} returns. Every chain method mutates
 * and returns {@code this} (the same pattern {@code QuestDefinition.Builder} already uses), and
 * {@link #cast()} is the only thing that actually touches the world. Nothing here is a new physics
 * engine: block resolution is one {@code Level#clip(ClipContext)} call (the exact primitive {@code
 * cinematic.client.CameraCollision} and vanilla's own {@code Camera#getMaxZoom} already use), and
 * entity resolution is vanilla's own {@code ProjectileUtil.getEntityHitResult} (the exact primitive
 * every vanilla projectile/fishing-rod/spectral-arrow raycast already uses) — this class only
 * composes those two into one query with a readable builder in front, per {@code ARCHITECTURE.md}'s
 * "engine wraps vanilla, never replaces it" principle.
 *
 * <p><b>Block filter semantics</b> ({@link #blockFilter}): applies to the single nearest solid hit,
 * not a "skip past non-matching blocks and keep looking" scan — vanilla's collision API has no
 * built-in primitive for that, and hand-rolling one would mean re-implementing voxel traversal
 * (explicitly out of scope). This is also the physically honest behavior: a ray genuinely can't see
 * through an opaque block it doesn't care about to find one it does further along, the same way a
 * player can't see through a stone wall to spot ore behind it.
 *
 * <p><b>{@code .all()}</b> is only offered in {@link #entities()} mode for the same reason — vanilla
 * exposes no "every block along this ray" primitive, but {@code Level#getEntities} +
 * {@code AABB#clip} naturally support enumerating every matching entity.
 */
public final class RaycastQuery {

    private static final double DEFAULT_DEBUG_SPACING = 0.5;

    private final Level level;
    private final Entity fromEntity;
    private final Vec3 start;
    private Vec3 direction;
    private Double distance;
    private RaycastMode mode;

    private ClipContext.Block blockShape = ClipContext.Block.COLLIDER;
    private ClipContext.Fluid fluidMode = ClipContext.Fluid.NONE;

    private final Set<Entity> excluded = new HashSet<>();
    private Predicate<Entity> entityFilter;
    private Predicate<BlockState> blockFilter;

    private ParticleOptions debugParticle;
    private double debugSpacing = DEFAULT_DEBUG_SPACING;

    RaycastQuery(Level level, Vec3 start, Entity fromEntity) {
        this.level = level;
        this.start = start;
        this.fromEntity = fromEntity;
    }

    // ---- source ----

    /** Explicit direction (normalized internally) — still needs {@link #distance(double)} to know how far. */
    public RaycastQuery direction(Vec3 direction) {
        this.direction = direction.normalize();
        return this;
    }

    /** Sets both direction and distance from an explicit end point — works from any {@link Raycast#from} source, including an entity's eye position. */
    public RaycastQuery to(Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        this.distance = length;
        this.direction = length < 1.0e-6 ? new Vec3(0, 0, 1) : delta.scale(1.0 / length);
        return this;
    }

    public RaycastQuery distance(double distance) {
        this.distance = distance;
        return this;
    }

    // ---- mode ----

    public RaycastQuery blocks() {
        this.mode = RaycastMode.BLOCKS;
        return this;
    }

    public RaycastQuery entities() {
        this.mode = RaycastMode.ENTITIES;
        return this;
    }

    public RaycastQuery any() {
        this.mode = RaycastMode.ANY;
        return this;
    }

    // ---- block options (ClipContext, hidden behind names that don't require knowing it exists) ----

    /** Collision shape — what a player/entity would actually bump into. The default. */
    public RaycastQuery collision() {
        this.blockShape = ClipContext.Block.COLLIDER;
        return this;
    }

    /** Outline shape — the selection/highlight box (e.g. a fence's thin post, not its full collision box). */
    public RaycastQuery outline() {
        this.blockShape = ClipContext.Block.OUTLINE;
        return this;
    }

    /** Visual shape — what's actually rendered (e.g. tripwire, which has no collision but *is* visually clippable). */
    public RaycastQuery visual() {
        this.blockShape = ClipContext.Block.VISUAL;
        return this;
    }

    public RaycastQuery ignoreFluids() {
        this.fluidMode = ClipContext.Fluid.NONE;
        return this;
    }

    public RaycastQuery sourceFluids() {
        this.fluidMode = ClipContext.Fluid.SOURCE_ONLY;
        return this;
    }

    public RaycastQuery anyFluids() {
        this.fluidMode = ClipContext.Fluid.ANY;
        return this;
    }

    /**
     * Only accepts the nearest solid hit if it satisfies {@code filter} — otherwise the cast reports
     * a miss. See this class's own doc for why this isn't a "skip past and keep scanning" filter.
     */
    public RaycastQuery blockFilter(Predicate<BlockState> filter) {
        this.blockFilter = this.blockFilter == null ? filter : this.blockFilter.and(filter);
        return this;
    }

    // ---- entity options ----

    public RaycastQuery exclude(Entity... entities) {
        for (Entity entity : entities) {
            excluded.add(entity);
        }
        return this;
    }

    public RaycastQuery filter(Predicate<Entity> filter) {
        this.entityFilter = this.entityFilter == null ? filter : this.entityFilter.and(filter);
        return this;
    }

    /** Sugar for {@code filter(type::isInstance)}. */
    public RaycastQuery filter(Class<? extends Entity> type) {
        return filter(type::isInstance);
    }

    /** Sugar for {@code filter(LivingEntity.class)}. */
    public RaycastQuery livingEntities() {
        return filter(net.minecraft.world.entity.LivingEntity.class);
    }

    // ---- debug visualization (vanilla particles only — see RaycastDebug) ----

    public RaycastQuery debug() {
        return debug(ParticleTypes.END_ROD);
    }

    public RaycastQuery debug(ParticleOptions particle) {
        return debug(particle, DEFAULT_DEBUG_SPACING);
    }

    public RaycastQuery debug(ParticleOptions particle, double spacing) {
        this.debugParticle = particle;
        this.debugSpacing = spacing;
        return this;
    }

    // ---- execution ----

    public RaycastResult cast() {
        requireReady();
        Vec3 end = start.add(direction.scale(distance));
        RaycastResult result = switch (mode) {
            case BLOCKS -> castBlocks(end);
            case ENTITIES -> castEntities(end);
            case ANY -> castAny(end);
        };
        if (debugParticle != null) {
            RaycastDebug.visualize(level, start, result, debugParticle, debugSpacing);
        }
        return result;
    }

    /** Every matching entity along the ray, nearest first — only valid in {@link #entities()} mode. */
    public List<RaycastResult> all() {
        if (mode != RaycastMode.ENTITIES) {
            throw new UnsupportedOperationException(
                    "Raycast.all() is only supported for .entities() — vanilla exposes no equivalent "
                            + "multi-hit primitive for .blocks()/.any() without re-implementing voxel traversal");
        }
        requireReady();
        Vec3 end = start.add(direction.scale(distance));
        BlockHitResult blockClamp = clipBlocks(end);
        Vec3 searchEnd = blockClamp.getType() != HitResult.Type.MISS ? blockClamp.getLocation() : end;

        List<RaycastResult> hits = new ArrayList<>();
        for (Entity candidate : level.getEntities(fromEntity, searchBox(searchEnd), entityPredicate())) {
            AABB hitBox = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            hitBox.clip(start, searchEnd).ifPresent(hitPos ->
                    hits.add(RaycastResult.entity(new EntityHitResult(candidate, hitPos), start)));
        }
        hits.sort(Comparator.comparingDouble(RaycastResult::distance));
        return hits;
    }

    private void requireReady() {
        if (mode == null) {
            throw new IllegalStateException("Raycast: call .blocks()/.entities()/.any() before .cast()");
        }
        if (direction == null || distance == null) {
            throw new IllegalStateException("Raycast: call .distance(...) (with an entity/direction source) or .to(...) before .cast()");
        }
    }

    private RaycastResult castBlocks(Vec3 end) {
        BlockHitResult hit = clipBlocks(end);
        if (hit.getType() != HitResult.Type.MISS && passesBlockFilter(hit)) {
            return RaycastResult.block(hit, level, start);
        }
        return RaycastResult.miss(end, start, hit);
    }

    private RaycastResult castEntities(Vec3 end) {
        BlockHitResult blockClamp = clipBlocks(end);
        Vec3 searchEnd = blockClamp.getType() != HitResult.Type.MISS ? blockClamp.getLocation() : end;
        EntityHitResult entityHit = findNearestEntity(searchEnd);
        if (entityHit != null) {
            return RaycastResult.entity(entityHit, start);
        }
        return RaycastResult.miss(end, start, blockClamp);
    }

    private RaycastResult castAny(Vec3 end) {
        BlockHitResult blockHit = clipBlocks(end);
        Vec3 searchEnd = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : end;
        EntityHitResult entityHit = findNearestEntity(searchEnd);
        if (entityHit != null) {
            return RaycastResult.entity(entityHit, start);
        }
        if (blockHit.getType() != HitResult.Type.MISS && passesBlockFilter(blockHit)) {
            return RaycastResult.block(blockHit, level, start);
        }
        return RaycastResult.miss(end, start, blockHit);
    }

    private boolean passesBlockFilter(BlockHitResult hit) {
        return blockFilter == null || blockFilter.test(level.getBlockState(hit.getBlockPos()));
    }

    private BlockHitResult clipBlocks(Vec3 end) {
        return level.clip(new ClipContext(start, end, blockShape, fluidMode, fromEntity));
    }

    private EntityHitResult findNearestEntity(Vec3 searchEnd) {
        return ProjectileUtil.getEntityHitResult(level, fromEntity, start, searchEnd, searchBox(searchEnd), entityPredicate(), 0.0F);
    }

    private AABB searchBox(Vec3 searchEnd) {
        return fromEntity != null
                ? fromEntity.getBoundingBox().expandTowards(searchEnd.subtract(start)).inflate(1.0)
                : new AABB(start, searchEnd).inflate(1.0);
    }

    private Predicate<Entity> entityPredicate() {
        Predicate<Entity> base = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);
        Predicate<Entity> withExclusions = excluded.isEmpty() ? base : base.and(e -> !excluded.contains(e));
        return entityFilter == null ? withExclusions : withExclusions.and(entityFilter);
    }
}
