package org.infinitytwogames.vosklib.data;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.infinitytwogames.vosklib.VoskManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DataLoader {
    private static final Map<String, Path> models = Collections.synchronizedMap(new HashMap<>());
    private static final Path config = FMLPaths.CONFIGDIR.get().resolve("vosk");
    private static final Logger logger = LogUtils.getLogger();
    
    private static Path selectedPath;
    private static String selected;
    private static int refresh;
    
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
        models.put(version, model);
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
        List<String> toSave = new ArrayList<>(models.keySet());
        
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
        List<String> downloaded = new ArrayList<>(Config.DOWNLOADED_MODELS.get());
        List<String> validModels = new ArrayList<>();
        
        refresh = Config.REFRESH_TIME.get();
        
        for (String name : downloaded) {
            Path modelFolderPath = config.resolve(name);
            if (modelFolderPath.toFile().exists()) {
                register(name, modelFolderPath);
                validModels.add(name);
            }
        }
        
        // Clean up the config if folders were manually deleted
        if (validModels.size() != downloaded.size()) {
            Config.DOWNLOADED_MODELS.set(validModels);
            Config.SPEC.save();
        }
        
        // Set the initial selection from config
        selected = Config.SELECTED_MODEL.get();
        select(selected);
        
        if (!selected.toLowerCase().contains("none")) VoskManager.init();
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
        
        Path path = models.get(model);
        if (path != null) selectedPath = path;
        else {
            // Log a warning instead of crashing
            logger.warn("VoskLib: Configured model '{}' not found. Resetting to none.", model);
            selected = "none";
            selectedPath = null;
        }
        
        selected = model;
    }
    
    public static void fetchOnlineModels(Consumer<List<VoskModel>> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL("https://alphacephei.com/vosk/models/model-list.json");
                try (InputStreamReader reader = new InputStreamReader(url.openStream())) {
                    JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
                    List<VoskModel> models = getVoskModels(array);
                    
                    // Sort by language, then by type (small vs big)
                    models.sort(Comparator.comparing(VoskModel::langText).thenComparing(VoskModel::type));
                    
                    callback.accept(models);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private static @NotNull List<VoskModel> getVoskModels(JsonArray array) {
        List<VoskModel> models = new ArrayList<>();
        
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            
            // SKIP OBSOLETE MODELS to save user bandwidth and disk space
            if (obj.get("obsolete").getAsString().equals("true")) continue;
            
            models.add(new VoskModel(
                    obj.get("name").getAsString(),
                    obj.get("url").getAsString(),
                    obj.get("size_text").getAsString(),
                    obj.get("lang_text").getAsString(),
                    false,
                    obj.get("type").getAsString()
            ));
        }
        return models;
    }
    
    public static boolean isModelDownloaded(String name) {
        return models.get(name) != null;
    }
    
    public static void getOnlineModels(Consumer<List<VoskModel>> callback) {
        Path file = config.resolve("manifest.json");
        
        if (Files.exists(file)) {
            try {
                Instant fileInstant = Files.getLastModifiedTime(file).toInstant();
                Instant now = Instant.now();
                
                // Calculate the duration between them
                Duration age = Duration.between(fileInstant, now);
                
                boolean b = age.toDays() >= refresh;
                
                if (!(refresh > 0 && b)) {
                    JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    
                    if (element instanceof JsonArray array) {
                        List<VoskModel> models = getVoskModels(array);
                        models.sort(Comparator.comparing(VoskModel::langText).thenComparing(VoskModel::type));
                        
                        callback.accept(models);
                        return;
                        
                    } else {
                        logger.warn("Unable to parse a model. Getting from URL...");
                    }
                }
                
            } catch (IOException e) {
                logger.error("VoskLib: Cached manifest is corrupted. Re-fetching from source.", e);
            }
        }
        
        fetchOnlineModels(callback);
    }
    
    public record VoskModel(
            String name,
            String url,
            String sizeText,
            String langText,
            boolean obsolete,
            String type
    ) {
    }
}
