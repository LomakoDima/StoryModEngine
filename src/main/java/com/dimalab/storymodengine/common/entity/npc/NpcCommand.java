package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.entity.NpcBehavior;
import com.dimalab.storymodengine.api.entity.NpcHitboxMode;
import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.ModEntities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.AnimationClip;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

/**
 * {@code /sme npc …} — create and configure story characters in-world.
 *
 * <p>Server-side, because an NPC only exists if the server spawns it. Every property an NPC has is
 * settable here rather than compiled in, which is the point of the entity: {@code spawn} takes the
 * model, and {@code behavior}/{@code hitbox}/{@code animation}/{@code name} change the nearest one.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class NpcCommand {

    private static final double NEAREST_SEARCH_RADIUS = 16.0;

    private NpcCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("model", StringArgumentType.word())
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "model"), NpcBehavior.PASSIVE))
                                        .then(Commands.argument("behavior", StringArgumentType.word())
                                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "model"),
                                                        parse(NpcBehavior.class, StringArgumentType.getString(ctx, "behavior"), NpcBehavior.PASSIVE))))))
                        .then(Commands.literal("spawnas")
                                .then(Commands.argument("template", StringArgumentType.string())
                                        .then(Commands.argument("instanceName", StringArgumentType.string())
                                                .executes(ctx -> spawnAs(ctx, StringArgumentType.getString(ctx, "template"), StringArgumentType.getString(ctx, "instanceName"))))))
                        .then(Commands.literal("behavior")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setBehavior(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("hitbox")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setHitbox(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("animation")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setAnimation(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("name")
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> setName(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("skin")
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> setSkin(ctx, StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("width")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 4.0f))
                                        .executes(ctx -> setWidth(ctx, FloatArgumentType.getFloat(ctx, "value"))))
                                .then(Commands.literal("auto").executes(NpcCommand::resetWidth)))
                        .then(Commands.literal("sword").executes(NpcCommand::giveSword))
                        .then(Commands.literal("collect")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .executes(ctx -> collect(ctx, StringArgumentType.getString(ctx, "item"), 8.0f))
                                        .then(Commands.argument("radius", FloatArgumentType.floatArg(0.5f, 64.0f))
                                                .executes(ctx -> collect(ctx, StringArgumentType.getString(ctx, "item"), FloatArgumentType.getFloat(ctx, "radius"))))))
                        .then(Commands.literal("stopcollect").executes(NpcCommand::stopCollect))
                        .then(Commands.literal("breakblock")
                                .executes(ctx -> breakBlock(ctx, true))
                                .then(Commands.argument("requireTool", BoolArgumentType.bool())
                                        .executes(ctx -> breakBlock(ctx, BoolArgumentType.getBool(ctx, "requireTool")))))
                        .then(Commands.literal("placeblock")
                                .executes(ctx -> placeBlock(ctx, "up"))
                                .then(Commands.argument("facing", StringArgumentType.word())
                                        .executes(ctx -> placeBlock(ctx, StringArgumentType.getString(ctx, "facing")))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "item"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count"))))))
                        .then(Commands.literal("weapon")
                                .then(Commands.literal("none").executes(NpcCommand::clearCombatWeapon))
                                .then(Commands.argument("item", StringArgumentType.string())
                                        .executes(ctx -> setCombatWeapon(ctx, StringArgumentType.getString(ctx, "item"), true))
                                        .then(Commands.argument("restore", BoolArgumentType.bool())
                                                .executes(ctx -> setCombatWeapon(ctx, StringArgumentType.getString(ctx, "item"), BoolArgumentType.getBool(ctx, "restore"))))))
                        .then(Commands.literal("info").executes(NpcCommand::info))
                        .then(Commands.literal("clear").executes(NpcCommand::clear))
                        .then(Commands.literal("stresstest")
                                .executes(ctx -> stressTest(ctx, 100))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                                        .executes(ctx -> stressTest(ctx, IntegerArgumentType.getInteger(ctx, "count")))))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String model, NpcBehavior behavior) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        ModelDefinition definition = ModelPhysics.resolve(model);
        if (definition == null) {
            reply(player, "No model named '" + model + "' under assets/storymodengine/storymodengine/models/.");
            return 0;
        }

        NpcEntity npc = ModEntities.NPC.get().create(level);
        if (npc == null) {
            reply(player, "Could not create the NPC.");
            return 0;
        }
        Vec3 position = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        npc.moveTo(position.x, position.y, position.z, player.getYRot() + 180f, 0f);
        npc.setModelName(model);
        npc.setBehavior(behavior);
        // Same bug NpcScriptCommands.npcSpawn had (see its own comment): with no custom name set, the
        // nameplate falls through to vanilla's default Entity.getName(), the raw, lang-file-less
        // "entity.storymodengine.npc" translation key — this command path just never got the fix
        // applied there. "NPC" matches HollowEngine's own default nameplate text (a plain literal, not
        // a translation key); /sme npc name renames it same as always.
        npc.setCustomName(Component.literal("NPC"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);

        // npc.getBbHeight(), not ModelBounds.sizeY() — the latter is the raw, unclamped mesh height,
        // which can overstate the NPC's actual hitbox (see NpcEntity.DOORWAY_SAFE_HEIGHT) and would
        // report a number that has nothing to do with what the NPC can actually walk through.
        reply(player, String.format(Locale.ROOT, "Spawned %s NPC wearing '%s' (%.2f blocks tall, %d animation(s)).",
                behavior.name().toLowerCase(Locale.ROOT), model, npc.getBbHeight(), definition.animations().size()));
        return 1;
    }

    /** Debug counterpart to the script-facing {@code npc_spawn_as} — same underlying {@code NpcScriptCommands.npcSpawnAs}, so a declared template's own attributes/model/behavior get applied identically. */
    private static int spawnAs(CommandContext<CommandSourceStack> context, String template, String instanceName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (NpcLookup.resolve(player.serverLevel(), instanceName) != null) {
            reply(player, "'" + instanceName + "' is already alive in this level.");
            return 0;
        }
        NpcScriptCommands.npcSpawnAs(player, template, instanceName);
        if (NpcLookup.resolve(player.serverLevel(), instanceName) == null) {
            reply(player, "No npc declared under '" + template + "' (or its model is missing) — see the log.");
            return 0;
        }
        reply(player, "Spawned '" + instanceName + "' from template '" + template + "'.");
        return 1;
    }

    private static int setBehavior(CommandContext<CommandSourceStack> context, String value) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setBehavior(parse(NpcBehavior.class, value, npc.behavior()));
            reply(player, "Behaviour is now " + npc.behavior());
        });
    }

    private static int setHitbox(CommandContext<CommandSourceStack> context, String value) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setHitboxMode(parse(NpcHitboxMode.class, value, npc.hitboxMode()));
            reply(player, "Hitbox is now " + npc.hitboxMode());
        });
    }

    private static int setAnimation(CommandContext<CommandSourceStack> context, String value) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            String clip = "none".equalsIgnoreCase(value) ? "" : value;
            ModelDefinition definition = ModelPhysics.resolve(npc.modelName());
            if (!clip.isEmpty() && (definition == null || definition.animation(clip) == null)) {
                reply(player, "That model has no animation '" + clip + "'. " + describeAnimations(definition));
                return;
            }
            npc.setAnimation(clip);
            reply(player, clip.isEmpty() ? "Animation cleared." : "Playing '" + clip + "'.");
        });
    }

    private static int setName(CommandContext<CommandSourceStack> context, String value) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setCustomName(Component.literal(value));
            npc.setCustomNameVisible(true);
            reply(player, "Named '" + value + "'.");
        });
    }

    /** Only takes effect for a material literally named "skin" in the model — see {@code client.model.PlayerSkinSource}. A model without one just ignores this. */
    private static int setSkin(CommandContext<CommandSourceStack> context, String value) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            String owner = "none".equalsIgnoreCase(value) ? "" : value;
            // Temporary debug aid for the face-artifact investigation: names the exact model file this
            // command is about to affect, so a real in-game report can be matched against the matching
            // "[skin-debug] ...: 'skin'-material primitive ..." import-time dump. Remove once closed.
            EngineLog.channel("Model").info("[skin-debug] /sme npc skin: entity {} model='{}' value='{}'",
                    npc.getId(), npc.modelName(), value);
            npc.setSkinOwner(owner);
            reply(player, owner.isEmpty() ? "Skin override cleared." : "Wearing '" + owner + "'s skin (only where the model has a 'skin' material).");
        });
    }

    private static int setWidth(CommandContext<CommandSourceStack> context, float value) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setBodyWidth(value);
            reply(player, String.format(Locale.ROOT, "Body width is now %.2f.", value));
        });
    }

    private static int resetWidth(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.resetBodyWidth();
            reply(player, "Body width is now auto (derived from the model).");
        });
    }

    private static int giveSword(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            reply(player, "Armed with an iron sword.");
        });
    }

    /** Bypasses the whole {@code .sme}/{@code onInteract} script layer — dev shortcut for testing {@code npc_collect_items} without needing a script trigger to re-fire it. {@code item} may be {@code "any"} for no filter. */
    private static int collect(CommandContext<CommandSourceStack> context, String item, float radius) throws CommandSyntaxException {
        String filter = "any".equalsIgnoreCase(item) ? "" : item;
        return withNearest(context, (player, npc) -> {
            npc.beginMovementChannel();
            npc.setCanPickUpLoot(true);
            npc.setCollectRadius(radius);
            npc.setCollectFilter(filter);
            npc.ensureCollectGoal();
            reply(player, "Collecting " + (filter.isEmpty() ? "any item" : filter) + " within " + radius + " blocks.");
        });
    }

    private static int stopCollect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setCollectFilter(null);
            npc.beginMovementChannel();
            reply(player, "Stopped collecting.");
        });
    }

    /** Same "bypass the .sme layer" role as {@link #collect} — targets whatever block the commanding player is looking at, so testing doesn't need a script trigger just to re-fire the command. */
    private static int breakBlock(CommandContext<CommandSourceStack> context, boolean requireTool) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = lookedAtBlock(player);
        if (pos == null) {
            reply(player, "Not looking at a block within reach.");
            return 0;
        }
        return withNearest(context, (p, npc) -> {
            npc.beginMovementChannel();
            npc.setDestroyBlockRequireTool(requireTool);
            npc.setDestroyBlockTarget(pos);
            npc.ensureDestroyBlockGoal();
            reply(p, "Breaking block at " + pos.toShortString() + " (requireCorrectTool=" + requireTool + ").");
        });
    }

    /** Places whatever the nearest NPC is actually holding in its main hand — give it something first with {@link #give}. */
    private static int placeBlock(CommandContext<CommandSourceStack> context, String facing) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = lookedAtBlock(player);
        if (pos == null) {
            reply(player, "Not looking at a block within reach.");
            return 0;
        }
        Direction direction = Direction.byName(facing);
        Direction resolvedDirection = direction != null ? direction : Direction.UP;
        return withNearest(context, (p, npc) -> {
            npc.beginMovementChannel();
            npc.setPlaceBlockFacing(resolvedDirection);
            npc.setPlaceBlockTarget(pos);
            npc.ensurePlaceBlockGoal();
            reply(p, "Walking to place a block at " + pos.toShortString() + ".");
        });
    }

    private static int give(CommandContext<CommandSourceStack> context, String itemId, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Item item = ForgeRegistries.ITEMS.getValue(id(itemId));
        if (item == null) {
            reply(player, "Unknown item id '" + itemId + "'.");
            return 0;
        }
        return withNearest(context, (p, npc) -> {
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item, count));
            reply(p, "Gave " + count + "x " + itemId + " to the npc's main hand.");
        });
    }

    private static int setCombatWeapon(CommandContext<CommandSourceStack> context, String itemId, boolean restorePrevious) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.setCombatWeapon(itemId, restorePrevious);
            reply(player, "Will draw '" + itemId + "' on target acquisition (restorePrevious=" + restorePrevious + ").");
        });
    }

    private static int clearCombatWeapon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            npc.clearCombatWeapon();
            reply(player, "Auto weapon-draw disabled.");
        });
    }

    private static BlockPos lookedAtBlock(ServerPlayer player) {
        HitResult hit = player.pick(5.0D, 1.0F, false);
        return hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : null;
    }

    /** Same bare-id-defaults-to-minecraft convention {@code NpcScriptCommands.id(...)} already uses. */
    private static ResourceLocation id(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return withNearest(context, (player, npc) -> {
            ModelDefinition definition = ModelPhysics.resolve(npc.modelName());
            reply(player, String.format(Locale.ROOT,
                    "model=%s behavior=%s hitbox=%s animation=%s skin=%s size=%.2fx%.2f | %s",
                    npc.modelName().isEmpty() ? "(none)" : npc.modelName(),
                    npc.behavior(), npc.hitboxMode(),
                    npc.animation().isEmpty() ? "(none)" : npc.animation(),
                    npc.skinOwner().isEmpty() ? "(none)" : npc.skinOwner(),
                    npc.getBbWidth(), npc.getBbHeight(),
                    describeAnimations(definition)));
        });
    }

    private static String describeAnimations(ModelDefinition definition) {
        if (definition == null || definition.animations().isEmpty()) {
            return "model has no animation clips";
        }
        StringBuilder names = new StringBuilder("clips: ");
        for (AnimationClip clip : definition.animations()) {
            if (names.length() > 7) {
                names.append(", ");
            }
            names.append(clip.name());
        }
        return names.toString();
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<NpcEntity> found = player.serverLevel().getEntities(ModEntities.NPC.get(),
                player.getBoundingBox().inflate(32), npc -> true);
        found.forEach(NpcEntity::discard);
        reply(player, "Removed " + found.size() + " NPC(s) within 32 blocks.");
        return found.size();
    }

    /**
     * Debug counterpart to {@code npc_spawn_stress_test} — see {@link NpcStressTestCommands}' own doc
     * for why behaviour is fixed at {@code STATIONARY} (isolating render/animation cost from AI-goal-
     * tick cost, a separate question this tool doesn't answer). {@code /sme npc clear} already removes
     * everything spawned here too (it clears every NPC within 32 blocks, and a stress-test grid is
     * always well inside that), so there's no dedicated debug "stressclear".
     */
    private static int stressTest(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long startNanos = System.nanoTime();
        int spawned = NpcStressTestCommands.spawnGrid(player, count);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        reply(player, String.format(Locale.ROOT,
                "Spawned %d/%d stationary NPC(s) wearing '%s' in %d ms — %s. Use /sme npc clear to remove them.",
                spawned, count, NpcStressTestCommands.MODEL_NAME, elapsedMs, NpcStressTestCommands.describeClips()));
        return spawned;
    }

    @FunctionalInterface
    private interface NpcAction {
        void run(ServerPlayer player, NpcEntity npc);
    }

    /** Every configuration subcommand acts on the closest NPC, so there is nothing to select or name first. */
    private static int withNearest(CommandContext<CommandSourceStack> context, NpcAction action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AABB area = player.getBoundingBox().inflate(NEAREST_SEARCH_RADIUS);
        NpcEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (NpcEntity npc : player.serverLevel().getEntities(ModEntities.NPC.get(), area, e -> true)) {
            double distance = npc.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                nearest = npc;
            }
        }
        if (nearest == null) {
            reply(player, "No NPC within " + (int) NEAREST_SEARCH_RADIUS + " blocks.");
            return 0;
        }
        action.run(player, nearest);
        return 1;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static void reply(ServerPlayer player, String text) {
        EngineLog.channel("NPC").info(text).toChat(player);
    }
}
