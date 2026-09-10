package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.common.entity.EntityTargeting;
import com.dimalab.storymodengine.common.logging.EngineLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * {@code entity_set_material_texture}/{@code entity_set_material_skin}/{@code entity_reset_material}/
 * {@code entity_reset_materials} — the script-facing side of the generic material-override mechanism
 * (see {@link MaterialOverrides}'s own doc): retexture any named material on any entity's model, not
 * just an NPC's {@code "skin"} material. {@code target} accepts either an NPC's own script name or a
 * bare UUID (what {@code find_nearest_entity} binds into a variable) via the existing {@link
 * EntityTargeting#resolve} — the same resolution the generic follow/look-at commands already use.
 */
public final class MaterialCommands {

    private MaterialCommands() {
    }

    @StoryCommand("entity_set_material_texture")
    public static void entitySetMaterialTexture(ServerPlayer player, String target, String materialName, String texturePath) {
        Entity entity = resolve(player, target, "entity_set_material_texture");
        if (entity == null) {
            return;
        }
        if (ResourceLocation.tryParse(texturePath) == null) {
            EngineLog.channel("Model").warn("entity_set_material_texture: '{}' is not a valid resource location", texturePath);
            return;
        }
        MaterialOverrides overrides = Capabilities.get(entity, MaterialOverrideCapabilities.OVERRIDES);
        if (overrides == null) {
            return;
        }
        MaterialOverride override = new MaterialOverride();
        override.texture = texturePath;
        overrides.byName.put(materialName, override);
        sync(entity);
    }

    @StoryCommand("entity_set_material_skin")
    public static void entitySetMaterialSkin(ServerPlayer player, String target, String materialName, String playerNameOrUuid) {
        Entity entity = resolve(player, target, "entity_set_material_skin");
        if (entity == null) {
            return;
        }
        MaterialOverrides overrides = Capabilities.get(entity, MaterialOverrideCapabilities.OVERRIDES);
        if (overrides == null) {
            return;
        }
        MaterialOverride override = new MaterialOverride();
        override.skinPlayer = playerNameOrUuid;
        overrides.byName.put(materialName, override);
        sync(entity);
    }

    @StoryCommand("entity_reset_material")
    public static void entityResetMaterial(ServerPlayer player, String target, String materialName) {
        Entity entity = resolve(player, target, "entity_reset_material");
        if (entity == null) {
            return;
        }
        MaterialOverrides overrides = Capabilities.get(entity, MaterialOverrideCapabilities.OVERRIDES);
        if (overrides == null) {
            return;
        }
        overrides.byName.remove(materialName);
        sync(entity);
    }

    @StoryCommand("entity_reset_materials")
    public static void entityResetMaterials(ServerPlayer player, String target) {
        Entity entity = resolve(player, target, "entity_reset_materials");
        if (entity == null) {
            return;
        }
        MaterialOverrides overrides = Capabilities.get(entity, MaterialOverrideCapabilities.OVERRIDES);
        if (overrides == null) {
            return;
        }
        overrides.byName.clear();
        sync(entity);
    }

    private static Entity resolve(ServerPlayer player, String target, String command) {
        Entity entity = EntityTargeting.resolve(player.serverLevel(), target);
        if (entity == null) {
            EngineLog.channel("Model").warn("{}: no entity matches target '{}'", command, target);
        }
        return entity;
    }

    private static void sync(Entity entity) {
        Capabilities.markDirty(entity, MaterialOverrideCapabilities.OVERRIDES);
        MaterialOverrideCapabilities.OVERRIDES.sync(entity);
    }
}
