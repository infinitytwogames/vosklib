package org.infinitytwogames.vosklib.screen;

import net.minecraft.client.gui.GuiGraphics;
import org.infinitytwogames.vosklib.data.DataLoader;
import org.joml.Math;

public class ProgressBar {
    private final int x, y, width, height;
    private float currentLerpWidth = 0; // For that smooth sliding effect you had
    private final float speed = 10f;
    
    public ProgressBar(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public void draw(GuiGraphics graphics, float partialTick) {
        long total = DataLoader.totalSize.get();
        long current = DataLoader.downloadedBytes.get();
        
        if (total <= 0) return;
        
        // Bypass lerp for now to verify the logic works.
        // Once you see the bar moving, we can re-add smoothing.
        currentLerpWidth = (float) width * ((float) current / total);
        
        // 1. Background
        graphics.fill(x, y, x + width, y + height, 0xFF333333);
        
        // 2. Progress (Added FF for 100% Opacity)
        graphics.fill(x, y, x + (int)currentLerpWidth, y + height, 0xFF0915BD);
        
        // 3. Border
        graphics.renderOutline(x, y, width, height, 0xFFAAAAAA);
    }
}