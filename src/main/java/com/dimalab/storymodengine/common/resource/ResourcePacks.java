package com.dimalab.storymodengine.common.resource;

import com.dimalab.storymodengine.common.content.ContentDiscovery;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Wires synthetic, always-on generated packs into the game via Forge's {@link AddPackFindersEvent}
 * — the same event/mechanism Forge itself uses to add each mod's own {@code src/main/resources}
 * folder as the "mod_resources" pack. This is what makes generated resources/data available the
 * moment Minecraft starts, with no {@code runData} run and no hand-authored JSON: one pack's
 * content is computed by {@link AssetGenerator} ({@link PackType#CLIENT_RESOURCES} — blockstates,
 * models, lang, particle descriptions), the other by {@link DataGenerator} ({@link
 * PackType#SERVER_DATA} — e.g. the painting-variant placement tag), both from whatever {@link
 * ContentDiscovery} has registered, freshly, every time the game (re)loads. {@link
 * AddPackFindersEvent} fires once per {@link PackType} (verified against source: "fired on {@code
 * PackRepository} creation", and there is one such repository for each), so both branches below
 * are reached on their own, including on a dedicated server, which only ever builds the
 * {@code SERVER_DATA} repository.
 *
 * <p>Registered at {@link Pack.Position#BOTTOM}, fixed there — one step above vanilla's own
 * always-bottom pack, and below every mod's real {@code assets}/{@code data} folder (Forge's own
 * "mod_resources" packs) and any user-installed resource/data pack. That ordering is deliberate:
 * {@code Pack.Position.insert} (verified against the real 1.20.1 source, not assumed) puts
 * {@code TOP} packs at the *end* of the priority-ordered pack list — highest priority, resolved
 * first — which would mean a mod author's own hand-authored file for the same path (or, for
 * {@code lang/en_us.json} specifically, the same translation *key* — language files merge
 * per-key across every contributing pack, verified against {@code ClientLanguage.loadFrom}) could
 * never win over the generated default. {@code BOTTOM} makes the generated pack exactly that: a
 * default, silently overridden the moment the mod author supplies their own file or lang entry —
 * never the other way around. For the data pack's tag file specifically, position is moot anyway:
 * tag {@code values} merge across every contributing pack regardless of priority, only {@code
 * "replace": true} entries are position-sensitive, and the generated tag never sets it.
 */
public final class ResourcePacks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePacks.class);

    private ResourcePacks() {
    }

    /** Arms both pack finders. Call once, from the mod's {@code @Mod} constructor. */
    public static void register(IEventBus modEventBus) {
        String modId = ModLoadingContext.get().getContainer().getModId();
        modEventBus.addListener((AddPackFindersEvent event) -> onAddPackFinders(event, modId));
    }

    private static void onAddPackFinders(AddPackFindersEvent event, String modId) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(consumer -> consumer.accept(buildPack(
                    modId, PackType.CLIENT_RESOURCES, "auto_content_generated",
                    () -> {
                        var generated = AssetGenerator.generate(modId, ContentDiscovery.getDiscovered());
                        LOGGER.info("[{}] Generated {} resource-pack entr(y/ies) for @AutoContent", modId, generated.size());
                        return generated;
                    })));
        } else if (event.getPackType() == PackType.SERVER_DATA) {
            event.addRepositorySource(consumer -> consumer.accept(buildPack(
                    modId, PackType.SERVER_DATA, "auto_content_generated_data",
                    () -> {
                        var generated = DataGenerator.generate(modId, ContentDiscovery.getDiscovered());
                        LOGGER.info("[{}] Generated {} data-pack entr(y/ies) for @AutoContent", modId, generated.size());
                        return generated;
                    })));
        }
    }

    private static Pack buildPack(String modId, PackType packType, String idSuffix,
                                   Supplier<Map<ResourceLocation, byte[]>> generator) {
        return Pack.create(
                modId + ":" + idSuffix,
                Component.literal(modId + " (generated by StoryModEngine)"),
                true,
                packId -> new GeneratedResourcePack(packId, packType, generator.get()),
                new Pack.Info(
                        Component.literal("StoryModEngine generated resources"),
                        SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA),
                        SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES),
                        FeatureFlagSet.of(),
                        true),
                packType,
                Pack.Position.BOTTOM,
                true,
                PackSource.BUILT_IN);
    }
}
