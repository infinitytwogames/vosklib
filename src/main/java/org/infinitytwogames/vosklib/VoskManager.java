package org.infinitytwogames.vosklib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.infinitytwogames.vosklib.data.DataLoader;
import org.slf4j.Logger;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class VoskManager {
    private static Model vModel;
    private static boolean isListening = false;
    private static Recognizer recognizer;
    
    private static final Logger logger = LogUtils.getLogger();
    
    public static void init() {
        Path path = DataLoader.getSelectedPath();
        
        if (path == null || !path.toFile().exists()) {
            System.err.println("VoskLib: Cannot initialize! No model selected or folder missing.");
            return;
        }
        
        try {
            // Vosk loads its own natives if they are on the system library path.
            // In Forge, you might need to load them manually or ensure they're in the 'natives' folder.
            
            System.out.println("VoskLib: Loading model from " + path.toAbsolutePath());
            vModel = new Model(path.toAbsolutePath().toString());
            System.out.println("VoskLib: Vosk initialized successfully! Creating the Recognizer");
            
            
            
        } catch (Exception e) {
            System.err.println("VoskLib: Failed to initialize Vosk!");
            e.printStackTrace();
        }
    }
    
    public static Model getModel() {
        return vModel;
    }
    
    // Helper to extract values safely
    private static String getTextFromJson(String json, String key) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            if (jsonObject.has(key)) {
                return jsonObject.get(key).getAsString();
            }
        } catch (Exception e) {
            logger.error("Failed to parse Vosk JSON: {}", json);
        }
        return "";
    }
    
    public static void startListening(Consumer<String> partial, Consumer<String> sentence) {
        if (isListening) return; // Don't start twice!
        isListening = true;
        logger.info("Vosk is now listening...");
        
        // 1. Define the audio format
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        
        try {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            
            // 2. Run the capture in a background thread so the game doesn't hang
            CompletableFuture.runAsync(() -> {
                byte[] buffer = new byte[4096];
                int nbytes;
                
                while (isListening) {
                    nbytes = line.read(buffer, 0, buffer.length);
                    
                    double sum = 0;
                    for (int i = 0; i < nbytes; i++) {
                        sum += Math.abs(buffer[i]);
                    }
                    double averageVolume = sum / nbytes;
                    if (averageVolume < 0.1) {
                        logger.warn("Vosk is receiving SILENCE. Check OS mic settings.");
                    }
                    // ----------------------------
                    
                    if (recognizer.acceptWaveForm(buffer, nbytes)) {
                        String result = getTextFromJson(recognizer.getResult(), "text");
                        if (!result.isEmpty()) {
                            Minecraft.getInstance().execute(() -> sentence.accept(result));
                        }
                    } else {
                        String partialText = getTextFromJson(recognizer.getPartialResult(), "partial");
                        if (!partialText.isEmpty()) {
                            Minecraft.getInstance().execute(() -> partial.accept(partialText));
                        }
                    }
                }
                line.stop();
                line.close();
            });
            
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isListening() {
        return isListening;
    }
    
    public static void stopListening() {
        isListening = false;
    }
    
    public static void createRecognition(String[] grammar) {
        JsonArray array = new JsonArray();
        for (String s : grammar) array.add(s);
        
        if (!array.contains(new JsonPrimitive("[unk]"))) {
            array.add("[unk]");
        }
        
        recognizer = new Recognizer(getModel(), 16000f, array.toString());
    }
    
    public static void createRecognition() {
        try {
            recognizer = new Recognizer(getModel(), 16000f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Recognizer getRecognizer() {
        return recognizer;
    }
}
