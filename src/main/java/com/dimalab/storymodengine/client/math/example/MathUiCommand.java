package com.dimalab.storymodengine.client.math.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /sme mathui} — opens {@link EasingDemoScreen}. Client-only, like the screen it opens. */
@Mod.EventBusSubscriber(modid = StoryModEngine.MODID, value = Dist.CLIENT)
public final class MathUiCommand {

    private MathUiCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sme")
                .then(Commands.literal("mathui")
                        .executes(context -> {
                            Minecraft.getInstance().setScreen(new EasingDemoScreen());
                            return 1;
                        })));
    }
}
