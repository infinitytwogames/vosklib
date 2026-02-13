package org.infinitytwogames.vosklib;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
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
    
    private static final Object RECOGNIZER_LOCK = new Object();
    
    public static void startListening(Consumer<String> partial, Consumer<String> sentence) {
        if (isListening) return;
        isListening = true;
        logger.info("Vosk is now listening...");
        
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        
        CompletableFuture.runAsync(() -> {
            try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();
                
                byte[] buffer = new byte[4096];
                while (isListening) {
                    int nbytes = line.read(buffer, 0, buffer.length);
                    if (nbytes <= 0) continue;
                    
                    // LOCK: Ensure the recognizer isn't swapped during this call
                    synchronized (RECOGNIZER_LOCK) {
                        if (recognizer == null) continue;
                        
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
                }
                line.stop();
            } catch (LineUnavailableException e) {
                logger.error("Microphone unavailable: ", e);
            } finally {
                isListening = false;
            }
        });
    }
    
    public static void createRecognition(String[] grammar) {
        synchronized (RECOGNIZER_LOCK) {
            // 1. Clean up old native resources!
            if (recognizer != null) {
                recognizer.close();
            }
            
            // 2. Build the grammar
            JsonArray array = new JsonArray();
            for (String s : grammar) {
                if (s != null && !s.trim().isEmpty()) {
                    array.add(s.trim().toLowerCase());
                }
            }
            
            if (!array.contains(new JsonPrimitive("[unk]"))) {
                array.add("[unk]");
            }
            
            // 3. Assign the new recognizer
            recognizer = new Recognizer(getModel(), 16000f, array.toString());
            logger.info("Vosk grammar updated successfully.");
        }
    }
    
    public static boolean isListening() {
        return isListening;
    }
    
    public static void stopListening() {
        isListening = false;
    }
    
    public static void createRecognition() {
        try {
            recognizer = new Recognizer(getModel(), 16000f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void feedAudio(byte[] audioData) {
        if (VoskManager.getRecognizer() != null && VoskManager.isListening()) {
            VoskManager.getRecognizer().acceptWaveForm(audioData, audioData.length);
        }
    }
    
    protected static Recognizer getRecognizer() {
        return recognizer;
    }
}
