package com.dimalab.storymodengine.common.math.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.api.math.Angles;
import com.dimalab.storymodengine.api.math.curve.CatmullRomSpline;
import com.dimalab.storymodengine.api.math.interp.Easing;
import com.dimalab.storymodengine.api.math.interp.Interpolators;
import com.dimalab.storymodengine.api.math.transform.Transform;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /storymodengine campath} — moves the executing player along a closed
 * {@link CatmullRomSpline}{@code <Transform>} loop built around their current position, as a
 * concrete, in-game-testable demonstration of the {@code math} foundation: see
 * {@link CameraPathAnimator} for how the spline is actually walked tick by tick.
 */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID)
public final class CameraPathCommand {

    private static final int WAYPOINT_COUNT = 6;
    private static final float RADIUS = 6f;
    private static final float HEIGHT_VARIATION = 2.5f;
    private static final int DURATION_TICKS = 100;

    private CameraPathCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("storymodengine")
                .then(Commands.literal("campath")
                        .executes(CameraPathCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        Vector3f origin = new Vector3f((float) player.getX(), (float) player.getY(), (float) player.getZ());
        List<Transform> waypoints = buildLoop(origin, player.getYRot());

        CatmullRomSpline<Transform> path = CatmullRomSpline.centripetal(
                Interpolators.TRANSFORM,
                (a, b) -> a.translation().distance(b.translation()),
                waypoints);

        CameraPathAnimator.start(player, path, DURATION_TICKS, Easing.EASE_IN_OUT_CUBIC);
        context.getSource().sendSuccess(() -> Component.literal(
                "[StoryModEngine] Following a " + waypoints.size() + "-waypoint camera path (centripetal Catmull-Rom, arc-length constant speed)..."), false);
        return 1;
    }

    /**
     * A closed loop of waypoints around {@code center}, each carrying both a position and a
     * rotation — that pairing is what makes {@code CatmullRomSpline<Transform>} meaningful rather
     * than just a {@code CatmullRomSpline<Vector3f>} with rotation bolted on separately.
     */
    private static List<Transform> buildLoop(Vector3f origin, float baseYawDegrees) {
        Vector3f center = new Vector3f(origin).add(0f, 3f, 0f);
        List<Transform> waypoints = new ArrayList<>(WAYPOINT_COUNT + 1);
        for (int i = 0; i <= WAYPOINT_COUNT; i++) {
            double angle = i * 2 * Math.PI / WAYPOINT_COUNT;
            float x = center.x + RADIUS * (float) Math.sin(angle);
            float z = center.z + RADIUS * (float) Math.cos(angle);
            float y = center.y + HEIGHT_VARIATION * (float) Math.sin(angle * 2);
            Vector3f position = new Vector3f(x, y, z);

            float yawDegrees = baseYawDegrees + (float) Math.toDegrees(angle);
            float pitchDegrees = -15f * (float) Math.cos(angle);
            Quaternionf rotation = Angles.fromYawPitch(yawDegrees, pitchDegrees);

            waypoints.add(Transform.of(position, rotation, new Vector3f(1f, 1f, 1f)));
        }
        return waypoints;
    }
}
