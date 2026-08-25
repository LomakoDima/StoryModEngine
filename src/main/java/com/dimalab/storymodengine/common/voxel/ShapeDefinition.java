package com.dimalab.storymodengine.common.voxel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable list of {@link ShapeBox}es — what a block model's {@code elements} become — plus
 * the {@link VoxelShape} they union into. The union is built exactly once, in the constructor, and
 * handed back by every {@link #toVoxelShape()} call: {@code Shapes.or} is not free, and nothing
 * about a {@code ShapeDefinition} ever changes after construction, so recomputing it per call (or
 * per {@code Block#getShape()} tick) would be pure waste. {@link ShapeCache} is the layer above
 * this that avoids rebuilding the {@code ShapeDefinition} itself on every access.
 */
public final class ShapeDefinition {

    public static final ShapeDefinition EMPTY = new ShapeDefinition(List.of());

    private final List<ShapeBox> boxes;
    private final VoxelShape voxelShape;

    private ShapeDefinition(List<ShapeBox> boxes) {
        this.boxes = boxes;
        VoxelShape union = Shapes.empty();
        for (ShapeBox box : boxes) {
            union = Shapes.or(union, Shapes.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        }
        this.voxelShape = union;
    }

    static ShapeDefinition of(List<ShapeBox> boxes) {
        return boxes.isEmpty() ? EMPTY : new ShapeDefinition(List.copyOf(boxes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ShapeBox> boxes() {
        return boxes;
    }

    public VoxelShape toVoxelShape() {
        return voxelShape;
    }

    /**
     * This definition's boxes rotated to face {@code facing}, on the convention that a model's
     * boxes are authored facing {@link Direction#NORTH} — see {@link ShapeRotation} for how.
     */
    public ShapeDefinition rotated(Direction facing) {
        return ShapeRotation.rotate(this, facing);
    }

    public static final class Builder {
        private final List<ShapeBox> boxes = new ArrayList<>();

        private Builder() {
        }

        /** {@code from}/{@code to} in model units (0..16), matching a block model's own {@code elements}. */
        public Builder box(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            boxes.add(ShapeBox.fromModelUnits(fromX, fromY, fromZ, toX, toY, toZ));
            return this;
        }

        public ShapeDefinition build() {
            return of(boxes);
        }
    }
}
