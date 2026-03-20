package com.dimalab.storymodengine;

import com.dimalab.storymodengine.logging.SmeLog;
import com.dimalab.storymodengine.logging.LogSubsystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(StoryModEngine.MODID)
public class StoryModEngine {
    public static final String MODID = "storymodengine";


    public StoryModEngine() {
        SmeLog.info(LogSubsystem.LIFECYCLE, "Constructing mod instance: {}", MODID);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        SmeLog.debug(LogSubsystem.CONFIG, "Registered COMMON config spec");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        SmeLog.info(LogSubsystem.LIFECYCLE, "Common setup");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        SmeLog.info(LogSubsystem.LIFECYCLE, "Server starting");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            SmeLog.info(LogSubsystem.LIFECYCLE, "Client setup");
        }
    }
}
