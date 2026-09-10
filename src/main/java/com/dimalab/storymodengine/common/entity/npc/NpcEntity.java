package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.entity.NpcAnimationMode;
import com.dimalab.storymodengine.api.entity.NpcBehavior;
import com.dimalab.storymodengine.api.entity.NpcHitboxMode;
import com.dimalab.storymodengine.common.entity.ai.NpcCollectItemsGoal;
import com.dimalab.storymodengine.common.entity.ai.NpcDestroyBlockGoal;
import com.dimalab.storymodengine.common.entity.ai.NpcPlaceBlockGoal;
import com.dimalab.storymodengine.common.entity.ai.NpcFollowGoal;
import com.dimalab.storymodengine.common.entity.ai.NpcMeleeAttackGoal;
import com.dimalab.storymodengine.common.entity.ai.NpcRandomStrollGoal;
import com.dimalab.storymodengine.common.entity.ai.SmoothGroundNavigation;
import com.dimalab.storymodengine.common.entity.ai.SmoothLookControl;
import com.dimalab.storymodengine.common.entity.ai.SmoothMoveControl;
import com.dimalab.storymodengine.common.flow.FlowManager;
import com.dimalab.storymodengine.common.model.physics.ModelBounds;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import com.dimalab.storymodengine.common.entity.ai.NpcLookAtGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A story NPC: a character that wears a glTF model, behaves the way it is told to, and persists.
 *
 * <p>Modelled on how HollowEngine builds its own NPC, with the same central idea — <b>what an NPC is
 * belongs to the entity, not to its class</b>. Its model, its behaviour and how solid it is are all
 * synced values that can change at runtime, so one registered entity type serves a guard, a
 * shopkeeper and a monster. The mob this replaces hardcoded its model name inside the renderer and
 * its hostility inside {@code registerGoals}, which meant a second character required a second entity
 * class and a second renderer.
 *
 * <p>Only the model's <b>name</b> is ever sent; geometry lives in the resource pack on both sides —
 * the same principle {@code DialogueStepPacket} follows.
 */
