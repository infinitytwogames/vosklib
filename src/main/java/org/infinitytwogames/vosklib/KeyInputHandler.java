package org.infinitytwogames.vosklib;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {
    public static final String KEY_CATEGORY = "key.categories.vosklib";
    public static final String KEY_TOGGLE_VOSK = "key.vosklib.toggle";
    
    public static KeyMapping toggleVoskKey;
    
    public static void register() {
        toggleVoskKey = new KeyMapping(
                KEY_TOGGLE_VOSK,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V, // Default key is 'V'
                KEY_CATEGORY
        );
    }
}
