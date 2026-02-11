package org.infinitytwogames.vosklib.data;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    // These will hold our actual values
    public static final ForgeConfigSpec.ConfigValue<String> SELECTED_MODEL;
    public static final ForgeConfigSpec.DoubleValue SENSITIVITY;
    public static final ForgeConfigSpec.BooleanValue AUTO_DOWNLOAD;
    public static final ForgeConfigSpec.IntValue REFRESH_TIME;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DOWNLOADED_MODELS;
    
    static {
        BUILDER.push("VOSK Settings");
        
        SELECTED_MODEL = BUILDER
                .comment("The model currently selected for voice recognition (Small, Medium, Large)")
                .define("selectedModel", "Small");
        
        SENSITIVITY = BUILDER
                .comment("Microphone sensitivity (0.0 to 1.0)")
                .defineInRange("sensitivity", 0.5, 0.0, 1.0);
        
        AUTO_DOWNLOAD = BUILDER
                .comment("Should the mod automatically download missing models?")
                .define("autoDownload", true);
        
        DOWNLOADED_MODELS = BUILDER
                .comment("List of models currently downloaded and unzipped in the config folder.")
                .defineList("downloadedModels",
                        List.of(), // Default value (if empty)
                        entry -> entry instanceof String); // Validator
        
        REFRESH_TIME = BUILDER
                .comment("Time for the manifest to be redownloaded.")
                .defineInRange("days", 36, 0, 356)
        ;
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
