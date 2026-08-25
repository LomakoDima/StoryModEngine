package com.dimalab.storymodengine.common.raycast;

/** What {@link RaycastQuery#cast()} looks for — set via {@link RaycastQuery#blocks()}/{@link RaycastQuery#entities()}/{@link RaycastQuery#any()}. */
enum RaycastMode {
    BLOCKS, ENTITIES, ANY
}
