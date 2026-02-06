package org.infinitytwogames.vosklib.events.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.infinitytwogames.vosklib.KeyInputHandler;
import org.infinitytwogames.vosklib.VoskManager;
import org.infinitytwogames.vosklib.events.VoskChangeRecognitionState;
import org.infinitytwogames.vosklib.events.VoskVoiceEvent;

@Mod.EventBusSubscriber(modid = "vosklib", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {
    
    @SubscribeEvent
    public static void onKeyInput(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { // Only check once per tick
            while (KeyInputHandler.toggleVoskKey.consumeClick()) {
                if (VoskManager.getRecognizer() == null) VoskManager.createRecognition();
                
                if (!VoskManager.isListening()) {
                    VoskManager.startListening(
                            s -> Minecraft.getInstance().player.displayClientMessage(Component.literal(s), true),
                            s -> Minecraft.getInstance().player.displayClientMessage(Component.literal("Parsed: " + s), true)
                    );
                    
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("Vosk is now listening..."), true
                    );
                } else {
                    VoskManager.stopListening();
                    
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("Vosk has stopped listening."), true
                    );
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onStartCommand(VoskChangeRecognitionState e) {
        if (e.getGrammar() != null && e.getGrammar().length > 0)
            VoskManager.createRecognition(e.getGrammar());
        else VoskManager.createRecognition();
        
        if (e.getState()) VoskManager.startListening(
                partial -> MinecraftForge.EVENT_BUS.post(new VoskVoiceEvent.Partial(partial)),
                total -> MinecraftForge.EVENT_BUS.post(new VoskVoiceEvent.Result(total))
        ); else VoskManager.stopListening();
    }
}
