package com.dimalab.storymodengine.common.model.debug;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.entity.ModEntities;
import com.dimalab.storymodengine.common.entity.custom.ModelEntity;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.model.ModelDefinition;
import com.dimalab.storymodengine.common.model.block.ModelBlock;
import com.dimalab.storymodengine.common.model.block.ModelBlockEntity;
import com.dimalab.storymodengine.common.model.block.ModelBlocks;
import com.dimalab.storymodengine.common.model.physics.ModelBounds;
import com.dimalab.storymodengine.common.model.physics.ModelPhysics;
import com.dimalab.storymodengine.common.voxel.ShapeDefinition;
import net.minecraft.core.BlockPos;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@code /sme prop spawn|clear|info} — the <b>physical</b> side of the model system, and
 * deliberately a server command (unlike {@code /sme model ...}, which is client-side
 * asset debugging): an entity only exists if the server spawns it, and collision is only real if the
 * server agrees to it.
 *
 * <p>Named {@code prop} rather than sharing the {@code model} subtree so the client and server
 * command dispatchers never have to disambiguate the same literal path.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class PropCommand {

    private PropCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("prop")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> spawn(context, StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> place(context, StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> info(context, StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("clear").executes(PropCommand::clear))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        ModelDefinition definition = ModelPhysics.resolve(name);
        if (definition == null) {
            reply(player, "No model named '" + name + "' — it must exist at assets/storymodengine/storymodengine/models/" + name + ".gltf (or .glb) inside the mod.");
            return 0;
        }

        ModelEntity prop = ModEntities.MODEL.get().create(level);
        if (prop == null) {
            reply(player, "Could not create the prop entity.");
            return 0;
        }
        Vec3 position = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        prop.moveTo(position.x, position.y, position.z, player.getYRot() + 180f, 0f);
        prop.setModelName(name);
        level.addFreshEntity(prop);

        ModelBounds bounds = ModelPhysics.bounds(name);
        reply(player, String.format("Spawned '%s' — solid, hitbox %.2f x %.2f blocks. Walk into it.",
                name, Math.max(bounds.sizeX(), bounds.sizeZ()), bounds.sizeY()));
        return 1;
    }

    /**
     * Places the model <b>block</b> — the shaped-collision path. Unlike the entity, a block gets a
     * full multi-box {@code VoxelShape} derived from the mesh, so you collide with the model's actual
     * silhouette rather than a bounding box.
     */
    private static int place(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        if (ModelPhysics.resolve(name) == null) {
            reply(player, "No model named '" + name + "'.");
            return 0;
        }

        BlockPos pos = BlockPos.containing(player.position().add(player.getLookAngle().multiply(3, 0, 3)));
        while (pos.getY() > level.getMinBuildHeight() && level.getBlockState(pos.below()).isAir()) {
            pos = pos.below();
        }

        level.setBlockAndUpdate(pos, ModelBlocks.MODEL_BLOCK.get().defaultBlockState()
                .setValue(ModelBlock.FACING, player.getDirection().getOpposite()));
        if (level.getBlockEntity(pos) instanceof ModelBlockEntity blockEntity) {
            blockEntity.setModelName(name);
        }

        ShapeDefinition shape = ModelPhysics.blockShape(name);
        reply(player, String.format("Placed '%s' at %s — shaped collision, %d box(es). Try walking into it.",
                name, pos.toShortString(), shape.boxes().size()));
        return 1;
    }

    /** Reports what the physics layer derived for a model, without spawning anything — the fast way to check a hitbox or collision shape is sane. */
    private static int info(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ModelDefinition definition = ModelPhysics.resolve(name);
        if (definition == null) {
            reply(player, "No model named '" + name + "'.");
            return 0;
        }
        ModelBounds bounds = ModelPhysics.bounds(name);
        ShapeDefinition shape = ModelPhysics.blockShape(name);
        reply(player, String.format("'%s': bounds %.2f x %.2f x %.2f blocks; entity hitbox %.2f x %.2f; block collision %d box(es).",
                name, bounds.sizeX(), bounds.sizeY(), bounds.sizeZ(),
                Math.max(bounds.sizeX(), bounds.sizeZ()), bounds.sizeY(),
                shape.boxes().size()));
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AABB area = player.getBoundingBox().inflate(32);
        List<ModelEntity> props = player.serverLevel().getEntities(ModEntities.MODEL.get(), area, e -> true);
        for (ModelEntity prop : props) {
            prop.discard();
        }
        reply(player, "Removed " + props.size() + " prop(s) within 32 blocks.");
        return props.size();
    }

    private static void reply(ServerPlayer player, String text) {
        EngineLog.channel("Model").info(text).toChat(player);
    }
}
