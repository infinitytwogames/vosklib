package org.infinitytwogames.vosklib.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.infinitytwogames.vosklib.VoskManager;
import org.infinitytwogames.vosklib.data.DataLoader;
import org.infinitytwogames.vosklib.data.FileDownloader;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {
    private Screen lastScreen;
    private final List<String> temp = new ArrayList<>(3);
    private final List<Button> models = new ArrayList<>(3);
    private final List<String> needed = new ArrayList<>(3);
    private final List<Button> missingModels = new ArrayList<>(3);
    private static String selected;
    private Button download;
    private ProgressBar progressBar;
    
    public ConfigScreen(Screen lastScreen) {
        super(Component.literal("VOSK Model Manager"));
        
        this.lastScreen = lastScreen;
    }
    
    @Override
    protected void init() {
        // Clear lists to prevent duplicates if init() is called again (like on window resize)
        this.models.clear();
        this.temp.clear();
        this.missingModels.clear();
        this.needed.clear();
        
        // 1. Fill temp list from DataLoader
        if (DataLoader.getSmallPath() != null) temp.add("Small");
        else needed.add("Small");
        if (DataLoader.getMediumPath() != null) temp.add("Medium");
        else needed.add("Medium");
        if (DataLoader.getLargePath() != null) temp.add("Large");
        else needed.add("Large");
        
        int row = 0;
        for (String modelName : temp) {
            // Check if this button should start as 'Selected'
            boolean isSelected = modelName.toLowerCase().contains(DataLoader.getSelected().toLowerCase());
            
            Button button = Button.builder(Component.literal(modelName), this::onRadioButtonPressed)
                    .bounds(16, row * (20 + 4) + 40, 100, 20) // Simplified math: row * (height + spacing)
                    .build();
            
            // Use the 'active' state logic you wrote
            button.active = !isSelected;
            
            addRenderableWidget(button);
            models.add(button);
            row++;
        }
        
        row = 0;
        int centerX = width / 2;
        
        for (String missing : needed) {
            Button button = Button.builder(Component.literal(missing), this::downloadModelRadio)
                    .bounds(centerX, row * (20 + 4) + 40, 100, 20)
                    .build();
            
            addRenderableWidget(button);
            missingModels.add(button);
            row++;
        }
        
        // Back Button (Bottom Right)
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            back();
            minecraft.setScreen(lastScreen);
        })
                .bounds(this.width - 116, this.height - 36, 100, 20).build());
        
        // Download button
        download = Button.builder(Component.literal("Download"), this::download)
                .bounds(this.width - 224, this.height - 36, 100, 20)
                .build();
        download.active = false;
        
        addRenderableWidget(download);
        
        progressBar = new ProgressBar(16, height - 36, width - 248, 20);
    }
    
    private void back() {
        VoskManager.init();
    }
    
    private void download(Button button) {
        startDownload(selected);
    }
    
    private void downloadModelRadio(Button button) {
        selected = button.getMessage().getString();
        
        for (Button b : missingModels) {
            b.active = true; // Enable all
        }
        button.active = false; // Disable only the clicked one
        download.active = true;
    }
    
    private void onRadioButtonPressed(Button button) {
        String modelName = button.getMessage().getString();
        
        // 1. Update the actual Mod Data
        DataLoader.select(modelName.toLowerCase());
        DataLoader.save();
        
        // 2. Update UI Visuals
        for (Button b : models) {
            b.active = true; // Enable all
        }
        button.active = false; // Disable only the clicked one
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        FileDownloader.getSpeedTimer().update();
        
        guiGraphics.drawString(font, "Downloaded Models", 16, 16, 0xffffff);
        guiGraphics.drawString(font, "Available Models", (width / 2) + 8, 16, 0xffffff);
        
        if (temp.isEmpty()) guiGraphics.drawString(font, "No Models were found.", 40, 32, 0x919191);
        
        if (DataLoader.totalSize.get() > 0) {
            progressBar.draw(guiGraphics, partialTick);
            
            // Add your speed/ETA text below it
            guiGraphics.drawString(font, FileDownloader.getSpeedText(), 16, height - 12, 0xFFFFFF);
            guiGraphics.drawString(font, FileDownloader.getETAText(), 128, height - 12, 0xFFFFFF);
            guiGraphics.drawString(font, "Downloading " + selected + "...", 16, height - 48, 0xffffff);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void startDownload(String model) {
        String url;
        if (model.toLowerCase().contains("small")) url = DataLoader.small;
        else if (model.toLowerCase().contains("medium")) url = DataLoader.medium;
        else if (model.toLowerCase().contains("large")) url = DataLoader.large;
        else throw new RuntimeException("Unknown Model name: "+model);
        
        DataLoader.startDownload(model.toLowerCase(), url);
    }
}