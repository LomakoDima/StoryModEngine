package com.dimalab.storymodengine.common.entity.custom;

import com.dimalab.storymodengine.common.model.physics.ModelBounds;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A glTF model existing as a real, solid object in the world: you collide with it, you can look at
 * it, and the server agrees you can't walk through it.
 *
 * <p><b>Only the model's name crosses the network</b> — never geometry. Both sides already have the
 * file (it ships in the mod jar), so a synced string is enough for the client to know what to draw
 * and for the server to know how big it is. That's the same principle {@code DialogueStepPacket}
 * already follows: send the id, not the content.
 *
 * <p><b>Collision is a single upright box.</b> Vanilla entities have no multi-box or mesh collision
 * in 1.20.1 — {@link EntityDimensions} is one width and one height, and that's the whole vocabulary.
 * The box is fitted to the model's bind-pose extent (see {@link ModelPhysics#footprint}, which
 * — same as {@code NpcEntity} uses for its own width — leaves out whatever node subtrees the model's
 * own {@code .smemeta} sidecar names in {@code hitboxExcludeNodes}, e.g. a humanoid's outstretched
 * arms), deliberately not to its current animated pose: a hitbox that resized as a character moved
 * would read as broken collision rather than as fidelity. Shaped, per-part collision is only possible
 * for blocks — see {@code model.block.ModelBlock}.
 */
public class ModelEntity extends Entity {

    private static final EntityDataAccessor<String> MODEL_NAME =
            SynchedEntityData.defineId(ModelEntity.class, EntityDataSerializers.STRING);

    private ModelBounds bounds = ModelBounds.EMPTY;
    private float dimensionsYaw = Float.NaN;

    public ModelEntity(EntityType<? extends ModelEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MODEL_NAME, "");
    }

    public String modelName() {
        return this.entityData.get(MODEL_NAME);
    }

    public void setModelName(String name) {
        this.entityData.set(MODEL_NAME, name);
        refreshModelDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // The client learns the model name only when this arrives, so that's when its hitbox is known.
        if (MODEL_NAME.equals(key)) {
            refreshModelDimensions();
        }
    }

    private void refreshModelDimensions() {
        String name = modelName();
        if (name.isEmpty()) {
            return;
        }
        ModelBounds resolved = ModelPhysics.footprint(name);
        if (!resolved.isEmpty()) {
            this.bounds = resolved;
            this.dimensionsYaw = getYRot();
            refreshDimensions();
        }
    }

    /**
     * Sized for the model <em>as drawn</em> — i.e. rotated by this entity's yaw. The box itself stays
     * world-axis-aligned (Minecraft has no rotated entity hitbox), but it grows to contain the turned
     * model instead of staying sized for the unrotated one; see {@link ModelBounds#toEntityDimensions(float)}.
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return bounds.toEntityDimensions(getYRot());
    }

    /** What makes the player actually bump into this rather than walk through it — the same opt-in boats and shulkers use. */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        // A static prop: no AI, no physics integration beyond staying put. Gravity is deliberately
        // not applied — a display object should stay exactly where it was placed.
        this.setDeltaMovement(Vec3.ZERO);

        // The box is sized for the current yaw, so a turned prop has to resize. Minecraft caches the
        // bounding box and only rebuilds it on refreshDimensions(), so a yaw change has to say so —
        // guarded by a threshold since refreshDimensions() is not free and yaw can jitter by a hair.
        if (!bounds.isEmpty() && (Float.isNaN(dimensionsYaw) || Math.abs(Mth.degreesDifference(dimensionsYaw, getYRot())) > 1.0f)) {
            dimensionsYaw = getYRot();
            refreshDimensions();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("ModelName")) {
            setModelName(tag.getString("ModelName"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("ModelName", modelName());
    }
}
