package org.infinitytwogames.vosklib.data;

import net.minecraftforge.fml.loading.FMLPaths;
import org.infinitytwogames.vosklib.VoskManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DataLoader {
    public static final String small = "https://alphacephei.com/kaldi/models/vosk-model-small-en-us-0.15.zip";
    public static final String medium = "https://alphacephei.com/kaldi/models/vosk-model-en-us-0.22-lgraph.zip";
    public static final String large = "https://alphacephei.com/kaldi/models/vosk-model-en-us-0.22.zip";
    
    private static Path smallPath, mediumPath, largePath;
    private static Path config = FMLPaths.CONFIGDIR.get().resolve("vosk");
    private static Path selectedPath;
    private static String selected;
    
    public static final AtomicLong downloadedBytes = new AtomicLong(0);
    public static final AtomicLong totalSize = new AtomicLong(0);
    
    public static void unzip(File zipFile, File destDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String fileName = entry.getName();
                
                // Check if the entry is inside a top-level directory and strip it
                // This turns "vosk-model-small-en-us-0.15/am/model" into "am/model"
                String flattenedName = fileName.substring(fileName.indexOf("/") + 1);
                
                if (flattenedName.isEmpty()) {
                    entry = zis.getNextEntry();
                    continue;
                }
                
                File newFile = new File(destDir, flattenedName);
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
        }
        zipFile.delete();
    }
    
    public static void register(String version, Path model) {
        switch (version.toLowerCase().trim()) {
            case "small" -> smallPath = model;
            case "medium" -> mediumPath = model;
            case "large" -> largePath = model;
        }
    }
    
    public static void startDownload(String modelType, String url) {
        new Thread(() -> {
            try {
                // 1. Setup paths
                Path modelFolder = config.resolve(modelType.toLowerCase());
                
                // 2. Prepare the downloader (Resets bytes and starts speed timer)
                FileDownloader.prepareForDownload();
                
                FileDownloader.DownloadTask task = FileDownloader.createTask(url, config, false, null);
                
                FileDownloader.processTask(task, new CountDownLatch(1));
                Path targetZip = task.target();
                
                // 4. Unzip and register
                unzip(targetZip.toFile(), modelFolder.toFile());
                
                register(modelType, modelFolder);
                
                // 5. Cleanup and save config
                save();
                FileDownloader.stopTracking();
                
                System.out.println("Download complete: " + modelType);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    public static void save() {
        if (selected == null) {
            selected = "none";
        }
        Config.SELECTED_MODEL.set(selected);
        
        // 2. Ensure the list doesn't contain nulls
        List<String> toSave = new java.util.ArrayList<>();
        if (smallPath != null) toSave.add("small");
        if (mediumPath != null) toSave.add("medium");
        if (largePath != null) toSave.add("large");
        
        // Double check: Forge's ConfigValue.set() fails if the object itself is null
        Config.DOWNLOADED_MODELS.set(toSave);
        
        Config.SPEC.save();
    }
    
    public static void markModelAsDownloaded(String modelName) {
        // 1. Get current list from the config
        // Note: Forge returns a List<? extends String>, so we copy it to a modifiable ArrayList
        List<String> currentList = new java.util.ArrayList<>(Config.DOWNLOADED_MODELS.get());
        
        // 2. Add entry if missing
        if (!currentList.contains(modelName)) {
            currentList.add(modelName);
            
            // 3. Update the config value
            Config.DOWNLOADED_MODELS.set(currentList);
            // We don't necessarily need to save every time if we call save() at the end
        }
    }
    
    public static void loadFromConfig() {
        List<String> downloaded = (List<String>) Config.DOWNLOADED_MODELS.get();
        
        // Check for each model in the config and verify the folder exists
        for (String name : downloaded) {
            Path modelFolderPath = config.resolve(name);
            if (modelFolderPath.toFile().exists()) {
                register(name, modelFolderPath);
            }
        }
        
        // Set the initial selection from config
        selected = Config.SELECTED_MODEL.get();
        select(selected);
        
        if (!selected.toLowerCase().contains("none")) VoskManager.init();
    }
    
    public static Path getSmallPath() {
        return smallPath;
    }
    
    public static void setSmallPath(Path smallPath) {
        DataLoader.smallPath = smallPath;
    }
    
    public static Path getMediumPath() {
        return mediumPath;
    }
    
    public static void setMediumPath(Path mediumPath) {
        DataLoader.mediumPath = mediumPath;
    }
    
    public static Path getLargePath() {
        return largePath;
    }
    
    public static void setLargePath(Path largePath) {
        DataLoader.largePath = largePath;
    }
    
    public static Path getConfig() {
        return config;
    }
    
    public static Path getSelectedPath() {
        return selectedPath;
    }
    
    public static String getSelected() {
        return selected;
    }
    
    public static void select(String model) {
        if (model == null || model.equalsIgnoreCase("none") || model.isEmpty()) {
            selectedPath = null;
            selected = "none";
            return; // Exit early safely
        }
        
        if (model.contains("small")) selectedPath = smallPath;
        else if (model.contains("medium")) selectedPath = mediumPath;
        else if (model.contains("large")) selectedPath = largePath;
        else throw new RuntimeException("Unknown value: "+model);
        
        selected = model;
    }
}
