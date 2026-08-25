package com.dimalab.storymodengine.api.capabilities;

/**
 * Which kind of object a {@code @Capability} data class attaches to — derived from which of
 * {@link EntityCapability}/{@link BlockEntityCapability}/{@link LevelCapability} the data type
 * implements, never declared directly by the mod author.
 */
public enum OwnerKind {
    ENTITY,
    BLOCK_ENTITY,
    LEVEL;

    /**
     * Derives an owner kind from a discovered data type's declared marker. Returns {@code null}
     * if the type implements none, or more than one, of the three markers — a discovery error,
     * not a valid declaration.
     */
    public static OwnerKind of(Class<?> dataType) {
        boolean entity = EntityCapability.class.isAssignableFrom(dataType);
        boolean blockEntity = BlockEntityCapability.class.isAssignableFrom(dataType);
        boolean level = LevelCapability.class.isAssignableFrom(dataType);
        int matches = (entity ? 1 : 0) + (blockEntity ? 1 : 0) + (level ? 1 : 0);
        if (matches != 1) {
            return null;
        }
        if (entity) return ENTITY;
        if (blockEntity) return BLOCK_ENTITY;
        return LEVEL;
    }
}
