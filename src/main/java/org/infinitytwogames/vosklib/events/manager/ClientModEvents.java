package org.infinitytwogames.vosklib.events.manager;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.infinitytwogames.vosklib.KeyInputHandler;

@Mod.EventBusSubscriber(modid = "vosklib", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        KeyInputHandler.register();
        event.register(KeyInputHandler.toggleVoskKey);
    }
}
