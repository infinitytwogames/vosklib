package org.infinitytwogames.vosklib;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.infinitytwogames.vosklib.data.Config;
import org.infinitytwogames.vosklib.events.VoskChangeRecognitionState;
import org.infinitytwogames.vosklib.events.VoskVoiceEvent;

@Mod.EventBusSubscriber(modid = "vosklib", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {
    public static String transcription = "";
    public static Interval timer = new Interval(5000, () -> transcription = "");
    
    @SubscribeEvent
    public static void onKeyInput(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { // Only check once per tick
            while (KeyInputHandler.toggleVoskKey.consumeClick()) {
                if (VoskManager.getRecognizer() == null) VoskManager.createRecognition();
                
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                
                if (!VoskManager.isListening()) {
                    VoskManager.startListening();
                    
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
    public static void onResult(VoskVoiceEvent.Result result) {
        transcription = result.getText();
        timer.reset();
    }
    
    @SubscribeEvent
    public static void onStartCommand(VoskChangeRecognitionState e) {
        if (e.getGrammar() != null && e.getGrammar().length > 0)
            VoskManager.createRecognition(e.getGrammar());
        else VoskManager.createRecognition();
        
        if (e.getState()) VoskManager.startListening();
        else VoskManager.stopListening();
    }
    
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!timer.isActive()) timer.start();
        timer.update();
        
        Minecraft mc = Minecraft.getInstance();
        
        // Only draw if we have a reason to
        if (!transcription.isEmpty() && Config.SHOW_TRANSCRIPT.get()) {
            GuiGraphics graphics = event.getGuiGraphics();
            
            // Draw the actual tooltip box
            graphics.renderTooltip(
                    mc.font,
                    Component.literal(transcription),
                    4,
                    mc.font.lineHeight + 8
            );
        }
    }
}
