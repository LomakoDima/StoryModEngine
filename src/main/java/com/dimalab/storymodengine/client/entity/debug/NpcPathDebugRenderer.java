package com.dimalab.storymodengine.client.entity.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.npc.NpcEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.PathfindingRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /sme npc pathdebug} (see {@code NpcPathDebugCommand}) toggles this. Reuses
 * vanilla's own {@link PathfindingRenderer} directly — the same class backing F3's own path debug,
 * and the same one HollowEngine itself reuses rather than drawing its own line/box renderer from
 * scratch (architecture idea studied, not their code: see {@code sme-vs-he.html}). Vanilla's own path
 * lives behind a server round-trip ({@code DebugPackets.sendPathFindingPacket}, gated on the F3 screen
 * being open) that {@link PathfindingRenderer#addPath} feeds from; that whole mechanism is skipped
 * here in favor of calling {@link PathfindingRenderer#renderPath} directly every frame from each
 * nearby {@link NpcEntity}'s own already-client-side {@code Mob#getNavigation()} — no packet, no F3
 * dependency, no new GL code.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class NpcPathDebugRenderer {

    private static boolean enabled = false;

    private NpcPathDebugRenderer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** @return the new state, so the command that calls this can report it back to the player. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        // Not self-contained the way InstanceFlush's own instanced RenderType draws are — this writes
        // into the shared entity buffer source (debugLineStrip/debugFilledBox/text, all through
        // PathfindingRenderer's own calls), so it needs the same explicit endBatch() InstanceFlush's
        // fallback path already uses, once after every NPC's path this frame has been queued.
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        boolean drewAny = false;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof NpcEntity npc)) {
                continue;
            }
            PathNavigation navigation = npc.getNavigation();
            Path path = navigation.getPath();
            if (path == null || path.isDone()) {
                continue;
            }
            PathfindingRenderer.renderPath(event.getPoseStack(), bufferSource, path, navigation.getMaxDistanceToWaypoint(),
                    true, true, cameraPos.x, cameraPos.y, cameraPos.z);
            drewAny = true;
        }
        if (drewAny) {
            bufferSource.endBatch();
        }
    }
}