public class NpcEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> MODEL =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> BEHAVIOR =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HITBOX_MODE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ANIMATION =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> BODY_WIDTH =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SKIN_OWNER =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** The {@code npc_*} script commands' lookup key (see {@code NpcLookup}) — distinct from the player-facing {@link #getCustomName()} component, which is cosmetic and can change or be absent. */
    private static final EntityDataAccessor<String> SCRIPT_NAME =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ANIMATION_MODE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /**
     * A synced mirror of {@code getTarget() != null} — see {@link #setTarget} for why {@code
     * Mob.target} itself can't be read directly for this. Client-visible, unlike the plain field it
     * mirrors.
     */
    private static final EntityDataAccessor<Boolean> HAS_TARGET =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** Drives {@code query.is_breaking_block} (see {@code QueryTable}) — same synced-boolean shape as {@link #HAS_TARGET}, set by {@link NpcDestroyBlockGoal} while it's actually mid-break, not merely walking toward the target. */
    private static final EntityDataAccessor<Boolean> IS_BREAKING_BLOCK =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Pathfinding measures a mob in whole blocks as {@code floor(height + 1)}, so anything at or over
     * 2.0 becomes a three-block-tall pathfinder that will not walk through an ordinary doorway. A
     * humanoid model that measures slightly over two blocks is clamped just under the line rather than
     * being quietly unable to enter buildings.
     */
    private static final float DOORWAY_SAFE_HEIGHT = 1.99F;
    private static final float MAX_HEIGHT = 4.0F;
    public static final float DEFAULT_BODY_WIDTH = 0.6F;
    /** Synced-field sentinel meaning "no manual width override — auto-derive from the model." */
    public static final float AUTO_BODY_WIDTH = -1F;
    /** Floor for an auto-derived width, guarding against a degenerate near-zero footprint. */
    private static final float MIN_BODY_WIDTH = 0.1F;

    /** How far away an NPC still notices and watches a player. Vanilla's idle glance goal uses 8; a character should notice you sooner. */
    private static final float LOOK_RANGE = 16.0F;

    private EntityDimensions dimensions = EntityDimensions.scalable(DEFAULT_BODY_WIDTH, 1.95F);

    public NpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new SmoothMoveControl(this);
        this.lookControl = new SmoothLookControl(this);
        setPersistenceRequired();
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        SmoothGroundNavigation navigation = new SmoothGroundNavigation(this, level);
        // Only lets the pathfinder route THROUGH a door node (see WalkNodeEvaluator's own
        // setCanPassDoors(true), already set in SmoothGroundNavigation#createPathFinder) — actually
        // swinging a door open is a separate vanilla concept (DoorInteractGoal/OpenDoorGoal, added
        // below in applyBehavior) that also needs this flag on, verified directly in
        // GroundPathNavigation.canOpenDoors()/DoorInteractGoal.canUse().
        navigation.setCanOpenDoors(true);
        return navigation;
    }

    /**
     * {@code attackAnim}/{@code swingTime} — what {@code query.is_swinging} reads (see {@code
     * QueryTable}), and what drives the animation controller's {@code attack} state — is only ever
     * advanced by {@link #updateSwingTime()}. Verified directly against the decompiled source: {@code
     * LivingEntity} itself never calls it; the only two callers in all of vanilla are {@code
     * Player.serverAiStep()} (why a player's own swing animates) and, tellingly, {@code
     * RemotePlayer.aiStep()} — an explicit, unconditional call Mojang added specifically so a
     * <i>remote</i> player's swing still animates for everyone watching. Ordinary {@code Mob} has no
     * such call anywhere, so without this override an NPC's own {@code swing()} (triggered by {@code
     * MeleeAttackGoal}) sets {@code swinging=true} but {@code attackAnim} would silently stay at 0
     * forever — {@code is_swinging} always false, the attack state never reached. This mirrors {@code
     * RemotePlayer}'s own fix exactly, just applied to a {@code Mob} instead of a {@code Player}.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        updateSwingTime();
    }

    /**
     * Vanilla's own persistent-mob upkeep: {@code Mob.checkDespawn()}'s final {@code else} branch
     * resets {@code noActionTime} to 0 whenever {@code isPersistenceRequired() ||
     * requiresCustomPersistence()} — both true here — and nothing else ever calls it for a mob that
     * extends {@code PathfinderMob} directly. Without this, {@code noActionTime} (incremented
     * unconditionally every tick by {@code Mob.serverAiStep()}) climbs past 100 within about five
     * seconds and never comes back down, which permanently blocks {@code WaterAvoidingRandomStrollGoal}
     * (vanilla {@code RandomStrollGoal.canUse()} refuses once {@code getNoActionTime() >= 100}) for the
     * rest of the NPC's life — confirmed against the real decompiled source, not guessed. The branch
     * that can actually {@code discard()} the entity requires the opposite of both persistence checks
     * above, so this can never despawn an NPC.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        checkDespawn();
    }

    // ---- synced state ----

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MODEL, "");
        this.entityData.define(BEHAVIOR, NpcBehavior.PASSIVE.ordinal());
        this.entityData.define(HITBOX_MODE, NpcHitboxMode.PUSHABLE.ordinal());
        this.entityData.define(ANIMATION, "");
        this.entityData.define(BODY_WIDTH, AUTO_BODY_WIDTH);
        this.entityData.define(SKIN_OWNER, "");
        this.entityData.define(SCRIPT_NAME, "");
        this.entityData.define(ANIMATION_MODE, NpcAnimationMode.LOOP.ordinal());
        this.entityData.define(HAS_TARGET, false);
        this.entityData.define(IS_BREAKING_BLOCK, false);
    }

    /**
     * {@code query.has_target}'s real source (see {@code QueryTable}) — deliberately not {@code
     * Mob.getTarget() != null} read straight from the client. {@code Mob.target} is a plain field,
     * never a {@code SynchedEntityData} accessor (verified against source), so it's always {@code
     * null} on the client regardless of what the server is doing. {@code Mob.isAggressive()} looked
     * like the fix (it genuinely is synced), but {@code MeleeAttackGoal.canContinueToUse()} — with
     * {@code followingTargetEvenIfNotSeen=false}, what this engine's NPCs use — returns {@code
     * !navigation.isDone()}, which goes false the instant the NPC arrives within melee range and stops
     * moving; the goal then stops (clearing aggressive) and only restarts on its own ~1-second {@code
     * canUse()} cooldown, so aggressive itself flickers throughout the stationary part of a fight, not
     * just between individual swings. Overriding {@link #setTarget} to mirror it into a dedicated
     * synced field sidesteps that goal-internal churn entirely — this reflects "does this NPC have a
     * target at all," independent of which specific goal currently considers itself active.
     */
    public boolean hasTarget() {
        return this.entityData.get(HAS_TARGET);
    }

    /** {@code query.is_breaking_block}'s real source (see {@code QueryTable}) — set only by {@link NpcDestroyBlockGoal}, once the NPC is actually mid-break rather than still walking toward the target. */
    public boolean isBreakingBlock() {
        return this.entityData.get(IS_BREAKING_BLOCK);
    }

    public void setBreakingBlockAnim(boolean breaking) {
        this.entityData.set(IS_BREAKING_BLOCK, breaking);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        boolean hadTarget = getTarget() != null;
        super.setTarget(target);
        this.entityData.set(HAS_TARGET, target != null);
        if (combatWeaponItemId != null) {
            if (target != null && !hadTarget) {
                preTargetMainHand = getMainHandItem().copy();
                Item weapon = ForgeRegistries.ITEMS.getValue(parseItemId(combatWeaponItemId));
                if (weapon != null) {
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
                }
            } else if (target == null && hadTarget && restorePreviousWeapon && preTargetMainHand != null) {
                setItemSlot(EquipmentSlot.MAINHAND, preTargetMainHand);
                preTargetMainHand = null;
            }
        }
    }

    // ---- scripted combat weapon (npc_set_combat_weapon / npc_clear_combat_weapon) ----

    /** Item id to draw into the main hand the instant a target is acquired, or {@code null} for "no auto-draw" (the default — every existing script keeps its current manual-only equip behavior). */
    @Nullable
    private String combatWeaponItemId;
    /** Whether losing the target puts back whatever was held before the weapon was drawn. Per-call, not hardcoded — a guard keeping the sword out and a villager returning to a hoe are both legitimate scripted beats. */
    private boolean restorePreviousWeapon = true;
    /** Captured only while a drawn weapon is "on loan" — {@code null} whenever no weapon is currently out because of this feature. */
    @Nullable
    private ItemStack preTargetMainHand;

    public void setCombatWeapon(@Nullable String itemId, boolean restorePrevious) {
        this.combatWeaponItemId = (itemId == null || itemId.isEmpty()) ? null : itemId;
        this.restorePreviousWeapon = restorePrevious;
    }

    public void clearCombatWeapon() {
        this.combatWeaponItemId = null;
    }

    public String modelName() {
        return this.entityData.get(MODEL);
    }

    public void setModelName(String name) {
        this.entityData.set(MODEL, name);
    }

    public NpcBehavior behavior() {
        return NpcBehavior.byOrdinal(this.entityData.get(BEHAVIOR));
    }

    public void setBehavior(NpcBehavior behavior) {
        this.entityData.set(BEHAVIOR, behavior.ordinal());
    }

    public NpcHitboxMode hitboxMode() {
        return NpcHitboxMode.byOrdinal(this.entityData.get(HITBOX_MODE));
    }

    public void setHitboxMode(NpcHitboxMode mode) {
        this.entityData.set(HITBOX_MODE, mode.ordinal());
    }

    /** The clip this NPC is playing, or empty for none. The renderer reads it; nothing here interprets it. */
    public String animation() {
        return this.entityData.get(ANIMATION);
    }

    public void setAnimation(String animation) {
        this.entityData.set(ANIMATION, animation == null ? "" : animation);
    }

    /** How {@link #animation()}'s clip should play — see {@code npc_play_once}/{@code npc_play_freeze}/{@code npc_play_looped}. */
    public NpcAnimationMode animationMode() {
        return NpcAnimationMode.byOrdinal(this.entityData.get(ANIMATION_MODE));
    }

    public void setAnimationMode(NpcAnimationMode mode) {
        this.entityData.set(ANIMATION_MODE, mode.ordinal());
    }

    public void setBodyWidth(float width) {
        this.entityData.set(BODY_WIDTH, width);
    }

    /** Clears a manual width override — reverts to the model's own auto-derived footprint. */
    public void resetBodyWidth() {
        this.entityData.set(BODY_WIDTH, AUTO_BODY_WIDTH);
    }

    /** A real player's name or UUID to render this NPC's {@code "skin"} material as, or empty for none. See {@code client.model.PlayerSkinSource}. */
    public String skinOwner() {
        return this.entityData.get(SKIN_OWNER);
    }

    public void setSkinOwner(String nameOrUuid) {
        this.entityData.set(SKIN_OWNER, nameOrUuid == null ? "" : nameOrUuid);
    }

    /** The {@code npc_*} script commands' identifier for this specific spawned NPC — see {@code NpcLookup}. Empty for an NPC that was never spawned by a script (e.g. the old {@code /sme npc spawn}). */
    public String scriptName() {
        return this.entityData.get(SCRIPT_NAME);
    }

    public void setScriptName(String name) {
        this.entityData.set(SCRIPT_NAME, name == null ? "" : name);
    }

    // ---- scripted look override (npc_look_at_player / npc_look_at_pos / npc_stop_look) ----

    /** Non-null while a script has overridden {@link NpcLookAtGoal}'s own nearest-player logic with a fixed point. Server-only — never synced, since only that goal's own {@code tick()} reads it. */
    @Nullable
    private Vec3 lookOverridePos;
    /** Non-null while the override target is a specific player rather than a fixed point; re-resolved to a live entity every tick rather than cached, so a relogging player doesn't leave a stale reference. */
    @Nullable
    private UUID lookOverridePlayer;

    @Nullable
    public Vec3 lookOverridePos() {
        return lookOverridePos;
    }

    @Nullable
    public UUID lookOverridePlayer() {
        return lookOverridePlayer;
    }

    public void setLookOverridePos(Vec3 pos) {
        this.lookOverridePos = pos;
        this.lookOverridePlayer = null;
    }

    public void setLookOverridePlayer(UUID playerId) {
        this.lookOverridePlayer = playerId;
        this.lookOverridePos = null;
    }

    public void clearLookOverride() {
        this.lookOverridePos = null;
        this.lookOverridePlayer = null;
    }

    // ---- scripted follow target (npc_follow_player / npc_stop_move) ----

    /** Non-null while {@link com.dimalab.storymodengine.common.entity.ai.NpcFollowGoal} is actively repathing toward this player. Server-only, same reasoning as the look override above. */
    @Nullable
    private UUID followTarget;

    @Nullable
    public UUID followTarget() {
        return followTarget;
    }

    public void setFollowTarget(UUID playerId) {
        this.followTarget = playerId;
    }

    // ---- scripted item collection (npc_collect_items / npc_stop_collect) ----

    /** Item id to seek (empty string = any item), or {@code null} while not collecting. Read every cycle by {@link com.dimalab.storymodengine.common.entity.ai.NpcCollectItemsGoal}. */
    @Nullable
    private String collectFilter;
    /** Resolved once in {@link #setCollectFilter}, not re-resolved per pickup check — {@code null} for "any item" or when not collecting. */
    @Nullable
    private Item collectItem;
    private double collectRadius = 8.0D;

    @Nullable
    public String collectFilter() {
        return collectFilter;
    }

    public void setCollectFilter(@Nullable String itemId) {
        this.collectFilter = itemId;
        this.collectItem = (itemId == null || itemId.isEmpty()) ? null : ForgeRegistries.ITEMS.getValue(parseItemId(itemId));
    }

    public double collectRadius() {
        return collectRadius;
    }

    public void setCollectRadius(double radius) {
        this.collectRadius = radius;
    }

    /**
     * The real gate vanilla's own passive pickup (see {@code Mob.aiStep()}'s {@code
     * canPickUpLoot()} scan, confirmed by reading the decompiled source) checks per candidate item —
     * default {@code Mob} behavior wants every item unconditionally ({@code wantsToPickUp} defaults to
     * {@code canHoldItem}, which defaults to {@code true}), which is exactly why enabling {@code
     * canPickUpLoot} alone during {@code npc_collect_items} was scooping up whatever else happened to
     * be nearby (dirt sitting next to the intended stick) instead of respecting the collect filter.
     * Narrowing this hook — not reimplementing pickup — means every other vanilla mechanic
     * (reach distance, pickup delay, equip-slot logic, item events) still runs untouched; only *which*
     * items this NPC is willing to take changes. Outside an active collection ({@code collectFilter ==
     * null}), behavior is exactly what {@code npc_set_pickup} originally provided — unrestricted.
     */
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (collectFilter == null) {
            return super.wantsToPickUp(stack);
        }
        return collectFilter.isEmpty() || (collectItem != null && stack.is(collectItem));
    }

    private static ResourceLocation parseItemId(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }

    // ---- movement channel (npc_move_to / npc_follow_player / npc_stop_move / npc_collect_items) ----

    /** Bumped by {@link #beginMovementChannel}; a running {@code NpcMoveToTask} compares this against the value it captured when it started to notice it's been superseded. */
    private long movementGeneration = 0L;

    public long movementGeneration() {
        return movementGeneration;
    }

    /**
     * True while {@code npc_move_to} is driving navigation directly with {@code
     * getNavigation().moveTo(...)} rather than through a registered {@code Goal} — which means it
     * holds no {@code Goal.Flag.MOVE} lock vanilla's own goal selector would otherwise use to keep
     * {@code WaterAvoidingRandomStrollGoal} off the navigation. {@link NpcRandomStrollGoal} checks this
     * itself; {@link NpcFollowGoal} needs no equivalent since it IS a real {@code Goal} with that flag,
     * so vanilla's own mutual exclusion already covers it.
     */
    private boolean scriptNavigationActive = false;

    public boolean isScriptNavigationActive() {
        return scriptNavigationActive && !getNavigation().isDone();
    }

    /** Call right after issuing a raw {@code navigation.moveTo(...)} from a script command — after {@link #beginMovementChannel()} has already cleared the flag for whatever came before. */
    public void markScriptNavigationActive() {
        scriptNavigationActive = true;
    }

    /**
     * Bumps {@link #movementGeneration} without touching navigation or the follow goal — for a goal
     * that legitimately takes over movement outside the script channel (combat) rather than being
     * blocked from it (wandering, see {@link NpcRandomStrollGoal}). Lets an {@code NpcMoveToTask}
     * already awaiting the move it just interrupted resolve as {@code CANCELLED} instead of a
     * misleading {@code FAILURE} that reads as "got stuck".
     */
    public void noteExternalMovementTakeover() {
        movementGeneration++;
    }

    /**
     * Hands the MOVEMENT channel to whoever is calling this — cuts off the previous owner (follow
     * goal + navigation) and bumps the generation counter, so any {@code NpcMoveToTask} already
     * awaiting an earlier move (see {@code SequenceCompiler#compileAwaitableMoveTo}) notices next
     * tick that it's been superseded and resolves as cancelled instead of silently fighting the new
     * command for control of the navigation. Every {@code npc_move_to}/{@code npc_follow_player}/
     * {@code npc_stop_move} calls this first, before doing anything of its own.
     */
    public void beginMovementChannel() {
        movementGeneration++;
        scriptNavigationActive = false;
        removeFollowGoal();
        removeCollectGoal();
        removeDestroyBlockGoal();
        removePlaceBlockGoal();
        getNavigation().stop();
    }

    // ---- scripted block destruction (npc_break_block) ----

    /** Non-null while {@link NpcDestroyBlockGoal} is walking to and/or actively breaking this position. */
    @Nullable
    private BlockPos destroyBlockTarget;
    private boolean destroyBlockRequireTool = true;

    @Nullable
    public BlockPos destroyBlockTarget() {
        return destroyBlockTarget;
    }

    public void setDestroyBlockTarget(@Nullable BlockPos pos) {
        this.destroyBlockTarget = pos;
    }

    public boolean destroyBlockRequireTool() {
        return destroyBlockRequireTool;
    }

    public void setDestroyBlockRequireTool(boolean requireTool) {
        this.destroyBlockRequireTool = requireTool;
    }

    /** Same on-demand-add shape as {@link #ensureCollectGoal()} — priority 3, alongside it (mutually exclusive in practice since both claim {@code Flag.MOVE} and are only ever driven by one script command at a time). */
    public void ensureDestroyBlockGoal() {
        boolean present = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof NpcDestroyBlockGoal);
        if (!present) {
            this.goalSelector.addGoal(3, new NpcDestroyBlockGoal(this));
        }
    }

    public void removeDestroyBlockGoal() {
        this.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof NpcDestroyBlockGoal)
                .findFirst()
                .ifPresent(g -> this.goalSelector.removeGoal(g));
    }

    // ---- scripted block placement (npc_place_block) ----

    /** Non-null while {@link NpcPlaceBlockGoal} is walking to and/or about to place at this position. */
    @Nullable
    private BlockPos placeBlockTarget;
    private Direction placeBlockFacing = Direction.UP;

    @Nullable
    public BlockPos placeBlockTarget() {
        return placeBlockTarget;
    }

    public void setPlaceBlockTarget(@Nullable BlockPos pos) {
        this.placeBlockTarget = pos;
    }

    public Direction placeBlockFacing() {
        return placeBlockFacing;
    }

    public void setPlaceBlockFacing(Direction facing) {
        this.placeBlockFacing = facing;
    }

    /** Same on-demand-add shape as {@link #ensureDestroyBlockGoal()} — priority 3, alongside it. */
    public void ensurePlaceBlockGoal() {
        boolean present = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof NpcPlaceBlockGoal);
        if (!present) {
            this.goalSelector.addGoal(3, new NpcPlaceBlockGoal(this));
        }
    }

    public void removePlaceBlockGoal() {
        this.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof NpcPlaceBlockGoal)
                .findFirst()
                .ifPresent(g -> this.goalSelector.removeGoal(g));
    }

    /**
     * A cached, per-NPC {@link ServerPlayer} used only to drive real vanilla item-use pipelines
     * ({@code ServerPlayerGameMode.handleBlockBreakAction}, {@code ItemStack.useOn}) that otherwise
     * hard-require a {@code Player} instance — {@code FakePlayerFactory.get} already caches by
     * {@code (level, profile)} (verified against Forge's own source), so no separate field is needed
     * here; using this NPC's own {@link #getUUID()} as the profile id keeps one stable fake player per
     * NPC. Position/rotation/held-item are refreshed on every call since the real NPC moves between
     * calls and the fake player otherwise wouldn't.
     */
    public ServerPlayer fakePlayer() {
        ServerLevel level = (ServerLevel) level();
        ServerPlayer fake = FakePlayerFactory.get(level, new GameProfile(getUUID(), "npc"));
        fake.setPos(position());
        fake.setYRot(getYRot());
        fake.setXRot(getXRot());
        fake.setItemInHand(InteractionHand.MAIN_HAND, getMainHandItem().copy());
        return fake;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MODEL.equals(key) || BODY_WIDTH.equals(key)) {
            refreshModelDimensions();
        } else if (BEHAVIOR.equals(key)) {
            applyBehavior();
        }
    }

    // ---- size, taken from the model ----

    /**
     * Both dimensions come from the model by default. Height is always the full bind-pose extent
     * ({@link ModelBounds#sizeY}). Width uses {@link ModelPhysics#footprint} instead of the full
     * extent — a humanoid mesh in its bind pose is often as wide as its outstretched arms, and a
     * hitbox that wide feels bloated and blocks doorways; a model's own {@code .smemeta} sidecar
     * names which of its own nodes to leave out of that measurement for exactly this reason (see
     * {@code ModelBounds#footprint}'s own doc — a model that declares nothing there is unaffected). A
     * manual {@link #setBodyWidth} override — a positive value in {@link #BODY_WIDTH} rather than the
     * {@link #AUTO_BODY_WIDTH} sentinel — always wins over the auto-derived width, same as before this
     * was auto-derived at all.
     */
    private void refreshModelDimensions() {
        String name = modelName();
        float explicitWidth = this.entityData.get(BODY_WIDTH);
        if (name.isEmpty()) {
            this.dimensions = EntityDimensions.scalable(explicitWidth > 0F ? explicitWidth : DEFAULT_BODY_WIDTH, 1.95F);
            refreshDimensions();
            return;
        }
        ModelBounds bounds = ModelPhysics.bounds(name);
        float height = bounds.isEmpty() ? 1.95F : bounds.sizeY();
        // The upper bound has to be 3.0F, not 2.0F: per this class's own DOORWAY_SAFE_HEIGHT doc,
        // ANY height at or over 2.0 already becomes a three-block-tall pathfinder (floor(height+1)),
        // so the whole [2.0, 3.0) band still needs squeezing to fit a doorway, not just (1.99, 2.0) —
        // that off-by-one-tier upper bound was exactly why a 2.09-tall model stayed unclamped.
        if (height > DOORWAY_SAFE_HEIGHT && height < 3.0F) {
            height = DOORWAY_SAFE_HEIGHT;
        }
        float width;
        if (explicitWidth > 0F) {
            width = explicitWidth;
        } else {
            ModelBounds footprint = ModelPhysics.footprint(name);
            width = footprint.isEmpty() ? DEFAULT_BODY_WIDTH
                    : Math.max(Math.max(footprint.sizeX(), footprint.sizeZ()), MIN_BODY_WIDTH);
        }
        this.dimensions = EntityDimensions.scalable(width, Math.min(height, MAX_HEIGHT));
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return dimensions;
    }

    // ---- behaviour ----

    @Override
    protected void registerGoals() {
        applyBehavior();
    }

    /**
     * Rebuilds the goal list for the current {@link NpcBehavior}. Goals normally get registered once,
     * in the constructor, before any synced value is known — so a behaviour that can change at runtime
     * has to be able to re-register them, which is what makes one entity type able to be a villager or
     * a monster.
     */
    private void applyBehavior() {
        if (this.goalSelector == null || this.targetSelector == null || level().isClientSide) {
            return;
        }
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        setTarget(null);

        NpcBehavior behavior = behavior();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Present for every behaviour, same as FloatGoal above — an NPC that can't open a door in its
        // own way is stuck at it regardless of hostility. canUse() itself already gates on the mob
        // having bumped into something on its current path (see DoorInteractGoal), so this never fires
        // for an NPC that never needs a door.
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));

        if (behavior == NpcBehavior.HOSTILE) {
            this.goalSelector.addGoal(1, new NpcMeleeAttackGoal(this, 1.0D, false));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        } else if (behavior == NpcBehavior.PASSIVE) {
            // Fights back if struck, but never picks the fight — the difference between a guard and a monster.
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.goalSelector.addGoal(1, new NpcMeleeAttackGoal(this, 1.0D, false));
        }

        if (behavior != NpcBehavior.STATIONARY) {
            this.goalSelector.addGoal(5, new NpcRandomStrollGoal(this, 1.0D));
        }
        // Ours, not vanilla's LookAtPlayerGoal: that one only starts on ~2% of ticks and hands the
        // head straight back to the random look-around, which reads as an NPC that never looks at you.
        this.goalSelector.addGoal(6, new NpcLookAtGoal(this, LOOK_RANGE));
        // Only runs when nobody is around for the goal above to watch.
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ---- scripted follow goal (npc_follow_player / npc_stop_move) ----

    /** Adds {@code NpcFollowGoal} if it isn't already in the goal list — {@code applyBehavior} rebuilds the whole list on a behavior change, so this can't just be added once in the constructor. */
    public void ensureFollowGoal() {
        boolean present = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof NpcFollowGoal);
        if (!present) {
            this.goalSelector.addGoal(2, new NpcFollowGoal(this));
        }
    }

    public void removeFollowGoal() {
        this.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof NpcFollowGoal)
                .findFirst()
                .ifPresent(g -> this.goalSelector.removeGoal(g));
    }

    // ---- scripted item-collection goal (npc_collect_items / npc_stop_collect) ----

    /** Same on-demand-add shape as {@link #ensureFollowGoal()} — priority 3, between follow (2) and stroll (5). */
    public void ensureCollectGoal() {
        boolean present = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof NpcCollectItemsGoal);
        if (!present) {
            this.goalSelector.addGoal(3, new NpcCollectItemsGoal(this));
        }
    }

    public void removeCollectGoal() {
        this.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof NpcCollectItemsGoal)
                .findFirst()
                .ifPresent(g -> this.goalSelector.removeGoal(g));
    }

    /**
     * Makes sure something actually acts on {@link #setTarget} — {@link NpcBehavior#STATIONARY}
     * registers no {@code MeleeAttackGoal} at all, so setting a target on one used to store the field
     * and then visibly do nothing. A script explicitly commanding a target is asking for the NPC to
     * engage, so the goal is added on demand rather than the command silently no-opping; the
     * behaviour's own "never wanders on its own" meaning is untouched, since no stroll goal is added.
     */
    public void ensureAttackGoal() {
        boolean present = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof MeleeAttackGoal);
        if (!present) {
            this.goalSelector.addGoal(1, new NpcMeleeAttackGoal(this, 1.0D, false));
        }
    }

    /**
     * {@code onInteract { }}/{@code onShiftInteract { }} — {@code Mob.interact(Player,
     * InteractionHand)} itself is {@code final}; this is the correct override point (verified against
     * the decompiled source). Shift is checked first so a Shift+right-click NPC with both blocks
     * declared runs {@code onShiftInteract}, not {@code onInteract} — a plain click always falls
     * through to {@code onInteract} unchanged, exactly as before this method knew about shift at all.
     * Only fires for an NPC that was spawned by a script and whose declaration named the matching
     * body — every other NPC (spawned via the plain {@code /sme npc spawn} command, or one whose
     * block declared neither) falls through to vanilla's own default, unremarkable {@code PASS}.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && !scriptName().isEmpty()) {
            NpcDefinition definition = NpcDefinitionRegistry.get(scriptName());
            if (definition != null) {
                if (player.isShiftKeyDown() && definition.onShiftInteractFlowId() != null) {
                    FlowManager.start(definition.onShiftInteractFlowId(), serverPlayer);
                    return InteractionResult.sidedSuccess(false);
                }
                if (definition.onInteractFlowId() != null) {
                    FlowManager.start(definition.onInteractFlowId(), serverPlayer);
                    return InteractionResult.sidedSuccess(false);
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    // ---- collision, from the hitbox mode ----

    @Override
    public boolean canBeCollidedWith() {
        return hitboxMode() == NpcHitboxMode.SOLID && isAlive();
    }

    @Override
    public boolean isPushable() {
        return hitboxMode() == NpcHitboxMode.PUSHABLE;
    }

    @Override
    public void push(Entity entity) {
        if (hitboxMode() != NpcHitboxMode.GHOST) {
            super.push(entity);
        }
    }

    @Override
    public boolean isPickable() {
        return hitboxMode() != NpcHitboxMode.GHOST && !isRemoved();
    }

    // ---- persistence: a story character does not wander off or despawn ----

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /**
     * Despite the name, this is a per-tick pitch turn-<i>speed</i>, not an absolute limit — see {@link
     * com.dimalab.storymodengine.common.entity.ai.SmoothLookControl}, which is what actually bounds how
     * far the head can tilt now ({@code MAX_HEAD_PITCH_DEGREES}, 70°). 40°/tick reaches any realistic
     * target inside 1-2 ticks either way, which is why this is left at vanilla's own default.
     */
    @Override
    public int getMaxHeadXRot() {
        return 40;
    }

    /** Real cervical-spine yaw range is commonly cited around 80 degrees to each side of center before it reads as an unnatural, owl-like twist — 75 here used to just be vanilla's own unmodified default reused without a real reason; this is the same margin, chosen deliberately instead. */
    @Override
    public int getMaxHeadYRot() {
        return 80;
    }

    @Override
    public int getHeadRotSpeed() {
        return 10;
    }

    // ---- saving ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Model", modelName());
        tag.putString("Behavior", behavior().name());
        tag.putString("HitboxMode", hitboxMode().name());
        tag.putString("Animation", animation());
        tag.putFloat("BodyWidth", this.entityData.get(BODY_WIDTH));
        tag.putString("SkinOwner", skinOwner());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BodyWidth")) {
            setBodyWidth(tag.getFloat("BodyWidth"));
        }
        if (tag.contains("Model")) {
            setModelName(tag.getString("Model"));
        }
        if (tag.contains("Behavior")) {
            setBehavior(parseOr(NpcBehavior.class, tag.getString("Behavior"), NpcBehavior.PASSIVE));
        }
        if (tag.contains("HitboxMode")) {
            setHitboxMode(parseOr(NpcHitboxMode.class, tag.getString("HitboxMode"), NpcHitboxMode.PUSHABLE));
        }
        if (tag.contains("Animation")) {
            setAnimation(tag.getString("Animation"));
        }
        if (tag.contains("SkinOwner")) {
            setSkinOwner(tag.getString("SkinOwner"));
        }
    }

    /** Saved as names rather than ordinals, so reordering an enum can't silently turn every saved guard into a monster. */
    private static <E extends Enum<E>> E parseOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                // 16 is Mob's default and it is short: the NPC forgets a target barely past melee
                // range and re-paths constantly. 35 is what vanilla's hostile mobs use.
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }
}
