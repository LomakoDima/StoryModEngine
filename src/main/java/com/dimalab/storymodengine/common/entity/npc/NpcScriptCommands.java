package com.dimalab.storymodengine.common.entity.npc;

import com.dimalab.storymodengine.api.entity.NpcAnimationMode;
import com.dimalab.storymodengine.api.scripting.annotation.StoryCommand;
import com.dimalab.storymodengine.common.entity.ModEntities;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.dimalab.storymodengine.common.scripting.ast.NpcAttributeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code npc_*} — every script action an {@code .sme} story can take on a story NPC, resolved by the
 * name its {@code npc { }} declaration gave it (see {@code NpcLookup}). Same shape and discipline as
 * {@code scripting.command.BuiltinStoryCommands}: plain {@code @StoryCommand}-annotated static
 * methods, first parameter always {@code ServerPlayer} (required by {@code StoryCommandDiscovery}),
 * second parameter always the npc's own name — a bad name or a missing npc costs that one command
 * call, logged, never a thrown exception.
 */
public final class NpcScriptCommands {

    private NpcScriptCommands() {
    }

    // ---- lifecycle ----

    @StoryCommand("npc_spawn")
    public static void npcSpawn(ServerPlayer player, String name) {
        spawnFromTemplate(player, name, name);
    }

    /**
     * The other half of "reusable NPC templates" (see {@code NpcDeclNode#attributes()}'s own doc): a
     * declared {@code npc "dragon_boss" { }} is a template, not a single living character — {@code
     * npc_spawn} always treated "declared name" and "the one live instance" as the same string, which
     * meant a second {@code npc_spawn "dragon_boss"} was always refused while the first was alive
     * ({@code spawnFromTemplate}'s own duplicate-name check below). This just lets a script pick a
     * distinct {@code instanceName} per spawn instead — every other {@code npc_*} command already keys
     * off that name via {@code NpcLookup}/{@code scriptName()}, so two instances spawned this way are
     * exactly as independently addressable as two separately-declared NPCs would be.
     */
    @StoryCommand("npc_spawn_as")
    public static void npcSpawnAs(ServerPlayer player, String templateName, String instanceName) {
        spawnFromTemplate(player, templateName, instanceName);
    }

    private static void spawnFromTemplate(ServerPlayer player, String templateName, String instanceName) {
        ServerLevel level = player.serverLevel();
        if (NpcLookup.resolve(level, instanceName) != null) {
            EngineLog.channel("Npc").warn("npc_spawn: '{}' is already alive in this level — not spawning a duplicate", instanceName);
            return;
        }
        NpcDefinition definition = NpcDefinitionRegistry.get(templateName);
        if (definition == null) {
            EngineLog.channel("Npc").warn("npc_spawn: no npc declared under the name '{}'", templateName);
            return;
        }
        ModelDefinition model = ModelPhysics.resolve(definition.model());
        if (model == null) {
            EngineLog.channel("Npc").warn("npc_spawn: '{}': no model named '{}'", templateName, definition.model());
            return;
        }
        NpcEntity npc = ModEntities.NPC.get().create(level);
        if (npc == null) {
            EngineLog.channel("Npc").error("npc_spawn: '{}': could not create the entity", templateName);
            return;
        }
        Vec3 pos = definition.pos();
        npc.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
        npc.setModelName(definition.model());
        npc.setBehavior(definition.behavior());
        npc.setScriptName(instanceName);
        // Without this every script-spawned NPC fell through to vanilla's default Entity.getName(),
        // which resolves to the raw, lang-file-less "entity.storymodengine.npc" translation key — a
        // real bug, not a missing feature (the admin /sme npc name command already did this correctly).
        npc.setCustomName(Component.literal(definition.displayName()));
        npc.setCustomNameVisible(true);
        if (definition.skin() != null) {
            npc.setSkinOwner(definition.skin());
        }
        for (NpcAttributeSpec spec : definition.attributes()) {
            applyAttribute(npc, spec.attributeId(), spec.value(), "npc_spawn");
        }
        level.addFreshEntity(npc);
        NpcLookup.register(level, instanceName, npc.getUUID());
    }

    @StoryCommand("npc_despawn")
    public static void npcDespawn(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_despawn");
        if (npc == null) {
            return;
        }
        NpcLookup.unregister(player.serverLevel(), name);
        npc.discard();
    }

    // ---- movement ----

