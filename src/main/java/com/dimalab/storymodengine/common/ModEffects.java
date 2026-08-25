package com.dimalab.storymodengine.common;

import com.dimalab.storymodengine.api.content.AutoContent;
import com.dimalab.storymodengine.common.content.ContentHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * A mod author's own effect class — same shape as {@link ModItems}: plain Java, {@link AutoContent}
 * infers the id ({@code bleeding}) from the field name and routes it to
 * {@code ForgeRegistries.MOB_EFFECTS} from the field's {@code MobEffect} type. No JSON is generated
 * for effects; the icon is picked up automatically from
 * {@code assets/storymodengine/textures/mob_effect/bleeding.png} by vanilla's own effect texture
 * atlas — put the texture there and it renders with no further wiring.
 *
 * <p>{@code Bleeding} deals periodic damage like vanilla Poison, on the same tick cadence
 * ({@code 25 >> amplifier}), but through {@code damageSources().generic()} instead of Poison's
 * {@code magic()} — so, unlike Poison, it isn't blocked by magic resistance/immunity. It won't
 * reduce an entity below 1 HP, matching Poison's own non-lethal behavior.
 */
public class ModEffects {

    @AutoContent
    public static final Supplier<MobEffect> BLEEDING = ContentHolder.of(() -> new MobEffect(MobEffectCategory.HARMFUL, 0xA10000) {
        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            int interval = 25 >> amplifier;
            if (interval > 0) {
                return duration % interval == 0;
            }
            return true;
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.getHealth() > 1.0F) {
                entity.hurt(entity.damageSources().generic(), 1.0F);
            }
        }
    });
}
