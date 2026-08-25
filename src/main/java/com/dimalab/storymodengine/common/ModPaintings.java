package com.dimalab.storymodengine.common;

import com.dimalab.storymodengine.api.content.AutoContent;
import com.dimalab.storymodengine.common.content.ContentHolder;
import net.minecraft.world.entity.decoration.PaintingVariant;

import java.util.function.Supplier;

/**
 * A mod author's own painting class — same shape as {@link ModItems}/{@link ModEffects}:
 * {@link AutoContent} infers the id ({@code starry_night}) from the field name and routes it to
 * {@code ForgeRegistries.PAINTING_VARIANTS} from the field's {@code PaintingVariant} type.
 *
 * <p>The {@code 16, 16} arguments are the variant's width/height in pixels — they must match the
 * texture's real dimensions exactly, or the painting renders stretched. Put the texture at
 * {@code assets/storymodengine/textures/painting/starry_night.png}, 16×16 pixels; no other file is
 * needed — {@code DataGenerator} already adds this id to the {@code minecraft:placeable} tag, so
 * it turns up as a normal placeable variant the moment a player right-clicks with a painting item.
 */
public class ModPaintings {

    @AutoContent
    public static final Supplier<PaintingVariant> STARRY_NIGHT = ContentHolder.of(() -> new PaintingVariant(16, 16));
}
