package com.dimalab.storymodengine.common.voxel.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.logging.EngineLog;
import com.dimalab.storymodengine.common.voxel.ModelShapes;
import com.dimalab.storymodengine.common.voxel.ShapeDefinition;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /sme voxel demo} — places one {@link ExampleMachineBlock} per horizontal
 * facing near the player so the model-derived, rotated outline is directly comparable in-world
 * (the offset "vent" cuboid visibly moves with each facing). {@code /sme voxel test} —
 * the diagnostic coverage this package doesn't otherwise have a JUnit harness for (see
 * {@code ARCHITECTURE.md}): single/multiple boxes, model-unit conversion, empty definitions,
 * per-direction rotation, cache reuse, malformed-model handling, and parse determinism.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class VoxelDemoCommand {

    private static final ResourceLocation MACHINE_MODEL = new ResourceLocation("storymodengine", "block/example_machine");
    private static final ResourceLocation MISSING_MODEL = new ResourceLocation("storymodengine", "block/does_not_exist_voxel_test");

    private VoxelDemoCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("voxel")
                        .then(Commands.literal("demo").executes(VoxelDemoCommand::demo))
                        .then(Commands.literal("test").executes(VoxelDemoCommand::test))));
    }

    private static int demo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < facings.length; i++) {
            BlockPos pos = origin.relative(Direction.EAST, 2 * (i + 1));
            level.setBlockAndUpdate(pos, VoxelExampleContent.EXAMPLE_MACHINE.get()
                    .defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facings[i]));
        }
        EngineLog.channel("Voxel").success(
                "Placed 4 example machines (NORTH/EAST/SOUTH/WEST) east of you — compare their outlines.").toChat(player);
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> results = new ArrayList<>();

        ShapeDefinition single = ShapeDefinition.builder().box(0, 0, 0, 16, 16, 16).build();
        check(results, "single box", single.boxes().size() == 1);

        ShapeDefinition multi = ShapeDefinition.builder().box(0, 0, 0, 16, 8, 16).box(2, 8, 2, 14, 16, 14).build();
        check(results, "multiple boxes", multi.boxes().size() == 2);

        var box = multi.boxes().get(0);
        check(results, "coordinate conversion (16 -> 1.0, 8 -> 0.5)",
                box.minX() == 0 && box.minY() == 0 && box.minZ() == 0
                        && box.maxX() == 1.0 && box.maxY() == 0.5 && box.maxZ() == 1.0);

        ShapeDefinition empty = ShapeDefinition.builder().build();
        check(results, "empty definition", empty == ShapeDefinition.EMPTY && empty.toVoxelShape().isEmpty());

        ModelShapes.clearCache();
        ShapeDefinition machineNorth = ModelShapes.load(MACHINE_MODEL);
        check(results, "machine model has 2 elements (base + vent)", machineNorth.boxes().size() == 2);

        var north = machineNorth.rotated(Direction.NORTH).boxes();
        var east = machineNorth.rotated(Direction.EAST).boxes();
        var south = machineNorth.rotated(Direction.SOUTH).boxes();
        var west = machineNorth.rotated(Direction.WEST).boxes();
        check(results, "rotation NORTH/EAST/SOUTH/WEST all distinct",
                !north.equals(east) && !east.equals(south) && !south.equals(west) && !west.equals(north));
        check(results, "rotation NORTH is identity", north.equals(machineNorth.boxes()));

        VoxelShape shapeA = ModelShapes.get(MACHINE_MODEL, Direction.EAST);
        VoxelShape shapeB = ModelShapes.get(MACHINE_MODEL, Direction.EAST);
        check(results, "cache reuse (same VoxelShape instance)", shapeA == shapeB);

        ShapeDefinition missing = ModelShapes.load(MISSING_MODEL);
        check(results, "malformed/missing model returns EMPTY without throwing", missing == ShapeDefinition.EMPTY);

        ModelShapes.clearCache();
        List<?> firstParse = ModelShapes.load(MACHINE_MODEL).boxes();
        ModelShapes.clearCache();
        List<?> secondParse = ModelShapes.load(MACHINE_MODEL).boxes();
        check(results, "deterministic output across independent parses", firstParse.equals(secondParse));

        long passed = results.stream().filter(line -> line.startsWith("PASS")).count();
        for (String line : results) {
            EngineLog.channel("Voxel").info(line);
        }
        EngineLog.channel("Voxel").success(
                passed + "/" + results.size() + " checks passed (see console for detail).").toChat(player);
        return 1;
    }

    private static void check(List<String> results, String name, boolean condition) {
        results.add((condition ? "PASS" : "FAIL") + ": " + name);
    }
}
