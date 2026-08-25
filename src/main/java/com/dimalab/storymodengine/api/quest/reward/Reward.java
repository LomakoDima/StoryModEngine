package com.dimalab.storymodengine.api.quest.reward;

import com.dimalab.storymodengine.common.flow.Flow;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.quest.QuestSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;

/**
 * The reward extension point — same shape as {@code quest.objective.Objective} (an interface that's
 * its own static-factory holder), just simpler: a reward has no id/progress, only "what it looks
 * like" and "what it does." {@link #compile()} produces a single {@code Flow.action} step;
 * {@code QuestCompiler} appends one per declared reward after the quest's completion step.
 */
public interface Reward {

    /** Player-facing text for {@code QuestToast}. */
    String description();

    Flow compile();

    static Reward item(ResourceLocation itemId, int count) {
        return new Reward() {
            @Override
            public String description() {
                return count + "x " + itemId;
            }

            @Override
            public Flow compile() {
                return Flow.action(ctx -> {
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item == null) {
                        EngineLog.channel("Quest").warn("Reward.item({}): unknown item id", itemId);
                        return;
                    }
                    giveItem(ctx.player(), new ItemStack(item, count));
                });
            }
        };
    }

    static Reward item(String itemId, int count) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId);
        if (parsed == null) {
            throw new IllegalArgumentException("Not a valid namespaced id: '" + itemId + "' — expected 'namespace:path'");
        }
        return item(parsed, count);
    }

    static Reward experience(int amount) {
        return new Reward() {
            @Override
            public String description() {
                return amount + " XP";
            }

            @Override
            public Flow compile() {
                return Flow.action(ctx -> ctx.player().giveExperiencePoints(amount));
            }
        };
    }

    /** Runs a literal Minecraft command as the completing player — the JSON-friendly counterpart to {@link #action}, since a JSON reward can't carry a {@code Consumer<ServerPlayer>}. */
    static Reward command(String command) {
        return new Reward() {
            @Override
            public String description() {
                return "Run: " + command;
            }

            @Override
            public Flow compile() {
                return Flow.action(ctx -> {
                    ServerPlayer player = ctx.player();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
                });
            }
        };
    }

    /** The escape hatch for anything the named factories don't cover — runs {@code action} against the completing player. Java-only, not JSON-representable (see {@link #command} for that). */
    static Reward action(String description, Consumer<ServerPlayer> action) {
        return new Reward() {
            @Override
            public String description() {
                return description;
            }

            @Override
            public Flow compile() {
                return Flow.action(ctx -> action.accept(ctx.player()));
            }
        };
    }

    /** Starts {@code questId} for the player the instant this quest completes — the forward half of a quest chain (see the design doc §7); reuses {@code QuestSystem.start} rather than inventing a second "quest chain" concept. */
    static Reward unlockQuest(ResourceLocation questId) {
        return new Reward() {
            @Override
            public String description() {
                return "Unlocks " + questId;
            }

            @Override
            public Flow compile() {
                return Flow.action(ctx -> QuestSystem.start(ctx.player(), questId));
            }
        };
    }

    static Reward unlockQuest(String questId) {
        ResourceLocation parsed = ResourceLocation.tryParse(questId);
        if (parsed == null) {
            throw new IllegalArgumentException("Not a valid namespaced id: '" + questId + "' — expected 'namespace:path'");
        }
        return unlockQuest(parsed);
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