    /**
     * When called from a {@code sequence}/{@code trigger} body, {@code SequenceCompiler} compiles
     * this call specially (see {@code compileAwaitableMoveTo}) into a suspending {@code Flow} step
     * that waits for arrival before the script continues — this method itself stays exactly what it
     * was: fire off the move and return. From a dialogue action/choice body (which can't suspend at
     * all) it's still plain fire-and-forget, unchanged.
     */
    @StoryCommand("npc_move_to")
    public static void npcMoveTo(ServerPlayer player, String name, double x, double y, double z) {
        NpcEntity npc = resolve(player, name, "npc_move_to");
        if (npc == null) {
            return;
        }
        npc.beginMovementChannel();
        npc.getNavigation().moveTo(x, y, z, 1.0D);
        npc.markScriptNavigationActive();
    }

    @StoryCommand("npc_follow_player")
    public static void npcFollowPlayer(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_follow_player");
        if (npc == null) {
            return;
        }
        npc.beginMovementChannel();
        npc.setFollowTarget(player.getUUID());
        npc.ensureFollowGoal();
    }

    @StoryCommand("npc_stop_move")
    public static void npcStopMove(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_stop_move");
        if (npc == null) {
            return;
        }
        npc.setFollowTarget(null);
        npc.beginMovementChannel();
    }

    // ---- look ----
    // Already a channel: setLookOverridePlayer/setLookOverridePos (NpcEntity.java) each null the
    // other's field, so a new look command always fully supersedes whichever one was active — no
    // separate cancel step needed here, unlike movement.

    @StoryCommand("npc_look_at_player")
    public static void npcLookAtPlayer(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_look_at_player");
        if (npc == null) {
            return;
        }
        npc.setLookOverridePlayer(player.getUUID());
    }

    @StoryCommand("npc_look_at_pos")
    public static void npcLookAtPos(ServerPlayer player, String name, double x, double y, double z) {
        NpcEntity npc = resolve(player, name, "npc_look_at_pos");
        if (npc == null) {
            return;
        }
        npc.setLookOverridePos(new Vec3(x, y, z));
    }

    @StoryCommand("npc_stop_look")
    public static void npcStopLook(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_stop_look");
        if (npc == null) {
            return;
        }
        npc.clearLookOverride();
    }

    // ---- identity / attributes ----

    @StoryCommand("npc_set_name")
    public static void npcSetName(ServerPlayer player, String name, String displayName) {
        NpcEntity npc = resolve(player, name, "npc_set_name");
        if (npc == null) {
            return;
        }
        npc.setCustomName(Component.literal(displayName));
        npc.setCustomNameVisible(true);
    }

    /** Generic by-registry-id setter — mirrors HollowEngine's own {@code setAttributes}: base value only, no fixed property list, works for anything {@link ForgeRegistries#ATTRIBUTES} knows about. */
    @StoryCommand("npc_set_attribute")
    public static void npcSetAttribute(ServerPlayer player, String name, String attributeId, double value) {
        NpcEntity npc = resolve(player, name, "npc_set_attribute");
        if (npc == null) {
            return;
        }
        applyAttribute(npc, attributeId, value, "npc_set_attribute");
    }

