package org.infinitytwogames.vosklib.data;

import org.infinitytwogames.vosklib.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public class FileDownloader {
    private static final Logger logger = LoggerFactory.getLogger(FileDownloader.class);
    
    public static double currentSpeedBps = 0;
    private static long lastBytes = 0;
    private static final double SPEED_ALPHA = 0.2;
    
    private static final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    private static final Interval speedTimer = new Interval(1000, () -> {
        long currentTotal = DataLoader.downloadedBytes.get();
        long delta = currentTotal - lastBytes;
        lastBytes = currentTotal;
        
        currentSpeedBps = (currentSpeedBps == 0)
                ? delta
                : (SPEED_ALPHA * delta) + (1 - SPEED_ALPHA) * currentSpeedBps;
    });
    
    public static void prepareForDownload() {
        DataLoader.downloadedBytes.set(0);
        DataLoader.totalSize.set(0);
        FileDownloader.startTracking();
    }
    
    public static void startTracking() {
        logger.info("Starting download speed tracking.");
        lastBytes = DataLoader.downloadedBytes.get();
        currentSpeedBps = 0;
        speedTimer.start();
    }
    
    public static void stopTracking() {
        logger.info("Stopping download speed tracking.");
        speedTimer.end();
        
        currentSpeedBps = 0;
        DataLoader.downloadedBytes.set(0);
        DataLoader.totalSize.set(0);
    }
    
    public static String getSpeedText() {
        if (currentSpeedBps < 1024) return String.format("%.2f B/s", currentSpeedBps);
        if (currentSpeedBps < 1024 * 1024) return String.format("%.2f KB/s", currentSpeedBps / 1024.0);
        return String.format("%.2f MB/s", currentSpeedBps / 1024.0 / 1024.0);
    }
    
    public static String getETAText() {
        long total = DataLoader.totalSize.get();
        long downloaded = DataLoader.downloadedBytes.get();
        long remaining = total - downloaded;
        
        if (currentSpeedBps <= 0 || remaining <= 0) return "Calculating...";
        
        long seconds = (long) (remaining / currentSpeedBps);
        if (seconds > 3600) return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        if (seconds > 60) return String.format("%dm %ds", seconds / 60, seconds % 60);
        return seconds + "s";
    }

    public static RemoteFileInfo discover(String url) throws Exception {
        HttpRequest head = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        
        HttpResponse<Void> response = http.send(head, HttpResponse.BodyHandlers.discarding());
        
        long size = -1;
        String fileName = null;
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // Size
            size = response.headers()
                    .firstValue("Content-Length")
                    .map(Long::parseLong)
                    .orElse(-1L);
            
            // Filename
            fileName = response.headers()
                    .firstValue("Content-Disposition")
                    .map(FileDownloader::parseFileName)
                    .orElse(null);
        }
        
        // Fallbacks
        if (fileName == null) {
            fileName = Path.of(URI.create(url).getPath()).getFileName().toString();
        }
        
        if (size <= 0) {
            HttpRequest get = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            
            HttpResponse<InputStream> getResp =
                    http.send(get, HttpResponse.BodyHandlers.ofInputStream());
            
            size = getResp.headers()
                    .firstValue("Content-Length")
                    .map(Long::parseLong)
                    .orElse(-1L);
            
            getResp.body().close();
        }
        
        return new RemoteFileInfo(size, fileName);
    }
    
    private static String parseFileName(String disposition) {
        // attachment; filename="example.jar"
        for (String part : disposition.split(";")) {
            part = part.trim();
            if (part.startsWith("filename=")) {
                return part.substring("filename=".length())
                        .replace("\"", "");
            }
        }
        return null;
    }
    
    public static DownloadTask createTask(String url, Path dir, boolean isNative, Path nativesDir) throws Exception {
        RemoteFileInfo info = discover(url);
        
        Path target = dir.resolve(info.fileName());
        long size = info.size();
        
        // Register total size BEFORE download starts
        if (size > 0) {
            DataLoader.totalSize.addAndGet(size);
        }
        
        return new DownloadTask(url, target, size, isNative, nativesDir);
    }
    
    public static void processTask(DownloadTask task, CountDownLatch latch) {
        String fileName = task.target().getFileName().toString();
        try {
            // 1. Validation check
            if (Files.exists(task.target())) {
                long size = Files.size(task.target());
                if (task.expectedSize() > 0 && size == task.expectedSize()) {
                    if (task.isNative()) extractNatives(task.target(), task.nativesDir());
                    return;
                }
                
                logger.warn("Size mismatch for {}: expected {} but found {}. Deleting...", fileName, task.expectedSize(), size);
                Files.delete(task.target());
            }
            
            // 2. Download
            downloadWithRetry(task, 3);
            
            // 3. Post-process
            if (task.isNative()) {
                extractNatives(task.target(), task.nativesDir());
            }
            
        } catch (Exception e) {
            logger.error("Failed to process task for {}: {}", fileName, e.getMessage());
        } finally {
            if (latch != null) latch.countDown();
        }
    }
    
    public static void downloadFileSync(String url, Path target) {
        DownloadTask task = new DownloadTask(url, target, -1, false, null);
        FileDownloader.processTask(task, null);
    }
    
    private static void downloadWithRetry(DownloadTask task, int max) throws Exception {
        int attempt = 0;
        String fileName = task.target().getFileName().toString();
        
        while (attempt < max) {
            attempt++;
            try {
                Files.createDirectories(task.target().getParent());
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(task.url())).GET().build();
                
                if (attempt > 1) logger.info("Retrying download {} (Attempt {}/{})", fileName, attempt, max);
                else logger.info("Starting download: {}", fileName);
                
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Server returned HTTP " + response.statusCode());
                }
                
                try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(task.target())) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        DataLoader.downloadedBytes.addAndGet(read);
                    }
                }
                logger.info("Successfully downloaded: {}", fileName);
                return;
            } catch (Exception e) {
                logger.warn("Attempt {} failed for {}: {}", attempt, fileName, e.getMessage());
                if (attempt >= max) {
                    logger.error("Maximum retries reached for {}", fileName);
                    throw e;
                }
            }
        }
    }
    
    public static void extractNatives(Path jar, Path nativesDir) throws Exception {
        logger.info("Extracting natives from {} to {}", jar.getFileName(), nativesDir);
        Files.createDirectories(nativesDir);
        
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
            jf.stream().forEach(entry -> {
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) return;
                
                String name = entry.getName();
                if (!(name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib"))) return;
                
                Path out = nativesDir.resolve(name);
                try (InputStream in = jf.getInputStream(entry)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Extracted native: {}", name);
                } catch (Exception e) {
                    logger.error("Failed to extract native {}: {}", name, e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    public static Interval getSpeedTimer() { return speedTimer; }
    
    public record DownloadTask(
            String url,
            Path target,
            long expectedSize,
            boolean isNative,
            Path nativesDir
    ) {}
    
    public record RemoteFileInfo(
            long size,
            String fileName
    ) {}
}