package com.dimalab.storymodengine.common.core;

import com.dimalab.storymodengine.common.capabilities.CapabilityBootstrap;
import com.dimalab.storymodengine.common.cinematic.CinematicBootstrap;
import com.dimalab.storymodengine.client.content.AutoParticleRegistration;
import com.dimalab.storymodengine.common.concurrent.ConcurrencyBootstrap;
import com.dimalab.storymodengine.common.content.ContentDiscovery;
import com.dimalab.storymodengine.common.dialogue.DialogueBootstrap;
import com.dimalab.storymodengine.common.event.EventBootstrap;
import com.dimalab.storymodengine.common.flow.FlowBootstrap;
import com.dimalab.storymodengine.common.flow.FlowState;
import com.dimalab.storymodengine.common.network.NetworkBootstrap;
import com.dimalab.storymodengine.common.quest.QuestBootstrap;
import com.dimalab.storymodengine.common.resource.ResourcePacks;
import com.dimalab.storymodengine.common.trigger.TriggerBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The engine's single bootstrap call — everything a mod author needs to write, once, in their
 * {@code @Mod} constructor:
 *
 * <pre>{@code
 * public MyMod() {
 *     IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
 *     EngineBootstrap.init(modEventBus);
 *     ...
 * }
 * }</pre>
 *
 * Internally this sequences the pipelines described in {@code ARCHITECTURE.md}:
 * {@link ContentDiscovery} (discovery → registration, including any companion content like a
 * {@code Block}'s {@code BlockItem}) runs first, then {@link ResourcePacks} arms the generated
 * resource/data packs that turn the resulting {@code ContentDescriptor}s into client resources and
 * server data, then {@link NetworkBootstrap} discovers every {@code @Packet} record and wires it
 * into that mod's own {@code SimpleChannel} — common-side, no {@link Dist} gate, since channel
 * creation behaves identically on a dedicated server (verified against source). Right after,
 * {@code FlowState.registerSerializer()} installs Flow's own hand-written, version-gated
 * serializer — it has to run before capability discovery resolves {@code StoryFlowData}'s field
 * type, so it can't simply live inside {@link FlowBootstrap} alongside Flow's other setup (see that
 * method's own Javadoc). {@link
 * CapabilityBootstrap} then discovers every {@code @Capability} field and arms Forge capability
 * attachment for {@code Entity}/{@code BlockEntity}/{@code Level} — also no {@code Dist} gate, and
 * placed after {@code NetworkBootstrap} since its own sync packets are {@code @Packet}s (though
 * discovery order doesn't actually matter for correctness — both scan pre-populated {@code
 * ModFileScanData}, not each other's runtime state; this is purely for readability, matching the
 * architecture diagram's Network → Capabilities layering). {@link EventBootstrap} runs last of the
 * common-side subsystems: it discovers every {@code @SubscribeEvent} static listener and posts an
 * {@code AnnotationProcessorEvent} marking this mod's engine setup complete — deliberately after
 * every other discovery pass, so a listener reacting to that event sees a fully-initialized engine.
 * {@link FlowBootstrap} runs right after: Flow's own persistence ({@code @Capability}) and
 * join-resume listener ({@code @SubscribeEvent}) are already found by the two discovery passes
 * above, so all that's left is arming the one new Forge touch-point Flow actually needs — the
 * server tick source that drives {@code FlowManager.tick()}.
 * {@link AutoParticleRegistration} —
 * giving every discovered {@code SimpleParticleType} a default renderer — only runs {@link
 * Dist#CLIENT}-side: the {@link FMLEnvironment#dist} check below is what keeps a dedicated server
 * from ever resolving {@code AutoParticleRegistration} or the true client-only classes it
 * references, since evaluating a method reference to it is what would force that resolution — the
 * check has to happen here, not inside that class. Mod authors never call any of these subsystems
 * directly; this is the only entry point meant to grow as more engine subsystems need their own
 * one-time wiring.
 *
 * <p>{@link ConcurrencyBootstrap} runs first of all, ahead even of {@link ContentDiscovery} — it has
 * no dependency on anything else here, and every other subsystem is free to use {@code
 * common.concurrent.Async} internally without caring about init order. It only registers listeners;
 * the real thread pools aren't created until {@code ServerStartingEvent} (see {@code
 * concurrent.integration.AsyncLifecycle}), so nothing here actually starts a thread.
 */
public final class EngineBootstrap {

    private EngineBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        ConcurrencyBootstrap.init();
        ContentDiscovery.run(modEventBus);
        ResourcePacks.register(modEventBus);
        NetworkBootstrap.init(modEventBus);
        // FlowState needs its own hand-written Serializer (for version-gated persistence — see its
        // Javadoc) registered before CapabilityBootstrap resolves StoryFlowData's Map<String,
        // FlowState> field; a CapabilityDescriptor's serializer chain is built once and never
        // re-resolved, so this has to run first, not just "before Flow's own bootstrap".
        FlowState.registerSerializer();
        CapabilityBootstrap.init(modEventBus);
        EventBootstrap.init();
        FlowBootstrap.init();
        // Cinematic's own @AutoCutscene discovery + server tick source — the client-side half
        // (camera/actor/subtitle/audio playback) self-registers via @Mod.EventBusSubscriber(value =
        // Dist.CLIENT) on its own classes (the same dist-safe pattern MathUiCommand already uses),
        // so nothing client-specific needs wiring here.
        CinematicBootstrap.init();
        // Dialogue compiles onto the already-running Flow engine, so — like Cinematic — it needs no
        // tick source of its own; DialogueDiscovery just needs FlowBootstrap's FlowRegistry (and
        // FlowState's serializer) already in place, which this ordering guarantees.
        DialogueBootstrap.init();
        // Quest compiles onto Flow exactly like Dialogue does, and its objectives can start a
        // Dialogue directly (Objective.dialogue) — so it needs Flow, Dialogue, Capability
        // (QuestProgressData persistence), Network, and Event all already initialized, which is
        // guaranteed by placing it last among the common-side discovery/bootstrap calls.
        QuestBootstrap.init();
        // Trigger only *starts* Flow/Quest/Dialogue/Cinematic — it never compiles onto them the way
        // Quest/Dialogue/Cinematic compile onto Flow — but a mod author's own trigger Flow can freely
        // reference any of those already-initialized systems, so it goes last among common-side calls.
        TriggerBootstrap.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            String modId = ModLoadingContext.get().getContainer().getModId();
            AutoParticleRegistration.register(modEventBus, modId);
        }
    }
}
