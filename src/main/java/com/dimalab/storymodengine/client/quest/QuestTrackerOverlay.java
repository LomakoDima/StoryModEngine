package com.dimalab.storymodengine.client.quest;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.capabilities.Capabilities;
import com.dimalab.storymodengine.api.quest.ObjectiveState;
import com.dimalab.storymodengine.common.quest.QuestDefinition;
import com.dimalab.storymodengine.common.quest.QuestProgress;
import com.dimalab.storymodengine.common.quest.QuestRegistry;
import com.dimalab.storymodengine.api.quest.QuestState;
import com.dimalab.storymodengine.common.quest.objective.Objective;
import com.dimalab.storymodengine.common.quest.persistence.ModQuestCapabilities;
import com.dimalab.storymodengine.common.quest.persistence.QuestProgressData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * A read-only list of active quests + objective progress, top-left of the screen — reads the
 * client-side copy of {@code QuestProgressData} the generic capability-sync mechanism already
 * delivers (see the design doc §8); decides nothing, sends nothing, matches {@code
 * cinematic.client.TitleCardOverlay}'s own {@code RenderGuiEvent.Post} shape. Deliberately not a
 * clickable/interactive {@code QuestScreen} — see the design doc §11 for why that's out of scope
 * this pass.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class QuestTrackerOverlay {

    private static final int MARGIN = 6;
    private static final int LINE_HEIGHT = 10;

    private QuestTrackerOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        QuestProgressData data = Capabilities.get(minecraft.player, ModQuestCapabilities.PROGRESS_DATA);
        if (data == null || data.quests.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int y = MARGIN;
        for (Map.Entry<ResourceLocation, QuestProgress> entry : data.quests.entrySet()) {
            if (entry.getValue().state() != QuestState.ACTIVE) {
                continue;
            }
            QuestDefinition quest = QuestRegistry.get(entry.getKey());
            String title = quest != null ? quest.title() : entry.getKey().toString();
            graphics.drawString(minecraft.font, title, MARGIN, y, 0xFFFFFF55, true);
            y += LINE_HEIGHT;

            if (quest != null) {
                for (Objective objective : quest.objectives()) {
                    ObjectiveState state = entry.getValue().objectiveStates().getOrDefault(objective.id(), ObjectiveState.INACTIVE);
                    if (state == ObjectiveState.INACTIVE || state == ObjectiveState.COMPLETED) {
                        continue;
                    }
                    int progress = entry.getValue().objectiveProgress().getOrDefault(objective.id(), 0);
                    String line = "  " + objective.description() + " (" + progress + "/" + objective.requiredCount() + ")";
                    graphics.drawString(minecraft.font, line, MARGIN, y, 0xFFCCCCCC, true);
                    y += LINE_HEIGHT;
                }
            }
            y += 2;
        }
    }
}
