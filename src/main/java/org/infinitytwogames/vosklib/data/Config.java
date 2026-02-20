package org.infinitytwogames.vosklib.data;

import net.minecraftforge.common.ForgeConfigSpec;
import oshi.hardware.platform.unix.solaris.SolarisHWDiskStore;

import java.util.List;

public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    // These will hold our actual values
    public static final ForgeConfigSpec.ConfigValue<String> SELECTED_MODEL;
    public static final ForgeConfigSpec.IntValue REFRESH_TIME;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DOWNLOADED_MODELS;
    public static ForgeConfigSpec.BooleanValue SHOW_TRANSCRIPT;
    
    static {
        BUILDER.push("VOSK Model Settings");
        
        SELECTED_MODEL = BUILDER
                .comment("The model currently selected for voice recognition.")
                .define("selectedModel", "none");
        
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
        BUILDER.push("VoskLib Settings");
        
        SHOW_TRANSCRIPT = BUILDER
                .comment("Should the transcribed voice text be displayed?")
                .define("show_transcript", true);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