    /** Shared by {@code npc_set_attribute} above and {@code spawnFromTemplate}'s bulk apply of a template's own declared {@code attribute} lines — same resolve-and-set logic either way. */
    private static void applyAttribute(NpcEntity npc, String attributeId, double value, String command) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(id(attributeId));
        if (attribute == null) {
            EngineLog.channel("Npc").warn("{}: unknown attribute id '{}'", command, attributeId);
            return;
        }
        AttributeInstance instance = npc.getAttribute(attribute);
        if (instance == null) {
            EngineLog.channel("Npc").warn("{}: '{}' is not a valid attribute for an npc", command, attributeId);
            return;
        }
        instance.setBaseValue(value);
    }

    // ---- teleport ----

    @StoryCommand("npc_teleport")
    public static void npcTeleport(ServerPlayer player, String name, double x, double y, double z) {
        NpcEntity npc = resolve(player, name, "npc_teleport");
        if (npc == null) {
            return;
        }
        // A path/follow target computed for the old position makes no sense after the jump — same
        // reasoning as every other movement command, this one was just missing it.
        npc.beginMovementChannel();
        npc.teleportTo(x, y, z);
    }

    @StoryCommand("npc_teleport_rotated")
    public static void npcTeleportRotated(ServerPlayer player, String name, double x, double y, double z, double yaw, double pitch) {
        NpcEntity npc = resolve(player, name, "npc_teleport_rotated");
        if (npc == null) {
            return;
        }
        npc.beginMovementChannel();
        npc.teleportTo(x, y, z);
        npc.setYRot((float) yaw);
        npc.setXRot((float) pitch);
        npc.setYHeadRot((float) yaw);
        npc.yBodyRot = (float) yaw;
    }

    @StoryCommand("npc_teleport_dimension")
    public static void npcTeleportDimension(ServerPlayer player, String name, String dimensionId, double x, double y, double z) {
        NpcEntity npc = resolve(player, name, "npc_teleport_dimension");
        if (npc == null) {
            return;
        }
        ResourceLocation dimensionRl = ResourceLocation.tryParse(dimensionId);
        if (dimensionRl == null) {
            EngineLog.channel("Npc").warn("npc_teleport_dimension: '{}' is not a valid dimension id", dimensionId);
            return;
        }
        ServerLevel targetLevel = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionRl));
        if (targetLevel == null) {
            EngineLog.channel("Npc").warn("npc_teleport_dimension: no loaded dimension '{}'", dimensionId);
            return;
        }
        // changeDimension returns a NEW entity instance and discards npc — the name index must follow
        // the new instance, or every later npc_* command for this name would silently stop resolving.
        ServerLevel sourceLevel = player.serverLevel();
        Entity moved = npc.changeDimension(targetLevel);
        if (moved instanceof NpcEntity movedNpc) {
            NpcLookup.unregister(sourceLevel, name);
            NpcLookup.register(targetLevel, name, movedNpc.getUUID());
            movedNpc.teleportTo(x, y, z);
        } else {
            EngineLog.channel("Npc").error("npc_teleport_dimension: '{}': dimension change did not return the moved entity", name);
        }
    }

    // ---- items ----

    @StoryCommand("npc_give_main_hand")
    public static void npcGiveMainHand(ServerPlayer player, String name, String itemId, int count) {
        giveHand(player, name, itemId, count, EquipmentSlot.MAINHAND);
    }

    @StoryCommand("npc_give_off_hand")
    public static void npcGiveOffHand(ServerPlayer player, String name, String itemId, int count) {
        giveHand(player, name, itemId, count, EquipmentSlot.OFFHAND);
    }

    private static void giveHand(ServerPlayer player, String name, String itemId, int count, EquipmentSlot slot) {
        NpcEntity npc = resolve(player, name, "npc_give_" + slot.getName());
        if (npc == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id(itemId));
        if (item == null) {
            EngineLog.channel("Npc").warn("npc_give_{}: unknown item id '{}'", slot.getName(), itemId);
            return;
        }
        npc.setItemSlot(slot, new ItemStack(item, count));
    }

    /** "From this NPC," narratively — mechanically the same robust give/drop-on-overflow {@code give} already does for the player-only case. */
    @StoryCommand("npc_drop_item")
    public static void npcDropItem(ServerPlayer player, String name, String itemId, int count) {
        NpcEntity npc = resolve(player, name, "npc_drop_item");
        if (npc == null) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id(itemId));
        if (item == null) {
            EngineLog.channel("Npc").warn("npc_drop_item: unknown item id '{}'", itemId);
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---- blocks ----

    // double, not int — floored below via BlockPos.containing. A block position is naturally
    // integer, but every other coordinate-taking command here (npc_move_to/npc_teleport/...) takes
    // a double, and a script author's most natural source of a position is a player's own fractional
    // F3 coordinates — an int-only version would reject those with a confusing type error instead of
    // just floor-and-go the way vanilla's own block-position code always does.
    @StoryCommand("npc_destroy_block")
    public static void npcDestroyBlock(ServerPlayer player, String name, double x, double y, double z) {
        NpcEntity npc = resolve(player, name, "npc_destroy_block");
        if (npc == null) {
            return;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        // destroyBlock returns false and does nothing at all when the target is already air (verified
        // against the decompiled source — no other failure mode exists in that method) — silent
        // otherwise, so this is the one place worth logging: a wrong/rounded position is the most
        // likely reason a script author sees "nothing happened."
        if (!player.serverLevel().destroyBlock(pos, true, npc, Block.UPDATE_ALL)) {
            EngineLog.channel("Npc").warn("npc_destroy_block: nothing at {} (already air) — nothing to destroy", pos);
        }
    }

    /**
     * Deliberately scoped to lever/button/door — vanilla's generic {@code BlockState.use} wants a
     * real {@code Player}, but none of these three concrete blocks actually need one for the toggle
     * itself (verified against the decompiled source), so a non-player mob calls the block-specific
     * toggle directly rather than faking a player. Any other block type logs and no-ops.
     */
    @StoryCommand("npc_use_block")
    public static void npcUseBlock(ServerPlayer player, String name, double x, double y, double z) {
        NpcEntity npc = resolve(player, name, "npc_use_block");
        if (npc == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof LeverBlock lever) {
            lever.pull(state, level, pos);
        } else if (block instanceof ButtonBlock button) {
            button.press(state, level, pos);
        } else if (block instanceof DoorBlock door) {
            door.setOpen(npc, level, state, pos, !state.getValue(DoorBlock.OPEN));
        } else {
            EngineLog.channel("Npc").warn("npc_use_block: block at {} is not a lever/button/door — unsupported", pos);
        }
    }

    /**
     * The real, tool-aware sibling of {@code npc_destroy_block} above — that one stays instant and
     * always-succeeds (legitimate for a scripted "wall crumbles on cue" beat), this one walks to the
     * block and breaks it through actual vanilla destroy-progress physics (see {@code
     * NpcDestroyBlockGoal}), respecting {@code requireCorrectTool} as a pre-flight-only gate exactly
     * like HollowEngine's own {@code startDestroyBlock(requireCorrectTool = true)}.
     */
    @StoryCommand("npc_break_block")
    public static void npcBreakBlock(ServerPlayer player, String name, double x, double y, double z, boolean requireCorrectTool) {
        NpcEntity npc = resolve(player, name, "npc_break_block");
        if (npc == null) {
            return;
        }
        npc.beginMovementChannel();
        npc.setDestroyBlockRequireTool(requireCorrectTool);
        npc.setDestroyBlockTarget(BlockPos.containing(x, y, z));
        npc.ensureDestroyBlockGoal();
    }

    /**
     * Walks the NPC to the target the same way {@code npc_break_block} does, then places whatever
     * block item is actually in its main hand — not an {@code itemId} parameter that would silently
     * swap the hand's contents for the placement. A script that wants a specific block placed gives it
     * to the NPC first ({@code npc_give_main_hand}), the same "the NPC acts with what it's actually
     * holding" rule {@code npc_set_combat_weapon} already follows for weapons. See {@code
     * NpcPlaceBlockGoal} for the walk/animate/place sequence and how the placed item is actually
     * consumed from the hand stack.
     */
    @StoryCommand("npc_place_block")
    public static void npcPlaceBlock(ServerPlayer player, String name, double x, double y, double z, String facing) {
        NpcEntity npc = resolve(player, name, "npc_place_block");
        if (npc == null) {
            return;
        }
        Direction direction = facing.isEmpty() ? Direction.UP : Direction.byName(facing);
        if (direction == null) {
            EngineLog.channel("Npc").warn("npc_place_block: unknown facing '{}'", facing);
            return;
        }
        npc.beginMovementChannel();
        npc.setPlaceBlockFacing(direction);
        npc.setPlaceBlockTarget(BlockPos.containing(x, y, z));
        npc.ensurePlaceBlockGoal();
    }

    // ---- scripted combat weapon (npc_set_combat_weapon / npc_clear_combat_weapon) ----

    @StoryCommand("npc_set_combat_weapon")
    public static void npcSetCombatWeapon(ServerPlayer player, String name, String itemId, boolean restorePrevious) {
        NpcEntity npc = resolve(player, name, "npc_set_combat_weapon");
        if (npc == null) {
            return;
        }
        npc.setCombatWeapon(itemId, restorePrevious);
    }

    /** Turns the auto-draw behavior off going forward — does not undo a weapon already drawn, same "turns the behavior off, doesn't undo its effect" precedent as {@code npc_stop_collect}. */
    @StoryCommand("npc_clear_combat_weapon")
    public static void npcClearCombatWeapon(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_clear_combat_weapon");
        if (npc == null) {
            return;
        }
        npc.clearCombatWeapon();
    }

    // ---- combat target ----

    @StoryCommand("npc_set_target")
    public static void npcSetTarget(ServerPlayer player, String name, String targetPlayerName) {
        NpcEntity npc = resolve(player, name, "npc_set_target");
        if (npc == null) {
            return;
        }
        LivingEntity target = targetPlayerName.isEmpty() ? player : player.getServer().getPlayerList().getPlayerByName(targetPlayerName);
        if (target == null) {
            EngineLog.channel("Npc").warn("npc_set_target: no online player named '{}'", targetPlayerName);
            return;
        }
        // Without this, a STATIONARY npc stores the target and does nothing — no goal reads the field.
        npc.ensureAttackGoal();
        npc.setTarget(target);
    }

    @StoryCommand("npc_clear_target")
    public static void npcClearTarget(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_clear_target");
        if (npc == null) {
            return;
        }
        npc.setTarget(null);
    }

    /** The entire {@code requestItems} feature: vanilla's own {@code Mob.aiStep()} already scans for and picks up nearby dropped items every tick once this is true — no custom goal needed. */
    @StoryCommand("npc_set_pickup")
    public static void npcSetPickup(ServerPlayer player, String name, boolean enabled) {
        NpcEntity npc = resolve(player, name, "npc_set_pickup");
        if (npc == null) {
            return;
        }
        npc.setCanPickUpLoot(enabled);
    }

    // ---- active item collection (npc_collect_items / npc_stop_collect) ----
    // A separate channel-aware upgrade from npc_set_pickup above: that one only enables vanilla's
    // passive touch-based pickup, this one actively seeks a matching dropped item within radius and
    // walks to it (see NpcCollectItemsGoal), reserving it via ItemClaims so two NPCs never converge
    // on the same stack. Pickup itself still happens passively once close enough — auto-enabling it
    // here means a script author doesn't also have to remember npc_set_pickup.

    @StoryCommand("npc_collect_items")
    public static void npcCollectItems(ServerPlayer player, String name, String itemId, double radius) {
        NpcEntity npc = resolve(player, name, "npc_collect_items");
        if (npc == null) {
            return;
        }
        npc.beginMovementChannel();
        npc.setCanPickUpLoot(true);
        npc.setCollectRadius(radius);
        npc.setCollectFilter(itemId);
        npc.ensureCollectGoal();
    }

    @StoryCommand("npc_stop_collect")
    public static void npcStopCollect(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_stop_collect");
        if (npc == null) {
            return;
        }
        npc.setCollectFilter(null);
        npc.beginMovementChannel();
    }

    // ---- animation ----
    // Already a channel too: npc_play_once/npc_play_freeze/npc_play_looped/npc_stop_animation all
    // write the same ANIMATION/ANIMATION_MODE synced fields (see playClip below), so a new one always
    // fully replaces whatever clip was playing.

    @StoryCommand("npc_play_once")
    public static void npcPlayOnce(ServerPlayer player, String name, String clip) {
        playClip(player, name, "npc_play_once", clip, NpcAnimationMode.ONCE);
    }

    @StoryCommand("npc_play_freeze")
    public static void npcPlayFreeze(ServerPlayer player, String name, String clip) {
        playClip(player, name, "npc_play_freeze", clip, NpcAnimationMode.FREEZE);
    }

    @StoryCommand("npc_play_looped")
    public static void npcPlayLooped(ServerPlayer player, String name, String clip) {
        playClip(player, name, "npc_play_looped", clip, NpcAnimationMode.LOOP);
    }

    @StoryCommand("npc_stop_animation")
    public static void npcStopAnimation(ServerPlayer player, String name) {
        NpcEntity npc = resolve(player, name, "npc_stop_animation");
        if (npc == null) {
            return;
        }
        npc.setAnimation("");
    }

    private static void playClip(ServerPlayer player, String name, String command, String clip, NpcAnimationMode mode) {
        NpcEntity npc = resolve(player, name, command);
        if (npc == null) {
            return;
        }
        ModelDefinition definition = ModelPhysics.resolve(npc.modelName());
        if (definition == null || definition.animation(clip) == null) {
            EngineLog.channel("Npc").warn("{}: '{}' has no animation named '{}'", command, name, clip);
            return;
        }
        npc.setAnimationMode(mode);
        npc.setAnimation(clip);
    }

    // ---- helpers ----

    private static ResourceLocation id(String raw) {
        return raw.contains(":") ? new ResourceLocation(raw) : new ResourceLocation("minecraft", raw);
    }

    private static NpcEntity resolve(ServerPlayer player, String name, String command) {
        NpcEntity npc = NpcLookup.resolve(player.serverLevel(), name);
        if (npc == null) {
            EngineLog.channel("Npc").warn("{}: no live npc named '{}' in this level", command, name);
        }
        return npc;
    }
}
