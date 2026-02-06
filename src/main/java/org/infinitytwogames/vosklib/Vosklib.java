package org.infinitytwogames.vosklib;

import com.mojang.logging.LogUtils;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.ModLoadingWarning;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.infinitytwogames.vosklib.data.Config;
import org.infinitytwogames.vosklib.data.DataLoader;
import org.infinitytwogames.vosklib.screen.ConfigScreen;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Vosklib.MODID)
public class Vosklib {
    public static final String MODID = "vosklib";
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public Vosklib() {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC, "vosklib-common.toml");
        
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, lastScreen) -> {
                    // This code runs when the 'Config' button is clicked
                    
                    // We must return a screen so Minecraft doesn't get confused.
                    // Returning the 'lastScreen' just keeps the mod menu open
                    // while your external window pops up.
                    return new ConfigScreen(lastScreen);
                })
        );
        
        MinecraftForge.EVENT_BUS.addListener(this::onShutDown);
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 2. Register the setup method
        modEventBus.addListener(this::setup);
        
        try {
            Class.forName("org.vosk.Model");
            LOGGER.info("VOSK LIBRARY DETECTED!");
        } catch (ClassNotFoundException e) {
            ModLoader.get().addWarning(new ModLoadingWarning(ModLoadingContext.get().getActiveContainer().getModInfo(),
                    ModLoadingStage.CONSTRUCT, "VOSK Library is not loaded therefore VoskLib will not function properly.", e
            ));
        }
    }
    
    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(DataLoader::loadFromConfig);
    }
    
    private void onShutDown(GameShuttingDownEvent event) {
    
    }
}
