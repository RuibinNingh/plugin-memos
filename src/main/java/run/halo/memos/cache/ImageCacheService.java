package run.halo.memos.cache;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.plugin.PluginsRootGetter;

@Service
@RequiredArgsConstructor
public class ImageCacheService {

    private static final String IMAGE_ROUTE_PREFIX = "/memos/proxy/image/";
    private static final String ORIGINAL_ROUTE_PREFIX = "/memos/proxy/file/";

    private final PluginsRootGetter pluginsRootGetter;
    private final WebClient memosWebClient;

    public Mono<ImageResult> getOrCreate(String rawImagePath, ImageCacheProperties properties) {
        if (!properties.enabled()) {
            return Mono.just(ImageResult.original(originalUrl(rawImagePath)));
        }
        ImageResult existing = findExisting(rawImagePath, properties);
        if (existing != null) {
            return Mono.just(existing);
        }
        return Mono.fromCallable(() -> createCachedImage(rawImagePath, properties))
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<WarmupItemResult> warmup(String attachmentPath, ImageCacheProperties properties) {
        return getOrCreate(attachmentPath, properties)
            .map(result -> {
                if (result.useOriginal()) {
                    return WarmupItemResult.asSkipped();
                }
                return result.cacheHit() ? WarmupItemResult.asHit() : WarmupItemResult.asCreated();
            })
            .onErrorReturn(WarmupItemResult.asFailed());
    }

    public Mono<CacheStats> stats() {
        return Mono.fromCallable(() -> {
            Path root = cacheRoot();
            AtomicLong files = new AtomicLong();
            AtomicLong bytes = new AtomicLong();
            if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile).forEach(path -> {
                        files.incrementAndGet();
                        try {
                            bytes.addAndGet(Files.size(path));
                        } catch (IOException ignored) {
                            // Best-effort status only.
                        }
                    });
                }
            }
            return new CacheStats(root.toString(), files.get(), bytes.get());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Long> clear() {
        return Mono.fromCallable(() -> deleteChildren(cacheRoot()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Long> cleanExpired(int maxAgeDays) {
        return Mono.fromCallable(() -> {
            Path root = cacheRoot();
            if (!Files.isDirectory(root)) {
                return 0L;
            }
            Instant cutoff = Instant.now().minus(Duration.ofDays(maxAgeDays));
            AtomicLong deleted = new AtomicLong();
            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                            Files.deleteIfExists(path);
                            deleted.incrementAndGet();
                        }
                    } catch (IOException ignored) {
                        // Best-effort cleanup only.
                    }
                });
            }
            return deleted.get();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public String originalUrl(String rawImagePath) {
        return ORIGINAL_ROUTE_PREFIX + normalizeImagePath(rawImagePath);
    }

    private ImageResult createCachedImage(String rawImagePath, ImageCacheProperties properties)
        throws IOException {
        Files.createDirectories(cacheRoot());
        ImageResult existing = findExisting(rawImagePath, properties);
        if (existing != null) {
            return existing;
        }

        Path tempOriginal = Files.createTempFile(cacheRoot(), "memos-original-", ".tmp");
        Path tempOutput = null;
        try {
            fetchOriginal(rawImagePath, properties, tempOriginal).block(Duration.ofMinutes(5));
            if (Files.size(tempOriginal) <= properties.minSourceBytes()) {
                return ImageResult.original(originalUrl(rawImagePath));
            }
            BufferedImage source = ImageIO.read(tempOriginal.toFile());
            boolean sourcePng = normalizeImagePath(rawImagePath).toLowerCase().endsWith(".png");
            if (source == null || (sourcePng && isAnimatedPng(tempOriginal))) {
                return ImageResult.original(originalUrl(rawImagePath));
            }
            boolean outputPng = sourcePng && source.getColorModel().hasAlpha();
            CacheKey key = cacheKey(rawImagePath, properties, outputPng);
            Files.createDirectories(key.path().getParent());
            if (Files.isRegularFile(key.path())) {
                return ImageResult.cached(key.path(), key.contentType(), true);
            }
            tempOutput = Files.createTempFile(key.path().getParent(), "memos-image-", ".tmp");
            BufferedImage scaled = scale(source, properties.normalizedWidth());
            if ("image/png".equals(key.contentType())) {
                writePng(scaled, tempOutput);
            } else {
                int quality = Math.max(key.quality(), key.sourcePng() ? 86 : 50);
                writeJpeg(scaled, tempOutput, quality);
            }
            Files.move(tempOutput, key.path(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return ImageResult.cached(key.path(), key.contentType(), false);
        } finally {
            Files.deleteIfExists(tempOriginal);
            if (tempOutput != null) {
                Files.deleteIfExists(tempOutput);
            }
        }
    }

    private Mono<Void> fetchOriginal(String rawImagePath, ImageCacheProperties properties,
        Path destination) {
        String base = StringUtils.trimTrailingCharacter(properties.baseUrl(), '/');
        URI target = URI.create(base + "/file/" + normalizeImagePath(rawImagePath));
        WebClient.RequestHeadersSpec<?> spec = memosWebClient.get().uri(target);
        if (StringUtils.hasText(properties.accessToken())) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken());
        }
        return spec.exchangeToMono(response -> {
            HttpStatusCode status = response.statusCode();
            if (status.isError()) {
                return Mono.error(new IllegalStateException("memos file response " + status));
            }
            return DataBufferUtils.write(response.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class),
                    destination)
                .then();
        });
    }

    private ImageResult findExisting(String rawImagePath, ImageCacheProperties properties) {
        CacheKey jpg = cacheKey(rawImagePath, properties, false);
        if (Files.isRegularFile(jpg.path())) {
            return ImageResult.cached(jpg.path(), jpg.contentType(), true);
        }
        CacheKey png = cacheKey(rawImagePath, properties, true);
        if (Files.isRegularFile(png.path())) {
            return ImageResult.cached(png.path(), png.contentType(), true);
        }
        return null;
    }

    private CacheKey cacheKey(String rawImagePath, ImageCacheProperties properties,
        boolean outputPng) {
        String normalized = normalizeImagePath(rawImagePath);
        String lower = normalized.toLowerCase();
        boolean sourcePng = lower.endsWith(".png");
        String extension = outputPng ? "png" : "jpg";
        String contentType = outputPng ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
        String digest = sha256(normalized).substring(0, 16);
        String uid = uidOf(normalized);
        int width = properties.normalizedWidth();
        int quality = properties.normalizedQuality();
        Path path = cacheRoot()
            .resolve("attachments")
            .resolve(safeName(uid))
            .resolve("w" + width + "-q" + quality + "-" + digest + "." + extension);
        return new CacheKey(path, contentType, quality, sourcePng);
    }

    private BufferedImage scale(BufferedImage source, int maxWidth) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxWidth && height <= maxWidth) {
            return source;
        }
        double ratio = Math.min((double) maxWidth / width, (double) maxWidth / height);
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        int type = source.getColorModel().hasAlpha()
            ? BufferedImage.TYPE_INT_ARGB
            : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private void writeJpeg(BufferedImage source, Path path, int quality) throws IOException {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality / 100f);
        }
        try (OutputStream outputStream = Files.newOutputStream(path);
            ImageOutputStream imageOutput = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void writePng(BufferedImage source, Path path) throws IOException {
        if (!ImageIO.write(source, "png", path.toFile())) {
            throw new IOException("No PNG writer available");
        }
    }

    private boolean isAnimatedPng(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] marker = new byte[] {'a', 'c', 'T', 'L'};
        for (int i = 0; i <= bytes.length - marker.length; i++) {
            if (bytes[i] == marker[0] && bytes[i + 1] == marker[1]
                && bytes[i + 2] == marker[2] && bytes[i + 3] == marker[3]) {
                return true;
            }
        }
        return false;
    }

    private Path cacheRoot() {
        return pluginsRootGetter.get().resolve("memos").resolve("cache").resolve("images");
    }

    private long deleteChildren(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        AtomicLong deleted = new AtomicLong();
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .filter(path -> !path.equals(root))
                .forEach(path -> {
                    try {
                        if (Files.deleteIfExists(path)) {
                            deleted.incrementAndGet();
                        }
                    } catch (IOException ignored) {
                        // Best-effort clear only.
                    }
                });
        }
        return deleted.get();
    }

    private String normalizeImagePath(String path) {
        String value = path == null ? "" : path;
        if (value.startsWith(IMAGE_ROUTE_PREFIX)) {
            value = value.substring(IMAGE_ROUTE_PREFIX.length());
        }
        if (value.startsWith(ORIGINAL_ROUTE_PREFIX)) {
            value = value.substring(ORIGINAL_ROUTE_PREFIX.length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (!value.startsWith("attachments/") || value.contains("..")) {
            throw new IllegalArgumentException("Invalid memos attachment path");
        }
        return value;
    }

    private String uidOf(String normalizedPath) {
        String[] parts = normalizedPath.split("/", 3);
        return parts.length >= 2 ? parts[1] : "unknown";
    }

    private String safeName(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record CacheKey(Path path, String contentType, int quality, boolean sourcePng) {
    }

    public record ImageResult(Path path, String contentType, boolean cacheHit, String originalUrl) {
        public static ImageResult cached(Path path, String contentType, boolean cacheHit) {
            return new ImageResult(path, contentType, cacheHit, null);
        }

        public static ImageResult original(String originalUrl) {
            return new ImageResult(null, null, false, originalUrl);
        }

        public boolean useOriginal() {
            return originalUrl != null;
        }
    }

    public record WarmupItemResult(boolean created, boolean hit, boolean skipped, boolean failed) {
        public static WarmupItemResult asCreated() {
            return new WarmupItemResult(true, false, false, false);
        }

        public static WarmupItemResult asHit() {
            return new WarmupItemResult(false, true, false, false);
        }

        public static WarmupItemResult asSkipped() {
            return new WarmupItemResult(false, false, true, false);
        }

        public static WarmupItemResult asFailed() {
            return new WarmupItemResult(false, false, false, true);
        }
    }

    public record CacheStats(String root, long files, long bytes) {
    }
}
