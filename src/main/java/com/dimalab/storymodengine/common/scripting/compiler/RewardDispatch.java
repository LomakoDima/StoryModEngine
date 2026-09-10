package com.dimalab.storymodengine.common.scripting.compiler;

import com.dimalab.storymodengine.api.quest.reward.Reward;
import com.dimalab.storymodengine.common.scripting.ast.*;
import com.dimalab.storymodengine.common.scripting.registry.ScriptingRegistryFacade;

/** {@code item/xp/command/unlock_quest} reward entries → {@code api.quest.reward.Reward}'s matching factories — mirrors {@link ObjectiveDispatch}. */
public final class RewardDispatch {

    private RewardDispatch() {
    }

    public static Reward compile(RewardNode node) {
        if (node instanceof ItemRewardNode item) {
            return Reward.item(ScriptingRegistryFacade.contentId(item.itemId()), item.count());
        }
        if (node instanceof XpRewardNode xp) {
            return Reward.experience(xp.amount());
        }
        if (node instanceof CommandRewardNode cmd) {
            return Reward.command(cmd.command());
        }
        if (node instanceof UnlockQuestRewardNode unlock) {
            return Reward.unlockQuest(ScriptingRegistryFacade.storyId(unlock.questId()));
        }
        throw new IllegalStateException("Unknown reward node: " + node);
    }
}
