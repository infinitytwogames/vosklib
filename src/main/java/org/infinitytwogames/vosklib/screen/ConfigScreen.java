package org.infinitytwogames.vosklib.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.infinitytwogames.vosklib.VoskManager;
import org.infinitytwogames.vosklib.Vosklib;
import org.infinitytwogames.vosklib.data.Config;
import org.infinitytwogames.vosklib.data.DataLoader;
import org.infinitytwogames.vosklib.data.FileDownloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;
    private ModelList modelList;
    private Button downloadButton;
    private ProgressBar progressBar;
    private List<DataLoader.VoskModel> onlineModels = Collections.synchronizedList(new ArrayList<>());
    private boolean isLoading = true;
    private DataLoader.VoskModel toDownload;
    private EditBox searchBox;
    private String lastSearch = "";
    private boolean modelSelectedChanged;
    
    public ConfigScreen(Screen lastScreen) {
        super(Component.literal("VOSK Model Manager"));
        this.lastScreen = lastScreen;
    }
    
    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 22, 200, 14, Component.literal("Search..."));
        this.searchBox.setResponder(text -> {
            this.lastSearch = text.toLowerCase();
            this.refreshList(); // Re-filter the list whenever typing happens
        });
        this.addRenderableWidget(this.searchBox);
        
        // 2. Adjust the list top margin so it doesn't overlap the search bar
        // Changed top from 40 to 45 or 50
        this.modelList = new ModelList(this.minecraft, this.width, this.height, 50, this.height - 60, 25);
        this.addWidget(this.modelList);
        
        // Populate the list
        this.isLoading = true;
        
        // Call static method
        DataLoader.getOnlineModels(models -> {
            // IMPORTANT: We must jump back to the Minecraft thread to update UI components!
            Minecraft.getInstance().execute(() -> {
                this.onlineModels = models;
                this.isLoading = false;
                this.refreshList(); // Method to populate the ModelList
            });
        },
            error -> Minecraft.getInstance().execute(() -> {
                this.isLoading = false;
                // Show the error to the user via a Toast or a red label in the UI
                Vosklib.showToast("Network Error", "Could not reach Vosk servers.");
            })
        );
        
        this.addRenderableWidget(Button.builder(
                        Component.literal("Transcript: " + (Config.SHOW_TRANSCRIPT.get() ? "ON" : "OFF")),
                        b -> {
                            // Toggle the value
                            boolean newValue = !Config.SHOW_TRANSCRIPT.get();
                            Config.SHOW_TRANSCRIPT.set(newValue);
                            DataLoader.save(); // Ensure it writes to the .toml file
                            
                            // Update the button text immediately
                            b.setMessage(Component.literal("Transcript: " + (newValue ? "ON" : "OFF")));
                        })
                .bounds(16, this.height - 25, 100, 20)
                .build());
        
        // Action Buttons
        this.downloadButton = addRenderableWidget(Button.builder(Component.literal("Download"), b -> startDownload())
                .bounds(this.width - 232, this.height - 25, 100, 20).build());
        this.downloadButton.active = false;
        
        addRenderableWidget(Button.builder(Component.literal("Save & Exit"), b -> {
            if (modelSelectedChanged) VoskManager.init();
            this.minecraft.setScreen(lastScreen);
        }).bounds(this.width - 116, this.height - 25, 100, 20).build());
        
        this.progressBar = new ProgressBar(16, height - 55, width - 36, 10);
    }
    
    private void refreshList() {
        this.modelList.clear();
        AtomicReference<ModelEntry> toSelect = new AtomicReference<>();
        String currentlySelected = DataLoader.getSelected();
        
        onlineModels.stream()
                .filter(model -> {
                    if (lastSearch.isEmpty()) return true;
                    return model.name().toLowerCase().contains(lastSearch) ||
                            model.langText().toLowerCase().contains(lastSearch);
                })
                .sorted((m1, m2) -> {
                    // Keep your improved multi-level sorting logic here
                    boolean d1 = DataLoader.isModelDownloaded(m1.name());
                    boolean d2 = DataLoader.isModelDownloaded(m2.name());
                    int downloadCompare = Boolean.compare(d2, d1);
                    if (downloadCompare != 0) return downloadCompare;
                    
                    return m1.langText().compareToIgnoreCase(m2.langText());
                })
                .forEach(model -> {
                    boolean isDownloaded = DataLoader.isModelDownloaded(model.name());
                    ModelEntry entry = new ModelEntry(model, isDownloaded);
                    
                    this.modelList.add(entry);
                    
                    if (model.name().equalsIgnoreCase(currentlySelected)) {
                        toSelect.set(entry);
                    }
                });
        
        if (toSelect.get() != null) {
            this.modelList.setSelected(toSelect.get());
        }
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ensure the search box gets focus when typing
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FileDownloader.getSpeedTimer().update();
        
        this.renderBackground(guiGraphics);
        this.modelList.render(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 10, 0xFFFFFF);
        if (isLoading) {
            String text = "Loading...";
            guiGraphics.drawCenteredString(font, text, this.width / 2, font.lineHeight + 12 + 2, 0xa3a3a3);
        }
        
        if (DataLoader.totalSize.get() > 0) {
            progressBar.draw(guiGraphics, partialTick);
            guiGraphics.drawString(font, FileDownloader.getSpeedText() + " | " + FileDownloader.getETAText(), 16, height - 42, 0xAAAAAA);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void startDownload() {
        if (FileDownloader.getSpeedTimer().isActive()) return;
        DataLoader.startDownload(
                toDownload.name(),
                toDownload.url(),
                () -> Minecraft.getInstance().execute(this::refreshList),
                () -> Minecraft.getInstance().execute(() -> Vosklib.showToast("Download Failure", "Could not download the selected model."))
        );
        downloadButton.active = false;
    }
    
    // --- INNER CLASSES FOR SCROLLING ---
    
    class ModelList extends ObjectSelectionList<ModelEntry> {
        public ModelList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
        }
        
        public void add(ModelEntry entry) {
            addEntry(entry);
        }
        
        public void clear() {
            clearEntries();
        }
    }
    
    class ModelEntry extends ObjectSelectionList.Entry<ModelEntry> {
        private final boolean isDownloaded;
        private DataLoader.VoskModel model;
        
        public ModelEntry(DataLoader.VoskModel model, boolean isDownloaded) {
            this.isDownloaded = isDownloaded;
            this.model = model;
        }
        
        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float pt) {
            int color = isDownloaded ? 0x55FF55 : 0xFFFFFF;
            int nameColor = model.obsolete() ? 0x777777 : 0xAAAAAA;
            String label = isDownloaded ? " (Installed)" : "";
            
            // Calculate available width (list width minus some padding for the scrollbar area)
            int maxWidth = width - 20;
            
            // Truncate the name if it's too long
            String displayName = font.plainSubstrByWidth(model.name() + label, maxWidth);
            if (model.name().length() > displayName.length()) {
                displayName = font.plainSubstrByWidth(model.name(), maxWidth - font.width("...")) + "...";
            }
            
            // Render the (possibly truncated) name
            guiGraphics.drawString(font, displayName, left + 5, top + 2, color);
            
            // Do the same for subtext just in case
            String subtext = String.format("%s | %s | %s", model.langText(), model.sizeText(), model.type());
            String displaySubtext = font.plainSubstrByWidth(subtext, maxWidth);
            guiGraphics.drawString(font, displaySubtext, left + 5, top + 12, nameColor, false);
            
            if (isHovered) {
                guiGraphics.renderTooltip(font, Component.literal(model.name()), mouseX, mouseY);
            }
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (FileDownloader.getSpeedTimer().isActive()) {
                downloadButton.active = false;
                return true;
            }
            if (isDownloaded) {
                modelSelectedChanged = true;
                DataLoader.select(model.name().toLowerCase());
                DataLoader.save();
                downloadButton.active = false;
            } else {
                toDownload = model;
                downloadButton.active = true;
            }
            return true;
        }
        
        @Override
        public Component getNarration() { return Component.literal(model.name()); }
    }
}