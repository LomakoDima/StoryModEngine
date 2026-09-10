package com.dimalab.storymodengine.common.model.attachment;

import com.dimalab.storymodengine.api.capabilities.EntityCapability;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "This entity's model wears these textures instead of the ones it was authored with" — keyed by the
 * glTF material's own {@code name} (see {@code MaterialData#name()}), so a script can retexture
 * {@code "skin"} on one model and {@code "armor"} on another without either engine or script needing
 * to know the material's index. Attaches to <b>any</b> {@code Entity} for free, same as {@link
 * ModelAttachment} — a script can retexture a plain vanilla mob wearing an attached model exactly as
 * easily as an NPC.
 *
 * <p>Deliberately separate from {@code NpcEntity}'s own {@code skinOwner} field/{@code "skin"}-material
 * special case (see {@code client.model.ModelRenderer#resolveTexture}'s own doc for the resolve order)
 * — that one keeps working exactly as it always has; this is the generic mechanism layered on top of it,
 * not a replacement.
 */
public final class MaterialOverrides implements EntityCapability {

    public Map<String, MaterialOverride> byName = new LinkedHashMap<>();
}
