package com.dimalab.storymodengine.common.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A lightweight, opaque reference to one just-started quest — what {@link QuestSystem#start} returns, mirroring {@code flow.FlowHandle}/{@code dialogue.DialogueHandle}'s own shape. Every query delegates straight back to {@link QuestSystem}'s own static facade — this holds nothing a caller couldn't otherwise look up by {@code (player, questId)}, it just saves repeating both. */
public final class QuestHandle {

    private final ServerPlayer player;
    private final ResourceLocation questId;

    QuestHandle(ServerPlayer player, ResourceLocation questId) {
        this.player = player;
        this.questId = questId;
    }

    public ResourceLocation questId() {
        return questId;
    }

    public boolean isActive() {
        return QuestSystem.isActive(player, questId);
    }

    public boolean isCompleted() {
        return QuestSystem.isCompleted(player, questId);
    }

    public QuestProgress progress() {
        return QuestSystem.progress(player, questId);
    }
}
